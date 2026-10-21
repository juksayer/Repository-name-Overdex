# QTE samples and evening match review — 2026-10-14

## Recordings inspected

Source device: Moondrop MD_PH_001, serial MDPH00124112500414. The installed app reported an update at 15:56:33 CDT. Read-only copies are in `/tmp/overdex-review-20261014-qte-17h/`; the phone originals were retained. All five ZIPs passed the archive inspector's structural and artifact-hash verification.

| File start (CDT) / Match ID prefix | Articles | Recording duration | Finding |
| --- | ---: | ---: | --- |
| 15:42:59 / 4d738d60 | 2,266 | 110.36 s | Win; Abomasnow Weather Ball and Energy Ball announcements. |
| 17:02:51 / 5fd865bb | 121 | 55.27 s | Setup session with no MatchStarted; no recorded battle to replay. |
| 17:03:59 / d8a62a07 | 3,041 | 258.96 s | Win; Grimmsnarl then Greninja; opponent Flareon, Typhlosion, Magmortar. Match starts about 176 s into the recording. |
| 17:08:46 / cd1b5371 | 2,990 | 229.87 s | Win; Snorlax then Charizard; opponent Flareon, Typhlosion, Magmortar. Match starts about 147 s into the recording. |
| 17:12:39 / 37273f0d | 3,283 | 183.98 s | Win; Samurott then Ceruledge; opponent Jolteon, Luxray, Magnezone. |

These are recorded observations, not an independently counted ground truth for every attack or proof of HUD delivery time. Later replay reconstruction can also differ from live knowledge.

The 17:08 match has corroborated Snorlax Lick and Flareon Fire Spin cadence evidence. Typhlosion Ember and an early Charizard Ember attribution are explicitly higher-energy tie-break assumptions; Charizard later resolves to Dragon Breath from shorter cadence. The 17:12 match has effectiveness-corroborated Jolteon Thunder Shock and configured/cadence-supported Samurott Fury Cutter. The provisional ties remain a validation boundary.

## QTE evidence gap

None of these archives contains `QteTypeIconsCrop`. The existing box was a calibration guide only. The user's observation that at least one clear moving icon crosses it per QTE is useful placement feedback, but no retained samples were available to validate icon recognition. Full-frame artifacts in this group are Team Select frames.

The newest recording has two retrospectively inferred QTE pulse patterns but no GetReadyWitnessed. A gate relying only on Get Ready would miss those QTEs. A charged-move announcement arrives too late to start sampling the preceding animation, so it must not reopen the gate retrospectively.

## Implementation

- `QteTypeIconSampling` opens a source-timed window after GetReadyWitnessed. It rejects expired/future cues, deduplicates repeated cues, and closes at the charge announcement, ten-second limit, 80-sample limit, match end, or observer stop.
- `QTE_TYPE_ICONS_CAPTURE` preserves only the calibrated crop, at up to eight samples per second. Source capture wall time, monotonic time, bounds, image hash, and witness availability use the existing custody path. No type or attack is invented by saving a sample.
- HUD **QTE BOX → SAMPLE 10s** starts a manual sample using the currently visible draft. The sample count advances only after the image and testimony are preserved. Editing is disabled during sampling; the box remains touch-through. Sampling does not save the draft as calibration.
- Automatic sampling reads the current draft when the guide is open, or the current saved calibration at the next QTE. Other regions are unchanged. The guide follows the current sampler across back-to-back matches.
- Compact and full archives retain these specifically named, bounded calibration samples for review, even before a classifier exists. Other uncited artifact selection is unchanged. Existing archives are not rewritten.
- The thin purple guide and its control panel remain visible in whole-screen captures. Keep the movable panel outside the sampled area. Samples are for inspection; the badge-icon matcher is not enabled as an unvalidated moving-QTE classifier.

## Validation

- Full `:app:testDebugUnitTest`, `:app:assembleDebug`, and `:app:assembleDebugAndroidTest` passed. **764 unit tests**, zero failures/errors/skips. Logs: `/tmp/overdex-qte-final-checks.log`.
- Installed the debug APK on the Moondrop with a data-preserving update, after confirming MediaProjection was inactive. Package update time: **17:43:29 CDT**. APK SHA-256: `e9ec3885c4c29deff8cdc225dffe39b58287cb2cf1ac8f22451584e2f31f7f7e`.
- **3 physical-device tests passed**: two `LiveQteCalibrationDeviceTest` tests and `QteTypeIconCaptureDeviceTest`. These verify exact physical guide placement, transparent interior/touch-through, draft sampling without saving calibration, disabled adjustment during sampling, saved-count display, CRT box availability, rejected cached frames, actual PNG pixels/bounds/times, and compact retention after custody/Timeline publication. Log: `/tmp/overdex-qte-device-tests.log`.
- Compared SHA-256 hashes of **11 calibration/layout files** before and after installation and device tests; all were identical. Backups: `/tmp/overdex-qte-calibration-{before,after}.tar`. Guide screenshot inspected: `/tmp/overdex-qte-guide-checked.png`. Returned the phone to the normal Overdex activity.
- A subsequent real Pokémon GO QTE is needed to verify moving-icon coverage and choose a classifier from recorded samples. Synthetic source images and touch tests do not establish recognition accuracy for all 18 types.

For that check, keep the calibrated box where the icons cross it. Open **QTE BOX** during a practice match; **SAVED** should rise after Get Ready. If automatic sampling does not start, tap **SAMPLE 10s** while the QTE is starting. Leave the panel outside the box and save the match normally; either archive mode retains these samples.
