package io.github.agentxzy.inboxpilot.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data
public class ProcessedEmail {
    @Id
    private String gmailMessageId;
    private LocalDateTime processedAt;
}