# Working agreement

How work is planned, executed, verified and reported on this project. Grounded
in [`AGENTS.md`](../AGENTS.md) and [`.agentkit/config.yaml`](../.agentkit/config.yaml)
(`coding_level: 4`, `workflow_artifact_gate.enabled: true`).

This document exists because the project has drifted from its own contract more
than once. It states the rules explicitly so a drift is visible rather than
discovered later.

---

## 1. Establish plan identity before doing anything

**One active plan. Never two.**

`AGENTS.md` requires that a resume verifies plan identity and that a ledger is
never reused across plans. That only works if there is exactly one candidate.

Before the first action of any work session:

```bash
ls plans/*/reports/execution-ledger.md      # every ledger must name its plan
sed -n '1,3p' plans/<active>/reports/execution-ledger.md
```

The ledger's first non-heading line must name its plan path. If more than one
plan claims the same work, **stop and resolve it with the user** — do not pick
one silently. Guessing wrong means executing the wrong plan's steps, which is
worse than not starting.

### Current plan inventory (as of 2026-09-12)

| Directory | Belongs to | Status |
| --- | --- | --- |
| `20260909-linguaai` | LinguaAI — original A→Z build, phases A…AK | Superseded by the completion plan; ledger records phases A–I with commit evidence |
| `260911-2254-linguaai-completion` | LinguaAI — 6-phase completion | **Active** |
| `20260815-1600-gemini-harness` | AgentKit itself | Not this project |
| `20260827-agentkit-ultra-agy` | AgentKit itself | Not this project |
| `20260827-grok-build-adapter` | AgentKit itself | Not this project |

The three AgentKit plans are not part of this repository's work and should be
removed from `plans/` so that resume cannot land on one. They are gitignored, so
removal is local-only and safe.

---

## 2. Route the request through the AgentKit workflow

| Situation | Route |
| --- | --- |
| Large or long-running objective | `/ak:goal-warmup` → outcome contract |
| Intent, scope or trade-offs ambiguous | `/ak:advise` (a decision artefact, not implementation) |
| Need codebase evidence | `/ak:scout` |
| Work needs an executable plan | `/ak:plan` |
| Implement against an accepted plan | `/ak:cook` / `/ak:fix` |
| Verify | `/ak:test` + domain checks |
| Review before shipping | `/ak:code-review`; `/ak:wukong` for a contested claim |
| Continue later | `/ak:journal` / `/ak:handoff` |

Read-only questions do not authorise edits, commits, pushes or deployment.

---

## 3. Decompose before executing

A step is only acceptable if it has all four:

1. **A single verifiable outcome** — "add `findLanguageById`" not "improve the AI".
2. **The exact files it touches.**
3. **Its exit criterion** — the specific command and the result that means done.
4. **Its verification budget** — see §5.

If a step cannot state its exit criterion, it is not yet a step. Split it.

---

## 4. Execute plan-locked

Per `AGENTS.md`: the accepted plan is the execution authority. The loop is

> resolve active plan → first incomplete step → smallest reversible action →
> record evidence → next incomplete step

and it continues **without asking whether to proceed**, unless a user decision
boundary, an external-authority boundary, or a concrete blocker is reached.

While a step is safely actionable, do not: re-scout settled areas, reopen
accepted decisions, invent alternative architectures, refactor nearby code, or
add unplanned polish.

Classify anything new as **NOW** (required by this step), **LATER** (report at
handoff, keep out of the path), or **BLOCKER** (stop and resolve).

---

## 5. Verification budget — the rule most often broken here

Implementation and verification are separate lanes joined at planned
checkpoints. **Do not turn every edit into a test cycle.**

| Step type | Cheapest sufficient check |
| --- | --- |
| Kotlin source change, one module | `compileKotlin` (or `compileDebugKotlin`) |
| Schema or migration change | Compile, then diff the migration DDL against the exported Room schema |
| Test-only change | The single test class, not the suite |
| Behaviour change with tests | The affected suite |
| Phase boundary | Full suite for both builds + `assembleDebug` |

Do not rerun an unchanged passing gate. Do not broaden a focused gate because of
generic doubt. Batch full-suite runs at phase boundaries.

### Bounded repair circuit

At a failing checkpoint: **at most two** focused, cause-aligned repair attempts.
If the gate still fails, one specialist-guided attempt if the delegation gate
passes. If it still fails, record the evidence and report the blocker. **Never
enter an open-ended fix/retest loop.**

When a test fails, decide which side is wrong before changing anything. Twice on
this project the test was wrong and the implementation was right
(`HARD` at mastery 1 is 60 minutes, not 10; `AGAIN` at mastery 2 yields 1, not 2).
Assertions were corrected, not loosened.

---

## 6. Commit discipline

- **Conventional Commits**: `feat:`, `fix:`, `chore:`, `test:`, `docs:`, `refactor:`.
- **Never commit on a tree that does not compile.** This project has a stretch of
  five consecutive commits on a broken build because nothing ran the build.
- **One logical change per commit.** A commit message explains *why*, and states
  what was verified and what was not.
- **Record limitations in the commit message** when verification was partial.
  "Not verified: the migration has not run on a device" is a required sentence,
  not a confession.
- Leave the working tree clean at the end of a work session.

---

## 7. Reporting contract

Every progress report states, in this order:

1. **What changed** — concrete files and the commit.
2. **Evidence** — the exact command run and its result.
3. **What was verified** vs **what was not**, named explicitly.
4. **Findings** — including defects found in passing, marked NOW / LATER.
5. **Plan state** — phase, task count, next incomplete step.
6. **Blockers** — anything needing a user decision.

Never report an intention as an outcome. An agent's summary describes what it
intended; the report describes what the evidence shows.

---

## 8. Definition of done

A step is done when its exit criterion has been **executed**, not when the code
looks right. A phase is done when its planned gates pass and the ledger records
the evidence. Work is not done because it compiles.

If something cannot be verified in the current environment, say so and record it.
This project carries two such items — Room migrations that have never executed,
and Compose UI tests that cannot be written honestly without a device. They are
tracked in [`TESTING.md`](TESTING.md) rather than quietly omitted.

---

## 9. Environment constraints (learned the hard way)

| Constraint | Consequence |
| --- | --- |
| `JAVA_HOME` is not exported in a fresh shell | Always pass `JAVA_HOME="C:/Program Files/Java/jdk-24"` to Gradle. Without it Gradle takes JDK 26 and fails with a bare version number as the entire error. |
| No Android emulator or AVD | No UI change can be visually verified. Do not claim a UI improvement was verified. |
| `docker compose` is not available | Use `docker-compose` (hyphenated). |
| Gradle user home is a symlink | If the target is removed, every Gradle command fails with an "Access is denied" journal lock. |
| Server and Android are separate Gradle builds | Always say which one a command targets. |
