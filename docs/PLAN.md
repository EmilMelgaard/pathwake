# Pathwake development plan

Pathwake is an alarm clock that can only be dismissed by solving one or more randomly generated line-drawing
puzzles (in the style of the panels in *The Witness*). It has the features of a modern alarm app, but **no snooze**.
Tablets are the primary target; phones must work too (puzzles may be smaller).

This document is the source of truth for scope and decisions. Each chunk is delivered as a pull request with a CI-built
APK, demoed on tablet and phone emulators, and iterated on with feedback before the next chunk starts.

## Decisions

| Topic                      | Decision                                                                                                   |
|----------------------------|------------------------------------------------------------------------------------------------------------|
| Name                       | **Pathwake** (`io.github.emilmelgaard.pathwake`). The description may say the puzzles are inspired by *The Witness* (revisit if Play objects); the name and art stay original. |
| Puzzle mechanics           | Dots, broken edges, colored squares, stars, triangles, tetris pieces (fixed + rotatable), subtractive tetris, elimination marks, multiple starts/exits, symmetry |
| Difficulty                 | Preset levels per alarm (Trivial, Easy, Medium, Hard, Expert, Brutal), each mapping to grid size + mechanics. Trivial is a straight line, like the door panels in the game |
| Puzzles per alarm          | Configurable per alarm (1-10)                                                                              |
| Escape hatches             | "New puzzle" button (same difficulty); auto-silence: never / after N minutes / when the sound ends          |
| Volume while solving       | Setting: full / lower while drawing / mute while drawing (back to full after ~10 s idle); fades down fast, up slowly |
| Anti-cheat                 | Setting (on by default): volume keys and other apps can't turn down a ringing alarm. Changing alarms never needs a puzzle |
| Puzzle look                | Witness-inspired panels (original art, glowing line). A "Material" look option was dropped after testing |
| Rules                      | Never explained in the app (no spoilers): players find them out, or learn them in *The Witness*. Saving an alarm with a difficulty never solved warns once |
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
  - Data: Room 2.8 (alarms), DataStore (settings), manual dependency injection via an `AppContainer` (no Hilt).
    The database, settings and custom sound files live in device-protected storage so alarms can be rescheduled and
    ring after a reboot before the user unlocks the device (direct boot).
  - Alarm engine:
    - Scheduling: one `AlarmManager.setAlarmClock()` alarm for the next alarm time, with `USE_EXACT_ALARM` (Android 13+)
      or `SCHEDULE_EXACT_ALARM` (Android 12). Rescheduled after every change, on (locked) boot, time and time-zone
      changes and app updates. Direct-boot aware: alarms ring after a reboot before the first unlock.
    - Ringing: a `systemExempted` foreground service (allowed for apps with the exact-alarm permission) plays
      `USAGE_ALARM` audio, which Android 17's background audio hardening also allows, and posts a full-screen-intent
      notification that opens the lock-screen ringing activity with the puzzles.

## Alarm features

- Multiple alarms with label and an on/off toggle; sorted list with a "next alarm in X h Y min" summary.
- Repeat on weekdays, or one-time on the next occurrence or on a specific date.
- Pause date ranges per alarm (for example vacations), and skip the next occurrence.
- Sound: default, silent, system alarm tones, or custom audio files (copied into the app); vibration, preview.
  Alarm volume and gradual volume increase are app-wide settings; an alarm can have its own volume.
- Puzzle difficulty and puzzle count per alarm, with a "try it" preview.
- Auto-silence (never / N minutes / when the sound ends) and a missed-alarm notification.
- Undo after delete, duplicate alarm, and defaults for new alarms.
- Settings: alarm volume, gradual volume, volume while solving, anti-cheat, defaults for new alarms, theme
  (system/light/dark, dynamic color), color-blind marks, first day of week.
- No snooze.

## Chunks

Each chunk ends with a demo (screenshots on the tablet and phone emulators), a PR, and a feedback round.

