package io.github.agentxzy.inboxpilot.entity;

import lombok.Data;
import java.time.LocalDate;

@Data
public class Deadline {
    private String description;
    private LocalDate dueDate;
    private String sourceEmailId;
    private Urgency urgency;

    public enum Urgency { TODAY, TOMORROW, WITHIN_3_DAYS, NEXT_WEEK }
}