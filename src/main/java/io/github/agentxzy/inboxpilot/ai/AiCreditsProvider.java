package io.github.agentxzy.inboxpilot.ai;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import io.github.agentxzy.inboxpilot.entity.Deadline;
import io.github.agentxzy.inboxpilot.entity.Digest;
import io.github.agentxzy.inboxpilot.entity.Email;
import io.github.agentxzy.inboxpilot.entity.EmailSummary;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class AiCreditsProvider implements AiProvider {

    @Value("${ai.base-url}")
    private String baseUrl;

    @Value("${ai.api-key}")
    private String apiKey;

    @Value("${ai.model}")
    private String model;

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public Digest generateDigest(List<Email> emails) {
        RestClient client = RestClient.create(baseUrl);

        String systemPrompt = "You are an email assistant. You will be given a numbered list of exactly " + emails.size() +
            " emails, each starting with [EMAIL_ID:n]. For EACH email, return exactly ONE entry in your response. " +
            "You MUST include the matching \"emailId\": n field. Never skip, merge, or reorder emails, and never " +
            "return the same emailId twice. " +
            "Respond ONLY with raw JSON, no markdown: " +
            "{\"emails\": [{\"emailId\": integer, " +
            "\"category\": one of [College, Work, Finance, Shopping, Social, Newsletters, Promotions, Spam, Other, Security], " +
            "\"importance\": one of [HIGH, MEDIUM, LOW], \"summary\": string, \"deadline\": string in ISO-8601 (YYYY-MM-DDTHH:MM:SS) or null}]}. " +
            "Today's date is " + LocalDate.now() + ". Anchor relative dates ('tomorrow', 'today') to this date. " +
            "Importance guide — apply strictly: HIGH = concrete deadline/event within 48 hours, security alerts/OTPs, " +
            "real job/internship offers requiring action. MEDIUM = worth knowing, no urgent action, deadline more than 48 hours away. " +
            "LOW = newsletters, generic promotions, no actionable content.";

        StringBuilder userPrompt = new StringBuilder("Emails:\n");
        for (int i = 0; i < emails.size(); i++) {
            Email e = emails.get(i);
            userPrompt.append("[EMAIL_ID:").append(i).append("] From: ").append(e.getSender())
                      .append(" | Subject: ").append(e.getSubject())
                      .append(" | Body: ").append(e.getBody()).append("\n\n");
        }

        Map<String, Object> requestBody = Map.of(
            "model", model,
            "temperature", 0,
            "messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt.toString())
            )
        );

        JsonNode response = client.post()
            .uri("/chat/completions")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
            .body(requestBody)
            .retrieve()
            .body(JsonNode.class);

        String rawText = response.get("choices").get(0).get("message").get("content").asString();
        return parseDigest(rawText, emails);
    }

    private Digest parseDigest(String rawText, List<Email> originalEmails) {
        Digest digest = new Digest();
        digest.setDate(LocalDate.now());
        digest.setTotalEmails(originalEmails.size());

        try {
            String cleaned = rawText.replaceAll("```json|```", "").trim();
            JsonNode json = mapper.readTree(cleaned);
            JsonNode emailsNode = json.get("emails");

            List<EmailSummary> summaries = new ArrayList<>();
            Map<String, Integer> counts = new HashMap<>();
            List<String> actionItems = new ArrayList<>();
            List<Deadline> deadlineList = new ArrayList<>();
            Set<Integer> seenEmailIds = new HashSet<>();

            for (JsonNode e : emailsNode) {
                if (e.get("emailId") == null) continue;
                int emailId = e.get("emailId").asInt();
                if (emailId < 0 || emailId >= originalEmails.size()) continue;
                if (!seenEmailIds.add(emailId)) continue; // hard block on duplicate emailId

                Email original = originalEmails.get(emailId);

                EmailSummary es = new EmailSummary();
                es.setSender(original.getSender());
                es.setSubject(original.getSubject());
                es.setLink(original.getGmailLink());
                es.setCategory(e.get("category") != null ? e.get("category").asString() : "Other");
                es.setImportance(e.get("importance") != null ? e.get("importance").asString() : "LOW");
                es.setSummary(e.get("summary") != null ? e.get("summary").asString() : "");

                JsonNode deadlineNode = e.get("deadline");
                es.setDeadline(deadlineNode != null && !deadlineNode.isNull() ? deadlineNode.asString() : null);

                summaries.add(es);
                counts.merge(es.getCategory(), 1, Integer::sum);

                if ("HIGH".equalsIgnoreCase(es.getImportance())) {
                    actionItems.add(es.getSummary());
                }
                if (es.getDeadline() != null && !isDeadlineInPast(es.getDeadline())) {
                    Deadline d = new Deadline();
                    d.setDescription(es.getSubject());
                    d.setSourceEmailId(es.getSender());
                    deadlineList.add(d);
                }
            }

            digest.setEmailSummaries(summaries);
            digest.setCategoryCounts(counts);
            digest.setActionItems(actionItems);
            digest.setDeadlines(deadlineList);
            digest.setSummaryText(summaries.size() + " emails processed, " + actionItems.size() + " need action.");

        } catch (Exception ex) {
            digest.setCategoryCounts(Map.of("Uncategorized", originalEmails.size()));
            digest.setActionItems(List.of());
            digest.setDeadlines(List.of());
            digest.setSummaryText("Could not parse AI response.");
        }
        return digest;
    }

    private boolean isDeadlineInPast(String deadlineStr) {
        try {
            return LocalDateTime.parse(deadlineStr).isBefore(LocalDateTime.now());
        } catch (Exception e1) {
            try {
                return java.time.LocalDate.parse(deadlineStr).isBefore(LocalDate.now());
            } catch (Exception e2) {
                return false;
            }
        }
    }
}