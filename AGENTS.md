# Agent instructions

Pathwake: open-source Android alarm clock (Apache-2.0) that is dismissed by solving randomly generated line-drawing
puzzles. No snooze. Tablet-first, phones supported. Hosted at github.com/EmilMelgaard/pathwake, published on Google Play.

Read [docs/PLAN.md](docs/PLAN.md) first: it holds the decisions, architecture and chunked roadmap. Work is delivered
one chunk per branch + pull request; keep the plan updated when decisions change.

## Project facts

- Modules:
  - `:app`: Kotlin + Jetpack Compose + Material 3 + Navigation 3. AGP 9 built-in Kotlin
    (do **not** add the `org.jetbrains.kotlin.android` plugin). Packages:
    - `alarm`: the `Alarm` model, `AlarmSchedule` (pure next-occurrence logic) and `AlarmScheduler` (keeps the
      platform alarm set, rings due alarms, catches up after reboots/clock changes). Pure Kotlin behind small
      interfaces (`ExactAlarms`, `SchedulerState`, `Ringer`), unit-tested with fakes.
    - `ring`: `RingingSession` / `RingingController`: the alarm that is ringing and its puzzle progress, and
      `RingVolume` (gradual volume, volume while solving). All pure.
    - `system`: the Android side: `setAlarmClock` (`AndroidExactAlarms`), receivers, `RingingService` (sound,
      vibration, volume, auto-silence), `AlarmPlayer`, `SoundLibrary` (custom files, device tones, fallbacks),
      notifications, permissions, SharedPreferences stores.
    - `settings`: app-wide `Settings` in DataStore (`SettingsRepository`), including the defaults for new alarms.
    - `data`: Room 2.8 (KSP, exported schemas in `app/schemas`, migrations tested in `MigrationTest`) behind
      `AlarmRepository`. The UI gets a `SchedulingAlarmRepository` that reschedules after every change.
    - `ui`: one package per screen with a ViewModel; `ui/common` has formatting, the app-wide `Messenger`
      (snackbars) and the `UnsavedChangesGuard`. `Navigation.kt` uses the Material 3 list-detail scene strategy.
    - `ui/ringing`: `RingingActivity` (shows over the lock screen) with the puzzles that stop the alarm.
    - `PathwakeApplication` holds the `AppContainer` (manual DI). ViewModels take the repository and a `now` clock.
  - Everything the alarm engine touches is direct-boot aware and lives in device-protected storage, so alarms ring
    after a reboot before the first unlock. Don't read credential-protected storage (or shared media) from it.
    Build paths from `createDeviceProtectedStorageContext()` directly: helpers like `preferencesDataStoreFile()` go
    through the application context and silently end up in credential-protected storage.
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

## Testing alarms on an emulator

- Set an alarm 2 minutes ahead (time picker keyboard mode: delete, then type `hhmm`; `adb shell settings put system
  time_12_24 24` avoids AM/PM, and the keyboard moves the dialog's OK button, so find it with `android layout`), lock
  with `adb shell input keyevent 26` and wait. `adb shell dumpsys alarm | grep pathwake` shows the scheduled
  `setAlarmClock` alarm.
- Puzzles are seeded: read `seed` and `puzzle_number` with `adb shell run-as io.github.emilmelgaard.pathwake cat
  /data/user_de/0/io.github.emilmelgaard.pathwake/shared_prefs/ringing.xml`; `Generator.generate(difficulty,
  seed + puzzleNumber).solution` is the line to draw (doubled coordinates: node = pos / 2).
- Direct boot: `adb shell locksettings set-pin 1234`, then `adb reboot`; clear with `locksettings clear --old 1234`.
  `run-as` doesn't work before the first unlock.
- `adb shell dumpsys audio` shows the alarm's player and any `AudioHardening` (Android 17) messages; the player's own
  volume (gradual volume, lower while drawing) is the `L dB` column of its track in `dumpsys media.audio_flinger`.
- Custom sounds: `adb push` an audio file to `/sdcard/Download/` and add it from the sound picker.

## Rules

- Fix lint warnings rather than suppressing them; keep the lint report clean.
- Add unit tests for logic in `app/src/test` or `puzzle/src/test`, Compose UI tests in `app/src/androidTest`.
- Check UI changes on both the tablet and the phone emulator (landscape and portrait).
- Never commit keystores, passwords, or Play service-account JSON (see `.gitignore`, RELEASING.md).
- Keep Play policy in mind for permissions (exact alarms, foreground services, notifications, etc.); declare only what
  is used and document why.
