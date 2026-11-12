# Player appearance contraction feasibility

Follow-up: the bounded production implementation and its separate validation are
recorded in [player appearance reader](2026-11-12-player-appearance-reader.md).
The remainder of this document records the original offline experiment.

Status: an offline candidate experiment, not an installed faint detector or an
HP measurement. It addresses the player-side gap found in the
[production-reader coverage scan](2026-11-12-player-terminal-coverage.md).
Original recordings and archives are unchanged. Pixel 4 retains the previously
installed stable-count build.

## Why the particle path misses these endings

Examined every decoded image in the final Gourgeist and Shadow Camerupt charged
intervals of `Screen_recording_20261111_190345.mp4`: 31 and 18 images respectively.
This removes the earlier 20 Hz selection ceiling as an explanation for these
particular measurements. It does not establish what frames the phone displayed
between recorded images.

At capture-like 672×1493 dimensions, the factory player territory is
(0,522)–(417,1463). Expanding it to the full width recovers five existing
cool-halo candidates around Gourgeist at 170.168667 s. Their spread still exceeds
the production grouping radius: only two fall within 0.14 source widths, while
five fit within 0.42. Allowing neutral rather than strictly cool halos raises the
candidate count to six. This experiment does not change the calibrated territory
or installed thresholds.

Camerupt still supplies no four-point group, even with the wider territory,
neutral halos and enlarged particle-area ceiling. At 358.576278 s its body is
visibly smaller; at 358.772700 s it is gone and small stars remain. No qualifying
large white core appears between those recorded frames. Therefore fixing crop
width or waiting for an empty bar or bright core alone does not solve this case.

## Alternative: frozen foreground appearance with visibility checks

The prototype quantizes chromatic pixels into 18 hue bins at 336×747. It chooses
bins enriched in a fixed near-side territory compared with a separate far-side
territory, then freezes each candidate appearance for at most two seconds.
Initial connected support must dominate that appearance. A candidate needs an
observed reduction followed by near-clearance of the same colors, with source
gaps no larger than 250 ms. No species names, battle events, KO timestamps or
results enter this calculation.

A separate textured background patch must remain readable and stable throughout
the candidate. This rejects the reviewed living charge-camera changes and
screen fades. It does **not** establish sprite ownership or prove that an attack
has not hidden the foreground. Color support is not a segmented Pokémon mask;
Shadow aura, terrain, attack effects and an opponent centered by the camera can
all enter these territories.

The first full-recording pass found four candidates. Human review classified
them only after scanning:

| Recording | Candidate clear time | Reviewed scene |
| --- | ---: | --- |
| 190345 | 170.687067 s | Final player Gourgeist charged collapse |
| 190345 | 358.772700 s | Final player Shadow Camerupt charged disappearance |
| 124455 | 110.247922 s | Living player Gourgeist switch to Camerupt, with positive HP |
| 211335 | 78.930889 s | Living opponent Quagsire washed out by Brutal Swing's bright effect |

The Quagsire counterexample is decisive: a stable background does not make a
vanished color feature an observed departure. Quagsire remains visible through
the effect, has positive HP, and is plainly present afterward. Also, an object
in the near-side territory need not belong to the player during charged camera
movement. The living Gourgeist switch is a real departure, but not a faint.

## Bright-effect control

The revised prototype checks the original appearance support for brightening
when its color area contracts. If more than a quarter of those pixels brighten
by over 40 value levels, that baseline is retired as occluded. It cannot bridge
the hidden interval and call it disappearance.

At Quagsire's false clear frame, all of the original support brightens by more
than 40 levels. The corresponding fractions at the two charged terminal
candidates are approximately 5% and 1.3%; the living-switch fraction is zero.
These are diagnostic image values, not probabilities or proof of ownership.

The revised full scan processed **12,241 frames** at at most 20 Hz, with the same
parameters for every recording:

| Recording | Frames | Retained candidate clear times |
| --- | ---: | --- |
| 190345 | 3,137 | 170.687067 s, 358.772700 s — both reviewed charged endings |
| 124455 | 2,803 | 110.247922 s — living switch; not a KO |
| 161531 | 2,819 | None |
| 211335 | 1,752 | None; Quagsire washout rejected |
| 112107 | 1,730 | None |

The background/brightness checks retain both terminal candidates while rejecting
the bright-attack counterexample. They deliberately do not turn the living
switch into a faint. These are development/control recordings, not an unseen
accuracy benchmark, and an empty candidate list is not proof that no departure
or KO occurred.

Verified the original recording hashes, frozen script/helper hashes, frame
counts, candidate source ordering, gap/deadline bounds, past-only traces and
numeric visibility checks. All scripts parse. Android production code did not
change, so no Android test suite, APK build or installation was repeated.

## Limits and next implementation boundary

- This is a proposal for an additional visual measurement. A living switch also
  passes, so a candidate cannot directly fade a HUD slot, assign a faint or drain
  Replay HP. It needs independent identity, phase, remaining-team and terminal
  evidence, with contradictory survival preserved.
- The first 190345 archive still lacks a fully named final Weather Ball use.
  Better pixel recognition does not manufacture that missing announcement.
- The 124455 final player Fast Move KO is still missed: the selected foreground
  hue also occurs in the ground, and its apparent support grows during the
  departure. This approach is not a complete player faint solution.
- Gray/low-saturation bodies, new scenery, lighting, different species and
  device geometry remain unqualified. The fixed experimental near/far/background
  territories are not a calibrated Android reader.
- A phone implementation would need bounded measurement cost, independently
  adjustable/calibrated territories, capture-time overlay checks, source clocks,
  numeric provenance, interruption handling and shared live/Replay attribution.
  Desktop scans do not establish any of those runtime properties.

Before promoting this route, preserve the two terminal examples, the living
Gourgeist switch, the Quagsire washout and the still-missed Fast Move ending as
separate acceptance cases. Reuse the existing conservative terminal resolver's
identity/result and survival boundaries; do not encode the reviewed labels as
synthetic observations.

## Reproduction

Scripts, frozen first/revised prototype outputs, original PTS/timebases,
source hashes, full-frame review images and the verification summary are in
[/home/sean/overdex-device-backups/2026-11-12-player-particle-scale](/home/sean/overdex-device-backups/2026-11-12-player-particle-scale).
The standalone dependency paths used for these desktop experiments are
`/tmp/overdex-terminal-motion-python` and `/tmp/overdex-video-review-python`.
They are not Android dependencies. All frame comparisons use source PTS rather
than archive publication or filesystem times.
