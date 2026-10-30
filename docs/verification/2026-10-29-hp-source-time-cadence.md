# HP source-time cadence — October 29, 2026

## Problem and change

`FastMoveCadenceInference` previously gave every evidence family a fused use's
earliest cue time. Flashes at 1.00, 2.80, and 4.00 seconds, with real HP losses
at 1.35, 2.85, and 4.35 seconds, therefore produced 1.80/1.20-second intervals
for the HP-loss family. All offsets fit the existing 450 ms fusion window.

Cadence now resolves the cited source Articles separately by measurement family
and damaged side. The HP losses in that example retain their actual
1.50/1.50-second cadence. A bounded 256-Article impact index handles cited sources;
unseen citations are deferred until delivered. Existing source-less legacy uses
retain their recorded timing. Derived conclusions cite both source endpoints and
the fused occurrence proofs. Raw Articles, fused uses, and archives are unchanged.

This does not grant additional energy, relax audio thresholds, or infer a move
from an assumed amount of HP damage.

## Validation

- Seven focused regressions cover phase offsets, delayed source delivery,
  duplicate/family voting, 0.5-second cadence, damaged-side attribution, and
  charge/appearance boundaries.
- The recorded-identity test adapter now forwards available raw damage/pulse
  Articles instead of stripping them. Fixture JSON is unchanged. Sparse Dragonair
  evidence still cannot certify Iron Tail or award its energy.
- Focused cadence and recorded-identity suites: **81 tests passed**.
- Full JVM suite: **1,453 tests across 228 suites**, zero failures/errors/skips.
- Debug APK build succeeded with the bundled JBR.
- Installed and verified the exact APK on idle Pixel 4 `99261FFAZ0017J`.
  Six calibration/team/layout files remained byte-for-byte unchanged.
- Moondrop was disconnected at installation time; its installed build was not
  changed during this task.

APK SHA-256:
`ff0e7d1e46bb1f23b29378f738a80e9b65a610f363704607bbe83bbf560f3d3c`.

Logs and receipts are retained outside Git in
`/home/sean/overdex-device-backups/2026-10-29-fast-move-audio-review/`.
No new live battle was played, so these checks do not establish recognition
latency or count accuracy in a new match.

## Damage inference boundary

Current reference data lists Bubble and Waterfall as three-turn / 1,500 ms moves,
with base powers 8 and 11 respectively. Their shared cadence cannot distinguish
them by itself. Stable before/after HP geometry is useful evidence, but exact
damage exclusions need compatible effective-stat ranges, forms, shadow status,
stages, maximum HP, and pixel uncertainty. Current owned-team snapshots do not
provide a complete IV/level/maximum-HP stat model. CP alone does not supply one.

A future damage compatibility scorer should keep those uncertainties explicit,
scope measurements to one defender appearance and geometry, reject gaps/charge
losses as single-Fast-Move samples, and treat lethal HP loss as censored. Missing
knowledge must widen compatibility rather than eliminate a legal move.

The narrow cadence fix retains the existing latest-endpoint behavior within each
family. Arbitrarily reordered same-family source deliveries can miss a
retrospective intermediate interval; this change does not claim sorted-history
reconstruction. Normal live witnesses submit their measurements sequentially.
