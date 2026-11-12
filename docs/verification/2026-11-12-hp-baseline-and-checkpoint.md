# Normal HP baseline handoff and project checkpoint — November 12, 2026

## Behavior

`ChargedImpactHpWatchPolicy` now returns separate dispositions for excluded
charged-camera pixels, baseline-only normal samples, and ordinary evidence.
The live damage witness uses the same sample-consuming detector method as the
regression test. A baseline-only normal sample resets and seeds the detector,
returns no damage event, and lets the following decrease settle normally.
Charged-camera samples reset the detector without seeding it. Border, cadence,
and recipient-visual witnesses continue to exclude both non-evidence cases.

The Pixel 14:47 fixture's first 99% sample now seeds the baseline. The following
87% reading produces a settled damage tick at its original capture time. The
existing control verifies that this tick can support a Turtonator move use.
No archived article or timestamp is rewritten; this is a live handoff fix, not
an invented repair to the recorded match. This does not explain all Replay gaps.

## Checkpoint organization

The accumulated work is interdependent across live observation, archive payloads,
inference, Replay, and tests. Keep those sources together in one application
checkpoint rather than splitting shared files into incomplete feature commits.
Keep reusable calibration and regression assets with application source and tests
so the checkpoint includes its required inputs. Commit documentation and analysis
tools separately.

| Area | Included work |
| --- | --- |
| Calibration | Moondrop reference collection/catalog, screenshot preview, new reader regions and persistence checks |
| Reader scheduling | Event-triggered capture windows, departure checks, forced replacement and timer gates, bounded species search |
| Faint attribution | Capture-time team-loss resolution, unresolved/aggregate losses, positive-HP contradictions, shared live and Replay projections |
| Terminal evidence | Charged-impact HP ownership, early preparation, terminal particles and appearance sequences, bounded capture diagnostics |
| Fast Moves and HP | Recipient audio pairing, appearance/time boundaries, finishing observations, source-timed Replay impacts, baseline handoff |
| Replay and review | Event markers, comparison batches and charts, saved review inbox, navigation and display corrections |
| Performance | Bounded stage diagnostics, preserved-observation profiling, exact faint-history result reuse, finer raw-publication measurements |
| Evidence and rules | Recorded regression fixtures with provenance, reader inventory, battle rulebook, dated investigation and validation reports |

Android Studio's selected deployment device and generated compiler settings are
local workstation state and are intentionally left uncommitted. Private device
backups, full source archives, APKs, and generated performance reports remain
outside the repository on the 1 TB drive. Committed fixture extracts and screenshots
are intentional project regression/calibration assets.

Application checkpoint: `94b87e0b` (429 files, including intentional test assets).

## Validation

- Focused HP handoff and charged-impact policy: 23 tests passed, including camera
  exclusion, baseline-only return, appearance reset, and the original Pixel samples.
- Full unit suite: 2902 tests, 2901 passed, 1 skipped, no failures/errors across 385 suites.
- Debug APK build passed in the same verification run.
- Android device-test Kotlin compilation passed; no emulator or phone test run.
- JSON/gzip fixtures parsed; PNG/JPEG assets verified; analysis tools syntax checked.
- Changed project files checked for credential patterns and whitespace errors.
- No phone installation or remote push is part of this checkpoint. Instrumentation
  test sources are included; device tests were not rerun in this slice.
