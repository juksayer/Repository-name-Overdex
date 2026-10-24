# Visual witness diagnostic — October 24, 2026

## Scope

Added `tools/build_fast_move_visual_review.py`, its browser template, and seven
Python regressions. This is an offline review/annotation tool; no visual classifier
was added to Android and no historical Article was modified.

## Source custody and clocks

- Video: `/home/sean/Desktop/Screen_recording_20261023_175949.mp4`
- Video SHA-256: `114c1c20e7091d06ab5344d7ee01e5c1f4329c428eaa62bcd3b073d49120d7a2`
- Archive: `/home/sean/overdex-device-backups/2026-10-23-settled-fill/0330b719.odxmatch.zip`
- Archive SHA-256: `8c7bb47a7163480633c7963880edf3806ceeb6f5f041779bbe065cd287470bac`
- Match: `0330b719-8c57-45bc-9f41-247bf016a452`
- Record-start monotonic origin: `35330814992259` ns. This archive does not contain
  `MatchStarted`; it is not invented as an alignment origin.
- Source capture 672×1493; video 816×1808. Independent X/Y scaling, no fixed
  notification-bar subtraction.
- Existing alignment anchors give record-relative minus video time of
  86.719303226, 86.772418911, and 86.753452145 seconds. Review uses 86.75 seconds
  with declared uncertainty ±0.054 seconds.

Generated review set `239262127fadfa64ad0c`, video 139–159 seconds, requested
20 fps: 312 frames, median interval 0.0607777778 seconds, maximum 0.2120444444
seconds. Actual PTS/timebase values are retained. There are 25 selected original
events. All 50 visual-artifact records in the source archive have exact-time
same-side HP geometry matches; the selected review retains matching links.

Generated HTML, measurement JSON, and preview are outside Git under:
`/home/sean/.codex/visualizations/2026/09/13/01a09cc7-b682-79f0-ad52-76698424dade/fast-move-visual-review/`.

## What this first review establishes

The broad visual area includes sprite/body motion, aura, scenery, text, and attack
effects. It is not an isolated Fast Move artifact crop. Offline thresholds produce
three completed bursts in the player recipient region and none in the opponent
recipient region. These are **not three attacks**, nor evidence that the other
side performed none. The mismatch exposes the need for component isolation and
side/phase checks before visual changes can identify or count moves.

The selected interval includes the historical 144.848-second damage/attack and
145.941-second border-only duplicate documented in
`2026-10-23-incinerate-late-flash.md`. Both original records remain visible. The
viewer does not rewrite past counts or tune the mask to match expected counts.

## Validation

- Seven Python tests passed: fixed sampling schedule, source-space geometry,
  nonuniform scaling, missing/invalid geometry, causal same-side joins, repeated
  fragments, gap/relocation resets, and explicit record origin.
- Independent review found and corrected sampling drift (advancing from the
  selected frame rather than fixed time buckets) and notes changing frame during
  playback. The accepted analysis-rate range is 10–30 fps.
- Browser check: play/pause, frame stepping, event seeking, recipient selection,
  candidate selection, and annotation editing. Editing pauses playback.
- Exported review notes were checked for source hashes, configuration, review-set
  ID, and exact frame/side key. The QA note was cleared from the browser. Import
  compatibility is enforced in code; an end-to-end file-import check is pending.
- Screenshot saved with the generated review. Reduced JPEG previews are disclosed;
  numerical measurements use full decoded frames.

A browser download wait stalled for roughly two hours, then returned the valid
notes file. No app process was replaced during that wait. This tooling delay is
not a measured Overdex or Pokémon GO runtime performance result.

## Next acceptance boundary

Use isolated known Fast Moves and idle windows from different recordings to label
visible effects versus body/camera/aura changes. Measure false positives and
misses before enabling an Android classifier. Keep inference confidence distinct
from empirically measured accuracy. Regional form discrimination remains a
separate question from whether an individual attack was witnessed.
