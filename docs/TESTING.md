# Testing guide

## Suites

| Suite | Command | Count | Needs |
| --- | --- | --- | --- |
| Server integration | `cd server && ./gradlew test` | 35 | Nothing — H2 in-memory |
| Android unit (JVM) | `cd android && ./gradlew testDebugUnitTest` | 34 | Nothing |
| Android instrumented | `cd android && ./gradlew connectedDebugAndroidTest` | 4 | A device or emulator |

The server suite runs against a real Ktor module with real Flyway migrations on
H2 in MySQL mode, so it exercises routing, serialization, auth, persistence and
migrations together. Each test gets an isolated in-memory database.

## What is covered, and why those things

### Server

| Area | What is asserted |
| --- | --- |
| Auth | register, duplicate email, wrong password, refresh rotation, replay of a rotated token, protected endpoints without a token |
| Content | vocabulary search filtering, profile update, quiz submit |
| AI gateway | success path, provider timeout → `503`, empty response → `502`, unparseable structured output → `502`, rate limit → `429` |
| Progress | empty state, a meaningful event opening the streak, **operation-id replay being idempotent**, an unrecognised event type not extending the streak, auth and validation rejection |
| Prompt quality | the tutor is told the target language, no bare numeric id leaks, level guidance is present and descriptive, each mode has its own output contract, an unset profile asks rather than guesses |
| Summary folding | the cap holds across 50 eviction rounds, newest content survives, trimming respects line boundaries |

### Android (JVM)

| Area | What is asserted |
| --- | --- |
| `Sm2LiteScheduler` | AGAIN resets, HARD has a floor, intervals grow with mastery, EASY outlasts GOOD, mastery stays clamped for out-of-range input |
| `WorkScheduler` | reminder arithmetic — a time equal to *now* rolls to tomorrow, month-end rolls correctly |
| `TokenAuthenticator` | refresh-then-retry carries the new token, a failed refresh clears the session without retrying, unauthenticated requests are not refreshed, an auth-endpoint 401 never recurses |
| `Validators` | email, password and username rules at their boundaries |
| Auth use cases | an invalid form never reaches the network, values are normalised before being sent, field-check order is stable |

## Why the mock provider matters

`MockAiProvider` makes the AI paths testable without a network, a key, or money.
Failure injection (`timeout`, `empty`, `invalid_json`, `rate_limit`) is what makes
the error-mapping tests possible at all.

It also supports an `echo_system` scenario that returns the assembled system
prompt as the reply. That is how prompt quality is verified: the assertions run
against **the prompt the model actually received**, through the real pipeline,
rather than against a string the test built itself — which could pass while the
real prompt was wrong.

## Tests that are missing, and why

Two things cannot be verified in a headless environment. They are recorded here
rather than quietly skipped:

1. **Room migrations have never actually run.** `LinguaDatabaseMigrationTest`
   compiles and is ready, but every migration so far has only been checked by
   diffing its DDL against the exported schema. A schema diff cannot catch a
   migration that throws or leaves the database unopenable. Run
   `connectedDebugAndroidTest` on a machine with an emulator.
2. **Compose UI tests for login and onboarding.** `LoginScreen` takes
   `viewModel: LoginViewModel = hiltViewModel()` and its content composable is
   private, so a UI test needs either a production refactor (extract a stateless
   content composable, as `ProgressScreen` and `HomeScreen` already do) or a Hilt
   test harness. Writing it without a device would mean asserting against a
   guessed semantics tree — a test that compiles but asserts the wrong thing is
   worse than no test.

## Principles this suite follows

- **Assert behaviour, not implementation.** The idempotency test replays a real
  request; it does not assert that a function was called.
- **When a test fails, decide which side is wrong before touching anything.** Two
  tests in this project failed on first run and the *test* was wrong both times
  (HARD at mastery 1 is 60 minutes, not 10; AGAIN at mastery 2 yields 1, not 2).
  The assertions were corrected, not loosened.
- **Tests must be able to fail.** If a test would still pass with the behaviour
  removed, it is not testing anything.
