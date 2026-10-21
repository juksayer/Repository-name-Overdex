# October 20: new matches, false faints and announcement formatting

## Recordings reviewed

Nine newly discovered archives were copied from the Moondrop and checked against
their device SHA-256 hashes. ZIP integrity and every declared artifact passed the
archive inspector. Six contain completed battles; three are startup/menu
recordings, including one session left open for roughly 42.5 minutes.

Times below are local, America/Chicago. Duration includes recorded menus.
Fast Move counts are recorded observations, not verified ground-truth totals.

| Local start | Match ID | Duration | Articles | Recorded player Fast Moves |
| --- | --- | ---: | ---: | --- |
| Oct 19 20:35:23 | `16bc89d9-6fca-4c58-a8c5-07a5dda29f54` | 157.609 s | 3,176 | Camerupt 9; Abomasnow 0 |
| Oct 19 20:38:05 | `bb3803c8-e872-40dc-9d97-9ab452645ddb` | 148.474 s | 3,103 | Gourgeist 13; Abomasnow 0 |
| Oct 19 20:40:37 | `74745a98-1851-4f97-bb3e-42abd742e6ad` | 90.322 s | 1,670 | Abomasnow 18 |
| Oct 19 22:51:56 | `acfc3208-a234-41a6-bbd1-609cf85d3d2a` | 156.014 s | 2,835 | Abomasnow 24; Gourgeist 5; unresolved species 6 |
| Oct 20 07:47:14 | `0561cfd3-4be3-430d-9cac-d099d23a206d` | 101.166 s | 1,909 | Abomasnow 16; Camerupt 3 |
| Oct 20 07:52:56 | `945e6a6a-3deb-4b50-9897-64ea40df1202` | 132.347 s | 2,204 | Raichu 17; Hydreigon 22 |

All six contain MatchStarted and a WIN MatchEnded. The user confirmed that
Abomasnow was deliberately idle in the 20:35 and 20:38 battles; their zero counts
are expected.

All three opponent species appear on the correct side in each completed battle.
The newest match identifies Sneasel's Ice Shard and Sealeo's Water Gun from
cadence/motion evidence; Vaporeon's Water Gun comes from its single available
Fast Move. Raichu's later Thunder Shock identity remains explicitly marked as a
higher-energy tie-break assumption, not a confirmed move.

This morning's species publication delays were roughly 0.6–1.2 seconds from
their source observations. Older recordings still include much longer delayed
publications (including a stale Abomasnow read at about 12.6 seconds). This is
not a complete latency fix.

## Charge pauses and audio

There are **nine typed GetReadyWitnessed cues** across the six battles. No
FastMoveUseObserved article falls in the examined interval from 0.5 seconds after
each cue through the earlier of 12 seconds after the cue or the next charge
announcement. This checks those recorded intervals only. Some charge sequences
lack an early cue, and the live forecast itself is not archived, so this does
not prove that every live energy pause is correct.

All six archives contain non-silent audio snippets at 48 kHz, two channels.
This establishes capture availability, not QTE music/bubble recognition. No new
audio classifier is introduced by this change.

## False faint traced to the HUD

In `0561cfd3`, the resource count dropped from two to zero at approximately
59.707 seconds. The cited crops contain Overdex's pale sprite cards with question
marks, not the game's Poké Ball strip. Their dark shapes passed the previous
silhouette checks and falsely fainted Sealeo. A subsequent real count of one
could not undo the faint tracker's monotonic survivor count. The newest match
also has an unsupported zero from HUD pixels.

The resource witnesses now pass each captured frame's existing `overlayBounds`
to the counter. HUD pixels cannot support either Poké Ball or shield counts.
The upper glyph caps must be uncovered; an obscured cap cannot be inferred from
its dark lower rim. With visible caps, the counter can still use a partially
exposed badge: a bright red cap or dark spent cap supplies slot evidence, and
badge-background coverage is measured against visible pixels.

This does not alter the HUD's appearance, saved crop positions, raw PNGs, or
archived testimony. Existing recordings retain their erroneous historical count
articles; the new reader prevents those source crops from creating future false
counts when processed with the update.

The 23 unchanged source strips and provenance are in
`app/src/test/resources/battle/resources-20261020/`.

## Announcement crop and parser

The inspected announcement crops are 572 × 56 within a 672 × 1493 source frame,
covering `(50,475)` to `(622,531)`. Their top edges visibly slice through letters.
Examples include:

| Visible announcement | Preserved OCR |
| --- | --- |
| Hydreigon used Brutal Swing! | `ar\nOn Used Brutal Ning:` |
| Abomasnow used Energy Ball! | `ADOmlasnoW USedEnergY Dali:` |
| Combusken used Rock Slide! | `mbusKen used KocK\nIIae` |
| Flareon used Overheat! | `FlareOn used\nVerneat!` |

The parser now collapses OCR whitespace for matching and permits a missing space
after `used`, retaining the original raw text. Complete legal names such as
`Brutal\nSwing` and `usedEnergy Ball` resolve in regression tests. Illegal
movepools, ambiguous mirror attribution and insufficient clipped prefixes remain
unresolved.

This formatting fix does **not** claim to repair the heavily clipped examples
above. The Announcement Draggy Box needs its top edge expanded upward until
whole letters fit. Saved calibration coordinates were intentionally left intact.

Unchanged source example:

![Hydreigon announcement with letter tops cut by the crop](evidence/2026-10-20/945e-announcement.png)

Four original PNGs and their source articles/hashes are retained in
`evidence/2026-10-20/announcement-provenance.json`.

## Validation and installation

Focused counter, announcement and faint-tracker tests passed. The complete
`testDebugUnitTest` suite and `assembleDebug` passed: **936 tests, zero failures,
errors or skips**. All 23 new resource fixtures matched their source hashes.
Existing covered-badge, spent-resource and faint-tracker regressions also passed.

Before installation, all 184 on-device match archives were matched to local
backups. A separate backup contains 21,302 private files; all payloads were read
back successfully. All 33 settings/calibration/database files matched their
pre-install hashes. Backup location:
`/home/sean/overdex-device-backups/2026-10-20-match-review/`.

The update was installed using normal `adb install -r` on the Moondrop
`MD_PH_001`, serial `MDPH00124112500414`. No active MediaProjection session was
present before installation. No uninstall, data clearing or connected Gradle
instrumentation was used.

- APK SHA-256: `2da6246347dbb0705f04b27137fd07bc3e6a7d2d097f42f9b8a43bdb924e7ae9`.
- All **184 archives** matched their pre-install hashes after installation.
- All **33 settings, calibration and database files** matched their pre-install
  hashes after installation, before launching the app.
- All three copied databases passed SQLite `quick_check`.
- A cold launch completed successfully; MainActivity remained resumed and the
  process stayed alive. The checked AndroidRuntime error log was empty.

A fresh live battle remains necessary to validate resource counts with actual
HUD movement and to compare recorded Fast Moves against performed attacks.
