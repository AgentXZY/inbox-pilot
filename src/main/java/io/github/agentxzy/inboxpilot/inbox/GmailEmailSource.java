package io.github.agentxzy.inboxpilot.inbox;

import io.github.agentxzy.inboxpilot.entity.Email;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
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

        String userEmail = fetchAuthenticatedEmail(accessToken);

        JsonNode listResponse = client.get()
            .uri("/users/me/messages?maxResults=" + maxResults + "&q=newer_than:2d")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
            .retrieve()
            .body(JsonNode.class);

        if (listResponse == null || listResponse.get("messages") == null) {
            return emails;
        }

        for (JsonNode msgRef : listResponse.get("messages")) {
            String id = msgRef.get("id").asString();
            if (!seenIds.add(id)) continue;

            JsonNode msg = client.get()
                .uri("/users/me/messages/" + id + "?format=full")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .body(JsonNode.class);

            emails.add(parseMessage(msg, accessToken, userEmail));
        }

        return emails;
    }

    private String fetchAuthenticatedEmail(String accessToken) {
        try {
            JsonNode profile = client.get()
                .uri("/users/me/profile")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .body(JsonNode.class);
            return profile.get("emailAddress").asString();
        } catch (Exception e) {
            return null;
        }
    }

    private Email parseMessage(JsonNode msg, String accessToken, String userEmail) {
        Email email = new Email();
        String id = msg.get("id").asString();
        email.setId(id);
        email.setSnippet(msg.get("snippet") != null ? msg.get("snippet").asString() : "");

        String link = userEmail != null
            ? "https://mail.google.com/mail/?authuser=" + userEmail + "#all/" + id
            : "https://mail.google.com/mail/u/0/#all/" + id;
        email.setGmailLink(link);

        JsonNode payload = msg.get("payload");
        JsonNode headers = payload.get("headers");
        for (JsonNode header : headers) {
            String name = header.get("name").asString();
            String value = header.get("value").asString();
            if ("From".equals(name)) email.setSender(value);
            if ("Subject".equals(name)) email.setSubject(value);
        }

        String bodyText = extractBody(payload);
        String attachmentText = extractAttachmentText(id, payload, accessToken);

        StringBuilder combined = new StringBuilder();
        combined.append(bodyText != null && !bodyText.isBlank() ? bodyText : email.getSnippet());
        if (!attachmentText.isBlank()) {
            combined.append("\n\n--- ATTACHMENT CONTENT ---\n").append(attachmentText);
        }

        email.setBody(combined.toString());
        if (msg.get("internalDate") != null) {
            long epochMillis = Long.parseLong(msg.get("internalDate").asString());
            email.setReceivedAt(LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(epochMillis), java.time.ZoneId.systemDefault()));
        }
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
                if (result != null && !result.isBlank()) return result;
            }
        }
        return null;
    }

    private String extractAttachmentText(String messageId, JsonNode part, String accessToken) {
        StringBuilder combined = new StringBuilder();
        collectPdfAttachments(messageId, part, accessToken, combined);
        return combined.toString();
    }

    private void collectPdfAttachments(String messageId, JsonNode part, String accessToken, StringBuilder combined) {
        String filename = part.get("filename") != null ? part.get("filename").asString() : "";
        String mimeType = part.get("mimeType") != null ? part.get("mimeType").asString() : "";
        boolean isPdf = mimeType.equals("application/pdf") || filename.toLowerCase().endsWith(".pdf");

        if (isPdf && part.get("body") != null && part.get("body").get("attachmentId") != null) {
            String attachmentId = part.get("body").get("attachmentId").asString();
            String text = fetchAndExtractPdfText(messageId, attachmentId, accessToken);
            if (text != null && !text.isBlank()) {
                combined.append("\n[Attachment: ").append(filename).append("]\n").append(text).append("\n");
            }
        }

        if (part.get("parts") != null) {
            for (JsonNode child : part.get("parts")) {
                collectPdfAttachments(messageId, child, accessToken, combined);
            }
        }
    }

    private String fetchAndExtractPdfText(String messageId, String attachmentId, String accessToken) {
        try {
            JsonNode attachmentResponse = client.get()
                .uri("/users/me/messages/" + messageId + "/attachments/" + attachmentId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .body(JsonNode.class);

            byte[] pdfBytes = Base64.getUrlDecoder().decode(attachmentResponse.get("data").asString());

            try (PDDocument document = Loader.loadPDF(pdfBytes)) {
                int totalPages = document.getNumberOfPages();
                int pagesToRead = Math.min(totalPages, 5); // jist extraction — first 5 pages only

                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(1);
                stripper.setEndPage(pagesToRead);
                String text = stripper.getText(document);

                if (text == null || text.isBlank()) {
                    // Empty extraction almost always means scanned/image-based PDF — needs OCR (not yet implemented)
                    return "[Scanned document detected — text extraction unavailable, OCR not yet implemented]";
                }
                return text.length() > 3000 ? text.substring(0, 3000) + "... [truncated]" : text;
            }
        } catch (Exception e) {
            return null; // corrupted/unreadable attachment — fail silently, don't break the whole digest
        }
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