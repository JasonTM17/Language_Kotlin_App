# ADR-0005: The AI provider sits behind an interface

**Status:** Accepted

## Context

The tutor needs a model. Hard-coding one provider couples behaviour, cost and
availability to a single vendor, and makes the test suite depend on a paid
service and the network.

## Decision

`AiProvider` is an interface with two implementations:

- `OpenAiCompatibleProvider` — any OpenAI-compatible endpoint (OpenAI, DeepSeek,
  Groq, a local server).
- `MockAiProvider` — deterministic, no network, supports failure injection.

Selection is configuration (`AI_PROVIDER`), not code.

## Alternatives considered

| Option | Why rejected |
| --- | --- |
| Call the SDK directly from the service | Tests would need network and a paid key, and switching vendor would touch business logic. |
| A general-purpose abstraction over many non-compatible APIs | Enormous surface for no current need. The OpenAI-compatible shape is a de facto standard. |
| Only a mock | Nothing real to demo. |

## Consequences

- The whole test suite runs offline and deterministically, including failure
  paths — timeout, empty response, unparseable structured output and rate
  limiting are all exercised through the mock.
- Switching provider is an environment change.
- The mock supports an `echo_system` scenario that returns the assembled system
  prompt, which is how prompt quality is verified end-to-end.
- The abstraction is deliberately thin. It exposes one operation, so it does not
  pretend to normalise differences it cannot: streaming and provider-specific
  features would need a wider interface, and that is a decision to make when a
  feature actually needs it.
