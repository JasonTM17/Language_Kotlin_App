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

The current Flyway catalogue exposes 17 learning languages and contains
1,000,900 vocabulary records: 1,000,000 checksum-verified dictionary imports
plus 900 curated seed records. Each language advertises its own level set
through `/languages`, and vocabulary accepts the same level through the `level`
query parameter.

`GET /api/v1/vocabulary` is bounded for catalogue-scale reads. `limit` defaults
to `100` and accepts `0..200`; `offset` defaults to `0` and must be non-negative.
The existing filters (`languageId`, `level`, `category`, `query`) compose with
pagination, and invalid values return `400 VALIDATION_ERROR`. The endpoint
returns an array, so clients should request pages instead of loading the full
catalogue. `/api/v1/categories` is a distinct query over the category column
and returns the complete distinct category list.

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

### `GET /api/v1/progress/vocabulary`

Returns the authenticated learner's per-word state so a fresh device can
hydrate its local vocabulary cache. The response is account-scoped and sorted
by `vocabularyId`:

```json
[
  {
    "vocabularyId": 42,
    "favorite": true,
    "masteryLevel": 3,
    "reviewCount": 4,
    "correctCount": 3,
    "wrongCount": 1,
    "lastReviewedAtEpochMillis": 1780000000000,
    "nextReviewAtEpochMillis": 1780003600000,
    "stateUpdatedAtEpochMillis": 1780000000000
  }
]
```

### `POST /api/v1/progress/events`

Records a learning event. **Idempotent per `clientOperationId`** — this is what
makes offline retry safe.

```json
{ "clientOperationId": "5f3c…", "eventType": "QUIZ_ATTEMPT", "refId": 12, "minutes": 5 }
```

Flashcard reviews may include the local SRS snapshot. The snapshot is applied
in the same idempotent transaction as the event, so a retried operation cannot
overwrite a previously accepted state. Favorite changes use the same shape
with `VOCABULARY_STATE_SYNC`; that event never adds streak minutes and applies
the favorite while preserving a newer review snapshot:

```json
{
  "clientOperationId": "5f3c…",
  "eventType": "FLASHCARD_REVIEW",
  "refId": 42,
  "minutes": 1,
  "vocabularyProgress": {
    "favorite": false,
    "masteryLevel": 3,
    "reviewCount": 4,
    "correctCount": 3,
    "wrongCount": 1,
    "lastReviewedAtEpochMillis": 1780000000000,
    "nextReviewAtEpochMillis": 1780003600000,
    "stateUpdatedAtEpochMillis": 1780000000000
  }
}
```

`masteryLevel` is bounded to `0..5`, counters must be non-negative, and the
snapshot is accepted for `FLASHCARD_REVIEW` or `VOCABULARY_STATE_SYNC`. The
time fields are epoch milliseconds so Android and the server do not depend on a
shared timezone.

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
| `POST` | `/api/v1/ai/chat` | General tutoring turn (RAG-grounded) |
| `GET` | `/api/v1/ai/knowledge/search` | Raw retrieval over the course corpus |
| `POST` | `/api/v1/ai/explain` | Grammar explanation (RAG-grounded) |
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

`200` with `{ conversationId, reply, mode, sources[] }`. `sources` lists the
course-corpus chunks the answer was grounded in — `title`, `sourceType`
(`VOCABULARY` / `GRAMMAR` / `LESSON`), `sourceId`, `chunkIndex`, `level` and
similarity `score`. The field is defaulted: responses from deployments with
`RAG_ENABLED=false`, or replies whose retrieval found nothing relevant,
carry an empty array. Grounding applies to `general` and `grammar-explain`
modes only; see [ADR-0007](../architecture/adr/0007-retrieval-grounded-tutor.md).

Modes: `general`, `grammar-explain`, `sentence-correction`,
`conversation-practice`, `practice-score`, `mistakes-review`.

### `GET /api/v1/ai/knowledge/search`

Raw retrieval over the course corpus — the same engine the tutor uses, exposed
for demos, related-content surfaces and the E2E harness. Query params: `q`
(required), `level` (defaults to the learner profile level), `limit` (default
5, max 20). Shares the chat rate limiter. Responds with
`{ query, hits: sources[] }` in the same shape as chat `sources`.

### Specialized tutor requests

Sentence correction accepts an optional owned correction conversation so the
Android client can continue the same history:

```json
{ "sentence": "昨日学校へ行きますた。", "conversationId": 7 }
```

Role-play starts with `{ "scenario": "ordering lunch politely" }`, continues
with `{ "message": "ラーメンを一つお願いします。" }` on the `/reply` endpoint,
and returns a 0–100 score breakdown from `/score`. Replying to or scoring a
conversation whose mode is not `conversation-practice` returns `400`.

Generated quizzes accept optional `languageId`, `level`, `topic`, and `count`.
When language or level is omitted, the backend resolves it from the authenticated
learner profile; it returns `400` if neither request nor profile supplies a valid
learning identity.

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
| Provider returned a practice score outside 0–100 | `502` |
| Retrieval or indexing failure during a tutor turn | Grounded context is dropped, the turn succeeds without it |

Retrieval failures degrade to ungrounded answers on purpose: grounding must
never turn a working tutor into a 500.

## Operations — `/api/v1/ops`

Machine-facing endpoints. Every request must carry `X-Ops-Token` matching the
server's `OPS_TOKEN`; production refuses to boot without a real token.
Mismatch returns `401 UNAUTHORIZED`; a second concurrent reindex or seed
returns `409 CONFLICT`.

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/api/v1/ops/rag/reindex` | Re-embed the corpus (`?force=true` rewrites everything — the repair path for a lost or swapped vector engine) |
| `POST` | `/api/v1/ops/seed/scale` | Generate deterministic synthetic corpus (`wordsPerLanguage`, `grammarPerLanguage`, `lessonsPerLanguage`; caps 50k/10k/5k per language) |
| `DELETE` | `/api/v1/ops/seed` | Purge every synthetic row and its vectors |
| `GET` | `/api/v1/ops/stats` | Corpus counts, indexed chunk count, active vector engine and embedding model |

`POST /rag/reindex` responds with
`{ documentsScanned, chunksWritten, documentsUnchanged, durationMs }`.
`GET /stats` responds with
`{ vocabularies, grammarLessons, lessons, knowledgeChunks, engine, embeddingModel }`
where `engine` is `qdrant` or `sql`.

See [ADR-0005](../architecture/adr/0005-ai-provider-abstraction.md) for why the
provider sits behind an interface.
