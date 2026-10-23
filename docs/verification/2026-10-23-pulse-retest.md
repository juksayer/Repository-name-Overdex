# October 23 paired pulse-fix retest

User confirmed three Incinerates, pause, three more, then a charged move; other phone idle. Both devices had the 95588f3 build installed before this test. Copies are under `/home/sean/overdex-device-backups/2026-10-23-pulse-retest/`; phone originals remain untouched.

| Device | Match | Articles | Verified artifacts | SHA-256 |
| --- | --- | ---: | ---: | --- |
| Moondrop | 50d253c2-bb9b-4949-a279-bac0195b49f1 | 4071 | 393 | 8028ae468fc70a14fd4eee3b916515eeaee31291c62b82567efd0973542f89b8 |
| Pixel 4 | dbbd8461-df71-4417-8dbb-47bbff80df20 | 5321 | 676 | 7e63d0d44bce8f966eebb8397934a5514f2afefdb913c27fea56549ac63a35d8 |

ZIP structure, manifest article counts, and every declared artifact hash passed the inspector. Pixel recorded LOSS, Moondrop WIN. Moondrop has no MatchStarted article; its times below use its first article, not an invented GO. Pixel times use its observed MatchStarted (11091104512073 ns). Moondrop first article is 19508495019162 ns. Do not subtract the two hardware clocks to align captures.

## Improvements supported by this recording

Both archives contain six opening Turtonator uses in two groups of three. Pixel times are 2.76, 5.16, 7.81 then 14.21, 16.83, 19.43 seconds after GO. Moondrop times are 33.72, 36.28, 38.67 then 45.18, 47.61, 50.24 seconds after its first article. Both preserve the gap without extra attacking-side uses. Pixel resolves Incinerate from the measured impact stream on the third use (source time 7.81 s; not a measured publication latency).

Pixel incoming-attack cues occur at 28.14, 80.27, 120.83 s, with the new shield-text fallback independently supporting them at 30.17, 83.27, 122.37 s. Named impacts occur at 35.58, 88.47, 128.45 s. No FastMoveUse occurs between each incoming cue and its named impact. The earlier recording's blue-border burst is not repeated in these three intervals. This supports progress, not proof that every border detection is correct.

## Remaining discrepancies

1. Pixel records one false idle-player Swampert attack at 27.777 s, deriving +9 Mud Shot energy. Its basis is a supposed 93.62% HP loss on Turtonator. The bar moves horizontally from (334,440)-(526,458) at 27.465 s to (281,446)-(472,464) at 27.777 s; measured fill falls from .98936 to .05319. Incoming-attack text is captured at 28.144 s. This implicates measurement continuity during the camera transition, before the explicit pause cue. The user confirms the idle side did not attack.
2. Pixel records 19 Turtonator uses against Moondrop's 18. The unmatched Pixel use is at 89.629 s, just 1.157 s after the second Brutal Swing announcement. Its .2962963 HP loss occurs while the bar relocates from the bottom of the screen into the charged-impact view. The matching Moondrop post-charge reading is already .20212767, then ordinary losses of approximately .08 resume. This strongly suggests charged damage was misclassified after the fixed 750 ms impact tail, rather than a missed Incinerate on Moondrop. Do not treat nineteen as ground truth or suppress arbitrary large losses without phase evidence.
3. Pixel names all three charged moves: Brutal Swing, Brutal Swing, Dragon Pulse. Moondrop names only the first and third, so its charged-energy spending remains incomplete.
4. Moondrop misses the start testimony. Real impacts still enable accounting, but countdown/start presentation must remain explicit about its reconstruction basis.

## Next implementation targets

Protect HP damage baselines across horizontal/camera relocation. Then use phase/HP settling evidence to close charged damage attribution instead of assuming all damage beyond 750 ms is Fast Move damage. Preserve raw measurements and distinguish inference revisions from archive edits. No additional user test is necessary to establish these two defects; this paired recording is sufficient for regression work.

## Implemented HP continuity guard

The live damage witness now passes the measured rectangle to its detector, rather than only fill and width. Horizontal displacement beyond 3% of the anchored bar width (minimum 4 px), width changes beyond 10%, or vertical relocation beyond the larger of three bar heights / 20% of width break continuity. The anchor does not follow successive small horizontal drifts. After relocation the detector waits for 250 ms of geometry stability and reacquires the current fill without publishing the difference as one hit. Smaller vertical bobbing and locator jitter remain continuous. Species reset clears the geometry and fill state together.

Regression fixtures preserve the original Pixel numerical measurements, confidence, and GO-relative monotonic timestamps. The idle-camera fixture reproduces the old .9362265 false loss and emits no loss through the geometry-aware path. The opening player-HP fixture still yields all six Incinerate losses. A focused test also verifies real losses resume after reacquisition and ordinary bobbing does not erase them.

This guard affects new derived damage/attack/energy evidence, not archived raw measurements. It deliberately does not attribute damage across an uncertain camera relocation to a single attack. It does not solve the separate charged-impact tail or missing charged-move OCR issue, and is not a retrospective repair of existing replay HP readings.

Continuity validation: all 1,055 JVM tests passed (zero failures/errors/skips); debug APK build passed. New live-battle acceptance remains pending.
