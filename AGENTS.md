# Agent instructions

Pathwake: open-source Android alarm clock (Apache-2.0) that is dismissed by solving randomly generated line-drawing
puzzles. No snooze. Tablet-first, phones supported. Hosted at github.com/EmilMelgaard/pathwake, published on Google Play.

Read [docs/PLAN.md](docs/PLAN.md) first: it holds the decisions, architecture and chunked roadmap. Work is delivered
one chunk per branch + pull request; keep the plan updated when decisions change.

## Project facts

- Modules:
  - `:app`: Kotlin + Jetpack Compose + Material 3 + Navigation 3. AGP 9 built-in Kotlin
    (do **not** add the `org.jetbrains.kotlin.android` plugin). Packages:
    - `alarm`: the `Alarm` model and `AlarmSchedule` (pure next-occurrence logic, unit-tested).
    - `data`: Room 2.8 (KSP, exported schemas in `app/schemas`) behind `AlarmRepository`; device-protected storage.
    - `ui`: one package per screen with a ViewModel; `ui/common` has formatting, the app-wide `Messenger`
      (snackbars) and the `UnsavedChangesGuard`. `Navigation.kt` uses the Material 3 list-detail scene strategy.
    - `PathwakeApplication` holds the `AppContainer` (manual DI). ViewModels take the repository and a `now` clock.
  - The app's minSdk is 29 without core library desugaring: avoid `java.time`/JDK APIs newer than Java 8 + API 29
    (lint's NewApi check catches them, e.g. `Duration.toHoursPart()`).
  - `:puzzle`: pure Kotlin/JVM puzzle engine (model, `Validator`, `Solver`, `Generator`, `PathTracer`).
    No Android dependencies. It compiles against the JDK 17 API so nothing JDK 21-only reaches Android.
- Puzzle positions use a doubled grid (`Pos`): even/even = nodes, odd/odd = cells, mixed = edges. All rule logic lives
  in `Board`, which the validator, solver and generator share. The generator must stay deterministic per seed, so
  budgets count solver steps, not time.
- `applicationId` / `namespace`: `io.github.emilmelgaard.pathwake` (permanent once on Play, never change).
- `minSdk` 29, `targetSdk` 37, `compileSdk` 37.2 (minor API level, set via `compileSdk { version = release(37) { ... } }`).
- All dependency and plugin versions live in `gradle/libs.versions.toml`; never hard-code versions in build files.
- JDK 21: Gradle daemon pinned by `gradle/gradle-daemon-jvm.properties`, compilation by `kotlin { jvmToolchain(21) }`.
- Code style: ktlint (`android_studio` style, 120 cols) via Spotless; rules in root `build.gradle.kts` and `.editorconfig`.
- Release: R8 minify + resource shrinking. Signing config only exists when `UPLOAD_KEYSTORE_FILE` is provided.

## Commands (Windows: `.\gradlew.bat`)

```sh
./gradlew spotlessApply                                                          # format
./gradlew spotlessCheck lintDebug testDebugUnitTest :puzzle:test assembleDebug assembleRelease  # same as CI
./gradlew connectedDebugAndroidTest                                              # instrumented tests on device/emulator
```

## Running and inspecting the app (Android CLI, `android`)

```sh
android emulator start api37_tablet        # AVDs: api37_tablet, api37_phone (Android 17), medium_phone (Android 16)
android run --apks=app/build/outputs/apk/debug/app-debug.apk
android layout                              # JSON UI tree of the current screen
android screen capture -o screen.png        # screenshot; always look at it
android docs search "<query>"               # official Android knowledge base
```

## Rules

- Fix lint warnings rather than suppressing them; keep the lint report clean.
- Add unit tests for logic in `app/src/test` or `puzzle/src/test`, Compose UI tests in `app/src/androidTest`.
- Check UI changes on both the tablet and the phone emulator (landscape and portrait).
- Never commit keystores, passwords, or Play service-account JSON (see `.gitignore`, RELEASING.md).
- Keep Play policy in mind for permissions (exact alarms, foreground services, notifications, etc.); declare only what
  is used and document why.
