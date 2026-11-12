# Processing bottleneck measurement — 2026-11-12

Measurement-only slice. No recognition policy, HUD appearance, pipeline ordering,
archive timeout, installed phone build, or commit is changed by this work.

## Inputs and custody fidelity

Inputs and generated reports are on the 1 TB volume:
`/mnt/overdex-backup/archives/overdex-device-backups/2026-11-12-pixel-noon-review/`.
The source ZIPs are read-only inputs to the harness.

| Match | Archive ID | Direct records | Derived articles excluded |
| --- | --- | ---: | ---: |
| Blanche | `ede620a8-2068-4601-8376-e11c48358039` | 3,658 | 108 |
| Great League | `d6143667-345e-4443-83c5-f78ba98389c3` | 2,687 | 70 |

`sequenceNumber != null` identifies archived accepted records. The harness
reconstructs each once, validates its payload by round-trip serialization, and
retains original raw article IDs, capture clocks, publication clocks, confidence,
and evidence references. Relative custody order survives; local sequence numbers
are contiguous because compact archives have gaps. Availability records use the
existing separate publication path. Projection-only species fields that the
archive does not store are absent in accepted OCR inputs and retain null defaults.
Unreconstructable records fail the run and are listed in the mapping report.

The original *acceptance-arrival* clock is not archived. Therefore paced playback
uses the nondecreasing capture-time envelope in custody order, **not exact original
arrival cadence**. Original `recordedAt` is kept separately because Fast Move
fusion uses receipt/publication grace as well as capture-time proximity. Assigning
new wall times during replay changes that inference and is not a faithful
instrumentation comparison. Regenerated host-clock diagnostic submissions are
counted outside the replay ledger; their archived counterparts are replayed once.
The harness rejects unexpected regenerated non-diagnostic testimony.

Compact exports can omit uncited crop observations. This is a replay of the
preserved accepted subset, not a reconstruction of every frame or every original
custody record. Native OCR, MediaProjection, audio capture, Room lookups, GPU work,
and phone scheduling/contention are not reproduced. Reference knowledge uses the
same bundled Game Master and form lookup with an in-memory test adapter.

## Instrumentation and run method

`MatchProcessingDiagnostics` is opt-in and bounded: at most 2,048 stage/payload
keys plus one overflow bucket, 64 rebuild keys plus overflow, and nine histogram
buckets per clock. It records counts, cumulative/max elapsed and thread CPU time,
invalid CPU samples, processing position, and active testimony age. No timing measurement
is submitted as testimony. The existing HP timing diagnostics remain unchanged.
The harness samples accepted/completed testimony and oldest pending admission age
once per second, outside the processing queue, and exports JSON/CSV on the backup
volume. Availability completion remains covered by the existing drain contract.

Stage timing is inclusive of inline derivations invoked within that stage.
Thread CPU time is not per-coroutine CPU time: other work on the same thread
while a coroutine is suspended can contribute. Cross-thread/unsupported CPU
samples are rejected and counted. Elapsed time includes suspension and scheduling.
Rebuild counters cover the HUD faint resolver, Fast Move, cadence, energy, charge,
and forecast consumers, including resets. Forecast is a separate subscriber and
its counters are partial at the inline drain boundary; it is excluded from serial
stage rankings. No extra forecast drain was added.

For each match: one warm-up run excluded from rankings, three immediate bursts, three paced
runs, and an uninstrumented burst control. Each run checks raw evidence fidelity;
derived payloads, clocks, confidence, predecessor references, and order are
compared after normalizing newly assigned derivation UUIDs. Timings are desktop
diagnostics, not Pixel throughput claims.

Reproduction from the canonical repository:

```sh
cat > /tmp/overdex-profile-rerun.gradle <<'GRADLE'
allprojects { tasks.withType(Test).configureEach { outputs.upToDateWhen { false } } }
GRADLE
OVERDEX_PROFILE_INPUT=/mnt/overdex-backup/archives/overdex-device-backups/2026-11-12-pixel-noon-review \
JAVA_HOME="$PWD/.jbr" ./gradlew -I /tmp/overdex-profile-rerun.gradle \
  :app:testDebugUnitTest --tests '*MatchProcessingArchiveProfileTest'
python3 tools/summarize_match_processing.py \
  /mnt/overdex-backup/archives/overdex-device-backups/2026-11-12-pixel-noon-review/processing-profile
```

