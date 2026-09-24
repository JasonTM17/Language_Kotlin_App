# Testing guide

## Suites

| Suite | Command | Count | Needs |
| --- | --- | --- | --- |
| Server integration | `cd server && ./gradlew test` | 84 | Nothing — H2 in-memory |
| Android unit (JVM) | `cd android && ./gradlew testDebugUnitTest` | 120 | Nothing |
| Android instrumented | `cd android && ./gradlew connectedDebugAndroidTest` | 31 (30 pass, 1 opt-in skip) | A device or emulator |
| Live pronunciation smoke | See the opt-in command below | 1 | Emulator, network access, Wikimedia APIs and installed Android TTS voices |
| Live bigdata E2E | `bash scripts/e2e-bigdata.sh` | 13 checks | Docker (MySQL 8 + Qdrant), JDK — operational harness, not a CI gate |

The server suite runs against a real Ktor module with real Flyway migrations on
H2 in MySQL mode, so it exercises routing, serialization, auth, persistence and
migrations together. Each test gets an isolated in-memory database.

## Static analysis

| Gate | Command | Behaviour |
| --- | --- | --- |
| detekt | `./gradlew detekt` (in `server/` or `android/`) | Blocking, both builds |
| ktlint | `./gradlew ktlintCheck` (in `server/` or `android/`) | Blocking, both builds |

Both are explicit blocking CI steps and can be run locally. The Android module
invokes them explicitly before the CI test/build steps rather than attaching
them to Gradle `check`; this keeps the failure boundary visible. Neither has a
suppressed baseline: every exception in
`server/config/detekt/detekt.yml` and `android/config/detekt/detekt.yml` carries
a written reason, and there is no call-site `@Suppress` in either build.

`ktlintFormat` is the fix for a formatting finding. `MaxLineLength` is left to
ktlint, which can auto-fix it, rather than to detekt, which can only report it —
running both meant one tool complaining about a line the other had just
produced.

On the current working tree, both static gates and both unit suites are green
(`cd server && ./gradlew test detekt ktlintCheck`,
`cd android && ./gradlew testDebugUnitTest detekt ktlintCheck assembleDebug`).
The live bigdata E2E is an operational harness, not a CI gate: it needs Docker,
takes minutes, and seeds 100k+ rows. Its evidence lives in the plan ledger under
`plans/260914-2010-rag-uiux-bigdata/reports/`; a manual-equivalent Windows-Docker
run is acceptable when Bash cannot access the local Docker engine.

### Catalogue import verification

The dictionary importer has a dependency-free self-test and a network-free
manifest/dry-run path:

```bash
python scripts/import-multilingual-vocabulary.py --self-test
python scripts/import-multilingual-vocabulary.py --dry-run
```

The live import is resumable and idempotent; it verifies every source SHA-256
before streaming rows into MySQL. Run it with
`python scripts/import-multilingual-vocabulary.py --import`, then use the
[catalogue data guide](data/multilingual-vocabulary.md) for the per-language
count audit and explicit RAG reindex checkpoint.

## What is covered, and why those things

### Server

| Area | What is asserted |
| --- | --- |
| Auth | register, duplicate email, wrong password, refresh rotation, replay of a rotated token, protected endpoints without a token |
| Content | vocabulary search filtering, profile update, quiz submit, all six seeded quizzes exposing five questions with four JSON options each |
| AI gateway | success/history, specialized correction and practice routing, ownership and mode isolation, profile-derived quiz identity, blank input, practice-score range validation, atomic completed turns, provider timeout → `503`, empty/unparseable output → `502`, rate limit → `429` |
| RAG retrieval | a forced reindex writes the corpus and a second pass rewrites nothing (idempotent by content hash), a seeded Japanese word is the top hit for its own query, a Japanese query against the English corpus returns nothing (structural language isolation), chat replies cite corpus `sources`, retrieved corpus appears only inside the prompt injection fence with numbered citations, `RAG_ENABLED=false` grounds nothing, embeddings round-trip BLOBs byte-exactly |
| Ops (seed/scale/stats) | deterministic seed adds exactly the requested rows per language and purge reverts them including their vectors, seeded terms become retrievable and disappear after purge, caps and negative counts are rejected, missing ops token is unauthorized |
| Progress | empty state, a meaningful event opening the streak, **operation-id replay being idempotent**, flashcard SRS snapshot persistence and stale-order protection, account-scoped vocabulary progress pull, favorite state-sync ordering, operation-id length validation, an unrecognised event type not extending the streak, auth and validation rejection |
| Prompt quality | the tutor is told the target language, no bare numeric id leaks, level guidance is present and descriptive, each mode has its own output contract, an unset profile asks rather than guesses |
| Summary folding | the cap holds across 50 eviction rounds, newest content survives, trimming respects line boundaries |

### Android (JVM)

