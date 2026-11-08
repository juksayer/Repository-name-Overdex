# Concession with reserves: result observation never opened

The November 8 Moondrop concession did not fail to understand a result reading.
It never captured an outcome crop. The user reports Registeel entered, used a
few Fast Moves, stopped attacking, and conceded during ordinary Fast Move play.
The visible YOU WIN screen then remained unrecognized for approximately a minute.

## Preserved recordings

Three new internal archives were copied without modifying the phone originals.
Desktop copies match device SHA-256 hashes. Manifest counts and every declared
artifact verify:

| Match | Local start | Articles | Verified artifacts | Recorded result |
| --- | --- | ---: | ---: | --- |
| `35d5f541-8396-41aa-9623-fc7e82dd26a0` | Nov 7, 23:31:17 | 2,323 | 366 | None |
| `3172d184-ba06-4cb4-8d21-119f3a0dd4bd` | Nov 7, 23:33:28 | 2,749 | 381 | WIN |
| `4d0780a6-714d-4427-969a-a5d2fc652df0` | Nov 8, 08:23:49 | 2,300 | 329 | None |

The latest archive is 10,720,887 bytes, SHA-256
`7b1cc59ffa4219f623e37c981adbcf51ee6e839307285f0e12db77f328fc4fe2`.
It contains MatchStarted, Registeel entry at source +35.143 seconds, player balls
remaining at three, and opponent balls remaining at two. The final measured HP
at +38.920 seconds is approximately 77.4% player and 88.2% opponent. User Stop is
source +110.139 seconds. Times are relative to this archive's MatchStarted.

`MATCH_OUTCOME_CROP_CAPTURE` reports unavailable and never becomes available.
There are zero MatchOutcomeCrop Articles, zero outcome OCR latency completions,
zero result music measurements and no MatchEnded. Result OCR therefore had no
image to read; native-reader priority or text normalization cannot explain this
particular miss. In the preceding 23:33 battle the same result reader accepts
YOU WIN and records WIN at source +99.180 seconds.

## Source pixels and calibration

Eight latest loose full frames were copied and verified against their
content-addressed hashes. Four show YOU WIN. The saved normalized outcome box
resolves on the 672 × 1493 source to X 100–568, Y 627–888: 468 × 261 pixels. It
contains the complete result lettering. The source for the native fixture is
`c6076aafdb5a11ebbc3a1bd8c70bbfc43c68a691af4689fea9b4271f5d59956b`.
Its exact outcome crop hashes to
`3246fc83b57ac4b507250d5ad3c1e1e3136f423fe5ce68a7da78f5e3709f418e`.

These loose files have no known canonical capture clock. Their filesystem
modification times do not establish an exact concession or result timestamp.
No original archive was rewritten and no finishing KO was invented.

## Repair

The existing fast outcome gate opens only for last-member HP/absence, zero balls
or recognized result music. A healthy team with reserves can concede without
any of those cues. A new independent `MATCH_OUTCOME_PROBE_CAPTURE` checks the
same calibrated result crop every two seconds after combat is established by
GO or real HP evidence. It yields whenever the existing urgent window is open
and stops when a result is accepted.

Probe crops have a separate bounded reference queue and NORMAL native priority.
They never enter the URGENT near-KO queue. Visual inspection retains promising
text frames across later blank samples, while weak/failed inspection preserves
the ordinary latest-crop OCR opportunity. The previously announcement-specific
queue was renamed `PendingTransientTextCrops` without changing its behavior.
It stores artifact references, not Bitmaps.

The typed result still requires actual YOU WIN or GOOD EFFORT text. Reserves,
missing bars, inactivity and the probe itself do not decide an outcome. Source
pixels, clocks, raw OCR and the text → phrase → MatchEnded evidence chain remain
intact. Stop and a decisive result cancel all three reading lanes.

## Validation and live boundary

The full unit suite passes: **2,048 tests across 281 suites, zero failures,
errors or skips**. Debug and instrumentation APK builds pass. New regressions cover
healthy reserves after more than a minute, unavailable ball counts, missed GO
with real HP context, urgent-window suppression/restoration, normal-priority
selection, retained win before blank, weak inspection fallback, source clocks,
cancellation during inspection/native reading, and cancellation of all lanes.

Two emulator-only tests use the exact private source image and the actual
Android text reader. The first verifies that the saved normalized calibration
selects pixel-identical result pixels and that native OCR reads YOU WIN. The
second uses the production artifact store, probe, reader, phrase witness and
Match: healthy teams with three balls each use only NORMAL admission, retain a
win before a later battle control frame, and produce exactly one WIN with the
win frame's own source clocks and evidence chain. `OK (2 tests)`; no skips or
substituted OCR. Test capture clocks and team state are explicitly synthetic,
not recovered timestamps for the old loose frame.

The fresh API 37 Google Play emulator initially failed both tests at model
availability preflight. Its Google components were still bootstrapping. After
automatic Play services installation completed (26.11.36 → 26.43.30), the same
tests and APK passed without code or dependency changes. Initial failure and
successful retry reports are both preserved privately. The disposable emulator
was stopped after validation; existing user emulator data was not used.

## Physical update

Moondrop `MDPH00124112500414` had no active Overdex service or MediaProjection.
The app was stopped for a consistent private backup of settings, databases,
calibration, teams and archives, excluding only the loose artifact store.
All 410 backed-up file hashes match the device. The 352,845,312-byte backup
hashes to `4999d608525e9de783dab78a832037675b83d0deedd23944e8b127c3ef07dd30`.

Replacement installation succeeds. The installed base APK exactly matches the
tested build SHA-256:
`06763275a96331573ac2164e00302f6bdd7bcead819c84e4a269abe03b06851e`.
All 410 saved files have identical hashes before and after installation, with
no missing or added files before relaunch. MainActivity cold-launches in
3,730 ms, remains resumed and has no fatal process-log entries. No live battle
or new Droidball recording was started during this check.

Exact source screenshots, calibration, private fixture manifest, hashes and
diagnostic reports remain under
`/home/sean/overdex-device-backups/2026-11-08-result-concession/`.

A fresh concession on the updated physical phone remains the live acceptance
check. The result must produce a cited outcome crop, actual accepted result
text, one MatchEnded and a saved completed archive; the probe must then stop.
