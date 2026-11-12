# Terminal particle pixel reader

Status: implemented measurement primitive with recorded-image tests and a
standalone Pixel 4 cost check. Subsequent
[live observation integration](2026-11-12-terminal-particle-live-observation.md)
registers it for bounded charged-phase measurement. It does not assign KOs,
change HP, recognize a Pokémon, or classify a recall.

This is the next component of the no-empty final charged-KO path established
in [VIS-03](../reference/battle-rulebook.md) and the
[preceding visual study](2026-11-12-terminal-particle-feasibility.md). The empty
outline is not an acceptance requirement for that path.

## Implemented measurement

[TerminalParticleFrameMeasurer](../../app/src/main/java/com/example/overdex/battle/observation/TerminalParticleFrameMeasurer.kt)
ports the independent offline probe into Kotlin without adding OpenCV or another
runtime dependency. It reads only the supplied visible territory, reuses its
pixel and component buffers, and reports the source clock, preceding source
clock, explicit availability status, and a spatial group of measured points.
Each point retains its geometry, newly bright fraction, halo coverage and
brightness falloff. These values are measurements, not KO probabilities.

The first usable image is a baseline. A source gap over 250 ms, changed crop or
source dimensions starts a new baseline. Duplicate/older images cannot replace
a newer baseline, including after a newer invalid image. Transparent or failed
reads interrupt continuity. Broad whiteout, excessive component counts or more
than 128 eligible points report unavailable clutter; they do not truncate a
storm into an apparently reliable cluster. Allocation is capped at 1.5 million
territory pixels. The caller still owns attention windows, phase/side/appearance,
capture visibility, actual frame freshness and lifecycle resets.

## Recorded regression coverage

The [fixtures](../../app/src/test/resources/battle/recorded-evidence/2026-11-12-terminal-particles/README.md)
contain 16 image pairs (32 PNG crops), representing eight situations at native
and capture-like resolutions. Original source and crop hashes, exact MP4 clocks,
and transformations accompany them. Tests run from retained resources without
depending on the original videos or a connected phone.

[The tests](../../app/src/test/java/com/example/overdex/battle/observation/TerminalParticleFrameMeasurerTest.kt)
require the Kotlin measurements to agree with independent OpenCV reference
positions, retain positive Cramorant/Leavanny collapse signals, and reject the
selected living-recall/concession pixel patterns. Crucially, shield, QTE and
attacker-animation counterexamples must remain positive feature measurements.
Those tests prevent treating this component as a standalone faint classifier.
Separate checks cover clock ordering, gaps, invalid/unreadable frames, reset,
crop changes, excessive bounds and whiteout.

Final validation on November 12: all **2,822 unit tests across 370 suites** passed,
including the 12 new reader tests, with no failures, errors or skips. The debug
APK build succeeded. Production source, test, fixture manifest and APK hashes,
the test report and build log are retained in the study directory under
`reader-validation.json`, `reader-tests.xml` and `reader-full-build.log`.
That checkpoint APK was not installed; the phone cost check below used the
standalone probe only. The linked live-observation follow-up documents the
subsequent build's installation and controlled device check.

## Pixel 4 measurement boundary

A generated standalone DEX probe reads five pairs of saved frames on Pixel 4
`99261FFAZ0017J`, testing both the study crop and the wider factory opponent
territory at 672×1493. It calls the compiled production measurement method using
Android Bitmaps. It verifies the expected study-region results and reports cold
baseline, warm median, 95th-percentile and maximum wall time, plus thread CPU
time. This repeated-frame workload is a cost check, not live-capture validation.

The probe runs as a short shell process. It does not install an APK, start or
stop Overdex, request screen sharing, operate the UI, or access app-private data.
Its temporary device files are removed after the run. Build hashes, exact input
hashes, timing results and cleanup confirmation are retained in the
[study directory](/home/sean/overdex-device-backups/2026-11-12-terminal-particle-study),
under `phone-probe/`, `phone-run.json` and `phone-stdout.log`.

The final probe completed successfully at 2026-11-12 07:14:56 UTC and confirmed
temporary-file cleanup. Across ten case/territory combinations, with eight
warm-up and 25 measured iterations each, median readings ranged from
**2.553 to 7.865 ms**. The largest per-case 95th percentile was 7.956 ms and the
largest individual measured reading was 8.017 ms. The largest cold baseline
was 7.381 ms. These figures include bitmap pixel reads and measurement but
exclude live capture, testimony publication and competing battle workloads.
They support trying bounded live sampling; they do not establish a sustained
frame rate or phone-wide CPU budget.

## Remaining integration

Bounded live measurement, availability and typed archive publication are now
implemented in the linked follow-up. Reconcile the receiving side and phase
against source-time cues, including
delayed recognition. Establish a qualified collapse/recall sequence before any
KO publication; the particle primitive cannot supply that conclusion itself.
Then use the same resolved event for live and Replay HP, keeping inferred
depletion distinct from an observed empty bar and retaining contrary survival
evidence. Verify capture cost and actual KO/HP behavior in a fresh recorded
battle before claiming the full goal is complete.
