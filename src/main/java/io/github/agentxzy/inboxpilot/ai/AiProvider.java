package io.github.agentxzy.inboxpilot.ai;

import io.github.agentxzy.inboxpilot.entity.Email;
import io.github.agentxzy.inboxpilot.entity.Digest;
import java.util.List;

public interface AiProvider {
    Digest generateDigest(List<Email> emails);
}