Without `OVERDEX_PROFILE_INPUT`, the private-archive experiment is skipped by the
normal unit suite. `OVERDEX_PROFILE_MAPPING_ONLY=1` validates mapping alone.
The full experiment takes approximately 19 minutes of paced playback plus build
and burst processing. Do not run another build or CPU-heavy test concurrently.

For the report, sustained backlog means three consecutive one-second samples with
pending work and oldest age >=100 ms. Sampling misses shorter transients. The
burst begins with an intentionally artificial queue; it is not evidence of the
same onset during a real battle.

## Results

All 12 measured runs completed, plus two warm-ups and two instrumentation-off
controls. No mapping failures: 6,345 directly accepted records reconstructed;
178 derived records excluded. Raw fidelity and recomputed derived content/order
checks passed across the complete experiment. This is instrumentation equivalence,
not a claim that recomputation recreates every missing original live conclusion.

Intel Core i5-7500 desktop, repository JBR 21.0.10. These are **median total thread
CPU milliseconds per burst**, not per-observation latency:

| Rank (Blanche) | Serial stage | Blanche CPU ms | Great League CPU ms |
| ---: | --- | ---: | ---: |
| 1 | Fast Move inference | 1,060.13 | 252.20 |
| 2 | Cadence inference | 1,003.49 | 253.06 |
| 3 | Energy inference | 1,001.62 | 233.35 |
| 4 | HUD faint reconciliation | 937.09 | 206.65 |
| 5 | Charge announcement inference | 920.12 | 220.31 |
| 6 | Player information | 62.54 | 23.74 |
| — | Raw publication | 21.86 | 10.57 |

Detailed elapsed/CPU rankings, maxima, calls and histograms remain in the JSON
and `stage-times.csv` / `stage-summary.md`. HP payloads account for a median
94.43% of Blanche burst CPU and 83.47% of Great League burst CPU. Great League's
paced proportion is lower (61.53%); do not generalize the burst percentage to
all scheduling conditions.

| Match | Burst drain range, 3 runs | Paced duration | Highest sampled pending count | Highest sampled oldest age | Sustained paced backlog |
| --- | ---: | ---: | ---: | ---: | --- |
| Blanche | 5.670–6.697 s | 174.535–174.536 s | 4 | 12.16 ms | None, 0/3 |
| Great League | 0.773–1.728 s | 185.830–185.832 s | 4 | 7.83 ms | None, 0/3 |

The artificial Blanche burst qualifies as sustained backlog from its first
~1.00-second sample in all three repetitions; the queue was intentionally loaded
at time zero. Great League bursts drain before three samples can qualify.
**The long Pixel backlog did not reproduce under approximate desktop pacing.**
Bursts consistently identify the same cost cluster, but Great League wall/CPU
cost varies substantially despite one warm-up. JIT and scheduling remain timing
limitations; exact rebuild counts are the stronger repeatability result.

### Repeated reconstruction, measured

Each of the five inline consumers (HUD faint state, Fast Move, cadence, energy,
and charge inference) performed:

| Resolver | Blanche calls per consumer | Input rows visited per consumer | Maximum input | Great League calls per consumer | Input rows visited per consumer | Maximum input |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Opponent team loss | 746 | 397,619 | 906 | 223 | 63,415 | 400 |
| Opponent verified HP faint | 659 | 379,548 | 905 | 0 | 0 | 0 |

Blanche's inline resolvers together visit 3,904,238 input rows, including player
histories. Great League visits 319,716. Repeated opponent counters are identical
across consumers in these inputs; player histories differ because some consumers
also receive identity projections. The background forecast sometimes reaches
fewer rebuilds at the inline drain boundary and must not be used for an exact
cross-run total. Completion semantics were not extended to wait for it.

### Existing phone evidence and onset

`archive-hp-backlog.csv` extracts the already recorded HP diagnostic summaries;
no second HP timing reader was added. Relative to the first preserved source
clock, the first run of three consecutive ~5-second summaries with oldest pending
age >=1 second begins:

