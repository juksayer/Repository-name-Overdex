# Recorded no-player-charge review — 2026-10-14

Checkout: `/home/sean/AndroidStudioProjects/Overdex`, branch `odx-recovery`.
Device: Moondrop MD_PH_001, serial `MDPH00124112500414`, Android 14.
Local evidence: `/tmp/overdex-review-20261014-no-charge/`.

## Recordings and ground truth

The user confirms **no player shields and no player Charged Moves in the latest match**.
This does not exclude opponent Charged Moves. A species appearing before GO is expected.

| Recording | Bytes | Articles | Verified artifacts | Result |
| --- | ---: | ---: | ---: | --- |
| `2026-10-14_11-10-29_d5f90d3e-f06e-45be-99d3-e2c67047394b.odxmatch.zip` | 7,473,505 | 2,195 | 603 | WIN |
| `2026-10-14_11-25-40_f2a8010b-c042-44b6-a82f-ab09d7e9db7a.odxmatch.zip` | 17,860,362 | 4,511 | 911 | LOSS |

SHA-256, respectively:

- `6cbc531d57ad2afddfc7952e3074bc1ccaae43b131f14d2c2bd451123c1f2bc2`
- `287a1bf5832bdf7f1287248f3677992005c5922da9ed9bf4ec463499a35c5098`

Both result-music comparisons agree with the recorded result. Original phone archives were only read/copied.

The latest record contains four named opponent charges, at these times relative to GO:

- 20.386 s: Sneasel, Foul Play.
- 62.389 s and 76.734 s: Sealeo, Water Pulse.
- 97.922 s: Vaporeon, Aqua Tail.

There are no player energy-spend, Get Ready, or accepted QTE-pattern articles. The only player shield measurement is two. A further clear `Attack incoming!` was read at 108.291 s, without a resolved closing move name. It supports an incoming-charge pause, not an invented named impact. LOSS is recorded at 121.662 s.

Sneasel was identified 1.438 s before GO. This is expected early identification, not a timing defect: the species name appears before GO. Player identities are Abomasnow, Camerupt, and Gourgeist; opponent identities are Sneasel, Sealeo, and Vaporeon. Earlier record: player Weather Ball (Ice), then Energy Ball twice. These three charges must survive stricter QTE handling.

## Reproduced defects and corrections

1. **False player QTE during opponent charges.** Before the change, the latest record produced Nice/Great/Excellent from raw accelerometer pulses preceding a generic charge announcement. Replay now requires a player Get Ready cue or a resolved player charge. A known opponent performer disqualifies this recovery. Unattributed motion remains raw evidence. The earlier record retains its real player QTE and all three charges.
2. **Only the first incoming-charge warning was published.** `PersistedAttackIncomingWitness` latched `accepted` for the whole match. It now rearms between appearances, retaining capture time and source links. Replay also accepts the retained raw announcement for older archives, so the warning at 108.291 s starts a bounded opponent preparation and excludes Fast Moves. An unresolved closing announcement does not acquire a fabricated impact or type.
3. **Decision timer default clipped the dial.** The timer capture and reader were active, but neither archive contains accepted timer readings. Compact export omits uncited crops, so this absence alone does not mean capture was disabled. Retained full frames show the cause: the previous default cut through the numeral on the Moondrop. The default now covers X 440, Y 80, W 200, H 310 on the 1080×2400 reference. A bounded vertical/size search requires the white arc, neutral interior, and dark circular rim. Existing saved rectangles are not rewritten. A detected dial gets one normal-priority OCR read instead of being discarded whenever OCR is busy; the worker remains conflated and urgent species/result reads retain priority.

## Evidence and regression scope

The full-frame SHA filenames used for the new timer fixtures are:

- observed 3: `5269904875dd1681d7f0dd7213109a4fe1658ed18ae04c5365588bdc99c1be53.png`
- observed 1: `184ca893a28ab64841f58eda737ddf08b61bd0e781a20fc54e9f213d4d912001.png`
- absent dial: `c9eb0726abef816145e63e1f97bc495608047ed7c77a92fff7da93ddb13aa621.png`

These are actual 672×1493 captured frames. Fixtures crop `(273,49)-(398,243)` using the same floor/round convention as `BattleCropResolver`. The original video-derived 4/3/absent fixtures remain checks for the older layout.

The first device run exposed an additional OCR failure on the recorded 1: the dial was detected, but the recognizer returned empty text. The reader now falls back to connected white numeral components, excluding the larger ring and sky, and enlarges them as dark text on a light background. Adjacent glyphs with the same baseline/height stay together for two-digit readings. The original artifact and geometry are unchanged. The fallback does not infer a number from elapsed time.

Replay JSON fixtures retain original article IDs/payloads, omit media and unrelated observations, zero wall dates, and shift monotonic time to the first recorded article. No testimony was changed to match the user's expected outcome. The new tests initially reproduced two failures: false player QTE grades and missing later opponent preparation. They also check that archive serialization stays unchanged.

The latest live ledger has 43 fused Fast Move uses (22 player, 21 opponent), 40 damage-tick articles, and 1,377 raw HP measurements. These are observation counts, not verified ground-truth attack counts. This review does not establish complete move counting or accurate energy totals. The timer correction still needs a fresh live match to assess capture/OCR delivery under battle load; old archives do not gain numeric timer testimony from the new witness.

## Validation

- Final full unit suite: 746 passed, zero failures/errors/skips, including the numeral-isolation regression. Debug app and instrumentation APK builds succeeded.
- Three physical Moondrop checks passed: recorded timer pixels resolve 3 and 1; the actual witnesses publish repeated incoming-charge episodes, timer readings, and timer disappearance with source links; the replay displays, counts down, clears, and seeks through the timer. The replay display check uses synthetic testimony, while the witness check uses retained real timer pixels and announcement text. These are not a substitute for a fresh live battle under capture/OCR load.
- Both final APKs were installed with replacement installation, preserving app data, and Overdex was launched afterward. App APK SHA-256: `bbe3d10b0a5cf73998422780619bdb5305801d2ed7d90d53831ae8146dfbada4`. Test APK SHA-256: `5915d62e0863a2eca6f5a6ce1e947a1b32cd923f34b45ec2f3fa08310f2690c2`.
- Before/after snapshots of all 11 calibration preference/profile files are identical. Saved crop and HUD positions were not rewritten.
- Final build and device logs: `/tmp/overdex-review-20261014-no-charge/full-build-final.log` and `/tmp/overdex-review-20261014-no-charge/device-tests-final.log`. `git diff --check` passed.
