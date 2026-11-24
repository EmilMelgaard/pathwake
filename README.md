# Pathwake

An alarm clock for tablets and phones that you can only turn off by solving randomly generated line-drawing puzzles,
inspired by the panels in *The Witness*. No snooze.

Pathwake is an independent fan project and is not affiliated with or endorsed by the makers of *The Witness*.

[![CI](https://github.com/EmilMelgaard/pathwake/actions/workflows/ci.yml/badge.svg)](https://github.com/EmilMelgaard/pathwake/actions/workflows/ci.yml)

Work in progress. See the [development plan](docs/PLAN.md).

## Tech stack

- Kotlin, Jetpack Compose, Material 3, Navigation 3
- Android Gradle Plugin 9 with built-in Kotlin, Gradle version catalog (`gradle/libs.versions.toml`)
- minSdk 29 (Android 10), targetSdk 37 (Android 17)

## Building

Requirements: JDK 21 and the Android SDK (Android Studio installs both).

```sh
./gradlew assembleDebug            # build a debug APK
./gradlew installDebug             # install on a connected device/emulator
./gradlew spotlessApply            # format code (ktlint)
./gradlew spotlessCheck lintDebug testDebugUnitTest :puzzle:test   # what CI checks
./gradlew connectedDebugAndroidTest                   # instrumented tests (needs a device)
```

On Windows use `gradlew.bat`.

## Releasing

Releases are built and published by GitHub Actions when a `vX.Y.Z` tag is pushed. See [RELEASING.md](RELEASING.md).

## License

[Apache License 2.0](LICENSE)