| Area | What is asserted |
| --- | --- |
| `Sm2LiteScheduler` | AGAIN resets, HARD has a floor, intervals grow with mastery, EASY outlasts GOOD, mastery stays clamped for out-of-range input |
| `WorkScheduler` | reminder arithmetic — a time equal to *now* rolls to tomorrow, month-end rolls correctly |
| `TokenAuthenticator` | refresh-then-retry carries the new token, a failed refresh clears the session without retrying, unauthenticated requests are not refreshed, an auth-endpoint 401 never recurses |
| `Validators` | email, password and username rules at their boundaries |
| `ProgressHeatmapTest` | the heatmap intensity ladder over `intensityBucket`: zero and negative minutes stay in the empty bucket, 1–4 / 5–14 / 15–24 / 25+ minutes map to buckets 1–4, so the UI colours and any future analytics read the same tiers |
| Auth use cases | an invalid form never reaches the network, values are normalised before being sent, field-check order is stable |
| AI Tutor | correction and practice start/reply/score routing, correction-history reuse, connectivity transitions, Room fallback, retry without duplicate messages, best-effort cache failures, loading-state send guard, generated-quiz profile handoff, and navigation mode preservation |
| AI DTOs | a chat response without `sources` decodes to an empty list (backward compatibility with pre-RAG servers), citations decode with title/type/ids/level/score, a missing optional `level` is tolerated |
| Pronunciation (Wiktionary/Commons) | exact-language section selection, mismatched phrase-label rejection, reusable-license/URL version consistency, attribution and MIME requirements, long `Retry-After` cooldowns, bounded overflow handling, selected-locale TTS fallback and cancellation of stale word lookups |

## Why the mock provider matters

`MockAiProvider` makes the AI paths testable without a network, a key, or money.
Failure injection (`timeout`, `empty`, `invalid_json`, `rate_limit`, invalid
practice scores) is what makes
the error-mapping tests possible at all.

It also supports an `echo_system` scenario that returns the assembled system
prompt as the reply. That is how prompt quality is verified: the assertions run
against **the prompt the model actually received**, through the real pipeline,
rather than against a string the test built itself — which could pass while the
real prompt was wrong.

## Device execution evidence

The latest observed default device run on 2026-09-24 ran on
`linguaai-api35(AVD) - 15`: **31 tests, 0 failures, 0 errors, 1 intentional
skip**. The skipped case is the opt-in live pronunciation test; the default
suite does not contact Wikimedia or depend on installed language voices.

The live pronunciation smoke was run separately on that AVD with:

```powershell
cd android
.\gradlew.bat :app:connectedDebugAndroidTest --no-daemon `
  '-Pandroid.testInstrumentationRunnerArguments.class=com.linguaai.app.ui.util.WiktionaryAudioLiveSmokeTest' `
  '-Pandroid.testInstrumentationRunnerArguments.liveAudioSmoke=true'
```

It passed **1/1**. The test tapped English `cat` and Japanese `学生`, waited
for playback to start and checked source/file/license credits. For Japanese
`環境`, Wiktionary returned no Japanese recording; the test observed Android
TTS accept exactly `かんきょう` with `Locale.JAPANESE`. This confirms the
selected text and locale reached the installed engine. It is not an independent
acoustic transcription of Commons files or a measurement of the speaker output.

The current suite covers the Room migration chain through version 7,
chatbot/auth/onboarding Compose cases, language/account isolation, vocabulary
progress/outbox, and pronunciation fallback/API metadata regressions.

An earlier device run on 2026-09-17 passed **22/22** on the same emulator.
That run caught one real regression the new empty-transcript state introduced
(a scored role-play with no messages hid the practice-score card) — fixed in
`a1232c6` and covered by the re-run.

A manual UI walk on the same emulator then drove the app against a live
backend (no-Docker H2 file backend with the SQL vector store): login via
the app's own form, onboarding (Japanese · N3), Home, AI chat — typing
`kankyou` returned a tutor reply with the **"Grounded in your course"**
citation chip `環境 (かんきょう) — Môi trường`, closing the loop from device
input through server-side RAG retrieval back to the UI. Screenshots are
recorded with the plan artefacts.

An earlier device run on 2026-09-14 (same emulator) also passed **22/22**
and is the basis of the original evidence below.

The 22-test run covered:

- 6 chatbot Compose cases: input/send, practice score, retry, offline cache,
  loading guard and duplicate messages.
- 6 Room migration cases: each migration and the full 1-to-6 chain.
- 4 auth Compose cases for empty-form affordances, navigation paths,
  validation-error visibility and the loading-state action guard.
- 2 onboarding Compose cases for language selection and daily-goal selection.
- 1 vocabulary DAO regression case proving Review only returns cards for the
  authenticated learning language.
- 1 DataStore regression case proving the cached learning language is persisted
  for offline fallback and cleared at the account/session boundary.
- 2 vocabulary progress/outbox regressions covering state hydration and
  pending-local-state protection.

These tests prove the stateless Compose contracts and real Room migration
execution. A separate live route walk also exercised the authenticated app
against the local backend: Home, Learn, Vocabulary, Grammar, Review and Daily
Quiz. The quiz route initially exposed a malformed seeded JSON response; after
the scoped local data repair it returned five questions with four options and
completed a live 5/5 result.

## Principles this suite follows

- **Assert behaviour, not implementation.** The idempotency test replays a real
  request; it does not assert that a function was called.
- **When a test fails, decide which side is wrong before touching anything.** Two
  tests in this project failed on first run and the *test* was wrong both times
  (HARD at mastery 1 is 60 minutes, not 10; AGAIN at mastery 2 yields 1, not 2).
  The assertions were corrected, not loosened.
- **Tests must be able to fail.** If a test would still pass with the behaviour
  removed, it is not testing anything.
