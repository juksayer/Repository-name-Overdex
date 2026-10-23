# Independent charge-growth test: 99fe41cb

Moondrop archive 2026-10-23_14-53-07_99fe41cb-a61f-40bd-994a-e6d7a6301a59.odxmatch.zip,
paired with Screen_recording_20261023_145316.mp4 (816×1808, 131.456778 s).
Backup: /home/sean/overdex-device-backups/2026-10-23-charge-video-99fe41cb/.
Archive verified: 2,245 Articles, 280 unique declared artifacts, all hashes valid.
Video source, per-frame growth measurements, inferred candidates, candidate evidence
PNGs and image-alignment estimates retained in that backup. No game testimony altered.

## Unchanged detector result

Same calibrated centers (252,1620)/(564,1620), radii 60–110 and thresholds as the
previous 7.88 s clip. Whole-video run produces two two-frame growth candidates:

| Video time | Slot | Baseline -> expanded radius | Disappearance corroboration |
|---:|---:|---|---:|
| 43.170956 | 1, Brutal Swing | 77 -> 99 | 43.400678 |
| 113.165544 | 2, Dragon Pulse | 75 -> 87 | 113.337111 |

Allowing one enlarged frame adds slot 1 at 55.767889 s, corroborated at 55.873267 s.
The video visibly contains additional QTE sequences, so neither policy is complete.
No reliability percentage is justified from these two recordings.

## Failure evidence

- Slot 2 around 69.102–69.282 s: ring radii 95,79,94,94,86. Brief return toward
  baseline resets the pending growth even though the controls then fade.
- Slot 1 around 85.843–86.220 s: repeated touches produce radii jumping among
  75–100. The white-ring baseline is contaminated. Clear selected-button enlargement
  at about 86.154 s does not survive the current stable-baseline requirement.
- White touch rings persist after controls vanish, including around 96–97 s.
  A bright circumference alone does not establish that a charge button is present.
- Dark and Dragon QTEs are visibly distinct. These are manual visual findings;
  the prototype only measures bright circles and does not classify the icon.

## Timeline comparison

Only one GetReadyWitnessed is recorded, at 55.335 s relative to record start.
Six named charges are recorded: opponent Flame Charge; player Brutal Swing four
times; player Dragon Pulse once. Additional player QTE imagery exists in the video.
Archive has no MatchStarted Article, so all archive comparisons here use record
start, not GO. Matching retained announcement images against video gives approximate
offsets of 10.55–10.98 s. Repeated/static frames prevent claiming exact frame alignment.
Do not subtract one precise constant and call it ground truth.

## Next implementation boundary

Energy readiness is a sampling cue, not acceptance evidence. Keep baseline sampling
available when energy is uncertain. Require actual button appearance (filled disk /
type icon / known location), measure its scale separately from tap rings, preserve
short growth episodes through brief radius recovery, and corroborate with Get Ready,
layout transition or QTE imagery. Distinguish requested move from accepted phase and
later named release. Retest both recordings plus unready-button taps and other layouts
before connecting this experimental detector to the live timeline.

Offline review only: no Android production edits or installation. Earlier report's
20/20 sampling result was for one clip; this new test demonstrates generalization gaps.
