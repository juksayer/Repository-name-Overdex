# Charge-button fill height: independent energy cross-check

User confirms the signal is fill HEIGHT, not colored area. Source: preserved
816×1808 Screen_recording_20261023_145316.mp4, SHA256
766511b95718e0912a7f2551d95239d43d7737c27b2bc90060750c5c14e4e68e.
This is an offline test, not an installed live witness.

## Method

Use manually selected stable frames; measure the horizontal fill separator on both
sides of the central type icon. Ignore broad bright disks and multiple competing
lines. Source-video centers (252,1620), (564,1620), nominal fill radius 75 px.
A narrow bright line supplies offset y relative to center:
fraction = (radius - offsetY) / (2 * radius).

For each button enumerate every completed-layer count compatible with energy <=100:
energy = cost * (completedLayers + fraction).
Then intersect the two buttons' candidate intervals. The experimental +/-2px tolerance
is measurement tolerance ONLY; it is not a statistical confidence interval and omits
calibration/animation/occlusion uncertainty. Output rounded energy in conclusions.

Brutal Swing cost35, Dragon Pulse cost55. Do not assume a fixed maximum of two layers:
a displayed boundary can have several layer interpretations. At ~39.5s the common
interpretation uses two complete 35-energy layers and one complete 55-energy layer.
This does not establish a universal color scheme for every layer/type.

## Results (video-relative seconds)

| Video time | Brutal Swing separator y | Dragon Pulse separator y | Joint approximate energy |
|---:|---:|---:|---:|
| 39.032 | +33.5 | +7.0 | 80 |
| 39.504 | +33.5 | +7.5 | 80 |
| 54.216 | -55.0 | +49.5 | 65 |
| 54.700 | -55.0 | +49.5 | 65 |
| 55.223 | +11.5 | -7.0 | 85 |

At ~39.5s the independently archived energy derivations total four player gains of
20 and no player charge spending (80 energy) throughout the approximate video/archive
alignment bracket. Before first player spending, six gains yield 120 generated,
capped at100; the recorded Brutal Swing costs35. The later visual65 supports that
spend. The following visual85 is consistent with another20 energy gain. Exact frame
alignment is uncertain by several tenths of a second; do not treat this as a measured
live recognition latency or force the visual observations onto existing timestamps.

Other selected frames returned unknown (30.508,32.548,40.510,66.512,67.012,81.533,110.504).
The simple line detector is not complete: color/brightness, icon/scene contamination,
full layers and press/fade animations require further handling. No guessed energy
is emitted when no unique two-button intersection exists.

## Retained evidence and validation

Full frames, source PTS/time base, original hashes, ambiguous hypotheses and joint
intervals are in the backup folders fill-layers/ and fill-layers-after-spend/.
Small numeric results are tracked in charge-fill-height/99fe41cb-measurements.json.

Five unit tests cover layer ambiguity, dual-cost intersection, absent/conflicting
evidence, rejection of broad bright disks and separator geometry. Existing growth
tests remain separate. No Android code changed; no build/install required.

Next live implementation should emit measured height, geometry, layer candidates and
availability as evidence. A separate reconciliation step compares those measurements
with counted energy and spending; never overwrite original Articles or call saturated
or occluded buttons zero-energy. Energy readiness may raise sampling priority without
turning low estimated energy into a hard capture gate.
