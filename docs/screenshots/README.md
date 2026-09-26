# Screenshots

These are live captures from the current debug APK on the `linguaai-api35`
Android 15 emulator at 1080x2400. They document the auth entry surfaces in both
theme modes; the 16 Compose/device cases and the authenticated route walk were
verified separately. The broader route evidence and UI hierarchy dumps remain
in the local visualization workspace so this small catalogue stays focused on
the reusable entry-brand surfaces.

| Surface | Light | Dark |
| --- | --- | --- |
| System splash | ![System splash](splash-light.png) | — |
| Login | ![Login light](login-light.png) | ![Login dark](login-dark.png) |
| Register | ![Register light](register-light.png) | ![Register dark](register-dark.png) |

Captured: 2026-09-14 from the current debug APK after the Compose and
adaptive-launcher logo vectors were synchronized. The captures also verify the
light/dark auth lockup and the responsive bottom action placement.

## Listen and Type

This three-frame demo is built from the question, feedback, and completion frames
captured by `ListenAndTypeScreenTest.correctAnswer_showsWordMeaningScoreAndRetry`
on the Android 15 `linguaai-api35` emulator. The instrumented Compose test renders
the current screen with deterministic `hello` / `greeting` data at 1080x2400; it
does not exercise authenticated app navigation or record audible playback. The
audio action is injected by the test. See the [test evidence](../TESTING.md#android-instrumented-tests)
and [pronunciation guide](../PRONUNCIATION.md) for the separate audio behavior and
its existing attribution and fallback coverage.

![Listen & Type question, feedback, and completion demo](../img/listen-and-type/listen-and-type-demo.gif)

| State | Capture |
| --- | --- |
| Question | ![Listen & Type question](../img/listen-and-type/listen-and-type-question.png) |
| Correct-answer feedback | ![Listen & Type feedback](../img/listen-and-type/listen-and-type-feedback.png) |
| Completed round | ![Listen & Type completion](../img/listen-and-type/listen-and-type-complete.png) |
