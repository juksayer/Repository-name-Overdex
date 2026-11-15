# Whole-bar HP color order: recorded checks

## Scope

Offline checks of the trainer's proposed live-color-before-orange rule use
seven previously reviewed windows: the Pixel Sealeo Fast Move KO, the same
Sealeo's nonlethal Charged Move and Fast Move, low-HP nonlethal Fast Move
windows for both sides on the Moondrop, and two ordinary living switches (one
per side). The labels describe whole windows and come from prior review, not
from the color classifier. The study decodes 1,036 source-video frames and
measures 709 frames where the prior locator recorded strict complete geometry
(both rails and both caps). Source video PTS and source rectangles are retained.
The unmeasurable frames are unknown, not empty. A gap in strict geometry
breaks a consecutive-frame run.

The exploratory script computes median RGB over the interior of each column,
then separates bright live colors from orange, as in the earlier recording
study. It records whether any live column occurs before the first orange
column, whether either color occurs elsewhere, and whether a coherent
orange-only prefix persists for at least three consecutive readable frames over
100 ms. This is an exploratory classifier, not the production whole-bar reader:
median color can hide a one-pixel survivor, thresholds are not validated across
devices, and no faint, zero, or move association is generated. It is not a
blind or population-level accuracy test. The separate real reader tests protect
single pixels and unresolved colors.

| Reviewed window | Strict complete frames | Live before first orange | Bright orange-only frames | Sustained bright orange-only runs |
| --- | ---: | ---: | ---: | ---: |
| Sealeo Fast Move KO | 84 | 1 | 18 | 1 |
| Sealeo nonlethal Charged Move | 88 | 6 | 3 | 0 |
| Sealeo nonlethal Fast Moves | 136 | 48 | 0 | 0 |
| Opponent low-HP nonlethal Fast Moves | 134 | 36 | 0 | 0 |
| Player low-HP nonlethal Fast Moves | 181 | 50 | 0 | 0 |
| Opponent living switch | 26 | 0 | 0 | 0 |
| Player living switch | 60 | 10 | 0 | 0 |

The KO's exploratory orange-only run spans video 46.880044–47.328856 s (18
frames). This supports an orange transition during the known KO sequence, but
is not an independently proven exact damage or faint time. The production
whole-bar reader remains more conservative on the retained 46.880 and 47.012 s
images because potential live-colored background pixels remain unresolved.

The nonlethal Sealeo Charged Move provides two necessary counterexamples. At
41.299011 s, the median-column probe detects a small live sliver even though a
single orange-colored column precedes the first detected live column. The
production whole-bar reader detects both live and damage colors and does not
call this orange-only. Thus selecting the first orange-colored pixel as a hard
boundary could discard real remaining HP. Later, at 41.358233, 41.384022 and
41.793656 s, the median probe temporarily sees orange-only while Sealeo survives;
the longest consecutive run is 25.789 ms. This illustrates the danger of a
single frame and of median color, not proof that any fixed duration is always
safe. Another hit or unfinished animation may follow a still-live frame.

The switch windows have no orange-only candidate in the strictly measurable
frames; the many unmeasurable frames and limited sample mean this does not prove
all living recalls are distinguishable. In the same focused test run, a
separately retained Carbink living-recall sequence remains conscious, complete
bar geometry and transition resets remain enforced, and one bright red, yellow,
or green pixel injected at the left edge of either Sealeo orange bar blocks an
orange-only conclusion. Other tests retain dim pixels that can resemble
background. These controlled injections do not assert the injected pixels were
in the original frames.

Source hashes, individual frame positions/counts, the reproducible offline
script, and run logs are under the backup drive at
`2026-11-15-hp-spatial-rule/`. The original videos and archive are untouched;
no production reader, archive testimony, HUD, or Replay behavior was changed.
The results support preserving any verified live fill and treating orange-only
as a candidate requiring corroboration, with ambiguity retained when color or
geometry cannot be established.
