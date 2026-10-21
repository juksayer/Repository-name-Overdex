# Replay combatant lifetime and charged-move icons — 2026-10-13

## Recorded finding

Reviewed Moondrop recording `2026-10-13_17-34-14_b5a66d17-1eef-437f-bd56-f9144da0591d.odxmatch.zip`:
5,518 articles, 902 artifacts, 13,347,066 bytes. Times below are relative to its recorded Match Start.

| Time | Preserved evidence |
| --- | --- |
| 122.778 s | Player ball count decreases; Camerupt is marked fainted. |
| 123.203 s | The lingering player badge still reads Camerupt. |
| 123.934 s | Player badge OCR reads `gbl`. |
| 124.006 s | Announcement reads `Go, gbl!`. |
| 125.921 s | Player charge label 2 reads LUNGE. |
| 127.958 s | Player charge label 1 reads DISCHARGE. |
| 141.022 s onward | Player hits were attributed to the lingering Camerupt identity. |
| 167.625 s | WIN result recorded. |

The configured team uniquely associates the pair Lunge/Discharge with Galvantula. Replay now reconstructs
that player appearance at the nickname entry, citing the badge, entry and both labels. It does not infer
a move use from a label. Ambiguous pairs, one label, intervening faint/identity contradictions, and missing
badge anchors do not resolve an identity. Original archived articles remain unchanged.

Faint state now prevents further replay projectiles from the fallen actor. Live move-use, cadence and
energy inference also reject a lingering species badge after its faint; correctly timed pre-KO evidence
can still receive historical energy. Replay does not transfer old, incorrectly attributed energy to the
replacement or fabricate a complete energy total.

## Charged-move presentation

Preserved named announcements can resolve the move and type even without a live energy-spend article.
An exact player button label plus its sampled contact followed by Get Ready can identify the preparation.
Neither path invents a missing energy-spend article. A preparation without a closing cue expires without
inventing a completed strike. The identified type icon grows under the attacker and is thrown toward the
recipient. It does not borrow the species' own type. HUD corner geometry was not edited.

The newest recording's player QTE at 33.56 s remains unresolved: it has Get Ready and a vibration pattern,
but no named player move or qualifying button contact. Its later SHADOW BALL label establishes an available
move, not which move was used. Seed Bomb versus Shadow Ball cannot be chosen honestly from that evidence.

## Validation

- Full unit suite: **680 passed**, zero failures/errors/skips.
- Debug application and Android test APK builds passed.
- Installed with `adb install -r` on Moondrop `MDPH00124112500414`, after confirming no active capture.
- `ReplayCombatantReturnDeviceTest` passed with the reduced regression fixture and again with all 5,518
  articles from the original recording (only monotonic origin shifted for the test).
- `ReplayChargeTypeDeviceTest` passed: raw-announcement-to-type wiring, growth at QTE milestones, player
  and opponent directions, same type during flight, and reverse scrub restoration. This is a synthetic
  timing test of production rendering, not a claim of live charge-recognition accuracy.
- Visually inspected device captures: [recorded KO/return/result](evidence/2026-10-13-replay-return/replay-combatant-return.png),
  [QTE icon below attacker](evidence/2026-10-13-replay-return/qte-type-excellent.png),
  [icon in flight](evidence/2026-10-13-replay-return/qte-type-player-flight.png).
- All **11** saved calibration/profile/HUD-layout files had identical before/after hashes.

The source archive is retained in `/tmp/overdex-replay-ko-20261013/latest.odxmatch.zip` and on the phone.
Logs are in `/tmp/overdex-replay-ko-20261013/`. The regression fixture retains 225 original identity,
control, move and lifecycle articles without media or OCR word geometry. A fresh live battle is still
needed to validate the new inference guards under capture; device replay tests validate the saved record.
