package io.github.agentxzy.inboxpilot.repository;

import io.github.agentxzy.inboxpilot.entity.ProcessedEmail;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEmailRepository extends JpaRepository<ProcessedEmail, String> {
}