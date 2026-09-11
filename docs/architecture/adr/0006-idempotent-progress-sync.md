# ADR-0006: Progress sync is idempotent through a client operation id

**Status:** Accepted

## Context

Learning events — a flashcard review, a quiz attempt — are recorded on the device
and delivered later. Delivery can fail after the server has already processed the
request: the response is lost in transit and the client retries. A naive retry
double-counts, inflating streaks and minutes.

## Decision

The device mints a `clientOperationId` **once, when the event is created**, and
stores it with the event in an outbox table. Every delivery attempt carries the
same id. The server keys on `(user_id, client_operation_id)` via a unique index
and, on a replay, returns the existing row with `created = false` instead of
inserting a second one.

## Alternatives considered

| Option | Why rejected |
| --- | --- |
| Retry without an id, accept duplicates | Streaks and study minutes are user-visible claims. Silently inflating them is worse than losing an event. |
| Server-side dedupe by payload hash | Two genuine identical events (two identical reviews) would collapse into one. |
| Mint the id at delivery time | The id changes on every retry, which is exactly what defeats deduplication. |
| Exactly-once delivery | Not achievable over an unreliable network. Idempotency is the achievable version of the same goal. |

## Consequences

- Retrying is safe, which is what makes WorkManager's backoff strategy usable at
  all.
- The unique index is load-bearing. Dropping it silently turns every retry into a
  double count, and nothing in the code would look wrong.
- The outbox is durable: an event enqueued just before the process dies is still
  delivered on the next run.
- Operations that fail permanently are dead-lettered after 5 attempts rather than
  retried forever.
- Verified by test: replaying the same operation id returns the same event id,
  `created = false`, and unchanged totals.
