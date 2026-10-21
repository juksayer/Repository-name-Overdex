# Replay HP impacts, KO poses, and decision timer — 2026-10-14

Checkout: `/home/sean/AndroidStudioProjects/Overdex`, branch `odx-recovery`.
Device: Moondrop `MD_PH_001`, serial `MDPH00124112500414`, Android 14.
Evidence directory: `/tmp/overdex-review-20261014-impact/`.

## Recording reviewed

`2026-10-14_09-53-54_4f6f09a9-1360-494d-8b28-c354721884d7.odxmatch.zip`

- SHA-256: `a2a67c738dd82cad97057ab68406502b88d9f1fcc50e862945f8b285d13b0de8`.
- 9,968,560 bytes; 3,162 articles; all 988 declared artifacts verified.
- 149.074 seconds recorded; GO at approximately 27.359 seconds; WIN at 130.583 seconds.
- Player active identities include Abomasnow and later Camerupt. Opponents are Combusken, Magcargo, and Flareon. Roster entries are not evidence that every configured Pokémon fought.
- 127 HP measurements, but only four damage-tick articles and four canonical Fast Move uses. These counts are not ground truth.
- The old detector accumulated multiple changing HP levels while waiting for an identical confirming frame. Two late damage reports combined roughly 69 and 62 percentage points of loss into individual reports. Earlier raw measurements contradict treating these as single attacks.

## Changes

The live HP detector accepts one-pixel fill jitter, confirms an earlier loss when a later distinct lower level arrives, and dates the loss to its first measured change rather than its confirmation. A capture gap over 1.25 seconds reacquires the baseline without classifying all unseen loss as one hit. This is still an HP measurement, not a claim that every decrease has been correctly attributed to a Fast Move.

Replay can recover supported HP steps from the retained measurements. Recovery resets at entries, faints, charge/decision windows, and match boundaries. Reconstructed uses carry `REPLAY_CORROBORATED_HP_STEP` plus original source article IDs. Contradicted aggregate damage articles remain in the archive and may still inform HP level, but cannot independently manufacture a fresh attack. A move's measured HP loss is aligned to that move's displayed impact. Association is bounded to its source readings, not the whole animation interval. Timing correction for separate settled losses is bounded to half a reference cycle so intermediate draining levels cannot queue attacks into the future.

The new opening regression fixture retains original payloads, article IDs, and relative capture times, with GO as zero and unrelated/media articles omitted. Its first three supported Powder Snow hits are displayed at one-second intervals, with opponent HP at approximately 95.24%, 92.06%, and 87.77% at their impacts. The original archive is unchanged. Existing idle-player, HP-regeneration, switch, charged-impact, and Incinerate regressions remain acceptance checks.

KO color and opacity now remain intact through the stagger and fall. Desaturation starts only at the end of the fall and takes 350 ms. The pose is calculated from replay time, so pause, speed changes, and backward scrubbing are deterministic. The visible HUD positioning arrow is removed; its existing 14 dp touch target and accessible `Move HUD` description remain.

## Decision timer

`DecisionTimerCrop` is separate from the opening countdown and the inactive-card switch timer. Its default rectangle is X 440, Y 170, W 200, H 220 on a 1080×2400 reference screen. It is available as **Decision Timer** in Match Calibration and persists through normal and named-profile saves. Existing calibration fields are not migrated or offset.

The reference is the supplied `/home/sean/Desktop/ overdex related/Screen_recording_20260916_092303.mp4`: the shield dial is visible around 25 seconds. Small crops of observed 4, observed 3, and absent sky are retained as tests. These are game pixels, not generated numerals.

During live combat, a dedicated witness receives that crop at a requested 250 ms interval. A neutral circular dial with a white rim and repeated legal OCR readings (0–15) are required. Shared OCR is used only when idle, leaving priority work ahead of the timer. Two absent-dial observations clear the timer; an unreadable number alone does not. Requested cadence is not a guarantee of delivered frames.

`BattleDecisionTimerObserved` preserves the number or disappearance, raw OCR, capture time, and cited crop. The archive mapper and article inspector support it. Replay interpolates between observed seconds, marks interpolation in its state, draws the dial between the two resource badges, and suppresses Fast Moves during the window. Live Fast Move inference also remembers that interval so late-arriving HP noise from the pause is rejected. A bounded expiry prevents a missed clear from suppressing the rest of the match.

Context labels are used only with supporting incoming-charge or player-faint evidence. Older archives have no newly invented numeric readings; a witnessed player faint followed by a different player entry can show an unnumbered replacement pause. Missing shield timer footage cannot be recovered from this new witness retroactively.

## Validation

- Full unit suite: **739 passed**, zero failures, errors, or skipped tests.
- Debug app and Android test APK builds: passed after the final production changes.
- Four device tests passed on the Moondrop: actual recorded timer-crop OCR, replay timer display/interpolation/clearance/backward scrub, both KO sprite poses, and HUD reading/position-editor access.
- The timer OCR test read the real recorded **4** and **3**, and rejected the absent-dial sky sample. The replay timer UI test uses synthetic timer articles and checks that pause noise produces no Fast Move.
- Inspected `screenshots/replay-ko-poses.png`: both sprites retain color while falling and when first lying down, then finish gray. Inspected `screenshots/replay-decision-timer.png`: the dial and supported shield-choice label sit between the resource indicators.
- The older idle-match regression initially caught intermediate draining levels being scheduled as extra later attacks. The bounded retiming correction passed that original HP threshold without weakening it; an explanatory assertion message was added.
- All **11** calibration preference/profile files have identical hashes before and after installation. Snapshots are `/tmp/overdex-review-20261013-summary/install/before-impact-timer-20261014.json` and `after-impact-timer-20261014.json`.
- Installed with `adb install -r` after confirming no active projection; no uninstall or app-data reset. No commit or push.

App APK SHA-256: `855209efd20fcb3e2120bdc39dfd5412c898f488515be1a98369b0bf637b295c`.
Test APK SHA-256: `89123cd0ef0cbbc933e3100e9a378f3e975e6343c47f5108b15a361e4041232a`.
Logs: `full-build.log` and `device-tests.log` in the evidence directory.

A new live match remains necessary to verify the timer's calibrated placement, capture/OCR delivery, early shield selection, and replacement clearance on this phone. Synthetic timer articles validate replay projection, not live recognition. Sparse or obscured HP observations still cannot provide a verified count or damage allocation for every attack.
