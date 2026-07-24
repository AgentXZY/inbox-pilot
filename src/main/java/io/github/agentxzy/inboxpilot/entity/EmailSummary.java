package io.github.agentxzy.inboxpilot.entity;

import lombok.Data;

@Data
public class EmailSummary {
    private String sender;
    private String subject;
    private String category;
    private String importance;   // HIGH, MEDIUM, LOW
    private String summary;
    private String deadline;     // free text like "Sep 1, 2026" or null
}