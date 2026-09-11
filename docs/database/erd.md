# Database

MySQL 8 in production and Docker; H2 in MySQL compatibility mode for integration
tests. Schema is owned by Flyway — `V1__init.sql` creates 17 tables, `V2__seed.sql`
loads the demo catalogue. Never edit an applied migration; add a new one.

## ER diagram

```mermaid
erDiagram
    users ||--o| user_profiles : "has one"
    users ||--o{ refresh_tokens : "owns"
    users ||--o{ quiz_attempts : "makes"
    users ||--o{ ai_conversations : "starts"
    users ||--o{ user_progress : "records"
    users ||--o{ user_vocabulary_progress : "tracks"
    users ||--o{ user_mistakes : "accumulates"
    users ||--o{ learning_streaks : "earns"

    languages ||--o{ lessons : "contains"
    languages ||--o{ vocabularies : "contains"
    languages ||--o{ grammar_lessons : "contains"
    languages ||--o{ user_profiles : "target of"

    quizzes ||--o{ quiz_questions : "asks"
    quizzes ||--o{ quiz_attempts : "is attempted in"
    quiz_attempts ||--o{ quiz_answers : "records"
    quiz_questions ||--o{ quiz_answers : "answered by"

    ai_conversations ||--o{ ai_messages : "contains"
    vocabularies ||--o{ user_vocabulary_progress : "progress on"

    users {
        bigint id PK
        varchar email UK
        varchar username
        varchar password_hash
        varchar avatar_url
    }
    user_profiles {
        bigint user_id PK_FK
        bigint language_id FK
        varchar level
        varchar goal
        int daily_goal_minutes
    }
    refresh_tokens {
        bigint id PK
        bigint user_id FK
        varchar token_hash
        varchar family_id
    }
    languages {
        bigint id PK
        varchar code
        varchar name
        varchar levels
    }
    lessons {
        bigint id PK
        bigint language_id FK
        varchar level
        varchar title
        varchar type
        int estimated_minutes
        int difficulty
    }
    vocabularies {
        bigint id PK
        bigint language_id FK
        varchar level
        varchar word
        varchar reading
        varchar meaning
        varchar category
    }
    grammar_lessons {
        bigint id PK
        bigint language_id FK
        varchar level
        varchar title
        varchar structure
    }
    quizzes {
        bigint id PK
    }
    quiz_questions {
        bigint id PK
        bigint quiz_id FK
    }
    quiz_attempts {
        bigint id PK
        bigint user_id FK
        bigint quiz_id FK
        int score
        int total
        datetime completed_at
    }
    quiz_answers {
        bigint id PK
        bigint attempt_id FK
        bigint question_id FK
    }
    ai_conversations {
        bigint id PK
        bigint user_id FK
        varchar mode
        text summary
        bigint summarized_until
        bigint context_lesson_id FK
        bigint context_grammar_id FK
    }
    ai_messages {
        bigint id PK
        bigint conversation_id FK
        varchar role
        text content
        int token_count
    }
    user_progress {
        bigint id PK
        bigint user_id FK
        varchar client_operation_id
        varchar event_type
        bigint ref_id
        int minutes
        datetime occurred_at
    }
    user_vocabulary_progress {
        bigint id PK
        bigint user_id FK
        bigint vocabulary_id FK
        int mastery_level
        int review_count
        datetime next_review_at
    }
    user_mistakes {
        bigint id PK
        bigint user_id FK
        bigint language_id FK
        varchar topic
        varchar source
        boolean resolved
    }
    learning_streaks {
        bigint id PK
        bigint user_id FK
        date activity_date
        int minutes
    }
```

## The three tables that carry design weight

### `user_progress` — the event log and the idempotency key

```sql
UNIQUE KEY uq_progress_operation (user_id, client_operation_id)
```

This unique index is the whole reason offline retry is safe. The device mints a
`client_operation_id` once, when the event is created — not when it is sent — and
every retry carries the same value. The server returns the existing row with
`created = false` instead of inserting a second one. **Do not drop this index**;
doing so silently turns every retry into a double count.

### `learning_streaks` — one row per active day

```sql
UNIQUE KEY uq_streak_user_date (user_id, activity_date)
```

The streak is *derived* from this table (consecutive days present), never stored
as a counter. A counter can drift and can be inflated by changing the device
clock; a set of days cannot.

### `ai_conversations.summary` + `summarized_until`

Conversation memory is bounded rather than replayed in full. `summarized_until`
is a watermark: messages with a higher id are sent verbatim, everything older is
represented by `summary`. The summary itself is capped in size
(`PromptBuilder.MAX_SUMMARY_CHARS`) so that context cost stays flat as a
conversation grows — see
[ADR-0004](../architecture/adr/0004-bounded-conversation-memory.md).

## Migrations

| Version | Contents |
| --- | --- |
| `V1__init.sql` | All 17 tables, indexes and constraints |
| `V2__seed.sql` | Demo catalogue: languages, lessons, vocabulary, grammar, quizzes |

## Conventions

- Names are `snake_case`; Exposed tables map them to camelCase Kotlin properties.
- Money, timestamps and dates use `DATETIME` / `DATE`; no timezone-less strings.
- DDL stays close to ANSI SQL so the same migration runs on MySQL and H2.
- A column named `usage` had to be renamed in an early migration because `USAGE`
  is reserved in MySQL. Watch for reserved words when adding columns.
