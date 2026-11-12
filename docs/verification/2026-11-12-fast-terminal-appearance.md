# Fast terminal appearance experiment

The final charged-KO path must work without an empty HP outline. That requirement
is already implemented in the compound charged-finish resolver and remains
unchanged. This separate experiment investigates the missed **Fast Move** ending
of the November 11 12:44 Moondrop match: player Gourgeist visibly contracts and
disappears, but its hue overlaps the ground in the installed appearance reader.

## Method and retained evidence

An offline Python experiment decoded all five supplied recordings, selected
source frames at a 20 Hz ceiling and resized them to 336×747. It compared the
existing 18-bin hue union with hue/value, hue/saturation, hue/saturation/value
unions and separate individual hue/value supports. The extra dimensions use
four bins. The same three spatial regions, source-gap limit, background checks,
support-size/connectivity, two-second baseline retention, contraction/clear
thresholds and brightening rejection were retained. This is a development
comparison, not a new Android implementation or a live performance benchmark.

| Recording | Frames | Hue union | Hue/value union | Hue/saturation union | Hue/saturation/value union | Individual hue/value supports |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 124455 | 2,803 | 1 | 0 | 1 | 0 | 3 |
| 190345 | 3,137 | 2 | 3 | 2 | 2 | 5 |
| 161531 | 2,819 | 0 | 0 | 1 | 0 | 2 |
| 211335 | 1,752 | 0 | 0 | 0 | 0 | 1 |
| 112107 | 1,730 | 0 | 0 | 0 | 0 | 0 |

Counts are appearance candidates, **not faints**. The comparison contains 12,241
source frames. The original hue-union candidate times match the previous
compiled-reader study. No claim is made about exhaustively locating every KO in
these recordings or about the live resolver accepting these extra candidates.

The complete candidate traces, video and script hashes, selected-frame source
PTS manifests, contact sheets, and an unchanged compressed copy of the 12:44
archive timeline are retained in
[/home/sean/overdex-device-backups/2026-11-12-fast-terminal-appearance](/home/sean/overdex-device-backups/2026-11-12-fast-terminal-appearance).
`probe.py` reproduces the five-way scan. The original archive ZIP remains intact;
`archive-provenance.json` records its location and hash. The new video readings
have not been inserted into that archive.

## Reviewed individual hue/value candidates

Times below are original video seconds. Review uses baseline, contraction,
clearance and subsequent frames, not a presumed frame-to-archive clock mapping.

| Recording | Clear time | Visible context |
| --- | ---: | --- |
| 124455 | 109.609767 | Living switch/entry transition; departure is not a faint. |
| 124455 | 145.707233 | Opponent Gourgeist remains visible during Shadow Ball; the charge camera and effect change foreground color. |
| 124455 | 196.560644 | Final player Gourgeist Fast Move KO; contraction and departure are visible. |
| 190345 | 109.106544 | Camerupt remains visible during a shield effect. |
| 190345 | 128.468556 | Camerupt remains visible during ordinary attacks and Shadow aura. |
| 190345 | 170.687067 | Previously reviewed first-match terminal Gourgeist departure. |
| 190345 | 324.093733 | Nonfinal Gourgeist contraction/departure after Pelipper's Weather Ball. |
| 190345 | 358.772700 | Previously reviewed second-match terminal Camerupt departure. |
| 161531 | 81.477700 | Camerupt remains visible during its QTE. |
| 161531 | 92.672033 | Roselia entry and camera transition; player Camerupt remains visible. |
| 211335 | 124.283267 | Gourgeist remains visible behind a changing shield after Rage Fist. |

Separating brightness recovers the intended fast-finish candidate, but narrow
color supports can describe an effect, ground patch, or part of a living body.
Losing that support is not the same as observing the Pokémon disappear. The
existing brightening rejection does not eliminate all these examples. Phase,
identity, HP and continued-play evidence can reject some downstream candidates;
that does not establish the new visual measurement itself as body tracking.

## Decision and next acceptance boundary

Do not replace the installed hue reader with the individual-bin variant or use
its new candidate to invent a final attack/faint in the original archive. No
production code, APK, phone state or archive entries changed for this experiment.
No build or install is needed for these evidence/documentation changes.

Before promoting a new visual method, demonstrate that it follows a persistent
body region through contraction rather than a transient color patch, then rerun
the living switch, shield, QTE, charge-camera and Shadow-effect controls above.
Any eventual Fast Move finish inference still needs separately supported attack
and ownership evidence. It must not borrow the preceding player Seed Bomb as the
opponent's finishing move or manufacture a measured zero from the vanished bar.

The original late audio contains two accepted Mud Shot reference matches and a
later rejected near-tie with Incinerate. These are preserved sound observations,
not newly proven finishing attacks. The last independently recorded player HP
is approximately 3.19%; there is no recorded empty endpoint for this finish.
The video, audio-window clocks and existing recorded impacts require separate
alignment and occurrence checks before adding a Replay motion.

## Follow-up: spatial contraction and bounded baseline retention

`geometry_probe.py` remeasures the largest connected color component for every
frame in the eleven candidate traces. It preserves component area, bounds,
centroid and normalized-shape comparison. The final Gourgeist sequences show
joint narrowing/shortening and a downward shift; most effect-only candidates
lose color without that combination. Shape overlap alone is weak because the
Pokémon changes pose and the color represents only part of its body.

`contraction_probe.py` tests a candidate rule requiring at least two distinct
measurements, separated by at least 50 ms, with width at most 90% and height at
most 85% of the baseline and centroid at least 20% of baseline height lower.
Those samples must also satisfy the preceding area, timing, visibility and
brightening checks. This rule was developed using the reviewed examples and is
not an independently validated body classifier.

The first full scan kept the 21-support / one-million-pixel limits but cleared
all supports on overflow. That discarded the target's history. The follow-up
`contraction_rolloff_probe.py` instead evicts oldest supports individually, with
the same limits and no inference across an evicted support. Both complete
12,241-frame scans are retained with script/source hashes.

| Recording | Clear times with individual oldest-support eviction |
| --- | --- |
| 124455 | 109.609767 s — living switch; 196.560644 s — final Gourgeist KO |
| 190345 | 170.687067 s — first-match terminal Gourgeist departure |
| 161531 | None |
| 211335 | None |
| 112107 | None |

All six reviewed effect-only candidates from the preceding individual-bin scan
are absent in this revision. The living switch still passes. The revised method
also misses Camerupt's previously detected final charged departure, so it cannot
replace the installed hue-union reader. Peak retained supports remain 21, with
at most 253,330 support pixels in these scans. These are host measurements, not
Android cost or live capture validation. A possible additional reader still
needs production equivalence, device cost checks, independently supported Fast
Move finish attribution, and fresh-match acceptance.
