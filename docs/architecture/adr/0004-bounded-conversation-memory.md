# ADR-0004: Conversation memory is bounded in three parts

**Status:** Accepted

## Context

A tutoring conversation can run for hundreds of turns. Sending the full history on
every request is not viable: cost grows linearly, and eventually the history
exceeds the model's context window.

## Decision

Memory is three bounded parts:

1. **Recent messages** — the tail after a watermark, sent verbatim.
2. **A rolling summary** of everything older, itself capped in size.
3. **Learner state** — profile, level, weak topics — which does not grow with the
   conversation at all.

The watermark is `ai_conversations.summarized_until`. The cap is
`PromptBuilder.MAX_SUMMARY_CHARS` (~1,000 tokens).

## Alternatives considered

| Option | Why rejected |
| --- | --- |
| Send everything | Cost grows linearly and the request eventually fails outright. |
| Fixed window, drop the rest | Cheapest, but the tutor forgets a grammar point the learner struggled with ten turns ago. |
| Summarise with a model call | Better summaries, but adds a paid call on the hot path and a failure mode inside the failure path. Extractive summarisation is good enough to preserve *what was discussed*. |
| Vector retrieval over history | Real infrastructure for a problem that does not yet exist at this scale. |

## Consequences

- Context cost stays flat as a conversation grows.
- The summary is **extractive**, not abstractive: it keeps truncated lines from
  evicted messages. It preserves topic continuity, not nuance. Named honestly
  rather than called "the model's memory".
- The cap is load-bearing. Without it the summary is appended to on every
  eviction and grows without bound, eventually costing more than the messages it
  replaced — which is the failure this ADR exists to prevent, and which an
  earlier implementation had.
- Oldest content is dropped first, since it is least relevant to the next reply.
