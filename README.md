# Inbox Pilot

**An open-source AI-powered inbox assistant that transforms hundreds of emails into one intelligent daily briefing.**

> People don't need another email client — they need an assistant that tells them what actually matters.

[![Java](https://img.shields.io/badge/Java-21%2B-orange)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-brightgreen)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/license-MIT-blue)](LICENSE)
[![Status](https://img.shields.io/badge/status-early--MVP-yellow)]()

---

## Overview

A typical inbox receives anywhere from 50 to 1000+ emails a day — assignment notices, internship alerts, bills, newsletters, spam. Important things get buried. Deadlines get missed.

**Inbox Pilot** connects to your Gmail inbox and uses an LLM to read, understand, and condense your recent emails into a single structured briefing:

- 🗂️ **Categorizes** every email (College, Work, Finance, Shopping, Social, Newsletters, Promotions, Security, Other)
- ⚡ **Ranks importance** (HIGH / MEDIUM / LOW) based on urgency and relevance
- 📅 **Extracts deadlines** mentioned in the email body
- ✍️ **Summarizes** each email in one sentence
- ✅ **Surfaces action items** that actually need a response

Instead of reading 800 emails, you read one page.

---

## Status

This is an early, working MVP — not production-ready. It has been tested end-to-end against a real Gmail inbox and correctly identifies live deadlines and priorities. See [Known Limitations](#known-limitations) before relying on it.

---

## Architecture

```
User (OAuth2 login)
        │
        ▼
Spring Security OAuth2 Client
        │
        ▼
GmailEmailSource ──────► Gmail API (full message fetch + MIME parsing)
        │
        ▼
AiProvider (pluggable) ─► LLM chat completion
        │
        ▼
Digest (categorized, prioritized, deadline-tagged)
        │
        ▼
JSON response
```

### Design principles

- **Provider-agnostic by design.** `AiProvider` and `EmailSource` are interfaces, not concrete dependencies. Swapping the LLM backend (OpenAI, Gemini, Ollama, OpenRouter, Groq) or the email source (Outlook, IMAP) doesn't require touching the rest of the app.
- **No hardcoded AI vendor.** Bring your own API key. This keeps the project genuinely open source — no secrets baked in, no vendor lock-in.
- **Deterministic aggregation.** Category counts and totals are computed in Java from the AI's per-email tags, never trusted as raw AI-reported numbers. LLMs are unreliable at arithmetic; counting is not.
- **Privacy-minded.** No email content is intended to be persisted long-term — fetch, analyze, generate digest, discard.

---

## Setup

### Prerequisites

- Java 21+ (developed and tested against JDK 25, compiled with `java.version=24`)
- Maven (wrapper included — no separate install needed)
- A Google Cloud project with the Gmail API enabled
- An API key from any OpenAI-compatible LLM provider (OpenRouter, OpenAI, Groq, etc.)

### 1. Google OAuth setup

1. Create a project at [console.cloud.google.com](https://console.cloud.google.com)
2. Enable the **Gmail API** under APIs & Services → Library
3. Configure the **OAuth consent screen**:
   - User type: External
   - Scope: `.../auth/gmail.readonly`
   - Add yourself as a test user (required while the app is unverified)
4. Create an **OAuth Client ID** (Web application) with this exact redirect URI:
   ```
   http://localhost:8080/login/oauth2/code/google
   ```

### 2. Configure secrets

Copy the example file and fill in your real values — this file is gitignored and should never be committed:

```bash
cp src/main/resources/application-secret.properties.example src/main/resources/application-secret.properties
```

```properties
spring.security.oauth2.client.registration.google.client-id=YOUR_CLIENT_ID
spring.security.oauth2.client.registration.google.client-secret=YOUR_CLIENT_SECRET
spring.security.oauth2.client.registration.google.scope=openid,profile,email,https://www.googleapis.com/auth/gmail.readonly

ai.api-key=YOUR_LLM_API_KEY
```

### 3. Run

```bash
# Windows
mvnw.cmd spring-boot:run

# macOS / Linux
./mvnw spring-boot:run
```

### 4. Try it

| Endpoint | Description | Auth required |
|---|---|---|
| `GET /api/digest` | Digest generated from local test data (`src/main/resources/emails.txt`) | No |
| `GET /api/digest/gmail` | Digest generated from your real, live Gmail inbox | Yes (Google login) |

---

## Example output

```json
{
  "totalEmails": 10,
  "categoryCounts": { "Work": 6, "College": 2, "Promotions": 1, "Security": 1 },
  "actionItems": [
    "Round 3 applications for the PGP in Applied AI & Agentic Systems close in 2 days.",
    "22 new remote backend developer roles now available."
  ],
  "deadlines": [
    { "description": "2 Days left | This Decision Won't Wait", "sourceEmailId": "Masters' Union" }
  ]
}
```

---

## Known Limitations

- **Attachments are not parsed.** Emails that say "see attached PDF" won't surface the actual content inside — this is the next major planned feature.
- **Deadline parsing is loose.** Dates are extracted as free text by the LLM, not validated by a structured date parser. Ambiguous dates depend on the model correctly anchoring to the current date.
- **Only Gmail is supported today.** `EmailSource` is designed to be provider-agnostic, but Outlook and IMAP implementations don't exist yet.
- **No persistence or scheduling.** Every request re-fetches and re-analyzes live. No caching, no scheduled digests, no push notifications yet.
- **Single AI call per digest.** Very large inboxes may hit context/token limits — no batching implemented yet.

---

## Roadmap

- [ ] PDF and attachment parsing (extract text from linked documents)
- [ ] Structured deadline dates instead of free text
- [ ] Outlook / IMAP support via `EmailSource`
- [ ] Scheduled digest generation + push notification for critical emails
- [ ] Simple web dashboard
- [ ] Batching for large inboxes

---

## Tech Stack

- **Backend:** Java 21+, Spring Boot 4.1
- **Auth:** Spring Security OAuth2 Client (Gmail)
- **AI:** Any OpenAI-compatible chat completions API (provider-agnostic via `AiProvider`)
- **Build:** Maven

---

## Contributing

This is an early-stage open-source project — issues and pull requests are welcome. The [Roadmap](#roadmap) above is a good place to start if you're looking for where to help.

---

## License

MIT — see [LICENSE](LICENSE) for details.
