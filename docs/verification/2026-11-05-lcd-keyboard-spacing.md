# LCD alphabet spacing — November 5, 2026

The trainer supplied a compact-LCD mockup with a small suggestion chip and more
space between the letter rows. This refinement leaves the restored ODX-Fi
shell, tall CRT, 170 dp console, and vertical button columns unchanged.

## Change

- Removed the idle swipe-instruction line and idle tap instruction.
- Reduced the fixed suggestion strip and its candidate chips from 44 dp to
  24 dp, returning 20 dp to the keyboard grid. In the compact portrait LCD this
  increases key-row height from the 24 dp scrolling fallback to about 27 dp.
- Kept the strip's height constant through idle, matching, failure, and candidate
  states so the measured alphabet cannot shift during a swipe or its result.
- Clipped chip touch areas to the strip so they cannot extend into the first
  letter row. Real matching and no-match feedback remain visible.
- Retained QWERTY/ABC/token layouts, equal row allocation, letter font size,
  swipe recognition, explicit candidate selection, cancellation, instrument
  input, and the rotated binder keyboard.
- Exposed readiness through accessibility state instead of instruction text;
  tests wait for recognizer readiness directly.

Keyboard instructions still need a later How-to home. The remaining requests
stay in the [directory/LCD review queue](../plans/directory-and-lcd-review.md).

## Verification

- All 1,719 JVM tests passed, with no failures, errors, or skips.
- Debug and instrumentation APK assembly passed.
- API 34 emulator instrumentation: `OK (17 tests)` in 94.369 seconds. Includes
  compact shell geometry and letter entry, an immediate visible END key, a tap
  at the first letter row's top edge with candidates visible, repeated swipes
  with stable key bounds, candidate insertion, cancellation, instrument-input
  invalidation, token input, rotated world-coordinate gestures, registration
  navigation, and owned-Pokémon directory behavior.
- Separate read-only review found no blocking scope or gesture-geometry issue.
- `git diff --check` passed.

## Moondrop check

- Device USB serial: `MDPH00124112500414`.
- Confirmed no active Overdex service or MediaProjection before installation.
- Installed successfully. Installed APK hash matches the built APK:
  `a9fcc0e97ec9b6481cd2a66ad69494faf60cceb335c4ad030159ff1e336aa576`.
- Compared 49 protected files before/after installation: no files added or
  removed, only the Firestore local cache changed. Owned-card storage,
  preferences, calibration, registration, and replay settings retained their
  contents. Match archive inventories match exactly.
- Opened BATTLE → Add Pokémon → Species → START. Visually confirmed the fixed
  shell, removed instruction line, more space between letter rows, and visible
  bottom keyboard controls. Left the empty keyboard open for review. No card
  was created or saved.
- Native touch and swipe behavior passed on the emulator. The trainer's
  real-finger comfort assessment remains open.

Private backup and test evidence:
`/home/sean/overdex-device-backups/2026-11-05-keyboard-spacing/`.
