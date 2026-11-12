# Terminal motion and independent HP evidence

Status: offline feasibility experiment and recorded-pixel regression. The
tested motion tracker is not qualified for automatic departure or faint
decisions. No production inference or installed APK changed in this study.

## Question and sources

The final Cramorant KO has neither a captured native zero-ball count nor a
readable numerical zero-HP frame in the reviewed recording. Can a short visual
sequence supply the missing departure evidence without confusing a living recall,
camera movement, or Shadow effects with a faint?

The experiment uses 214 lossless native 816×1808 video frames in five intervals
from the user's `Screen_recording_20261111_211335.mp4` and
`Screen_recording_20261111_124455.mp4`. All video clocks below are MP4 presentation
timestamps, not archive clocks. Source and per-frame hashes, exact PTS/timebases,
decoded PNGs, contact sheets, extraction/probe scripts, and numerical results are
retained in the [study directory](/home/sean/overdex-device-backups/2026-11-11-terminal-motion-study).
The scripts read existing files; they never insert video observations into an
original match archive.

| Interval | Role | Extracted frames |
| --- | --- | ---: |
| Cramorant, 178.299–181.666 s | User-confirmed charged KO; subsequent collapse/departure visible | 36 |
| Carbink, 179.958–182.081 s | Living recall with retained positive HP | 49 |
| Gourgeist, 189.867–191.200 s | Living charge-camera movement | 33 |
| Gourgeist, 194.629–197.173 s | Human-reviewed final collapse, particles and departure | 65 |
| Shadow Camerupt, 146.528–147.772 s | Attack/aura occlusion before terminal classification is possible | 31 |

These labels are review context. The prototype does not recognize species,
classify the labels, or infer them from the match result.

## Motion experiment

The desktop-only prototype uses OpenCV 4.12.0 and NumPy 2.2.6, installed in an
isolated temporary analysis directory. These are not Android dependencies.
Analysis images are reduced to 408×904; original frames remain unmodified.
Human-selected subject rectangles and a separate background strip supply corner
features. Forward/backward optical flow rejects inconsistent tracks. Independent
robust similarity fits estimate subject and background scale; their ratio is a
candidate camera-corrected scale change.

The initial subject rectangle is not a segmented Pokémon. It can contain
scenery, particles and attack effects. Feature identity after occlusion is
unverified. Consequently a fit, loss of fit, or decrease in tracked corners is
not an observed disappearance.

The test retains a continuous scale estimate only while enough original points,
fit inliers and background points survive. Once continuity fails it reports
unknown; it does not reacquire a different object and join the tracks. Both all
decoded frames and one source-spaced 250 ms sampling phase were tested.

| Sequence | First loss of continuous support, all decoded | First loss, 250 ms sampling |
| --- | ---: | ---: |
| Cramorant KO | 179.549744 s | 179.379700 s |
| Carbink living recall | 181.083278 s | 181.260100 s |
| Gourgeist charge camera | 189.975033 s | 190.128133 s |
| Gourgeist final collapse | 195.584922 s | 195.675556 s |
| Shadow Camerupt occlusion | 147.451978 s | 146.804478 s |

For Cramorant, continuity fails during camera retreat, before the conspicuous
faint particles at 180.551 s. For the living camera and recall controls, it also
fails without establishing a faint. Treating tracker failure as disappearance
would therefore admit false evidence. The tested continuous tracker cannot
supply the required terminal sequence, even with all decoded frames. This does
not establish that every optical-flow or object-segmentation approach will fail.
Only one sampling phase and five reviewed intervals were tested; there is no
classifier accuracy or device-performance claim.

## Independent native-pixel HP probe

A separate Java probe calls the currently compiled production
`ActiveHpBarMeasurer` and `TrackedTerminalHpBarMeasurer` on the original-resolution
images. Its fixed search excludes native badges and move buttons, and candidates
are reviewed by location. It does not supply automatic side/appearance ownership.
The source/class hashes are recorded in `hp-reader-provenance.json`.

- Cramorant: a positive candidate at 178.299278 s measures **0.76419216**. The
  ordinary reader finds no later opponent candidate in this extracted interval;
  this is unavailability, not a measured transition to zero.
- Carbink: while it is shrinking, positive candidate bars at 181.297733 and
  181.368433 s measure **0.17467248**, and the stricter reader independently
  classifies their remaining fill as **RED**. The opponent's ordinary bar is
  no longer acquired after 181.368433 s in this probe. Disappearance of the sprite
  at about 181.421 s must not retroactively erase that positive HP or turn the
  subsequent unreadable bar into zero.

Four lossless native HP crops, with full transformation/hash provenance, are
retained under `app/src/test/resources/battle/recorded-evidence/2026-11-11-carbink-living-recall/`.
`RecordedRecallHpEvidenceTest` checks actual positive red HP while shrinking and
rejects empty-HP interpretation of the later fading/identity-transition frames.
These are video-derived regression fixtures, not newly recovered live Articles.

## Decision and next acceptance requirement

Do not connect this motion prototype to faint publication or HP reduction. A
future departure reader needs demonstrable ownership of the Pokémon's visual
features, explicit camera/occlusion uncertainty, and independent HP/team evidence.
Its positive and negative acceptance cases must include this living Carbink
recall as well as the true KOs. Losing the target must remain unavailable.

This study supplies a reproducible negative-control corpus and a tested HP
distinction; it does not resolve the historical missing final faints. The Pixel
still has the previously verified QTE-capture build, with live capture continuity
and final KO/HP accuracy awaiting a fresh battle.

Validation: **7 recorded-pixel tests in 3 suites passed**, with zero failures,
errors or skips. This includes the new living-recall tests and the existing
Charizard and charged Mismagius terminal cases. All 214 extracted frame hashes
and all four fixture crop hashes were verified. The
[validation summary](/home/sean/overdex-device-backups/2026-11-11-terminal-motion-study/validation.json),
[execution log](/home/sean/overdex-device-backups/2026-11-11-terminal-motion-study/recorded-pixel-tests.log)
and XML results are retained in the study directory. No production files changed;
the installed capture APK is unchanged. This focused run is not a new full-suite
or live-device acceptance claim.
