# October 19 evening: charge gaps, move confirmations and Replay types

## Recordings reviewed

Two completed Moondrop battles were recovered among six new archive files. The
other four were short startup/menu recordings without combatant identities.
Both completed battles feature Abomasnow against Plusle, Electrode and Jolteon.

| Match ID | Start, local | Duration | Articles | Archive SHA-256 |
| --- | --- | --- | --- | --- |
| `ecf7380a-4df6-4252-925c-daf4a9c9c294` | 18:22:09 | 127.23 s | 2,140 | `0fd221f276767168b49f712393522028cc4648fb8d2d855a9aea65c9ba6d9308` |
| `908a4239-0f21-4f1f-9491-b477943d8d1e` | 18:50:04 | 127.24 s | 2,202 | `2d3e48a0526405ee4af41c5f42cd165d533a9ed868b22ad1ae8f8b4e9e373413` |

Neither recording contained the early typed Get Ready, Attack Incoming or QTE
milestone cues. Raw OCR included `Ge Keady` in the first and `Get Kedu` in the
second. Later move announcements arrived after the forecast had continued through
the charge interval. Neither battle recorded opponent energy-gain articles for
Electrode or Jolteon: the reported rise was in the live perfect-play forecast.

The newest recording loses the paired-bar view during approximately 79.37–88.72 s
and 96.79–106.29 s. The earlier recording also includes isolated apparent bar pairs
during effects, so a single pair is insufficient to resume extrapolation.

## Changes

- The live forecast reuses the existing HP tracker's results. It requires two
  separately captured paired-bar frames and fresh battlefield visibility. Missing
  bars freeze extrapolation, and stale captures expire after 500 ms. A brief
  recovered dropout can bridge up to 250 ms; a long gap cannot complete a move
  cycle. Missing bars are not published as proof of a QTE, switch or faint.
- Charge and decision cues explicitly pause the forecast. Stray hit evidence
  cannot restart it inside those holds. The derived Attack Incoming article is
  now also published to live subscribers, rather than only archived.
- Bounded repair of the fixed Get Ready phrase accepts the two recorded OCR
  failures while retaining raw text and rejecting unrelated phrases in tests.
- Evidence-resolved opponent Fast Moves receive checkmarks, remembered by species
  through switches. Higher-energy tie-break assumptions do not count as confirmed.
  Contradictory candidate sets remove an obsolete confirmation. Existing charged
  move checkmarks remain in place.
- Replay shows each active species' type icons beneath its team resources at the
  outside edge. Energy is shifted inward, with the opponent layout mirrored.

No elapsed-time forecast attacks were added to Reality Timeline, and archived
articles were not edited. Replay's recorded energy accounting was not replaced
with the live forecast.

## Validation and installation

`testDebugUnitTest` and `assembleDebug` passed: **932 tests, zero failures, errors
or skips**. Coverage includes the recorded Electrode/Jolteon gaps, isolated false
bar pairs, capture staleness, interrupted cycles, charge holds, damaged phrases,
move confirmations and existing Replay energy timing. The recorded fixture's
759 articles were checked for exact equality with their source articles.

On the Moondrop, the newest archive opened in Replay and displayed Grass/Ice for
Abomasnow and Electric for Plusle/Electrode, with energy between the icons and
center of the battlefield. UI bounds and a screenshot confirmed the requested
placement without overlap. The layout check preceded the final clock-only
adjustment; the Replay UI code was unchanged by that adjustment.

The final APK was installed using a normal replacement install, then launched:

- Device: Moondrop `MD_PH_001`, serial `MDPH00124112500414`.
- APK SHA-256: `4f1150156b4aafb7002b00c0991b4f2d04e0e1ab5e28b01a2f3887eb0a62d3d4`.
- All **175 match archives** matched their pre-install SHA-256 values.
- All **33 private settings, calibration and database files** matched the snapshot
  taken immediately before the final installation.
- No uninstall, data clearing or connected Gradle instrumentation was used.
- Backup and comparison reports:
  `/home/sean/overdex-device-backups/2026-10-19-evening-update/`.

## Remaining live boundary and audio

A fresh live battle is still needed to validate forecast freeze/resume timing with
current capture scheduling. Unit and archived-data checks do not establish live
recognition accuracy or complete Fast Move counts.

The user clarified that QTE-specific audio means both the music change and bubble
pops. The current result-music recognizer has Win, Loss, Battle and Team Select
references, but no QTE-state signal. No explicitly labeled QTE music or bubble-pop
references were found in the inspected combat-sound inventory. This update does
not claim QTE audio recognition; that remains separate work requiring identified
reference samples and false-positive testing against ordinary battle audio.
