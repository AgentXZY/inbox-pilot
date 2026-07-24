package io.github.agentxzy.inboxpilot.entity;

import lombok.Data;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
public class Digest {
    private LocalDate date;
    private int totalEmails;
    private Map<String, Integer> categoryCounts;
    private List<String> actionItems;
    private List<Deadline> deadlines;
    private String summaryText;
    private List<EmailSummary> emailSummaries;
}