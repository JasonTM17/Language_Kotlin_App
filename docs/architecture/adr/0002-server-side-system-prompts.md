# ADR-0002: System prompts are assembled server-side only

**Status:** Accepted

## Context

The tutor needs a system prompt containing the learner's level, their target
language, the lesson in focus and their weak topics. The client knows some of
this; the server knows all of it.

## Decision

The client sends only user text, a mode, and context *references* (a lesson id, a
grammar id). The server resolves those references and assembles the entire system
prompt. No client-supplied string is ever treated as an instruction.

## Alternatives considered

| Option | Why rejected |
| --- | --- |
| Client builds the prompt | A client-supplied system prompt is a prompt-injection vector, and it lets the app define behaviour that costs the operator money. |
| Client sends free-text "context" the server interpolates | Same injection surface with extra steps. |
| Client sends nothing, server guesses context | Loses lesson and grammar anchoring, which is most of what makes the tutor useful. |

## Consequences

- Prompt behaviour changes ship with a server deploy, not an app release.
- The app cannot customise the tutor's personality; that is intentional.
- Context references must be validated server-side, since a client could send an
  id it does not own.
- Prompt content is testable end-to-end: because the mock provider can echo the
  assembled system prompt, integration tests assert on exactly what the model was
  told rather than on a string built separately in the test.
