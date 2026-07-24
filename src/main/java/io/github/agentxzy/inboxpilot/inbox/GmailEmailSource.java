package io.github.agentxzy.inboxpilot.inbox;

import io.github.agentxzy.inboxpilot.entity.Email;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class GmailEmailSource implements EmailSource {

    private final RestClient client = RestClient.create("https://gmail.googleapis.com/gmail/v1");

    @Override
    public List<Email> fetchRecentEmails(String accessToken, int maxResults) {
        List<Email> emails = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();

        JsonNode listResponse = client.get()
            .uri("/users/me/messages?maxResults=" + maxResults + "&q=newer_than:1d")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
            .retrieve()
            .body(JsonNode.class);

        if (listResponse == null || listResponse.get("messages") == null) {
            return emails;
        }

        for (JsonNode msgRef : listResponse.get("messages")) {
            String id = msgRef.get("id").asString();

            if (!seenIds.add(id)) {
                continue; // already processed this exact message ID — skip the duplicate
            }

            JsonNode msg = client.get()
                .uri("/users/me/messages/" + id + "?format=full")
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

        JsonNode payload = msg.get("payload");
        JsonNode headers = payload.get("headers");
        for (JsonNode header : headers) {
            String name = header.get("name").asString();
            String value = header.get("value").asString();
            if ("From".equals(name)) email.setSender(value);
            if ("Subject".equals(name)) email.setSubject(value);
        }

        String body = extractBody(payload);
        email.setBody(body != null && !body.isBlank() ? body : email.getSnippet());

        email.setReceivedAt(LocalDateTime.now());
        return email;
    }

    private String extractBody(JsonNode payload) {
        String mimeType = payload.get("mimeType") != null ? payload.get("mimeType").asString() : "";

        if ("text/plain".equals(mimeType) && payload.get("body") != null && payload.get("body").get("data") != null) {
            return decodeBase64Url(payload.get("body").get("data").asString());
        }

        if (payload.get("parts") != null) {
            for (JsonNode part : payload.get("parts")) {
                String result = extractBody(part);
                if (result != null && !result.isBlank()) {
                    return result;
                }
            }
        }

        return null;
    }

    private String decodeBase64Url(String data) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(data);
            return new String(decoded, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }
}