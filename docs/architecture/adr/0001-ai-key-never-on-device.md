# ADR-0001: The AI API key never ships to the device

**Status:** Accepted

## Context

The Android app needs an AI provider to power the tutor. The obvious shortcut is
to call the provider from the app and ship the key with it.

## Decision

The app never calls an AI provider. It calls our backend, which holds
`AI_API_KEY` in its environment and calls the provider on the app's behalf.

## Alternatives considered

| Option | Why rejected |
| --- | --- |
| Key in the APK | An APK is a zip. A key inside it is extractable in minutes, and revocation requires shipping a new build. |
| Key fetched at runtime and cached on device | Moves the problem; the key is still on the device and now also in transit to it. |
| Per-user provider keys | Puts provider cost and rate-limit exposure on the learner, and cannot be rate limited centrally. |

## Consequences

- The key is rotatable server-side without a client release.
- All AI traffic is rate limited, logged and cost-attributable per user.
- The app cannot offer AI features offline — accepted, and surfaced to the user
  as an explicit offline notice rather than a failure.
- The backend becomes a hard dependency for AI features, which is why the
  provider sits behind an interface (ADR-0005) so the demo can run on a mock.
