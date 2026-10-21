# Replay charge episodes and HUD join — October 13, 2026

## Recorded failure

Moondrop archive `2026-10-13_21-18-57_d88dde80-94f4-4511-b267-dc5dbd196ec7.odxmatch.zip` contains four accepted named Charged Moves:

| Time after GO | Attacker | Move | Reference type |
| --- | --- | --- | --- |
| 16.766 s | Sneasel | Foul Play | Dark |
| 61.311 s | Turtonator | Brutal Swing | Dark |
| 80.671 s | Turtonator | Brutal Swing | Dark |
| 107.730 s | Camerupt | Rock Tomb | Rock |

Raw announcement readings repeat at 17.904, 62.209, 62.471 and 82.281 seconds. Replay previously suppressed raw/canonical duplicates for only one second, but raw/raw duplicates for four seconds. The recorded regression therefore produced seven completed animations from these four accepted uses. Multiple animations could also compete for one sprite pose; a repeated unnamed announcement could become a separate, untyped action.

## Changes

- Correlate repeated raw, generic and named announcements within the same bounded four-second episode. A new Get Ready or Attack Incoming cue separates episodes. Independent accepted energy-spend Articles remain separate uses.
- Bound reconstructed preparation against the preceding merged charge, rather than its duplicate supporting readings.
- Preserve archived Articles and timestamps. The fix changes replay reconstruction, so existing archives benefit when reopened.
- Feather the HUD's upper contour over four display pixels, scaled with its width. The retained battle frame shows double-white compositing at the badge/HUD overlap, rather than an explicit normal-mode border. The body opacity, complementary corners, saved position and calibration geometry stay unchanged.

## Validation

- Baseline regression reproduced seven charge animations; the fixed recorded test produces four, with Dark/Dark/Dark/Rock reference types and no overlapping charge actions at sampled 100 ms intervals.
- Tests cover repeated unnamed announcements, a new preparation followed by another use of the same move, independent accepted uses close together, and unchanged archive serialization.
- Full JVM suite: **714 tests, zero failures/errors/skips**. Debug app and instrumentation APKs built successfully.
- Moondrop `MDPH00124112500414`: `ReplayChargeTypeDeviceTest`, **2 tests passed**. Actual phone reference data supplies all four recorded move types. Each sampled preparation/flight has exactly one charge icon. Reverse scrubbing restores the same type and attachment. The synthetic QTE check also verifies that the icon and sprite move upward together and retain horizontal alignment.
- Pulled and visually inspected phone renderings of recorded Foul Play, Rock Tomb, and synthetic graded Lunge. No new live battle was played by the agent.
- Eleven calibration/profile/HUD files had identical SHA-256 hashes before and after replacement installation. No MediaProjection session was active at installation.
- Installed debug APK SHA-256: `430a7d3eab26625d054be9dde506b6ae0562dacc7420b1f682024ae95fbc811a`.

The softened HUD join still needs a live visual check against Pokémon GO. This work does not establish that every Charged Move or QTE grade was observed accurately; an unresolved move continues to use a neutral marker.

Inspection logs and device captures: `/tmp/overdex-hud-seam-20261013/`. The regression fixture retains original Article IDs and payloads, filters to relevant testimony, and shifts only the monotonic clock origin to GO for readable test times.
