# Replay countdown cadence — October 13, 2026

Match Replay now reconstructs a complete, evenly spaced `3 → 2 → 1 → GO`
presentation for an established battle. Each glyph occupies one replay second;
the existing playback speed scales every beat equally.

`ReplayCountdown` retains the original glyph Article IDs and observation times,
the supporting evidence IDs, and the basis for placing GO. Recorded Match start
anchors the display when available. An observed GO, a numeral supported by later
combat, or timer support can supply a recovered anchor. With only later combat,
the display uses a labelled introduction; it does not declare a precise
`MatchStarted`. The LCD identifies reconstructed timing and unknown start time.

Raw Articles, timestamps, archive contents, combat events, and energy accounting
are not rewritten. Reconstructed beats are presentation metadata, not new
`CountdownGlyphWitnessed` testimony. Persisting broader prerequisite deductions
as separate Timeline Articles and correcting Team Select/VS capture gates are
not part of this presentation change.

## Recorded verification

Device: Moondrop MD-PH-001, serial `MDPH00124112500414`.

Source archive:
`2026-10-13_18-21-13_9bb4954a-a532-4961-843e-2f16ee2521cd.odxmatch.zip`.
It contains 462 Articles over approximately 35 seconds. Its original countdown
testimony includes only `3` and `2`.

Times relative to recording start:

| Glyph | Original observation | Recreated display begins |
| --- | --- | --- |
| 3 | 25.100 s | 25.367 s |
| 2 | 26.367 s | 26.367 s |
| 1 | Not witnessed | 27.367 s |
| GO | Not witnessed | 28.367 s |

The existing derived Match start remains at 28.367 seconds. GO is displayed
until 29.367 seconds. No battle event is moved to achieve this cadence.

## Checks

- 690 unit tests passed, with zero failures/errors/skips. Countdown tests cover
  repeated readings, missed glyphs, each isolated numeral, GO-only recordings,
  missing all glyphs, incomplete attempts, restarts, seeking, 0.1×/1×/5× playback,
  and unchanged archive serialization.
- Debug APK and instrumentation APK built successfully.
- `ReplayCountdownDeviceTest` passed on the Moondrop using the actual archive.
  All four glyphs were displayed at the start and last nanosecond of their
  respective one-second intervals. GO disappeared at the next boundary.
- Captured `1` and `GO` frames were visually inspected in the production replay
  battlefield. This checks reconstruction/rendering, not new live OCR accuracy.
- The APK was installed with replacement, after checking capture was inactive.
  All 11 calibration/profile/HUD preference file hashes remained unchanged.

Installed debug APK SHA-256:
`93bcb724da1c2907c4f74e3e512adf75f1264e0b3d59d14b52fbe2465f790bdd`.
