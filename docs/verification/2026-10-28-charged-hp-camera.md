# Charged impacts and QTE motion evidence — October 28, 2026

## Concrete recorded failure

The latest Halloween Cup recording (`fcc29d4c-ab31-4d99-af09-e3d3de9562c8`)
contains a named opponent Hydro Cannon at source +109.942 seconds. Its HP impact
and border pulse appear at +111.432 seconds, beyond the existing 750 ms charged
impact tail. The original processing publishes that pulse as an opponent Fast
Move before the slow announcement reaches custody.

The player's HP bar previously occupies about y434–485 in the 672×1493 source.
At +110.277 seconds it moves to y656, still showing about 85.6% HP. At +111.432
it is at y661 and shows about 32.8%. These are valid HP measurements in a
different camera layout. Rejecting them as malformed would discard the actual
health change. A damage-tick Article was not emitted for this loss, so fixing only
damage-tick consumers would leave the pulse-only misclassification intact.

## Implemented boundary

`ChargedHpCameraGuard` associates a recent, measured HP-bar relocation with an
independent used-move announcement. It uses full-source dimensions, horizontal
bar continuity, and at least three distinct recent baseline sample times. Normal
vertical bobbing remains inside the learned envelope. Neither damage magnitude,
move name, nor estimated energy is an input to this guard.

The relocated interval closes before the first measured return to the ordinary
envelope, or expires after five seconds. A relocation without a corroborating
announcement holds pending Fast Move candidates for at most five seconds, then
releases them with their original source clocks and evidence IDs. This targeted
hold gives delayed OCR an opportunity to arrive without slowing every ordinary
Fast Move observation. State retains bounded numeric measurements; no new image,
OCR, audio, or sensor reader is added.

Genuine appearance transitions and faints close the previous camera interval and
clear both HP baselines. This prevents a replacement's different ordinary HP
height from extending the preceding charged phase. A faint includes its exact
source timestamp so the final impact cannot leak through when HP depletion and
the KO share a frame. Repeated species OCR does not reset the baseline. Boundary
and frame delivery can be reordered; all comparisons use their source times.

Live use inference, the energy-duration ceiling, and Replay share this geometry
rule. Charged-camera pulses cannot create Fast Move uses or exact energy credits.
Full Fast Move cycles must fit after a measured camera return. Replay can exclude
the old archived misclassification while preserving the original Articles, HP
loss, named Hydro Cannon and its 40-energy expenditure. Original archives are
not migrated or rewritten. The existing 750 ms fallback remains when no reliable
relocation can be measured.

This is the first repair to phase attribution, not a general damage-to-move
resolver. The proposed next inference should compare legal charged and fast
damage ranges, elapsed legal cycles, actual coverage of the attack gap, energy
feasibility and supporting audio. An incomplete observed energy ledger cannot
veto a clean named charge. Large damage plus a gap can support an unnamed charged
episode; a specific move name requires candidates to resolve uniquely. Damage
after the bar has already returned, missing geometry and entirely missed charge
cues remain separate cases.

## Haptics audit

The user reports that battle vibration occurs only at the three QTE text
progressions, with no game-generated tap vibrations. That makes motor haptics a
useful potential QTE signal. The current capture records linear acceleration,
which also includes physical movement from fingers and handling; it does not
record the game's vibration commands directly.

This same recording contains 171 device-motion pulses, 26 within the current
25–300 ms acceptance range, and zero inferred QTE vibration patterns. The other
145 pulses reach the detector's 350 ms cap. No Get Ready cue was recorded. The
two named player Poison Fangs lack the necessary three-pulse run in the preceding
ten seconds; the first has two eligible pulses 3.75 seconds apart, the second
has one. Five short motion pulses also occur before the opponent's final Hydro
Cannon, demonstrating why motion alone must not imply the player's QTE.

Keep raw motion independent. A stationary-phone comparison with motor haptics
enabled versus disabled can establish the signature and separate it from handling
before assigning Nice/Great/Excellent from this sensor alone. No haptics detection
thresholds changed in this fix.

## Validation and acceptance

The regression fixture in `app/src/test/resources/battle/2026-10-28-charged-hp-camera/`
retains 91 untouched source Articles in original publication order. Its provenance
records source/fixture hashes, selection rules, and the exact charge, pulse, HP
and historical misclassification IDs. It is a timing excerpt with no media, not
a complete portable match.

Recorded tests cover the actual late-custody race, energy rejection and Replay
exclusion without changing archived testimony. Synthetic controls cover bounded
release without a charge, whole-cycle resumption, ordinary bobbing, a large real
Fast Move, missing/invalid/stale geometry, duplicate source times, reversed
delivery, camera return and historical-window retention.

Review also added switch/KO-to-replacement regressions and an exact-faint-frame
case. The numeric guard updates ordered frames incrementally; a bounded rebuild
handles out-of-order geometry. A long-stream regression compares both delivery
patterns across history pruning. Desktop helper timings are development checks,
not Android capture or whole-application performance measurements.

- Full unit suite: **1,334 tests / 216 suites**, zero failures, errors or skips.
- Debug APK build and whitespace validation passed. APK SHA-256:
  `1a6762a5d90c3c2eb1286b784c8df7bffb4fed2e2625ef3ffee377ed2923ba8e`.
- Build logs, setting backups/hashes and install evidence are retained at
  `/home/sean/overdex-device-backups/2026-10-28-charged-hp-camera/`.
- Installed on the idle Moondrop (`MDPH00124112500414`); all 17 saved calibration,
  profile, HUD-layout and current-team file hashes are unchanged. The Pixel was
  not updated. No fresh live battle was played or interrupted during installation.

A regression from this recording is not a new live battle test. The next live
check should verify a late charged impact is not counted or credited as a Fast
Move, and that the first real attacks after a charge or replacement still appear.
