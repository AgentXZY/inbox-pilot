package io.github.agentxzy.inboxpilot.inbox;

import io.github.agentxzy.inboxpilot.entity.Email;
import java.util.List;

public interface EmailSource {
    List<Email> fetchRecentEmails(String accessToken, int maxResults);
}