1. **Puzzle engine core and playground** *(done, #2)*
   - `:puzzle` module: model, validator, generator, solver and tracer for dots, gaps, colored squares, stars,
     triangles, and multiple starts/exits.
   - Puzzle panel UI with touch drawing, success/failure feedback, both looks.
   - A temporary "Puzzle playground" home screen: pick a difficulty, generate a new puzzle, toggle the look.
   - *Feedback on:* drawing feel, visuals, puzzle variety.
   - Status:
     - Generation: on desktop, Easy and Medium take under ~100 ms; Hard to Brutal average 150-175 ms (max ~0.5 s).
       Each run is capped at 2.5M solver steps.
     - Easy and Medium solution counts are exact. For larger puzzles where uniqueness can't be proven within budget,
       the generator accepts the puzzle once randomized probes find no alternative solution ("few solutions").
2. **Advanced mechanics and difficulty calibration** *(done, #3)*
   - Tetris (fixed/rotatable), subtractive tetris, elimination, symmetry.
   - Solver-checked generation, 5 calibrated presets, generation-time budget on low-end devices.
   - *Feedback on:* difficulty curve.
   - Rule details:
     - **Tetris:** the pieces in a region must tile it exactly. Subtractive pieces cancel blocks of positive pieces,
       so positives may overlap or stick out where negatives cover them. Equal positive and negative areas cancel
       completely, and then the region can be any shape.
     - **Elimination mark:** erases exactly one other symbol or uncovered dot in its region. The region must be broken
       without it, otherwise the mark itself is an error. Marks don't count towards star pairs.
     - **Symmetry:** the drawn line's mirror image is drawn too (left-right, top-bottom or 180° rotation). Lines may
       not touch each other or the axis. Line 0 (blue) starts at the start that comes first in reading order; colored
       dots must be covered by the line of their color. Either start can be dragged.
   - Presets:
     - Trivial: a straight track from a start to an exit on a square panel (a 3×0 grid), like the door panels.
     - Easy: 3×3, no triangles.
     - Medium: 4×4, may use symmetry; triangles are rarer than the other mechanics.
     - Hard: 5×4 or 5×5, may add elimination.
     - Expert: 5×5, always one of subtractive tetris or elimination.
     - Brutal (called Fiendish before): 6×6, one or two advanced mechanics.
     - All levels draw from the base mechanics: dots, squares, stars, triangles, tetris.
   - Caps: at most 8 stars and 2 elimination marks per puzzle. Colored squares always come in two colors or more.
   - Drawing: dots lie under the line (hidden when covered) and only show on top of it while they blink as an
     error. Rotatable tetris pieces are tilted, except shapes that look the same rotated (single blocks, squares).
   - Generation on desktop: Hard averages ~85 ms, Expert ~165 ms, Brutal ~285 ms (max < 1 s).
3. **Alarm management** *(done, #4)*
   - Room data model and next-occurrence calculation (repeat days, dates, pauses, skip, DST/time zones), fully
     unit-tested.
   - Alarm list and editor (adaptive list-detail on tablets).
   - No ringing yet.
   - *Feedback on:* UX.
   - Decisions:
     - Alarms repeat on weekdays, or ring once: on a picked date, or else at the next occurrence of their time.
       Pauses and "skip next" only apply to repeating alarms.
     - Times in a DST gap ring after the gap (02:30 becomes 03:30); in an overlap they ring at the first 02:30.
     - The editor has an explicit Save, which also turns the alarm on. Leaving with unsaved changes asks first.
     - Deleting shows an Undo snackbar. Duplicating copies everything except a skipped day.
     - The list highlights the open alarm on tablets; the playground is reachable from the list ("Try puzzles") and
       from the editor ("Try a puzzle" at the alarm's difficulty).
4. **Alarm engine** *(done, #5)*
   - Scheduling, reboot and time-change handling, direct boot.
   - Ringing service and lock-screen ringing activity with N puzzles, "new puzzle", auto-silence.
   - Permission onboarding (notifications, full-screen intent).
   - *Feedback on:* real overnight tests on phone/tablet.
   - Decisions:
     - Auto-silence is set per alarm: after 1, 5, 10 (default), 15, 20 or 30 minutes, or never. A silenced alarm
       leaves a "Missed alarm" notification.
     - Late alarms behave like the stock Android clock: an alarm that is at most 15 seconds late when the app catches up
       (device was off, clock changed) still rings; otherwise it shows a "Missed alarm" notification (up to 12 hours
       back) and one-time alarms turn off.
     - An alarm cut off while ringing (reboot, crash) rings again until its auto-silence time, with the same puzzle:
       puzzles are generated from a stored seed. So restarting the device doesn't get around the puzzles.
     - Alarms due at the same time ring together, with the hardest difficulty, the most puzzles and the longest
       auto-silence among them. An alarm due while another rings joins it and keeps the progress.
     - Back doesn't leave the puzzles. Home does, but the alarm keeps ringing; the notification and a banner in the
       alarm list lead back.
     - Sound: the system's default alarm sound, looping, with vibration. Before the first unlock after a reboot that
       sound can't be read, so a bundled tone plays instead. Custom sounds and volume options come in chunk 5.
     - Permissions: notifications are requested when the first alarm is saved. The alarm list warns (with a button to
       fix it) when notifications, exact alarms (Android 12) or full-screen intents (Android 14+) are missing.
5. **Sounds and settings** *(done, #7)*
   - Tone picker, custom files, gradual volume, vibration, preview.
   - Settings screen: theme, puzzle look, volume while solving, anti-cheat, defaults.
   - Decisions:
     - Per alarm: sound (default, silent, a device alarm tone or your own file), vibration, and when to stop ringing
       (after N minutes, never, or when the sound ends: the sound plays once).
     - App-wide (settings, behind the gear in the alarm list): the device's alarm volume with a test button, gradually
       increasing volume (off, 15 s to 5 min), volume while solving (default: lower while drawing, to 25 %, back to
       full 10 s after the last touch), "keep ringing alarms loud", defaults for new alarms, theme, dynamic color
       (Android 12+), puzzle look and first day of the week.
     - The puzzle requirement for changing alarms that ring soon is dropped. "Keep ringing alarms loud" (on by default)
       ignores volume-down while the puzzles show and restores the alarm volume if anything lowers it while ringing.
     - Custom sounds are copied into device-protected storage (max 30 MB), so they keep working if the original file
       moves and they play before the first unlock. Deleting one resets the alarms using it to the default sound.
       Device tones can't be read before the first unlock, so the bundled tone plays then; if the chosen sound fails
       later on, the default and then the bundled tone take over (and "when the sound ends" falls back to 10 minutes).
     - Alarms ringing together: the first audible sound wins, vibration if any wants it, "never" stops beats minutes
       (the longest wins), and "when the sound ends" only applies if every alarm uses it.
     - Settings are stored with DataStore in device-protected storage.
6. **Polish** *(done, #8)*
   - App icon, edge cases (overlapping alarms, phone call while ringing), accessibility and color-blind pass.
   - Decisions:
     - Icon: an alarm clock (bells, feet) whose face is a puzzle panel (4×4 grid, Panel colors). The lit line runs
       from the start circle at 9 o'clock to the center and up to the exit at 12, so it is also the clock's hands.
       Adaptive with a monochrome layer for themed icons.
     - Test alarm (alarm menu and editor, which tests the unsaved settings): rings now like the real alarm (sound,
       vibration, volume settings, puzzles) with a Cancel button. A test doesn't change the alarm, leaves no
       notifications and isn't resumed after a restart; a real alarm that comes due replaces it, and a ringing real
       alarm can't be tested over.
     - Phone calls: while a call rings or is active (also VoIP), the alarm plays at 1/8 volume, like the stock clock's
       in-call alarm, and keeps vibrating. When the call ends it is loud again and takes the audio focus back.
     - Backup: Auto Backup keeps alarms and settings only. Custom sound files only move with a device-to-device
       transfer (they can be too big for the cloud quota); the scheduler, ringing state and permission flags are
       never copied. After a restore the alarms are set right away, without opening the app; alarms whose sound file
       didn't come along use the default sound.
     - Color-blind marks (setting, off by default): colored squares and stars get a dark mark per color (orange bar,
       green vertical bar, magenta ring, cyan dot, yellow cross, blue plus; black and white none).
     - Accessibility: Trivial puzzles have a screen-reader action ("Draw the line"), so the alarm can be stopped
       without sight; section titles are headings; weekday buttons grow and wrap with large text.
     - The app waits (up to 1 s, normally a few ms) for the settings before drawing its first frame, so it doesn't
       flash the wrong theme. The theme now uses the full generated color scheme (it mixed in default purple text).
   - Feedback after testing v0.1.0 *(#10)*:
     - Touching the puzzle fades the alarm down quickly (0.3 s from full to silent) and back up slowly (3 s).
     - Own volume per alarm (off by default), with the same slider and test button as the app-wide alarm volume.
       While it rings, the device's alarm volume is set to it and restored afterwards (also after a restart). Alarms
       ringing together use the volume of the alarm whose sound plays.
     - Saving an alarm with a difficulty (other than Trivial) that no puzzle of has been solved yet (in the
       playground with the solution hidden, a test or an alarm) offers to try one first, once per difficulty. The
       warning, the playground and About say the rules can be found out by experimenting or learned in The Witness.
       Puzzle progress is backed up.
     - The Material puzzle look is gone, the permission warning shows whenever a permission is missing (not only with
       alarms on), and vibration is off by default for new alarms.7. **Release**
   - GitHub releases *(first: v0.1.0, a pre-release)*: tags build a signed APK, published on GitHub with its
     checksum and signing certificate fingerprint. One release key signs every build; it is meant to become the
     Play app signing key too, so GitHub and Play installs can update each other (see RELEASING.md).
   - Google Play (waiting for a developer account): Play policy audit, store listing and screenshots (tablet +
     phone), privacy policy page, internal testing, then closed testing (12 testers / 14 days).

Ideas for later: home-screen widget, solve statistics/streaks, global vacation mode, daily puzzle without alarm.

## Risks

- **Puzzle generation quality and speed** on large grids: solver budget plus iteration on presets with feedback.
- **OEM background restrictions** (some manufacturers kill apps): `setAlarmClock()` is the most reliable API;
  document workarounds.
- **Play review**: `USE_EXACT_ALARM`, full-screen intents and foreground-service types must be declared as core alarm
  functionality in Play Console.
