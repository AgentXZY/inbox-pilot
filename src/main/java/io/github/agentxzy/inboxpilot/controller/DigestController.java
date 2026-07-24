package io.github.agentxzy.inboxpilot.controller;

import io.github.agentxzy.inboxpilot.entity.Digest;
import io.github.agentxzy.inboxpilot.service.DigestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DigestController {

    @Autowired
    private DigestService digestService;

    @GetMapping("/api/digest")
    public Digest getDigest() {
        return digestService.getTodaysDigest();
    }
}