- Blanche: observed by **90.745 s**, previous sample **85.738 s** was clear.
  This follows the 3→2 ball count at 44.66 s, charged moves at 56.15/69.16 s,
  the 2→1 count at 72.28 s and Vaporeon identity at 73.83 s. Earlier stalls
  sometimes cleared (for example at 85.74 s), so 46 s is not a sustained queue
  onset even though some HP publications were already delayed then.
- Great League: observed by **85.528 s**, previous sample **80.522 s** was clear.
  This follows Exeggutor at 69.89 s, the 3→2 count at 73.39 s and Dragon Claw
  text at 77.57 s. It precedes the Camerupt entry captured at 88.37 s.

These are sampled onset brackets, not exact admission timestamps. Blanche's
final diagnostic has 512 tracked pending HP submissions, oldest 53.455 s, and
661 diagnostic-tracker evictions. Those evictions are tracking-capacity loss,
**not dropped accepted evidence**; older untracked pending ages cannot be inferred.
Great League ends with 34 tracked pending, oldest 52.152 s, with no tracker
evictions. Native capture/recognition and phone contention could contribute
cost not represented by this desktop harness.

### Recommended next slice

Start with a narrowly scoped reuse/cache of **identical source-history faint
reconciliations**, beginning with opponent histories. The measurement proves
repeated full reconstructions and similar cost across the five consumers; it
suggests these reconstructions explain much of their common CPU cost. It does
not isolate every resolver's CPU individually or establish an achievable speedup.

A cache must distinguish source-history contents/revisions, side and projections;
late/reordered observations, conflicts, resets, new identities and survival proof
must invalidate/recompute the result. Preserve every accepted observation and
all result evidence references. Do not share a merely latest result across
consumers with different histories. Validate output equivalence and remeasure
before considering any pipeline split. The actual optimization is **not** made
in this measurement slice. A separately authorized Pixel trial remains necessary
to attribute the phone's sustained delay conclusively.

## Verification

- Focused experiment/drain run: 10 tests passed, including all 12 measured
  replays, warm-ups, uninstrumented controls, suspended interpretation, accepted
  bursts, late evidence after results, and availability-only drain behavior.
- Full suite: **2,887 tests across 383 suites; 2,886 passed, one opt-in experiment
  skipped, zero failures/errors**. The private experiment had already passed
  separately. Additional tests cover unsupported/cross-thread CPU clocks,
  histogram bounds, no recursive diagnostic submissions, and eight-file export
  retention.
- `:app:assembleDebug`: passed. No installation or commit performed.
- All measured runs completed every accepted testimony; no diagnostic key
  overflow or invalid CPU samples occurred.
- Both original ZIP SHA-256 values match the pre-experiment values.
- Python report summarizer compiles and produces all 12 run summaries;
  `git diff --check` passes.

Evidence files under `processing-profile/` on the backup volume include
`full-profile.log`, `full-verification.log`, `focused-test-results/`,
`full-unit-results.zip`, `verification.json`, `input-sha256.json`, per-run JSON,
`stage-times.csv`, `backlog.csv`, `runs.csv`, `archive-hp-backlog.csv`, and
`stage-summary.md`. The prepared `overdex-processing-profile-debug.apk` has SHA-256
`a093215c30146fb41b5b443280768bfd01209f60082ce5630e31783d4f04ad1a`.
The opt-in flag remains absent unless explicitly created for a later device trial.

## Separate device trial readiness

The debug build has an opt-in flag at app-private
`files/diagnostics/enable-match-processing-profile`. If present before starting a
new Droidball Match, the normal factory attaches timing counters. It is absent
by default. After an archive attempt (including a drain timeout), the independent
archive worker writes `files/diagnostics/match_processing/<matchId>.json` and
retains at most eight summaries. It reuses the latest existing HP diagnostic
payload for HP submission ages rather than adding a second HP timing reader.
The measurement file is separate from the `.odxmatch` archive and does not submit
a diagnostic Article. Enabling the flag, installing, and performing a live trial
are deliberately left for the separate device trial. None was done here.
