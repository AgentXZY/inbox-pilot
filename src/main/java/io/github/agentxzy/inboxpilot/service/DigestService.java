package io.github.agentxzy.inboxpilot.service;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import io.github.agentxzy.inboxpilot.ai.AiProvider;
import io.github.agentxzy.inboxpilot.entity.Digest;
import io.github.agentxzy.inboxpilot.entity.Email;

@Service
public class DigestService {

    @Autowired
    private AiProvider aiProvider;

    @Autowired
    private EmailFileLoader emailFileLoader;

    public Digest getTodaysDigest() {
        List<Email> emails = emailFileLoader.loadEmails();
        return aiProvider.generateDigest(emails);
    }
}