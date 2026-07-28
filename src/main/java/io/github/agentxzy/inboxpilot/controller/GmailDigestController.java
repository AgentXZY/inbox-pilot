package io.github.agentxzy.inboxpilot.controller;

import io.github.agentxzy.inboxpilot.ai.AiProvider;
import io.github.agentxzy.inboxpilot.entity.Digest;
import io.github.agentxzy.inboxpilot.entity.Email;
import io.github.agentxzy.inboxpilot.entity.ProcessedEmail;
import io.github.agentxzy.inboxpilot.inbox.EmailSource;
import io.github.agentxzy.inboxpilot.repository.ProcessedEmailRepository;
import io.github.agentxzy.inboxpilot.service.ScheduledDigestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
public class GmailDigestController {

    @Autowired
    private EmailSource emailSource;

    @Autowired
    private AiProvider aiProvider;

    @Autowired
    private ProcessedEmailRepository processedEmailRepository;

    @GetMapping("/api/digest/gmail")
    public Digest getGmailDigest(
    		@RegisteredOAuth2AuthorizedClient("google") OAuth2AuthorizedClient client,
            @AuthenticationPrincipal OAuth2User principal,
            @RequestParam(defaultValue = "false") boolean includeSeen) {

        System.out.println(">>> MANUAL REQUEST HIT: /api/digest/gmail?includeSeen=" + includeSeen);
        ScheduledDigestService.registerLoggedInUser(principal.getName());

        String accessToken = client.getAccessToken().getTokenValue();
        List<Email> allEmails = emailSource.fetchRecentEmails(accessToken, 10);

        List<Email> emailsToProcess = includeSeen
            ? allEmails
            : allEmails.stream()
                .filter(e -> !processedEmailRepository.existsById(e.getId()))
                .collect(Collectors.toList());

        if (emailsToProcess.isEmpty()) {
            Digest empty = new Digest();
            empty.setTotalEmails(0);
            empty.setSummaryText("No new emails since last check.");
            return empty;
        }

        Digest digest = aiProvider.generateDigest(emailsToProcess);

        for (Email e : emailsToProcess) {
            ProcessedEmail pe = new ProcessedEmail();
            pe.setGmailMessageId(e.getId());
            pe.setProcessedAt(LocalDateTime.now());
            processedEmailRepository.save(pe);
        }

        return digest;
    }
}