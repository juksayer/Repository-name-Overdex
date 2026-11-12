# Replay premature HP drain — November 11, 2026

## Evidence and cause

Reviewed the newest Moondrop archive, match `e23c82aa-e814-47ac-b659-05485cdb7157`, copied without changing the phone. The 13,307,835-byte archive's SHA-256 was identical before and after transfer: `f7adee9b44b284b82ec363919e55bad938db9fc4117010753ba7f857ea7bcaac`. All 398 retained artifacts verified. It contains 3,205 Articles, including 544 active HP measurements. There is supporting start evidence but no witnessed GO/`MatchStarted`; elapsed times below are relative to recording start, not a claimed battle start.

At recording +82.152 seconds, Article `e405911b-8eda-4358-a1db-f1c78b6ee81c` reports opponent HP of 8.284% in a 173 × 23 rectangle at `(446,446)-(619,469)` in the 672 × 1493 capture. Stunfisk's established bar is approximately 192 × 18. The candidate passed independent width and height tolerances despite having only 70.5% of the established width-to-height proportion. The frame also generated the player Fast Move use `63f8094a-9b34-49cd-94e7-2f5ed20a99d1` from an HP border pulse.

The retained QTE crop 92 ms later shows GREAT. The exact candidate frame was not retained, so its particular visual source cannot be identified. Surrounding real HP observations contradict the low reading: approximately 70.74% before it and 69.68% afterward. Replay's per-combatant minimum then kept Stunfisk at 8.284%, swallowing subsequent real damage. The production Replay model reproduced that behavior from the unchanged archive.

## Change

The shared `ActiveHpBarGeometry.matchesSize` check now requires the candidate's width-to-height proportion to remain within 0.80–1.25 of its established anchor, in addition to the existing size tolerances. It does not constrain screen position or HP fraction. Live tracking and Replay therefore reject this incompatible shape through the same rule; Replay also rejects its co-timed HP-derived Fast Move cue. The original Timeline Articles remain intact.

An acceptance comparison over all 544 archived HP measurements changes exactly one decision: the false 173 × 23 candidate. All other 543 retain their previous size acceptance, including the three explicit charged-camera readings. Regression coverage exercises real recorded opening measurements, low HP, moving and centered bars, and empty outlines. This is an established-anchor safeguard; it does not establish that an incorrect first anchor or every other false bar is solved.

Reprocessing the original archive with the compiled production model gives:

| Recording time | Previous Replay HP | Corrected Replay HP |
| --- | ---: | ---: |
| +74.62 s | 85.19% | 85.19% |
| +76.18 s | 70.74% | 70.74% |
| +82.16 s | 8.28% | 70.74% |
| +87.80 s | 8.28% | 69.68% |
| +99.00 s | 8.28% | 55.85% |
| +101.90 s | 8.28% | 41.49% |
| +112.70 s | 8.28% | 20.21% |

Projected Fast Move cycles change from 24 to 23, removing only the false player cycle at +82.152 seconds. The before/after projections are separate files; no raw evidence was changed.

## Remaining Fast Move evidence gaps

The legitimate opening Stunfisk readings fall from about 99.47% to 85.19% and then 70.74% without matching recorded player Fast Move uses. Their observation gaps exceed the damage detector's 1.25-second continuity limit. Replay keeps those observed HP values; it cannot determine how many attacks occurred inside an unseen interval.

The live diagnostics also show a performance problem: the HP consumer received 677 enabled frames and skipped 1,783 intervening published sequence numbers. Locator elapsed time averaged 222 ms across those passes. All 544 submitted HP measurements reached the Timeline with no pending-submission evictions. These figures locate a significant bottleneck before Timeline persistence; they do not establish which optimization will fix sustained delivery. Broad/local bar-location cost and attention during QTE remain follow-up work. The continuity limit is unchanged, and this repair does not fabricate missing attacks.

The match's WIN was recorded from a complete YOU WIN text read. Three faints were recorded, but Stunfisk's earlier loss and Azumarill's final loss lack the necessary ball-count transitions. This recording confirms neither complete faint coverage nor the partial-result-text fallback.

## Validation and device boundary

Focused geometry, acquisition, terminal/charged policy, and recorded-Replay regressions passed. The full unit suite passed **2,410 tests in 316 suites**, with zero failures, errors, or skips; debug and Android test APK builds succeeded. The disposable API 34 emulator reported **OK (11 tests)** for the live charged-camera/terminal HP pipelines and the recorded Pixel empty-bar image at native and delivered resolution. `git diff --check` passed.

Independent review compared 5,036 measurements from eight recent Moondrop/Pixel archives. Only the known false shape and an older isolated 174 × 22 low outlier are newly rejected. The older outlier also contradicts adjacent same-combatant readings; no retained image establishes its exact visual source. All other dimensions retain prior size acceptance, including 1,157 low-HP measurements except those two outliers and both exact zeros. These are comparisons of stored measurements, not proof of every pixel classification.

The regression fixture preserves 101 exact original Articles; independent comparison found no changes to them. Private source copies, artifact verification, diagnostics, before/after model projections, and test logs are retained in `/home/sean/overdex-device-backups/2026-11-11-moondrop-hp-review/`.

Frozen APK: `overdex-hp-shape-guard-debug.apk`, 251,393,110 bytes, SHA-256 `2e4674b586caf8c3c56c15b2f1020ebe2bcff97121346a79c1d208139c918a8c`.

The review initially left the phone on its November 11 08:04 installation. At the user's subsequent request, the exact frozen APK above was installed on Moondrop `MDPH00124112500414` on November 11 at 11:14:54 (device local time). Both Droidball and MediaProjection were inactive before replacement. A verified 395,456,000-byte backup preserved 440 durable files and the previous APK; loose capture artifacts and generated caches remain on the phone.

The installed APK hash matches the frozen tested artifact. All 440 preexisting backed-up files were unchanged immediately after installation. After launch, only the Firestore cache database and `files/profileInstalled` changed; saved settings, calibration, archives and other records remained unchanged. The app reached its home screen, stayed running, and produced no new crash-buffer entries. Droidball remains stopped for the user to start before the next battle. A fresh physical battle is still needed to validate live recognition.
