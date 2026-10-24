# Audio attribution installation and new Moondrop recordings

## Installation

- Device: Moondrop MD_PH_001, serial `MDPH00124112500414`.
- Commit: `b3707b7`, on `odx-recovery`.
- APK SHA-256: `84c6fb1ea2d3cb80ff3945ae13117e8ecf78322dbbffb6514a86207f4ead1ef8`.
- In-place update succeeded; Android reports `lastUpdateTime=2026-10-24 12:34:24`.
- No Overdex service was running before installation. The Directory Tree opened
  normally afterward, showed IDLE, and did not start a capture session. No
  Overdex fatal exception appeared in the saved startup log.
- Before installation, copied and hash-verified the three new archives. Also
  saved a readable 267-file backup of preferences, databases, calibration
  profiles/reference, battle records and replay-editing data. The earlier full
  private-directory backup remains available separately.
- Compared file hashes immediately after installation and after launch. All
  preferences, calibration files, the owned-card/team database files and replay
  data were unchanged. Only the Firestore cache database changed. All 50 archive
  filenames remained present. No uninstall, data clear, or phone instrumentation
  was used.

The APK was already covered by 1,101 passing unit tests and the API 34 HUD test
documented in [the implementation report](2026-10-24-fast-audio-attribution.md).
This installation adds a physical startup check, not a live battle acceptance
test. Both reviewed battles below were recorded on the preceding build.

## Preserved recordings

| Recording | Match ID | Size | Articles | Verified media artifacts |
| --- | --- | ---: | ---: | ---: |
| 12:13:45 | `38a08a67-d2bc-4398-adfb-a815f6e97564` | 11,199,118 bytes | 3,064 | 391 |
| 12:16:21 | `84c8ec6d-dbbc-4165-9ca6-e3b3d68b55b2` | 7,190 bytes | 124 | 0 |
| 12:21:59 | `6fcb0fe7-5cac-44db-82e6-e160d145e7f0` | 19,409,978 bytes | 4,556 | 575 |

All original archives remain unchanged on the phone and desktop. Local hashes
matched phone hashes; each declared retained artifact passed size/hash checks.
Additionally copied 184 retained full frames from the relevant recording
intervals, totaling 166,471,087 bytes, and verified every content-addressed hash.
Filesystem frame times are approximate indexing aids, not exact event clocks.

Archive SHA-256 values, in table order:

```text
18808bf98fcb68743d6d36c3d0cf35a0793a951bc99c368354faebc1022054be
eab31b955535e95d6ac2bb16ad51ea9b4f32888733956d50c80c12e233a1e470
6ba425dbc76c1a22609684ce1a94c0eb9cf0d2695f3b8d86be772c60010ae659
```

Review outputs and private device backups are under
`/home/sean/overdex-device-backups/2026-10-24-audio-attribution-install/`.
They include archive inspection reports, `review-summary.json`, source frames,
`mawile-evidence.json`, contact sheets, settings/data comparisons and startup
evidence. Source screenshots and personal settings were not added to Git.

## Newest battle: useful audio confirmation and a form error

The player roster observed at Team Select was Aegislash, Flygon and Mawile.
The configured team copied at session start was different, so configured move
knowledge is not ground truth for every observed member. The user confirmed in
this review that Mawile had **Fire Fang** and Flygon had **Dragon Tail**.

Audio was present: 48 kHz, two channels, 50 retained audio captures and 31 Fast
Move sound measurements. Eleven measurements passed the reference-match
acceptance flag; that flag and its similarity scores are not accuracy rates.

Three early sound measurements favored Mud Shot. A `FastMoveIdentified` for
opponent Stunfisk cited `AUDIO_REFERENCE_REPEATED_UNIQUE_LEGAL_SIDE`, published
about 8.9 seconds after the recorded MatchStarted source time. The preserved
battle screenshot visibly contains a Mud Shot checkmark in the HUD. Thus the
existing strict confirmation path reached the display in this battle, before
the new attribution/preference update was installed.

Mawile's incorrect Ice Fang identity has a concrete cause:

1. The battlefield frame `22e3ec08b5d02d21f36475f2f845d12c36e87e0e66b626030e04fb725676d91f`
   shows **Galarian Stunfisk**, while the HUD displays the ordinary Stunfisk sprite
   and ordinary-form charged-move list.
2. The opponent species Article says only `Stunfisk`, as does the native badge.
   The catalogue lookup uses ordinary Stunfisk's Ground/Electric typing. The
   bundled catalogue separately contains Galarian Stunfisk as Ground/Steel.
3. At recording +89.397 seconds, `SUPER EFFECTIVE!` against the opponent was used
   with that wrong defensive typing to select Ice Fang for Mawile. The identity
   was published 5.502 seconds after its source observation.
4. After Forretress was recognized and further effectiveness text arrived,
   inference corrected Mawile to Fire Fang. The original wrong testimony still
   exists in the archive; review has not rewritten it.

This is an unresolved-form error, not evidence that the user changed Fast Moves
during battle, and not a demonstrated failure to read the effectiveness phrase.
Next identity work should prevent an unqualified species name from silently
locking the base form's types and movepool when battle-relevant forms differ.
Until form evidence resolves that ambiguity, effectiveness must retain the
compatible possibilities instead of issuing a confident single-move identity.
This review does not claim that opponent-form recognition has been fixed by the
audio update.

The newest recording contains 46 derived Fast Move uses (23 per side). These
are recorded counts, not hand-verified ground truth. Four explicit faint
testimonies exist: Mawile, Forretress, Stunfisk and Aegislash. Each side's measured
ball count decreases 3 -> 2 -> 1 without increasing. End-of-battle screenshots
show one bright opponent ball and two dark balls, including the retained badge.

The last result crop clearly reads `GOOD EFFORT!`; MatchEnded records LOSS,
published 1.630 seconds after that crop's source time. That interval measures
capture-to-publication, not the delay from the first visible result frame.
The saved result frame still shows the overlay; there is no subsequent retained
frame proving its post-publication dismissal.

## Earlier recording and short session

The 12:13 recording has a MatchStarted and 26 derived Fast Move uses (13 per
side), but no MatchEnded. Audio was present, including accepted Dragon Tail and
Dragon Breath reference comparisons. It contains two player faint testimonies
named Turtonator despite an intervening Aegislash identity. That attribution
needs its own source review; do not count the repeated name as two verified KOs
of the same owned Pokemon. This installation does not claim to resolve it.

The 12:16 record lasts about 16.8 seconds and contains
ObservationSessionStoppedByUser, no MatchStarted, and no combat evidence. It is
a short observation session, not another completed battle.

## Remaining acceptance

A fresh battle on the installed APK is needed to validate the new provisional
audio label and identity-reconfirmation behavior live. Keep the known form bug
separate when assessing that test. Regional-form identity must be resolved or
kept uncertain before its typing can safely confirm Fast Move names or supply
the opponent's charged-move list.
