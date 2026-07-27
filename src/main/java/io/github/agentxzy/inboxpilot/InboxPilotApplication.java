package io.github.agentxzy.inboxpilot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class InboxPilotApplication {

    public static void main(String[] args) {
        SpringApplication.run(InboxPilotApplication.class, args);
    }
}