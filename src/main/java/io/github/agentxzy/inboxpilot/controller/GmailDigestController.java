package io.github.agentxzy.inboxpilot.controller;

import io.github.agentxzy.inboxpilot.ai.AiProvider;
import io.github.agentxzy.inboxpilot.entity.Digest;
import io.github.agentxzy.inboxpilot.entity.Email;
import io.github.agentxzy.inboxpilot.inbox.EmailSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class GmailDigestController {

    @Autowired
    private EmailSource emailSource;

    @Autowired
    private AiProvider aiProvider;

    @GetMapping("/api/digest/gmail")
    public Digest getGmailDigest(@RegisteredOAuth2AuthorizedClient("google") OAuth2AuthorizedClient client) {
        String accessToken = client.getAccessToken().getTokenValue();
        List<Email> emails = emailSource.fetchRecentEmails(accessToken, 10);
        return aiProvider.generateDigest(emails);
    }
}