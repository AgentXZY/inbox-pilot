package io.github.agentxzy.inboxpilot.inbox;

import io.github.agentxzy.inboxpilot.entity.Email;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Component
public class GmailEmailSource implements EmailSource {

    private final RestClient client = RestClient.create("https://gmail.googleapis.com/gmail/v1");

    @Override
    public List<Email> fetchRecentEmails(String accessToken, int maxResults) {
        List<Email> emails = new ArrayList<>();

        // Step 1: list message IDs
        JsonNode listResponse = client.get()
            .uri("/users/me/messages?maxResults=" + maxResults + "&q=newer_than:1d")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
            .retrieve()
            .body(JsonNode.class);

        if (listResponse == null || listResponse.get("messages") == null) {
            return emails; // empty inbox or no recent mail
        }

        // Step 2: fetch each message's details
        for (JsonNode msgRef : listResponse.get("messages")) {
            String id = msgRef.get("id").asString();
            JsonNode msg = client.get()
                .uri("/users/me/messages/" + id + "?format=metadata&metadataHeaders=From&metadataHeaders=Subject&metadataHeaders=Date")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .body(JsonNode.class);

            emails.add(parseMessage(msg));
        }

        return emails;
    }

    private Email parseMessage(JsonNode msg) {
        Email email = new Email();
        email.setId(msg.get("id").asString());
        email.setSnippet(msg.get("snippet") != null ? msg.get("snippet").asString() : "");
        email.setBody(email.getSnippet()); // metadata format doesn't include full body; snippet is a preview

        JsonNode headers = msg.get("payload").get("headers");
        for (JsonNode header : headers) {
            String name = header.get("name").asString();
            String value = header.get("value").asString();
            if ("From".equals(name)) email.setSender(value);
            if ("Subject".equals(name)) email.setSubject(value);
        }

        email.setReceivedAt(LocalDateTime.now()); // Gmail's internalDate needs separate parsing, placeholder for now
        return email;
    }
}