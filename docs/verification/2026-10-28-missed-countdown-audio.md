# Missed countdown and Fast Move audio — October 28, 2026

## Recorded comparison

Two perspectives of the same private battle were copied without changing the
phone originals. Desktop/device SHA-256 hashes agree, and every declared source
artifact passed archive verification.

| Device | Match ID | Articles | Verified artifacts |
| --- | --- | ---: | ---: |
| Moondrop | `bba80cf7-6282-4ec7-8924-257336712896` | 3,494 | 518 |
| Pixel 4 | `084205bb-7764-48d4-9b2f-41439aea952f` | 3,801 | 505 |

The user clarified that **Swampert, Quagsire, and Obstagoon were idle**. The other
team contained Aegislash, Flygon, and Mawile. The video shows Aegislash using
Shadow Ball and Mawile using Iron Head. The user tentatively recalled Fire Fang
on Mawile; this remains unresolved external recollection, not observed testimony.

`/home/sean/Desktop/Screen_recording_20261028_075844.mp4` lasts 199.073 seconds,
at 816 × 1808 pixels, including Team Select and the win screen. It has a video
track and two data tracks, **no audio track**. Both Overdex archives retained
nonzero game audio. Video time is not archive-record-relative time; do not use
the filename or wall-clock difference as an exact frame alignment.

## What worked and what failed

The roster safeguard worked. Pixel accepted a newly saved move selection after
roster confirmation: Swampert/Mud Shot/Hydro Cannon/Muddy Water,
Quagsire/Mud Shot/Sludge Bomb, Obstagoon/Counter/Cross Chop/Night Slash.
Moondrop confirmed its roster twice but recorded no later configuration save.
The video at 12.020 seconds shows the move editor with no selections and `0/3`;
at 13.012 seconds Close is pressed. Earlier moves were correctly withheld.
There is no demonstrated automatic-close bug.

Both perspectives attributed Fast Move uses to Aegislash/Mawile and none to the
idle team. Moondrop recorded 21/26 uses; Pixel recorded 28/25. These are detector
counts, not video-verified ground truth, and their disagreement remains open.

Pixel recorded a countdown `1` and inferred MatchStarted from it plus combat.
Moondrop missed every countdown glyph and had no MatchStarted. Its countdown
capture rearmed after the counterpart's countdown had already finished.

The audio-listening gate opened only on a countdown/start or charged-phase cue.
On Moondrop it therefore stayed closed until Get Ready at record-relative
**+404.029 seconds**, although both active species and a persistent HP loss were
already recorded by **+305.261 seconds**. This is a concrete listening-activation
defect, independent of whether the user saved moves.

Moondrop retained six sound comparisons, one strictly accepted Fairy Wind.
Pixel retained 35, including three strictly accepted Psycho Cut and ten Fairy
Wind matches. Repeated Fairy Wind audio and effectiveness support conflict with
the tentative Fire Fang recollection; the actual equipped move still merits
checking. Similarity scores are not probabilities.

Moondrop recorded no exact energy credits. Missing MatchStarted does not itself
disable energy: the absent fresh loadout, weak cadence-only Psycho Cut identity,
and missing repeated audio corroboration explain this recording's limitation.
Pixel credited 17 Psycho Cuts and 14 Fairy Winds, all to its opponent. Those
credits do not prove that every real attack was observed.

## Bounded correction

FastMoveListeningGate can now open from a valid persistent HP decrease with
both sides' previously witnessed active species as battle context. It preserves
the three source references and the HP event's monotonic time. This starts audio
observation only: it creates no GO glyph, MatchStarted, Fast Move, or energy.

Names alone, full HP, border pulses, and charge-button shading cannot open this
fallback. Delayed pre-restart evidence cannot open a new attempt; out-of-order
delivery retains newer HP evidence until its earlier species context arrives.
Result, user Stop, and the fifth distinct faint still close listening. Existing
audio acceptance thresholds and energy restrictions are unchanged.

The recorded opening sequence would permit listening 98.768 seconds earlier.
That is a source-sequence regression expectation, not a measurement of the new
APK in a live battle. A new recording must establish actual recognition latency
and counts. The separate missed-countdown capture issue and approximately
one-second audio comparison cost remain follow-up work.

## Evidence and validation

Unmodified archives, hashes, video frames, and detailed audio/roster reports:
`/home/sean/overdex-device-backups/2026-10-28-roster-audio-review/`.

- Focused checks: **82 tests across four suites**, no failures/errors/skips.
  Includes the recorded opening sequence, source-order/restart boundaries,
  retained PCM provenance, and repeated audio identity after combat without GO.
- Final full suite: **1,235 tests across 205 suites**, no failures/errors/skips.
  Debug APK build succeeded; independent source review and `git diff --check`
  found no remaining issue in this change's scope.
- APK SHA-256:
  `a596567260b06b1f28bcd212d225284a6373e5fb1c3d09d6cc38d1f3eefcddb5`.
  Installed and hash-verified on Pixel 4 `99261FFAZ0017J` and Moondrop
  `MDPH00124112500414`. The user authorized stopping the completed Pixel
  session; its notification Stop control was used, and service/MediaProjection
  absence was verified before installation. Moondrop was already stopped.
- Both normal launcher starts succeeded (Pixel 1,522 ms; Moondrop 3,615 ms).
  Selected calibration profile, battle crops, Team Select crops, HUD layout,
  and selected-team preference hashes were unchanged on both devices.
- Live battle recognition after this update remains unverified. The next test
  should explicitly save the actual moves after roster confirmation, use SFX
  100% / music 0%, and retain a video with device audio enabled if available.
