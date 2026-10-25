# Fast Move visual review

This desktop tool compares a screen recording with an unchanged `.odxmatch.zip`.
It exposes what the existing broad visual sampling region contains before we
attempt a stronger shape, color, or motion classifier. It is an offline review;
it does not change battle testimony, energy, move counts, or Android runtime work.

## Build a review

Use Python with PyAV, NumPy, and Pillow installed in a dedicated environment.
From the repository root:

```sh
python3 tools/build_fast_move_visual_review.py \
  /path/to/recording.mp4 /path/to/match.odxmatch.zip \
  --offset 86.75 --alignment-uncertainty 0.054 \
  --start 139 --end 159 --output /path/to/review.html
```

The example numbers describe the October 23 Incinerate recording reviewed on
October 24; they are **not reusable alignment defaults**. `--offset` is archive
record-relative time minus video presentation time. Derive it from matching
visible/captured evidence in each pair. The record origin is the explicit
`MatchRecordStarted` monotonic timestamp, not an inferred Match Start. Declare
uncertainty rather than assuming a frame-exact alignment.

The HTML contains its preview frames and works locally without a server. Open
it in a browser. A companion JSON preserves measurements without embedded image
previews. Generated reviews can be large; keep them outside Git and retain the
original recording/archive. Source paths and hashes are included locally.

Supported intervals are at most 60 seconds, sampled at a requested 10–30 fps
(default 20). Actual source presentation timestamps are retained. The report
includes actual median/maximum intervals; it never invents missing frames.

## Read the display

- Select the **recipient** region: Player Pokémon or Opponent Pokémon.
- Play, step, or scrub. A recorded event or candidate button seeks its frame.
- The full scene outlines the sampled area; the enlarged crop shows the same
  region. Colored squares mark changed samples, not recognized elemental types.
- The arrow joins centers of changed samples across frames. It does not establish
  particle trajectory, attack direction, or which Pokémon attacked.
- Original recorded events and offline candidate bursts have separate lists.
  A candidate is a color-change episode with onset, peak, and release; it is not
  an observed attack and does not create an Article.
- Inspect timing/geometry/raw event details in the expandable sections.
- Mark what is visible, distinguish contextual inference, and add a note. Editing
  pauses playback so a note stays attached to one frame. Save review notes exports
  a separate JSON; Load review notes accepts only the matching review set and
  valid frame keys. Nothing is automatically trained or merged into the Timeline.

Drafts are stored in this browser's local storage. Export notes before clearing
browser storage or moving to a different browser/origin. The standalone file is
also usable when local storage is unavailable; export provides durable custody.

## Measurement boundaries

The tool reproduces the current 24×24 source-space sample locations, then scales
X and Y independently into the video dimensions. It joins same-side HP geometry
at or before each frame, at most 250 ms old by default. Missing/stale geometry is
unavailable; it is never borrowed from the future or the opposite Pokémon.

The baseline color thresholds mirror the live visual reader. This diagnostic
additionally resets on sample-region movement over four video pixels and on
sample gaps over 200 ms. Those safeguards, compressed MP4 pixels, and approximate
clock alignment mean its measurements are not the original MediaProjection
measurements. Original visual-event records retain their exact-time archived HP
geometry links when available.

All numerical samples use full decoded video frames. Embedded JPEG previews are
smaller for review and must not be treated as the source pixels. A broad sampling
region can contain sprites, shadow aura, scenery, text, camera movement, or several
effects. Mean color and changed-area displacement alone cannot identify a move.

## Controlled samples with highest value

1. Mawile / Fire Fang: revisit the known false Ice Fang identification.
2. Turtonator / Incinerate: distinguish a sustained effect and later flashes from
   additional attacks, using the same species/move already tested.
3. Raichu / Spark: a contrasting effect; ordinary and Alolan Raichu are both
   available and allow a same-move regional-form comparison.
4. Ordinary and Galarian Stunfisk, if already owned: confirm unresolved badge names
   retain alternative typings and pools until form evidence appears.
5. Shadow and non-shadow of the same species with the same move: isolate aura
   contamination after the baseline works. Shiny/non-shiny is a later cosmetic
   check; do not change several variables at once.

In a controlled match, have one side attack while the other remains idle; record
a short attack run, a clear five-second idle pause, then repeat. Change attacker
sides in another stretch. Keep the actual move and variant documented. Retain a
screen recording plus each device's Overdex archive; SFX-only audio is useful if
available. Do not spend TMs or collect special variants for this first pass.

For Fire Fang, label the **attacker's effects and recipient's effects separately**.
The October 25 repeat video shows a pale expanding tooth ring near Mawile and
closing jaws followed by fire near the recipient. Those are candidate visual
distinctions, not an implemented shape classifier. Particles near both Pokémon
are compatible with just one Pokémon attacking. An idle recipient's hit reaction
can also move its HP bar; proximity,
color, and bar movement alone do not establish ownership. The paired archive
[review](../verification/2026-10-25-fire-fang-paired.md) records the resulting
identity failure separately from attack-use counts. Mark the actual visible
shape in the notes rather than labeling every transient as another attack.

## Checks

```sh
python3 -m unittest discover -s tools/tests -p 'test_fast_move_visual_review.py' -v
```

See `docs/verification/2026-10-24-visual-witness-review.md` for the first source
pair, clocks, observed limitations, and checks. A good offline result is not proof
of live battle accuracy; validate a later classifier on different recordings.
