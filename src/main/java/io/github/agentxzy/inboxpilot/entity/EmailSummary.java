package io.github.agentxzy.inboxpilot.entity;

import lombok.Data;

@Data
public class EmailSummary {
    private String sender;
    private String subject;
    private String category;
    private String importance;
    private String summary;
    private String deadline;
    private String link;
}