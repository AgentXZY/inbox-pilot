package io.github.agentxzy.inboxpilot.service;

import io.github.agentxzy.inboxpilot.entity.Email;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class EmailFileLoader {

    public List<Email> loadEmails() {
        List<Email> emails = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ClassPathResource("emails.txt").getInputStream(), StandardCharsets.UTF_8))) {

            Email current = null;
            StringBuilder bodyBuilder = new StringBuilder();
            boolean readingBody = false;

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().equals("---")) {
                    finishCurrent(current, bodyBuilder, emails);
                    current = null;
                    bodyBuilder = new StringBuilder();
                    readingBody = false;
                } else if (line.startsWith("FROM:")) {
                    current = new Email();
                    current.setSender(line.substring(5).trim());
                } else if (line.startsWith("SUBJECT:") && current != null) {
                    current.setSubject(line.substring(8).trim());
                } else if (line.startsWith("RECEIVED:") && current != null) {
                    current.setReceivedAt(LocalDateTime.parse(line.substring(9).trim()));
                } else if (line.startsWith("BODY:")) {
                    readingBody = true;
                } else if (readingBody && current != null) {
                    bodyBuilder.append(line).append("\n");
                }
            }
            finishCurrent(current, bodyBuilder, emails);

        } catch (Exception e) {
            throw new RuntimeException("Failed to load emails.txt", e);
        }

        return emails;
    }

    private void finishCurrent(Email current, StringBuilder bodyBuilder, List<Email> emails) {
        if (current != null) {
            current.setBody(bodyBuilder.toString().trim());
            emails.add(current);
        }
    }
}