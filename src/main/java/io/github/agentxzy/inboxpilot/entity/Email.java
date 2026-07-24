package io.github.agentxzy.inboxpilot.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Email {
    private String id;
    private String sender;
    private String senderName;
    private String subject;
    private String snippet;
    private String body;
    private LocalDateTime receivedAt;
    private boolean read;
}