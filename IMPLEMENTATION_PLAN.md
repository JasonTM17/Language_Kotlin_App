# IMPLEMENTATION_PLAN.md — LinguaAI

**AI-Powered Language Learning Platform** — Android (Kotlin, Compose) + Ktor Backend + MySQL + AI Gateway

> Tagline: _Learn smarter with your personal AI language tutor._

---

## 1. Outcome & Success Signal

Build a complete, demo-ready capstone project ("đồ án") showing a production-minded software
system, not a UI demo:

| Layer        | Technology                                                              |
| ------------ | ----------------------------------------------------------------------- |
| Mobile       | Kotlin, Jetpack Compose, Material 3, MVVM + Clean Architecture, Hilt     |
| Local data   | Room (offline cache, pending sync), DataStore (settings, session tokens) |
| Backend      | Kotlin + Ktor 3, JWT auth, Flyway migrations, Exposed ORM                |
| Database     | MySQL 8 (Docker), H2 (tests)                                             |
| AI           | Server-side AI gateway, provider abstraction (OpenAI-compatible / Mock)  |
| Offline      | Offline-first repositories, WorkManager sync with idempotency            |
| Quality      | JUnit tests, detekt, ktlint, Compose UI tests, GitHub Actions CI         |
| Delivery     | Docker Compose, Conventional Commits, Mermaid docs, MIT license          |

**Success signal (Golden Demo Flow works end-to-end):**
Register → Onboarding (Japanese N3) → Home → Vocabulary flashcards → Quiz → Ask AI Tutor
(context-aware, knows current lesson + weak topics) → Conversation practice → Progress updated.

## 2. Scope

**In scope (P0–P4):** auth (JWT + refresh), onboarding, home dashboard, learning catalog
(lessons/vocabulary/grammar/quiz), flashcards + pluggable SRS, AI Tutor (chat, grammar explain,
sentence correction, conversation practice, quiz generation, memory summarization), progress +
streaks, reminders, offline cache + sync, profile/settings, Docker, CI, tests, docs.

**Non-goals (P5, documented as roadmap only):** RAG, speech-to-text/TTS, pronunciation scoring,
teacher dashboard, leaderboards, push (FCM), observability stack (Prometheus/Grafana).

**Authority:** user-approved master prompt; implementation follows this plan; autonomous
execution until terminal validation, stopping only at blockers.

## 3. Repository Layout

```
Language_Kotlin_App/
├── android/                  # Gradle project (Android app, single module + layered packages)
│   └── app/                  # com.linguaai.app — data/domain/di/ui/work/util layers
├── server/                   # Gradle project (Ktor backend)
│   ├── src/main/kotlin/com/linguaai/server/
│   └── src/main/resources/db/migration/   # Flyway V1 schema, V2 seed
├── docs/                     # architecture, api, database (ERD), diagrams, screenshots
├── scripts/                  # dev helper scripts
├── docker-compose.yml        # mysql + backend, healthchecks
├── .env.example
├── IMPLEMENTATION_PLAN.md    # this file
└── README.md
```

Two independent Gradle builds (`android/`, `server/`) — CI targets each directory; this avoids
AGP/JVM plugin coupling in one build.

## 4. Phases, Commits & Acceptance

Commit messages follow Conventional Commits. Every phase: build → targeted tests → commit.

| Phase | Goal | Commits |
| ----- | ---- | ------- |
| A | Bootstrap repo, git, docs skeleton | 01 |
| B | Android Gradle + Compose + deps (version catalog, wrapper) | 02 |
| C | Design system + theme (M3, light/dark, components) | 03 |
| D | Navigation structure (nav graph, bottom bar, top-level screens) | 04 |
| E | Auth presentation (login/register UI + validation + VM) | 05 |
| F | Ktor backend service (config, plugins, health, errors) | 06 |
| G | MySQL persistence (Flyway schema+seed, Hikari, content repos) | 07 |
| H | JWT auth backend (+ integration tests) | 08 |
| I | Android ↔ backend auth (API, DTOs, repo, SessionManager) | 09 |
| J | Secure session + token refresh flow (interceptor/authenticator) | 10 |
| K | Onboarding flow (language/level/goal/daily) | 11 |
| L | Home dashboard (progress, streak, continue, reviews due) | 12 |
| M | Room database + offline cache | 13 |
| N | Lesson catalog | 14 |
| O | Vocabulary module (search/filter/favorite/detail) | 15 |
| P | Flashcards + SRS ReviewScheduler | 16 |
| Q | Grammar module | 17 |
| R | Quiz engine + attempt tracking | 18 |
| S | AI provider abstraction + Mock (server) | 19 |
| T | AI gateway on backend (rate limit, timeout/retry, secret isolation) | 20 |
| U | AI Tutor chat UI (bubbles, retry, offline notice, history) | 21 |
| V | Personalized context builder (profile/lesson/mistakes → prompt) | 22 |
| W | Grammar explanation + sentence correction | 23 |
| X | Conversation practice (scenarios + scoring) | 24 |
| Y | AI-generated quizzes (structured output + validation) | 25 |
| Z | Conversation persistence (server + Room cache) | 26 |
| AA | Conversation memory summarization | 27 |
| AB | Progress analytics screen | 28 |
| AC | Streak calculation (meaningful events only) | 29 |
| AD | Study reminders (WorkManager + channel + settings) | 30 |
| AE | Offline-first repositories + network states | 31 |
| AF | Background sync (SyncWorker, idempotency, retry/backoff) | 32 |
| AG | Profile & settings | 33 |
| AH | Tests: domain/VM (android), repos, backend auth/AI, Compose UI | 34–38 |
| AI | Docker (backend + MySQL, healthchecks) | 39 |
| AJ | GitHub Actions CI (android-ci, backend-ci) + quality tools (detekt/ktlint) | 40–41 |
| AK | Docs: architecture+ADR, ERD+API, guides, final README/demo guide | 42–45 |

