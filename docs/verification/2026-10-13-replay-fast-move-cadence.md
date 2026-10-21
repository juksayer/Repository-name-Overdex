# Fast Move reference timing and recipient HP — October 13, 2026

## Behavior

Known Fast Moves now have a complete replay cycle using the duration retained
with their identification, falling back to the move catalogue's turn count.
The October 13 recording identifies Volt Switch as 2,000,000,000 ns. Previously
every attack used the same 450 ms visual, and separate canonical witnesses could
render impossible bursts of that two-second move.

`ReplayFastMoveSchedule` coalesces witnesses inside a known move's minimum
interval and snaps small capture jitter to the next cycle boundary. Each side
has its own schedule. It never queues rejected duplicates into later idle time
or fills missing observations with additional attacks. Entry, switch, faint,
charge-sequence and Match boundaries limit cycles. Unknown-duration moves keep
their existing short presentation rather than acquiring a fabricated duration.

For known cycles, `ReplayHpTrack` collects nearby accepted HP readings into a
single recipient damage chunk at the projectile's impact. The brief draining
tail belongs to the same hit. Actual measured fractions supply the damage;
move power is not treated as an HP amount, and a pulse without HP measurements
does not invent damage. Independent measurements outside supported cycles and
charge damage retain their own display times. Returning combatants retain their
HP, fresh entrants start full, and noisy later reads cannot regenerate health.

The sprite still performs a short, sharp bounce. Projectile travel follows the
complete reference cycle; playback speed changes the common replay clock.

Raw Articles, timestamps, live move inference, and recorded energy totals are
unchanged. Presentation actions retain source Article IDs, original witness
times, and a reconstruction flag. Aligned HP trails also identify reconstructed
display timing.

## Opening identity finding

The full 17:34 recording initially let the later Gourgeist badge overwrite the
first player-hit interval. A nickname badge plus the two recorded Discharge and
Lunge labels positively support Galvantula early in the recording. The opening
switch heuristic now respects such confirmation instead of assigning those
attacks to the replacement and using its different move duration.

This does not establish the precise subsequent switch time. The user may have
switched before successful species OCR; the configured lead and the late badge
alone cannot prove which Pokemon was active throughout that gap.

## Verification

- All 700 unit tests passed (zero failures, errors or skips). Added coverage for
  impossible Volt Switch clusters, independent half-second moves, boundaries,
  unknown durations, recipient attribution, measured orange chunks, reverse
  seeking, missing damage, charge damage, replacement HP and opening identity.
- Existing deliberately idle recording and returning Galvantula regressions
  passed. Debug and instrumentation APKs built successfully.
- `ReplayFastMoveImpactDeviceTest` passed on Moondrop MD-PH-001,
  serial `MDPH00124112500414`, using the unmodified Timeline from
  `2026-10-13_17-34-14_b5a66d17-1eef-437f-bd56-f9144da0591d.odxmatch.zip`.
  At the reconstructed impact two seconds after GO, the Volt Switch icon and
  the opponent's measured HP chunk render together. The test also checks the
  pre-impact frame, reverse seek, and unchanged archive serialization.
- The impact screenshot was inspected: player left, opponent right, Electric
  icon arriving at the opponent, and orange recipient HP chunk/border visible.
  Local evidence: `/tmp/overdex-replay-fast-impact-20261013/impact.png`.
- Installed with replacement after confirming no active screen capture.
  All 11 calibration/profile/HUD preference files have identical before/after
  hashes. No calibration migration or reset was performed.

This validates replay scheduling and rendering. It does not establish that live
capture observes every attack or resolve every uncertain switch in the archive.

Installed debug APK SHA-256:
`ab89d9b260dda0ff0698c706cea23eb8caa7b91098e9e64ef7e7856fd207e1fc`.
