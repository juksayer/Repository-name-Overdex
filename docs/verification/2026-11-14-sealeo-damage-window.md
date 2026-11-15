# Sealeo later damage window and Incinerate

## Original evidence

The untouched Pixel archive (`79786b739b7af08c414a488338a223ffd649df49291ec0e89eecfb81783cc6c5`)
contains an original `MatchOutcomeCrop` Article `5ccb340b-...` at capture clock
`487964516543029` ns. Its PNG SHA-256 is
`812af88f91f27bd8915266184c47c7c1b2eb0570782ff93d2243130452dee532`.
It visibly contains Sealeo and a small orange remainder in the left of its HP
bar. The crop ends at x=569 while the preceding whole-bar geometry ends at
x=580. This is original captured evidence, not a synthetic bar or complete
outline; it cannot independently prove there is no surviving fill elsewhere.

- The last archived complete red measurement is `68a22539-...`, source clock
  `487964151313826` ns.
- The retained partial orange image follows it by **365.229 ms**.
- The independently recorded complete EMPTY measurement follows the crop by
  **625.264 ms**, at `487965141807414` ns. The independently resolved Sealeo
  faint uses that source clock.
- The matched player visual/audio Incinerate effect is at
  `487963061257259` ns, before the last red. Replay's existing landing there
  is presentation timing, not observed settled damage.

The original silent video has a full native bar, red at 46.858633 s and orange
at 46.880044 s. Comparing the retained archive crop with video frames after
scaling to the archive screen dimensions gives the lowest mean absolute pixel
difference (7.58 in 8-bit channels) at video PTS 4245790, **47.175444 s**;
the next best frame is 47.145133 s (8.41). This single visual-content anchor
is consistent with the earlier geometry fit, but it does not establish an exact
synchronization or common clock, especially across a recording path. It puts
the video transition roughly 0.30–0.32 s before the retained orange crop; on a
naive offset it corresponds to source clock approximately
`487964199731918`–`487964221143029` ns. These are *estimates*, not archive
Articles, a measured zero, or server turn clocks. See the backup-drive file
`2026-11-14-sealeo-damage-window/crop-frame-matches.json` for all candidate
frame PTS and differences.

## Interpretation

The supported Incinerate, player-bar movement, subsequent full-video color
transition, retained orange source crop and independently verified Sealeo faint
make Incinerate a strong **candidate** for the finishing exchange. Opposing
Water Gun observations damage Camerupt, not Sealeo. Other recorded sound windows
near the end include Water Gun candidates and remain preserved. However, the
partial orange crop does not establish full-bar depletion, the archive lacks an
individual terminal damage tick, video-to-archive clock matching is approximate,
and missing observations do not prove no competing hit or interruption. The
2026 PvP ordering is not independently established for this Team Leader match.
These constraints do not support adding an automatic KO attribution to the
current archive-only Replay path yet. The old early presentation impact must not
be promoted to an observed damage clock.

A regression preserves the exact retained PNG, checks its original hash and
clipped geometry, verifies it falls between the original red and empty source
clocks, and confirms no original faint cites this partial crop. It does not
publish an inferred zero or modify the source archive. Focused Sealeo, verified
HP-faint, and charged-finish association suites passed (22 tests). The new PNG
is a byte-exact copy from the original immutable ZIP, verified by SHA-256.

Next work should use this image and whole native video as a positive evaluation
case for a source-timed **provisional** fast-hit association, with full-bar,
living switch, missing-frame, unsided audio, other player hit and long-move
continuation counterexamples; it must retain the association's uncertainty and
never backfill its evidence into the original archive. An automatic, decisive
KO link requires independently validated guards, not just nearest-event order.
