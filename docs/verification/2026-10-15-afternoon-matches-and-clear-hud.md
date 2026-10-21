# October 15 afternoon recordings and permanent announcement clearance

**Historical HUD layout:** the user subsequently clarified that a permanent
clear strip was not intended. The compact fixed HUD and separate energy estimate
supersede that presentation below; see
[the follow-up verification](2026-10-15-opponent-energy-forecast-and-compact-hud.md).
Archive findings and the pending-use attribution fix remain applicable.

Reviewed the internal archives from Moondrop `MD_PH_001`, serial
`MDPH00124112500414`. Read-only copies and inspection outputs are in
`/tmp/overdex-review-20261015-afternoon`. Original phone archives were not edited.

## Recordings

All five ZIPs passed manifest, timeline, and declared-artifact hash verification.

| Archive time / Match ID prefix | Articles | Artifacts | Recorded evidence |
| --- | ---: | ---: | --- |
| 11:35:35 / c6d147cf | 114 | 0 | Session/configuration, no recorded combat |
| 12:24:04 / be58e791 | 115 | 0 | Session/configuration, no recorded combat |
| 12:26:02 / 4ab9e28a | 312 | 34 | Short attempt; countdown and GO, no result |
| 12:27:15 / 64c400ba | 4,166 | 1,139 | Completed battle, WIN |
| 13:44:48 / 916d1aca | 2,661 | 743 | Completed battle, WIN; no GO testimony |

The two completed archive SHA-256 hashes are:

- `64c400ba-e36c-4d6e-b545-a531f99af1af`:
  `5d713283f72395958703229bda0ebca4d67ad3b3e174e5f43fa80d7ceeafcf4e`
- `916d1aca-c10b-49a6-9c1b-4dd8590ec8ff`:
  `9c6f9646569ad815025df49d37fe472ce05e07616e348bdb4854f6c57fea7e44`

The earlier completed battle records player Abomasnow/Camerupt and opponent
Sneasel/Sealeo/Vaporeon, 645 HP measurements and 52 canonical Fast Move uses.
The latest records player Abomasnow/Camerupt and opponent
Combusken/Magcargo/Flareon, 344 HP measurements and 31 canonical uses.
These are recorded counts, not manually verified ground truth. In particular,
the latest original record assigns only four uses to Camerupt despite two
50-energy charged moves. Counting and identity timing remain incomplete.

The Sealeo Powder Snow identity in the earlier recording rests on cadence;
Magcargo's Ember in the later recording is explicitly a higher-energy tie-break
assumption. Neither is reported here as independently confirmed. Combusken's
Ember has effectiveness evidence. An unresolved or provisional name must not
be presented as proof of exact energy.

## Audio

Both completed archives contain non-silent, mono 48 kHz WAV evidence: 66 distinct
clips in the earlier recording, 37 in the latest. Reused artifacts explain why
AudioCaptured article counts are slightly higher (67 and 38). Clips overlap, so
summing their durations is not the length of a continuous recording.

The earlier match recognizes WIN music with similarity approximately 0.965
(next candidate approximately 0.395), agreeing with the visual WIN. The latest
contains Camerupt's Rock Tomb announcement and an attack-down audio cue, with
the derived change attributed to the opponent. There is no accepted result-music
testimony in that latest archive. Non-silent audio alone does not prove reliable
cry or Fast Move name identification.

## Corrected pending Fast Move attribution

In `916d1aca`, Camerupt's species badge was captured at +43.282 seconds but
published at +45.382. A border-pulse impact at +44.205 was already pending. The
species handler flushed that pending impact using the old Abomasnow appearance
before installing Camerupt's identity.

`FastMoveUseInference` now retains observed appearance boundaries per side,
looks up identity at the evidence's capture time, reassigns unpublished pending
and charge-held evidence when a delayed boundary arrives, and flushes only
pre-boundary uses on a switch. Already published Articles and original archive
contents remain immutable. This is not permission to invent missed attacks.

The regression fixture retains twelve original Articles, their original order,
IDs, payloads and timestamps. Its separate provenance file identifies the
original ZIP and hash. Tests cover the recorded handoff, late old evidence,
independent sides, charge-held evidence, unknown identity and restart isolation.

## HUD behavior

The user reported that the timed transparent opening missed the announcement.
The OCR-triggered retry, cooldown and 450 ms restoration path have been removed.
Normal announcement OCR continues to consume current preserved crops.

The HUD now reserves the calibrated announcement band continuously. Entire move
rows and controls flow above or below it; a transparent opening removes the HUD
background from that band as well. This keeps the badge attachment and preserves
access to the move list and QTE calibration control. The mapping uses the full
physical screen and actual window position, without subtracting a guessed
notification-bar height or modifying saved calibration coordinates.

Capture metadata conservatively retains the window's bounding rectangle, which
can include the transparent opening; no fabricated game pixels are substituted.
This approach still depends on the announcement box covering the real text.

## Validation

- Full unit suite: **794 passed**, no failures, errors or skips.
- `:app:assembleDebug` and `:app:assembleDebugAndroidTest`: passed.
- Installed with `adb install -r` after confirming no active MediaProjection.
  No uninstall, data reset, commit or push.
- `HudAnnouncementReadDeviceTest`: **1 passed** on the Moondrop. Retained
  `Go, Plusle!` text was placed under a real floating HUD. More than 98% of
  sampled pixels in the permanent opening matched the unobstructed background;
  OCR read Plusle. The opening remained clear after 1.5 seconds. Every move row
  and the QTE button stayed outside the opening; QTE and position controls were
  reachable. The test does not run an actual Pokémon GO battle.
- All **13** calibration/profile/layout files had identical SHA-256 values
  before and after install and phone testing. Backups and comparisons are in
  the review directory. Overdex was reopened after the test.
- `git diff --check`: passed. Existing unrelated work was preserved.

App APK SHA-256:
`4b60b818f182423b1da51721d57cfac0a57ed2a795e40ad2ab290c0a05789573`

Test APK SHA-256:
`fd2f850d8009990933cc881c5f88945a67343b7da4bfb446b93bc2fd8ce10d90`

The first focused run exposed missing signal fields in the new synthetic pulse
test helper, and the geometry expectations needed to allow the conservative
one-pixel outward rounding of fractional Float coordinates. Both were corrected;
the original recorded-fixture attribution test passed. One compilation retry
added the Compose constraint-helper imports. Final verification above used the
corrected source, without relaxing the device pixel or OCR assertions.

Logs: `final-build.log`, `hud-device-test.log`. Inspected phone screenshot:
`hud-still-clear.png`, all under the review directory.

Remaining live acceptance: play a new battle to measure announcement latency,
actual-vs-recorded Fast Move counts, and the new layout under real species/move
changes. A retained-text screenshot test is not a live battle accuracy claim.
