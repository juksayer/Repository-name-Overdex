# Bounded terminal visual capture

This slice preserves actual image sequences for review. It does not recognize
sprite shrink, disappearance, recall, zero HP, or faint. Those conclusions need
independent evidence and a separately validated visual reader.

The later [QTE coverage change](2026-11-11-qte-terminal-coverage.md) adds one
bounded player-QTE assignment and per-window cost/coverage diagnostics. Its
implementation and validation limits are documented separately below the
original recorded counterexamples.

## Source review

The reviewed source is `Screen_recording_20261111_124455.mp4`, SHA-256
`3d0c54910dc590fcdcd33060ff535300f41f5bbf8170a29b21b2d9ff0f12e655`.
Its native decoded dimensions are 816 x 1808; it has no audio track. Times below
are source-video PTS seconds, not archive-relative times. Original decoded
frames were inspected, including the full-resolution terminal/recall frames.

| PTS | Visual evidence | Limit |
| --- | --- | --- |
| 146.804–147.452 | Incoming Shadow Ball and Shadow Camerupt's purple aura cover much of the sprite and its edge. | Foreground area or brightness change is not a reliable shrink measurement. |
| 180.958 | Carbink is present, taking an effect, with a nonzero red HP bar. | Hit effects themselves can dominate a sprite crop. |
| 181.421 | Carbink is gone, its HP bar remains visibly nonzero, and the opponent holds a ball. | A living recall is a necessary negative example for any future faint classifier. |
| 181.655 | The name changes to Quagsire while the previous colored bar fades. | Camera/identity transitions must not turn fading pixels into zero HP. |
| 190.005–190.515 | Living Gourgeist moves/scales in the centered charged-move camera while using Seed Bomb. | Scale change is not specific to fainting. |
| 194.772 | Gourgeist's ordinary HP bar still has a small colored sliver. | No opaque empty outline was verified before its later departure. |
| 195.950 | Gourgeist remains large with sparks. | Sparks alone do not identify a faint. |
| 196.232 | Gourgeist visibly contracts toward the lower-left. | This is a human-reviewed terminal sequence, not a new detector result. |
| 196.527 | A bright orb remains near normalized position (0.22, 0.86). | The ordinary HP territory does not reach the complete orb. |
| 197.173 | The battlefield fades after the departure. | Screen fading alone is not a terminal event witness. |

The original archive's last player HP reading is earlier than these terminal
frames, and it has no final zero team count. This change does not rewrite that
archive or add the missing Gourgeist faint. New captured sequences make future
gaps inspectable.

## Implemented attention and custody

Ordinary `TerminalVisualCaptureGate` requests open fixed three-second windows with at most thirty
sample attempts at 100 ms intervals. Ordinary requests never extend an active window.
A one-second cooldown follows its original source deadline. Low-HP threshold
entry opens one window; unchanged or further-decreased low HP alone cannot keep
reopening it. A fresh Fast Move or independent impact can request another window
only with recently measured low HP. A side-owned charge phase and its first
used-move announcement can capture a neutral charged-impact sequence without
requiring HP to have been found.

One independently measured battlefield-return transition can close the charged
impact sequence as a partial sequence and open one new three-second return tail.
This is the cooldown exception for ordinary, non-QTE charge capture. It must occur within three seconds of the
owned impact, so the last allowed tail ends at most six seconds after impact.
Repeated return frames and repeated used-move OCR cannot extend or reopen it.
The return tail cites the already preserved phase and impact articles. An
ordinary departure callback without its own article reference can request raw
capture only when a recent low-HP article provides real source context; an
unreferenced high-HP departure does not invent evidence to arm capture.

Delayed cue delivery can use only the remaining part of the original source
window. It never moves the deadline forward. Individual captured frames must
still be no more than 500 ms old. Failed persistence, obscured regions, and
skipped frames produce no image or absent-sprite assertion.

`TerminalVisualSequenceWitness` persists each successful crop as its own
`CropCaptured` testimony. The neutral `TerminalVisualSequenceCaptured` summary
cites those published raw article IDs and the triggering evidence. Its source
start/end and wall time are the actual first/last preserved frames, including
partial sequences. It never substitutes publication time for capture time.

Two adjustable territories are separate from HP geometry and the experimental
opponent-hand crop. The player starter extends down to the terminal orb. The
opponent starter includes the centered recipient area. Any overlap with the
retained badge or HUD overlay makes that crop unavailable; calibration may need
to narrow the territory on a particular device.

## Validation boundary

The focused gate tests cover source deadlines, cadence and sample limits,
duplicate cues, delayed publication, failed/obscured attempts, stop/result
lifecycle, identity changes, recent low-HP ownership, delayed charged-move OCR,
and neutral handling of living recall/camera cases. No automated shrink or
faint-classifier accuracy is claimed by these tests.

Original-pixel PNG encoding runs on the auxiliary dispatcher without a frame
queue. Physical-device validation must still measure archive size, sampling
coverage and CPU cost during low-HP and charged sequences. No synthetic frames
or interpolation fill any coverage gap.
