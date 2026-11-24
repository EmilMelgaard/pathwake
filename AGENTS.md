# Agent instructions

Open-source Android app (Apache-2.0), hosted at github.com/EmilMelgaard/witness-alarm and published on Google Play.

## Project facts

- Single module `:app`. Kotlin + Jetpack Compose + Material 3 + Navigation 3. AGP 9 built-in Kotlin
  (do **not** add the `org.jetbrains.kotlin.android` plugin).
- `applicationId` / `namespace`: `io.github.emilmelgaard.witnessalarm` (permanent once on Play, never change).
- `minSdk` 29, `targetSdk` 37, `compileSdk` 37.2 (minor API level, set via `compileSdk { version = release(37) { ... } }`).
- All dependency and plugin versions live in `gradle/libs.versions.toml`; never hard-code versions in build files.
- JDK 21: Gradle daemon pinned by `gradle/gradle-daemon-jvm.properties`, compilation by `kotlin { jvmToolchain(21) }`.
- Code style: ktlint (`android_studio` style, 120 cols) via Spotless; rules in root `build.gradle.kts` and `.editorconfig`.
- Release: R8 minify + resource shrinking. Signing config only exists when `UPLOAD_KEYSTORE_FILE` is provided.

## Commands (Windows: `.\gradlew.bat`)

```sh
./gradlew spotlessApply                                                          # format
./gradlew spotlessCheck lintDebug testDebugUnitTest assembleDebug assembleRelease  # same as CI; run before finishing
./gradlew connectedDebugAndroidTest                                              # instrumented tests on device/emulator
```

## Running and inspecting the app (Android CLI, `android`)

```sh
android emulator start api37_phone         # AVDs: api37_phone (Android 17), medium_phone (Android 16)
android run --apks=app/build/outputs/apk/debug/app-debug.apk
android layout                              # JSON UI tree of the current screen
android screen capture -o screen.png        # screenshot; always look at it
android docs search "<query>"               # official Android knowledge base
```

## Rules

- Fix lint warnings rather than suppressing them; keep the lint report clean.
- Add unit tests for logic in `app/src/test`, Compose UI tests in `app/src/androidTest`.
- Never commit keystores, passwords, or Play service-account JSON (see `.gitignore`, RELEASING.md).
- Keep Play policy in mind for permissions (exact alarms, foreground services, notifications, etc.); declare only what
  is used and document why.
