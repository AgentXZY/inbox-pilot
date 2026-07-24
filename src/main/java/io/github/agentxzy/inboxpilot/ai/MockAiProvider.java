package io.github.agentxzy.inboxpilot.ai;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import io.github.agentxzy.inboxpilot.entity.Digest;
import io.github.agentxzy.inboxpilot.entity.Email;

public class MockAiProvider implements AiProvider {
    @Override
    public Digest generateDigest(List<Email> emails) {
        Digest digest = new Digest();
        digest.setDate(LocalDate.now());
        digest.setTotalEmails(emails.size());
        digest.setCategoryCounts(Map.of(
            "College", 3,
            "Work", 2,
            "Promotions", 1
        ));
        digest.setActionItems(List.of("Reply to HR", "Submit Assignment"));
        digest.setDeadlines(List.of());
        digest.setSummaryText("You received " + emails.size() + " emails. 2 need action today.");
        return digest;
    }
}