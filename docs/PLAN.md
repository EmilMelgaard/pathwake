# Pathwake development plan

Pathwake is an alarm clock that can only be dismissed by solving one or more randomly generated line-drawing
puzzles (in the style of the panels in *The Witness*). It has the features of a modern alarm app, but **no snooze**.
Tablets are the primary target; phones must work too (puzzles may be smaller).

This document is the source of truth for scope and decisions. Each chunk is delivered as a pull request with a CI-built
APK, demoed on tablet and phone emulators, and iterated on with feedback before the next chunk starts.

## Decisions

| Topic                      | Decision                                                                                                   |
|----------------------------|------------------------------------------------------------------------------------------------------------|
| Name                       | **Pathwake** (`io.github.emilmelgaard.pathwake`). Store listing must not use "The Witness" trademark.       |
| Puzzle mechanics           | Dots, broken edges, colored squares, stars, triangles, tetris pieces (fixed + rotatable), subtractive tetris, elimination marks, multiple starts/exits, symmetry |
| Difficulty                 | Preset levels per alarm (Easy, Medium, Hard, Expert, Fiendish), each mapping to grid size + mechanics      |
| Puzzles per alarm          | Configurable per alarm (1-10)                                                                              |
| Escape hatches             | "New puzzle" button (same difficulty); auto-silence: never / after N minutes / when the sound ends          |
| Volume while solving       | Setting: full / lower while drawing / mute while drawing (back to full after ~10 s idle)                   |
| Anti-cheat                 | Settings: volume keys don't lower a ringing alarm; require a puzzle to disable/skip/delete an alarm due within 2 h |
| Puzzle look                | Setting: "Panel" (Witness-inspired, original art, glowing line) or "Material" (follows app theme)          |
| Languages                  | English; all strings in resources                                                                          |
| Workflow                   | One branch + PR per chunk; CI attaches a debug APK                                                         |

## Architecture

- **`:puzzle`**: pure Kotlin/JVM library, no Android dependencies, fast unit tests, deterministic with a seed.
  - Model in a doubled coordinate system: for a `w x h` cell grid, positions run `0..2w x 0..2h`.
    Even/even positions are nodes, odd/odd are cells, and mixed positions are edges.
  - `Validator`: checks a path against all rules and returns the violating symbols, which the UI flashes red.
  - `Generator`: picks a random winding solution path, computes its regions, then places symbols consistent with
    that path. Generation is seeded.
  - `Solver`: DFS with pruning (validates a region as soon as the path closes it). Used to check generated puzzles
    and calibrate difficulty (count of valid solutions, triviality checks).
  - `PathTracer`: touch-input state machine that moves the line along grid edges, with backtracking, collisions,
    gaps and exits. It works in grid units so it can be unit-tested.
- **`:app`**: Compose UI, data and alarm engine.
  - UI: Material 3, adaptive layouts (list-detail on tablets via Navigation 3 scenes), puzzle panel drawn on a
    `Canvas`.
  - Data: Room (alarms), DataStore (settings), manual dependency injection via an `AppContainer` (no Hilt).
  - Alarm engine:
    - Scheduling: `AlarmManager.setAlarmClock()` with `USE_EXACT_ALARM`. Alarms are rescheduled on boot (direct-boot
      aware), time change, time-zone change and app update.
    - Ringing: a foreground service plays `USAGE_ALARM` audio (required on Android 17 for background audio) and posts a
      full-screen-intent notification that opens the lock-screen ringing activity with the puzzles.

## Alarm features

- Multiple alarms with label and an on/off toggle; sorted list with a "next alarm in X h Y min" summary.
- Repeat on weekdays, or one-time on the next occurrence or on a specific date.
- Pause date ranges per alarm (for example vacations), and skip the next occurrence.
- Sound: default, system alarm tones, or custom audio files (persisted document URI); volume, gradual volume
  increase, vibration, preview.
- Puzzle difficulty and puzzle count per alarm, with a "try it" preview.
- Auto-silence (never / N minutes / when the sound ends) and a missed-alarm notification.
- Undo after delete, duplicate alarm, and defaults for new alarms.
- Settings: theme (system/light/dark, dynamic color), puzzle look, volume while solving, anti-cheat options, first day
  of week.
- No snooze.

## Chunks

Each chunk ends with a demo (screenshots on the tablet and phone emulators), a PR, and a feedback round.

1. **Puzzle engine core and playground**
   - `:puzzle` module: model, validator, generator, solver and tracer for dots, gaps, colored squares, stars,
     triangles, and multiple starts/exits.
   - Puzzle panel UI with touch drawing, success/failure feedback, both looks.
   - A temporary "Puzzle playground" home screen: pick a difficulty, generate a new puzzle, toggle the look.
   - *Feedback on:* drawing feel, visuals, puzzle variety.
2. **Advanced mechanics and difficulty calibration**
   - Tetris (fixed/rotatable), subtractive tetris, elimination, symmetry.
   - Solver-checked generation, 5 calibrated presets, generation-time budget on low-end devices.
   - *Feedback on:* difficulty curve.
3. **Alarm management**
   - Room data model and next-occurrence calculation (repeat days, dates, pauses, skip, DST/time zones), fully
     unit-tested.
   - Alarm list and editor (adaptive list-detail on tablets).
   - No ringing yet.
   - *Feedback on:* UX.
4. **Alarm engine**
   - Scheduling, reboot and time-change handling, direct boot.
   - Ringing service and lock-screen ringing activity with N puzzles, "new puzzle", auto-silence.
   - Permission onboarding (notifications, full-screen intent).
   - *Feedback on:* real overnight tests on phone/tablet.
5. **Sounds and settings**
   - Tone picker, custom files, gradual volume, vibration, preview.
   - Settings screen: theme, puzzle look, volume while solving, anti-cheat, defaults.
6. **Polish and release**
   - App icon, edge cases (overlapping alarms, phone call while ringing), accessibility and color-blind pass.
   - Play policy audit, store listing and screenshots (tablet + phone), privacy policy page.
   - `v0.1.0` to internal testing, then closed testing (12 testers / 14 days).

Ideas for later: home-screen widget, solve statistics/streaks, global vacation mode, daily puzzle without alarm.

## Risks

- **Puzzle generation quality and speed** on large grids: solver budget plus iteration on presets with feedback.
- **OEM background restrictions** (some manufacturers kill apps): `setAlarmClock()` is the most reliable API;
  document workarounds.
- **Play review**: `USE_EXACT_ALARM`, full-screen intents and foreground-service types must be declared as core alarm
  functionality in Play Console.
