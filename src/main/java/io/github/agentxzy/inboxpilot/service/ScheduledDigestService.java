package io.github.agentxzy.inboxpilot.service;

import io.github.agentxzy.inboxpilot.ai.AiProvider;
import io.github.agentxzy.inboxpilot.entity.Email;
import io.github.agentxzy.inboxpilot.entity.ProcessedEmail;
import io.github.agentxzy.inboxpilot.inbox.EmailSource;
import io.github.agentxzy.inboxpilot.repository.ProcessedEmailRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ScheduledDigestService {

    @Autowired
    private EmailSource emailSource;

    @Autowired
    private AiProvider aiProvider;

    @Autowired
    private ProcessedEmailRepository processedEmailRepository;

    @Autowired
    private OAuth2AuthorizedClientManager authorizedClientManager;

    @Autowired
    private OAuth2AuthorizedClientService authorizedClientService;

    // set once, right after the user logs in the first time — see controller change below
    private static String currentUserPrincipalName;

    public static void registerLoggedInUser(String principalName) {
        currentUserPrincipalName = principalName;
    }

    @Scheduled(fixedRate = 60000) // every 15 minutes
    public void pollAndDigest() {
        if (currentUserPrincipalName == null) {
            System.out.println("Poll skipped — no user has logged in yet this session.");
            return;
        }

        String accessToken = getFreshAccessToken();
        if (accessToken == null) {
            System.out.println("Poll skipped — could not obtain a valid access token.");
            return;
        }

        List<Email> recentEmails = emailSource.fetchRecentEmails(accessToken, 20);

        List<Email> newEmails = recentEmails.stream()
            .filter(e -> !processedEmailRepository.existsById(e.getId()))
            .collect(Collectors.toList());

        if (newEmails.isEmpty()) {
            System.out.println("Poll ran — no new emails.");
            return;
        }

        System.out.println("Poll found " + newEmails.size() + " new emails — sending to AI.");
        var digest = aiProvider.generateDigest(newEmails);
        System.out.println("Digest summary: " + digest.getSummaryText());
        // TODO: persist digest somewhere retrievable (a Digest history table) — next step after this works

        for (Email e : newEmails) {
            ProcessedEmail pe = new ProcessedEmail();
            pe.setGmailMessageId(e.getId());
            pe.setProcessedAt(LocalDateTime.now());
            processedEmailRepository.save(pe);
        }
    }

    private String getFreshAccessToken() {
        try {
            OAuth2AuthorizedClient client = authorizedClientService
                .loadAuthorizedClient("google", currentUserPrincipalName);

            if (client == null) return null;

            OAuth2AuthorizeRequest request = OAuth2AuthorizeRequest
                .withAuthorizedClient(client)
                .principal(currentUserPrincipalName)
                .build();

            OAuth2AuthorizedClient refreshedClient = authorizedClientManager.authorize(request);
            return refreshedClient != null ? refreshedClient.getAccessToken().getTokenValue() : null;

        } catch (OAuth2AuthorizationException e) {
            System.err.println("Token refresh failed: " + e.getMessage());
            return null;
        }
    }
}