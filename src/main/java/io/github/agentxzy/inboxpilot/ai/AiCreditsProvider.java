package io.github.agentxzy.inboxpilot.ai;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
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
        	    "\"Importance guide — apply strictly: \" +\r\n"
        	    + "\"HIGH = time-sensitive with a concrete deadline/event within 48 hours, security alerts, real job/internship offers requiring action. \" +\r\n"
        	    + "\"MEDIUM = worth knowing, no urgent action needed, deadline more than 48 hours away. \" +\r\n"
        	    + "\"LOW = newsletters, generic promotions, no actionable content. \" +\r\n"
        	    + "\"When uncertain between two levels, choose the LOWER one."+
        	    "When a deadline exists, format it strictly as ISO-8601: YYYY-MM-DDTHH:MM:SS (24-hour time), or YYYY-MM-DD if no time is mentioned. " +
        	    "If you cannot determine a real deadline, use null — never guess a format.";

        StringBuilder userPrompt = new StringBuilder("Emails:\n");
        for (Email e : emails) {
            userPrompt.append("- From: ").append(e.getSender())
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

            int index = 0;
            for (JsonNode e : emailsNode) {
                EmailSummary es = new EmailSummary();
                es.setSender(e.get("sender").asString());
                es.setSubject(e.get("subject").asString());
                es.setCategory(e.get("category").asString());
                es.setImportance(e.get("importance").asString());
                es.setSummary(e.get("summary").asString());

                JsonNode deadlineNode = e.get("deadline");
                es.setDeadline(deadlineNode != null && !deadlineNode.isNull() ? deadlineNode.asString() : null);

                // match by sender+subject instead of positional index — much harder to silently misalign
                String matchedLink = originalEmails.stream()
                    .filter(orig -> orig.getSender() != null && orig.getSender().equals(es.getSender())
                                  && orig.getSubject() != null && orig.getSubject().equals(es.getSubject()))
                    .map(Email::getGmailLink)
                    .findFirst()
                    .orElse(null);
                es.setLink(matchedLink);

                summaries.add(es);
                counts.merge(es.getCategory(), 1, Integer::sum);

                if ("HIGH".equalsIgnoreCase(es.getImportance())) {
                    actionItems.add(es.getSummary());
                }
                if (es.getDeadline() != null) {
                    boolean isPast = isDeadlineInPast(es.getDeadline());
                    if (!isPast) {
                        Deadline d = new Deadline();
                        d.setDescription(es.getSubject());
                        d.setSourceEmailId(es.getSender());
                        deadlineList.add(d);
                    }
                index++;
            }
         }
            
            summaries.sort((a, b) -> {
                Map<String, Integer> rank = Map.of("HIGH", 0, "MEDIUM", 1, "LOW", 2);
                return rank.getOrDefault(a.getImportance(), 3) - rank.getOrDefault(b.getImportance(), 3);
            });
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
                LocalDateTime dt = LocalDateTime.parse(deadlineStr);
                return dt.isBefore(LocalDateTime.now());
            } catch (DateTimeParseException e1) {
                try {
                    LocalDate d = LocalDate.parse(deadlineStr);
                    return d.isBefore(LocalDate.now());
                } catch (DateTimeParseException e2) {
                    return false; // unparseable — don't silently drop it, better to show than hide incorrectly
                }
            }
        }
        
}