## 5. Key Architecture Decisions (draft — Advisor/Kongming to challenge)

1. **AI key never on device.** Android talks only to our Ktor backend; backend owns
   `AI_API_KEY` via env. `AiProvider` interface: `OpenAiCompatibleProvider` (OpenAI/DeepSeek/
   any compatible base URL) + `MockAiProvider` (deterministic, test/demo).
2. **Server-side system prompts.** Client sends only user text + mode + context refs; the
   backend builds the full prompt (injection defense, §38).
3. **AI memory = recent N messages + rolling summary + user learning profile + lesson context**
   (token cost bounded, context preserved).
4. **Rate limiting** in-process per user (`AI_RATE_LIMIT_PER_MINUTE`, default 20) → HTTP 429;
   documented Redis upgrade path.
5. **Idempotent progress sync**: `client_operation_id` unique key; duplicate retries return the
   existing row (WorkManager backoff never duplicates).
6. **SRS behind `ReviewScheduler` interface** (SM-2-lite default) — swappable algorithm.
7. **Offline-first read path**: Room is source of truth for UI; remote refreshes it. AI requires
   connectivity (explicit friendly banner, no crash).
8. **Error model**: consistent `{error:{code,message,requestId}}`; domain error mapping on device;
   all UI states: Loading/Success/Empty/Error/Offline.
9. **Single Android module with strict package layering** (documented ADR: multi-module later;
   correctness and CI buildability first).
10. **Secrets**: `.env` gitignored, `.env.example` committed; no tokens/passwords/AI keys in logs.

## 6. Risks & Mitigations

| Risk | Mitigation |
| ---- | ---------- |
| JDK 24/26 vs Android Gradle Plugin compatibility | Run Gradle with JDK 24; pin AGP 8.7.3 + Gradle 8.14; CI uses Temurin 21; fall back to JDK via `org.gradle.java.home` |
| Version resolution failures (KSP/Ktor/Exposed pins) | Known-good matrix; verify with real build in Phase B/F before mass implementation |
| Android build impossible locally | SDK exists (platforms 33–37); CI is the authoritative build gate; report `NOT_RUN` honestly if env blocks |
| Flyway SQL portability MySQL↔H2 | Restrained ANSI-ish DDL; H2 `MODE=MySQL`; integration tests run H2 |
| Scope explosion | Phase gates + fixed commit plan; non-goals explicitly deferred |

## 7. Test Strategy

- **Server (JUnit5 + ktor-server-test-host + H2):** auth flow (register/login/refresh/logout,
  wrong credentials, expired/rotated refresh), health, content APIs, AI gateway with
  `MockAiProvider` (success/timeout/rate-limit/invalid-JSON/empty), rate limiter 429,
  idempotent progress.
- **Android (JVM unit):** `ReviewScheduler`, use cases (login validation, submit review, quiz
  grading), `TokenAuthenticator` with MockWebServer (401→refresh→retry; refresh fails→logout),
  repositories with fakes, prompt-independent pure logic.
- **Compose UI (androidTest, compiled in CI):** login flow, onboarding flow.
- **Static analysis:** detekt + ktlint on both builds; Android lint.
- **Definition of Done per feature:** builds, no secrets, loading/error/empty states, no business
  logic in composables, targeted tests, formatted code, conventional commit.

## 8. Delivery

- Push `main` → `github.com/JasonTM17/Language_Kotlin_App` (origin), no force push.
- GitHub About: description + topics via `gh repo edit`.
- Final report: implemented, evidence (commands+results), commit range, limitations, next steps.
