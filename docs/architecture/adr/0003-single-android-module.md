# ADR-0003: A single Android module with enforced package layering

**Status:** Accepted

## Context

The app has clear layers — UI, domain, data. The conventional Android answer is a
Gradle module per layer, with the compiler enforcing the boundaries.

## Decision

One `:app` module, with strict package layering (`ui` → `domain` → `data`)
enforced by convention and code review rather than by the build.

## Alternatives considered

| Option | Why rejected |
| --- | --- |
| One module per layer | Correct at scale, but adds build configuration, Hilt wiring and Gradle graph work that this project's scope cannot justify. The cost lands immediately; the benefit lands only once several teams touch the code. |
| No layering at all | Fastest to start and the reason most small apps become unmaintainable. Rejected outright. |
| Dynamic feature modules | Solves install size, which is not a problem here. |

## Consequences

- Nothing prevents a careless import from `ui` into `data`. Review is the gate.
- Refactoring to modules later is mechanical but real work: package structure is
  already layer-shaped, so the move is mostly build files.
- Build times stay low and the project is buildable in CI with one Gradle
  invocation, which matters more here than boundary enforcement.
- Revisit when a second developer joins or the app exceeds roughly 150 source
  files. It is currently ~75.
