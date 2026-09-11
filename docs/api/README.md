# API reference

Base URL: `/api/v1`. All responses are JSON. Authenticated endpoints require
`Authorization: Bearer <accessToken>`.

Errors use one envelope everywhere:

```json
{ "error": { "code": "VALIDATION_ERROR", "message": "…", "requestId": "…" } }
```

| Code | Meaning |
| --- | --- |
| `VALIDATION_ERROR` | Request body or parameter is malformed |
| `INVALID_CREDENTIALS` | Wrong email or password |
| `UNAUTHORIZED` | Missing, expired or revoked token |
| `FORBIDDEN` | Authenticated but not permitted |
| `NOT_FOUND` | Resource does not exist |
| `CONFLICT` | Duplicate (e.g. email already registered) |
| `RATE_LIMITED` | Per-user AI quota exceeded |
| `AI_UNAVAILABLE` | Provider timed out or is unreachable |
| `INTERNAL_ERROR` | Unexpected server failure |

---

## Health

### `GET /api/v1/health`

Unauthenticated liveness probe. Returns `200` with a status payload.

---

## Auth — `/api/v1/auth`

### `POST /api/v1/auth/register`

```json
{ "email": "son@example.com", "username": "Son", "password": "at-least-8-chars" }
```

`201` with `{ user, tokens }`. `409 CONFLICT` when the email is already registered.

### `POST /api/v1/auth/login`

```json
{ "email": "son@example.com", "password": "…" }
```

`200` with `{ user, tokens }`, or `401 INVALID_CREDENTIALS`.

### `POST /api/v1/auth/refresh`

```json
{ "refreshToken": "…" }
```

`200` with a new `{ tokens }`. Refresh tokens **rotate**: the presented token is
invalidated, and replaying it is rejected.

### `POST /api/v1/auth/logout`

```json
{ "refreshToken": "…" }
```

Revokes the refresh token family. Idempotent.

### Token shape

```json
{ "accessToken": "…", "refreshToken": "…", "expiresIn": 1800 }
```

Access tokens are short-lived (default 30 minutes). The Android client refreshes
transparently on `401` and retries once; see
[architecture](../architecture/README.md#token-refresh).

---

## Profile — `/api/v1/profile`

Both endpoints require authentication.

### `GET /api/v1/profile`

`200` with `{ user, languageId, level, goal, dailyGoalMinutes, onboarded }`.

### `PUT /api/v1/profile`

All fields optional; only the ones present are updated.

```json
{ "languageId": 1, "level": "N3", "goal": "pass JLPT N3", "dailyGoalMinutes": 20 }
```

---

## Content

Public content endpoints (authentication not required).

| Method | Path | Notes |
| --- | --- | --- |
| `GET` | `/api/v1/languages` | All languages with their supported levels |
| `GET` | `/api/v1/categories` | Vocabulary categories |
| `GET` | `/api/v1/lessons` | Filter with `languageId`, `level`, `type` |
| `GET` | `/api/v1/lessons/{id}` | Lesson detail |
| `GET` | `/api/v1/vocabulary` | Filter with `languageId`, `level`, `category`, `query` |
| `GET` | `/api/v1/vocabulary/{id}` | Vocabulary detail |
| `GET` | `/api/v1/grammar` | Filter with `languageId`, `level` |
| `GET` | `/api/v1/grammar/{id}` | Grammar detail |
| `GET` | `/api/v1/quizzes/{id}` | Quiz with its questions |

### `POST /api/v1/quizzes/{id}/submit`

Authenticated.

```json
{ "answers": [ { "questionId": 1, "answer": "…" } ], "durationSeconds": 95 }
```

`200` with the graded result: score, total, and per-question correctness.

---

## Progress — `/api/v1/progress`

Authenticated. The server is the only source of the streak; the client renders
what it receives.

### `GET /api/v1/progress`

```json
{
  "streak": { "current": 3, "longest": 11, "lastActiveDate": "2026-09-11" },
  "totals": { "minutesStudied": 95, "activeDays": 4, "quizAttempts": 6,
              "quizAverageScore": 0.83, "aiConversations": 2 },
  "vocabulary": { "tracked": 40, "mastered": 12, "learning": 21, "fresh": 7, "dueForReview": 5 },
  "recentActivity": [ { "date": "2026-09-11", "minutes": 20 } ],
  "weakTopics": [ { "topic": "て-form", "occurrences": 3 } ]
}
```

### `POST /api/v1/progress/events`

Records a learning event. **Idempotent per `clientOperationId`** — this is what
makes offline retry safe.

```json
{ "clientOperationId": "5f3c…", "eventType": "QUIZ_ATTEMPT", "refId": 12, "minutes": 5 }
```

`200` with `{ eventId, created }`. `created` is `false` when the operation had
already been recorded, in which case nothing is counted twice.

Recognised `eventType` values — only these extend the streak:

`FLASHCARD_REVIEW`, `QUIZ_ATTEMPT`, `AI_CORRECTION`, `LESSON_COMPLETED`

An unrecognised type is stored but does not count toward activity.

---

## AI Tutor — `/api/v1/ai`

Authenticated. The client sends only user text plus context references — never
system instructions. The backend builds the full prompt, so the AI provider key
never leaves the server.

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/v1/ai/conversations` | List conversations |
| `GET` | `/api/v1/ai/conversations/{id}/messages` | Message history |
| `POST` | `/api/v1/ai/chat` | General tutoring turn |
| `POST` | `/api/v1/ai/explain` | Grammar explanation |
| `POST` | `/api/v1/ai/correct` | Sentence correction |
| `POST` | `/api/v1/ai/generate-quiz` | Generate a quiz |
| `POST` | `/api/v1/ai/conversation-practice` | Start a role-play |
| `POST` | `/api/v1/ai/conversation-practice/{id}/reply` | Continue a role-play |
| `POST` | `/api/v1/ai/conversation-practice/{id}/score` | Score a role-play |

### `POST /api/v1/ai/chat`

```json
{ "conversationId": 7, "mode": "general", "message": "…",
  "contextLessonId": null, "contextGrammarId": null }
```

`conversationId` may be omitted to start a new conversation.

`200` with `{ conversationId, reply, mode }`.

Modes: `general`, `grammar-explain`, `sentence-correction`,
`conversation-practice`, `practice-score`, `mistakes-review`.

### Rate limiting

Each user gets `AI_RATE_LIMIT_PER_MINUTE` requests (default 20). Beyond that the
endpoint returns `429 RATE_LIMITED`. The counter is in-process; the upgrade path
to a shared store is documented in the
[architecture notes](../architecture/README.md#rate-limiting).

### Failure mapping

| Condition | Response |
| --- | --- |
| Provider timeout | `503 AI_UNAVAILABLE` |
| Provider returned nothing | `502` |
| Provider returned unparseable structured output | `502` |

See [ADR-0005](../architecture/adr/0005-ai-provider-abstraction.md) for why the
provider sits behind an interface.
