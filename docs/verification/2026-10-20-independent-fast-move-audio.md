# Independent Fast Move audio — October 20, 2026

## Observable change

Fast Move sound analysis now starts from any accepted `3`, `2`, `1`, or `GO`
countdown glyph. It stays armed across switches, cries, shield choices, and QTEs.
Five distinct recorded faints close the window. An accepted match result can
close it sooner; manual ignore/stop/restart also ends or resets attention.
`MatchStarted` remains a recovery trigger when countdown glyphs were missed.

The reader subscribes to Droidball's existing PCM stream. It no longer waits
for an HP pulse or damage tick to request a recording. The removed visual
request callback does not change visual attack measurements or their counts.
Battle-cry matching remains a separate reader.

## Evidence and resource limits

- `FastMoveAudioWindow` holds 1.2 seconds of 8 kHz mono analysis samples and
  proposes a window every 200 ms. Capture retains its existing source format;
  the retained recognition snippet explicitly declares the mono projection.
- One background worker compares the complete catalogue, with a conflated
  queue rather than an accumulating backlog. Windows over two seconds old are
  skipped. The worker rests for twice the comparison time between searches;
  this limits search duty but does not guarantee that every attack is seen.
- `FAST_MOVE_AUDIO_ANALYSIS_STATUS` records comparisons, skipped overlapping
  windows, mean/maximum comparison time, and the latest leading candidate and
  scores every five source seconds. These diagnostics are not attack events.
- Only strong matches retain WAV evidence. Other PCM remains volatile.
  The durable `AudioCaptured` article precedes and supports its
  `FastMoveSoundMeasured` article. Overlapping views of the same reference-aligned
  sound are deduplicated. Storage failure cannot create a measurement claiming
  a preserved artifact.
- Up to 24 pending sound measurements can await identity. A recent entry/cry
  check provides a separately cited audio boundary, up to three seconds before
  the readable badge. This does not loosen the timestamp guard that rejects
  stale species readings. A sound predating the replacement cannot be reassigned
  using the replacement's move pool. An unresolved entry keeps ownership pending;
  the three-second look-back limit never reauthorizes the old species by timeout.
  Shared/mirror sounds remain unassigned.
- Hearing and accepting an attack are separate. Existing inference guards still
  reject pre-GO and charged-sequence sounds as Fast Move identity evidence.
  This reader does not emit `FastMoveUseObserved` or add energy by itself.

## What the matcher measures

The bundled catalogue has 72 WAV references with 71 distinct candidate move
names. The matcher compares approximately 416 ms of spectral evolution against
each reference, using both the original spectrum and a version with the steady
spectral floor removed. Prepared references are reused across matches.

It requires separation from competing references in both comparisons. The
scores are similarities, not calibrated probabilities. Asset-derived move
labels also require field validation. The sound file's duration is not the
move's PvP duration: the latter comes from move data after identity is resolved.
Repeated reference-aligned occurrences support identity and prevent duplicate
overlapping windows from becoming independent confirmations. This is not yet
an independent counter of every repeated audio cycle.

## Reproducible checks

`FastMoveReferenceEvaluationTest` writes
`app/build/reports/fast-move-audio-evaluation.json` (generated, ignored).

The final focused audit used fixed 1.2-second windows at 40% sample amplitude,
padding short reference recordings with silence to reproduce the reader's
window size:

- 66 of 72 reference windows accepted the intended candidate.
- Six were rejected; none accepted an incorrect candidate.
- Rejections: Pound, Double Kick, Tackle, Bite, Peck, and Leafage.
- All 11 existing recorded-audio fixtures passed: eight accepted expected
  references and three remained rejected as ambiguous.
- The focused desktop run averaged about 46 ms per full-catalogue comparison,
  with approximately 49 ms at the 95th percentile. These are computer timings,
  not phone timings or whole-app CPU measurements.

An initial excerpt-length audit accepted 71/72. Fixed live-sized windows exposed
the additional ambiguous short effects, so the 66/72 result is the relevant
baseline here. Bite's 354 ms reference is shorter than the required comparison;
shortening that requirement alone did not establish a reliable accepted match.
The production matcher thresholds and sequence length remain unchanged.

These are reference self-comparisons and regression fixtures, **not live-match
precision or recall**. They do not establish performance under arbitrary
overlapping moves, music, packet loss, or missed source chunks.

Focused checks also cover independent PCM-to-custody-to-timeline publication,
overlap deduplication, every starting glyph, fifth-faint/result/manual stopping,
PCM gaps/source changes, early audio identity recovery, stale badge rejection,
and refusing to attribute shared sounds or departed-combatant audio.

Final validation: all **990 unit tests passed**, with zero failures, errors, or
skips. `:app:testDebugUnitTest :app:assembleDebug` completed successfully after
the final attribution change. The generated debug APK contains the new reader;
`git diff --check` is clean. No phone was visible over ADB during this work;
installation, phone cost, and a new live battle remain unverified.

## Visual artifact follow-up

The existing recipient detector samples a 24×24 color grid around each tracked
HP bar and reports transient color change, mean RGB, and centroid. It does not
recognize Fire/Electric palettes, snowflake shapes, or central upper/lower attack
paths. No color-to-type claim was added in this change.

The next useful measurement is a calibrated central battlefield region with
tracked changed-pixel hue distributions and movement. The user's observation
that opponent effects pass above player effects can guide attribution, checked
against travel direction and known isolated attacks. Color narrows type; shape,
repetition timing, species move pools, and independent damage/effectiveness
evidence can refine it. Background and sprite colors must not become move types.
