# Sealeo HP background ambiguity

## Source and experiment

The original Pixel recording `Screen_recording_20261112_111756.mp4` and its
original archived match remain unchanged. A reference crop at video PTS
4390730 (48.785889 s, timebase 1/90000) was selected after Sealeo's departure;
visual inspection shows the relevant bar rectangle clear of the bar and
Pokémon. It is a **later** frame useful for offline analysis, not a background
sample available to the live reader at the earlier source times. The retained
crop has SHA-256 `394d108db3e43338c9c2c7ef919a30a667f821d05f1a20679bdb48e68fa71f90`.

A scenery-only affine registration, with the bar region excluded, matched
515/583 features for the earlier orange frame and 524/625 for the later orange
frame; fitted translation was below 0.02 source pixels for these fits. Global
feature agreement does not by itself establish local background clearance,
unchanged illumination, or absence of effects. The reference and source crops,
contact sheet, script, alignment, per-column observations, and logs are retained
under the backup drive's `2026-11-15-sealeo-background-study/` folder. The
estimator fits a per-channel dimming factor and compares source pixels with the
predicted background using a 20-level maximum-channel tolerance. This is an
offline diagnostic, not a new production classifier or a proof of HP state.

## Results and safety boundary

The existing whole-bar profile leaves 92 uncertain columns in the 46.880044 s
orange frame and 14 in the 47.011856 s frame. In the exploratory comparison,
82 and 9 respectively also match the estimated background; the remainder do
not. The diagnostic reproduces the profile's protected-color categorization,
but its fitted background and tolerance are experimental. These matches
suggest an explanation for some of the pixels; they do not prove that those
pixels cannot also contain a dim HP sliver. All 92 and 14 unresolved columns
include colors the current profile protects.

Two concrete one-pixel counterexamples were constructed, one for each frame.
Each injected dim yellow/green pixel is within 20 channel levels of the fitted
background at its original screen coordinate, yet is a possible live-HP color.
`RecordedSealeoGapRecoveryTest` verifies the background proximity and verifies
that the actual whole-bar reader retains the added uncertainty, does not report
orange-only, and does not report empty. The comparison's earlier six fixed
bright/dim colors did not match within the same tolerance at any tested
interior pixel, which shows why a small palette alone missed this failure. The
examples demonstrate a risk, not an exhaustive model of the game's rendering.

Recorded Sealeo nonlethal controls, a recorded Carbink living recall,
source-time competing attacks, whole-outline/end-cap checks, tiny survivors,
and switch/reset protections were run as separate existing reader/Replay
regressions. They do not independently validate this experimental background
comparison for every video frame. The earlier 12 nonlethal and two switch-window
study used sparse samples with some unverified reference clearance; all four
sampled switch comparisons rejected their brightness fit. Those rejections are
unknowns, not evidence that all living switches will be identified.

The evidence does not justify masking protected pixels or concluding the
Sealeo bar is entirely orange. No reader, faint, KO attribution, Replay, or live
behavior was changed. Further progress requires another independently validated
signal capable of distinguishing a dim survivor from similarly colored
background, plus source-time capture and full sequence tests; the later
reference by itself cannot supply that distinction.
