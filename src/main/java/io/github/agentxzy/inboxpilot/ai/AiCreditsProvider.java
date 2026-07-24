package io.github.agentxzy.inboxpilot.ai;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

        String systemPrompt = "You are an email assistant. You will be given a numbered list of exactly " + emails.size() + " emails. " +
        	    "Return EXACTLY " + emails.size() + " entries in your response, one per email, in the same order, even if an email contains multiple sub-topics inside it — treat the whole email as ONE entry. " +
        	    "Today's date is " + LocalDate.now() + ". When an email mentions a date without a year, assume it refers to the nearest future occurrence of that date relative to today. " +
        	    "Respond ONLY with raw JSON, no markdown, matching this shape: " +
        	    "{\"emails\": [{\"sender\": string, \"subject\": string, " +
        	    "\"category\": one of [College, Work, Finance, Shopping, Social, Newsletters, Promotions, Spam, Other, Security], " +
        	    "\"importance\": one of [HIGH, MEDIUM, LOW], " +
        	    "\"summary\": a one-sentence summary of the whole email, " +
        	    "\"deadline\": a specific date/time mentioned, or null}]}. " +
        	    "Importance guide: HIGH = security codes/alerts, deadlines within 7 days, real internship/job opportunities, academic deadlines. " +
        	    "MEDIUM = registrations without urgent deadlines, informational digests worth skimming. " +
        	    "LOW = promotions, generic newsletters, entertainment content.";

        StringBuilder userPrompt = new StringBuilder("Emails:\n");
        for (Email e : emails) {
            userPrompt.append("- From: ").append(e.getSender())
                      .append(" | Subject: ").append(e.getSubject())
                      .append(" | Body: ").append(e.getBody()).append("\n\n");
        }

        Map<String, Object> requestBody = Map.of(
            "model", model,
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
        return parseDigest(rawText, emails.size());
    }

    private Digest parseDigest(String rawText, int totalEmails) {
        Digest digest = new Digest();
        digest.setDate(LocalDate.now());
        digest.setTotalEmails(totalEmails);   // always Java's own count, never trust the AI's

        try {
            String cleaned = rawText.replaceAll("```json|```", "").trim();
            JsonNode json = mapper.readTree(cleaned);
            JsonNode emailsNode = json.get("emails");

            List<EmailSummary> summaries = new ArrayList<>();
            Map<String, Integer> counts = new HashMap<>();
            List<String> actionItems = new ArrayList<>();

            for (JsonNode e : emailsNode) {
                EmailSummary es = new EmailSummary();
                es.setSender(e.get("sender").asString());
                es.setSubject(e.get("subject").asString());
                es.setCategory(e.get("category").asString());
                es.setImportance(e.get("importance").asString());
                es.setSummary(e.get("summary").asString());

                JsonNode deadlineNode = e.get("deadline");
                es.setDeadline(deadlineNode != null && !deadlineNode.isNull() ? deadlineNode.asString() : null);

                summaries.add(es);
                counts.merge(es.getCategory(), 1, Integer::sum);   // Java counts, not the AI

                if ("HIGH".equalsIgnoreCase(es.getImportance())) {
                    actionItems.add(es.getSummary());
                }
            }

            digest.setEmailSummaries(summaries);
            digest.setCategoryCounts(counts);
            digest.setActionItems(actionItems);
            List<Deadline> deadlineList = new ArrayList<>();
            for (EmailSummary es : summaries) {
                if (es.getDeadline() != null) {
                    Deadline d = new Deadline();
                    d.setDescription(es.getSubject());
                    d.setSourceEmailId(es.getSender());
                    deadlineList.add(d);
                }
            }
            digest.setDeadlines(deadlineList);  // structured deadline parsing is a later step
            digest.setSummaryText(summaries.size() + " emails processed, " + actionItems.size() + " need action.");
            if (emailsNode.size() != totalEmails) {
                // AI didn't return 1:1 — log it, don't trust this response's structure
                System.err.println("WARNING: AI returned " + emailsNode.size() + " summaries for " + totalEmails + " emails");
            }

        } catch (Exception ex) {
            digest.setCategoryCounts(Map.of("Uncategorized", totalEmails));
            digest.setActionItems(List.of());
            digest.setDeadlines(List.of());
            digest.setSummaryText("Could not parse AI response.");
        }
        return digest;
    }
}