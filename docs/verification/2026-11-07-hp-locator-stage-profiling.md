# HP locator stage profiling

November 7, 2026. Internal locator timing is implemented as a bounded extension
of the existing HP pipeline diagnostic. Final build, emulator, installation, and
new live-match acceptance are recorded below. The earlier
[Sierra diagnostic recording](2026-11-07-sierra-hp-review.md) remains the measured
baseline; it does not contain these new internal timing fields.

## Measured problem and acceptance boundary

The Sierra archive contains 38 pipeline summaries. Its final cumulative snapshot
has 646 enabled HP reader receipts and 646 locator passes. All 660 accepted
geometry submissions reached the Timeline: 340 player and 320 opponent readings,
with no final pending submissions, unmatched recordings, duplicates, or evictions.
The reader did not consume 1,329 of 1,974 sequence advances bracketed by enabled
receipts. Those feed gaps are not a count of missing attacks.

The locator averaged 232.464 ms of elapsed time across those 646 passes, totaling
150.172 seconds. Its p50 histogram bucket is `(150, 250] ms` and p90 bucket is
`(500, 1,000] ms`. Those bounds do not identify which internal operation was
expensive or distinguish CPU work from waiting/preemption. The original report
uses GO's source monotonic timestamp as zero and qualifies ordinary-play
segmentation. Its late missing-HP tail overlaps QTE and postbattle scenes; it is
not evidence of continuous ordinary-play blindness.

This extension measures the previously combined work. A fresh match recorded
**after installation** is still required to identify the live internal bottleneck
and select a performance change from those internal measurements.

## Timing and ownership

The existing [live HP witness](../../app/src/main/java/com/example/overdex/battle/observation/LiveActiveHpBarWitnesses.kt)
still receives each frame once, measures both sides, and submits accepted geometry
with the original source time. [LiveBattlefieldHpTracker](../../app/src/main/java/com/example/overdex/battle/observation/BattlefieldHpTracker.kt)
now returns optional [HpBarLocatorTimings](../../app/src/main/java/com/example/overdex/battle/observation/HpBarLocatorTimings.kt)
alongside the existing measurements and factual locator outcomes. This per-call
value is transient; it is not a per-frame Timeline Article or retained image.

| Measurement | Meaning and limit |
| --- | --- |
| `locatorDuration` | Existing elapsed interval around the complete locator call. Its meaning is unchanged. |
| `locatorBreakdown.lockWait` | Elapsed interval between requesting and entering the tracker monitor. It includes monitor contention and any scheduling/preemption within that interval; it is not proof of pure blocked-lock time. |
| `locatorBreakdown.threadCpu` | Separately measured current-thread CPU consumed during the locator call, when the device clock is available. It is not inferred by subtracting elapsed durations. |
| `localSearch` | Local tracking search work, with its own elapsed/CPU totals and invocation count. |
| `recoverySearch` | Wider recovery search work, with independent invocation count and elapsed/CPU totals. Existing recovery admission and search policy remain unchanged. |
| `pixelExtraction` | Allocation/read of source pixels for scanned windows. |
| `geometryDetection` | HP outline/fill detection inside the supplied pixels. |
| `assignment` | Existing side/home tracking assignment work. |
| Per-side `postLocatorSampling` | Appearance/border and recipient-effect sampling after accepted location, before the existing geometry submission. |

Search totals overlap their pixel-extraction/geometry sub-stages. Do not add every
row to reconstruct the complete locator duration. CPU and elapsed distributions
are distinct measurements. Thread CPU may legitimately be zero for an interval;
an unavailable clock instead produces no CPU sample and increments an explicit
unavailable counter.

A cached result reports its current call's monitor-admission and CPU measurements,
plus a cache-hit count. It must not replay the scan timings attached to the
original computation. Diagnostic aggregation defensively omits scan stages from
calls whose `workPerformed` flag is false.

The existing receipt, enabled/disabled, locator-pass, outcome, submission, and
actual-Timeline-recording counters keep their previous meanings. No new cue
classifies a missing bar as a QTE, faint, hidden target, or attack. Calibration,
recognition thresholds, source timestamps, Fast Move inference, and energy rules
are not changed by this instrumentation.

## Bounded summaries and archive compatibility

[HpObservationDiagnostics](../../app/src/main/java/com/example/overdex/battle/observation/HpObservationDiagnostics.kt)
adds the new measurements to its existing five-second and terminal window/cumulative
summaries. Fixed stage names and fixed histogram bins keep memory bounded. No
per-frame diagnostic Articles, PNGs, unbounded sample lists, or background timing
jobs are added.

Each stage accumulates its invocation `calls` separately from its timing histogram.
If a frame reads three pixel windows, it contributes three extraction calls and
one histogram sample containing their per-pass aggregate duration. It does not
pretend those three calls were three separate frames. Optional CPU measurements
are aggregated only when available; unknown CPU never becomes an invented zero.

The serializable additions are optional with `null` defaults:

- `HpPipelineStatistics.locatorBreakdown` contains timed/untimed pass counts,
  cache hits, monitor-admission elapsed, total thread CPU, unavailable-CPU counts,
  and the five fixed stage summaries.
- `HpPipelineSideStatistics.postLocatorSampling` contains side-specific call
  counts and separate elapsed/CPU distributions.

Older archives lacking these fields still decode. Their missing timing remains
unavailable rather than being reconstructed as a zero-cost locator. The existing
`HpObservationPipelineMeasured` / `ArchivedHpObservationPipelineMeasured`
envelopes and `hp_observation_pipeline_measured` kind are retained, so compact
exports preserve the additional counters without requiring image/audio artifacts.

The [research/log formatter](../../app/src/main/java/com/example/overdex/diagnostics/HpObservationDiagnosticText.kt)
shows local/recovery invocation counts and available CPU in the sparse summary,
with full stage details in the archive inspector. Percentiles remain inclusive
histogram bucket upper bounds, not exact quantiles. The ordinary battle HUD and
high-rate per-HP log presentation are unchanged.

As before, stage records can cross reporting-window boundaries. Terminal summaries
are point-in-time snapshots; they do not imply that producer threads were joined
or that no in-flight reader/Custody work can finish later. Archive draining and
snapshot limitations remain those documented by the
[original diagnostic implementation](2026-11-07-hp-observation-diagnostics.md).

## Isolated phone benchmark and rejected experiment

A standalone Java/D8 harness measured the actual pixel detector from the
installed APK on the Moondrop, running as the Android shell user in its own
`app_process`. It did not start Overdex, run app instrumentation, or access app
private data. Only benchmark files under `/data/local/tmp/odx-hp-benchmark` were
created. Decoding and copying pixels occur before the detector timing.

The original pure detector's CPU median was 27.512 ms on the retained Sierra
wide HP band and 3.128 / 2.919 ms on its two local tracking windows. These are
isolated workload measurements, not sustained live rates. Only one retained
Sierra full frame contains both battle HP bars; the workload set is deliberately
targeted, with additional retained nonbattle frames, existing recorded crops,
and generated gap/color/clipping/fragment cases.

A streamed-outline-run experiment removed temporary per-row pairs/lists without
changing the intended predicates or candidate order. Comparing original and
experimental APKs through independent classloaders in the same process,
alternating execution order, preserved every full candidate list on **21
workloads × 32 paired samples**, after 16 warm-ups per variant. Coordinates,
fill, confidence, count, and order all matched.

It failed the performance check: CPU medians rose from 27.512 to 28.776 ms for
the wide band, 3.128 to 3.364 ms for the player tracking window, and 2.919 to
3.040 ms for the opponent window. Nonbattle bands were 15–23% slower. Some crops
improved, but the results do not justify a general throughput claim.

**The experimental production change was reverted before installation.**
`PersistedActiveHpBarWitness.kt` retains the original detector. Two useful
fragment/negative regressions remain. The released slice adds profiling; it
does not claim faster live reading or more accurate attack recognition.

Original APK SHA-256:
`89e5ec8a34333320cea53fcbdac06c9bda16ebd05b5d4b02bc3c2cb1a886f8a5`.
Rejected experimental APK SHA-256:
`47dd7c9a935c41132324366cdf9909b8fef7fb21f7d2ded846c9ed2cdb9fb1ae`.
Reproduction sources, fixture hashes, exact candidate outputs, paired samples,
and comparison tables are retained privately under
`/home/sean/overdex-device-backups/2026-11-07-hp-measurer-benchmark/`.

## Validation to complete

| Check | Current status |
| --- | --- |
| Focused counter/timing tests | Pass in the full suite. Cover elapsed/CPU separation, unavailable CPU, cache hits, fixed stage counts, window/cumulative aggregation, and side-specific post-location sampling. |
| Archive compatibility and round trip | Pass. Cover older JSON without internal fields and typed compact retention of new statistics. |
| Research formatting regressions | Pass. Cover overlap labels, CPU unknowns, and older reports. |
| Tracker instrumentation tests | Pass. Use deterministic clocks, real monitor contention, and unchanged geometry/search-policy checks. |
| Full unit suite and debug APK builds | **1,904 tests**, zero failures/errors/skips; debug app and Android-test APKs build successfully on the final source with the rejected optimization removed. |
| Geometry equality and allocation/performance benchmark | Completed on the Moondrop; the slower experiment was rejected and reverted. |
| Emulator integration | API 34 `Overdex_API34_Check`, `emulator-5556`: **OK (2 tests)** in `HpObservationDiagnosticsDeviceTest`. Actual Bitmap → witness → Custody → Match → Timeline verifies stage samples, per-side post-location calls, archive retention, source times, and no invented battle events. |
| Phone replacement install and data-preservation check | Installed on Moondrop MD_PH_001 (`MDPH00124112500414`) with Droidball stopped, using replacement without clearing data or running app instrumentation. Detailed comparison/provenance below. |
| Fresh live battle with internal stage reports | Pending after installation. Inspect search mode, elapsed/CPU/monitor intervals, ordinary-scene availability, reader throughput, and accepted-geometry custody preservation. |

The next performance decision should follow that new live breakdown. The Sierra
recording establishes the old combined elapsed cost; it does not justify guessing
which internal stage is responsible or claiming this slice has solved Fast Move
identification.

## Installed build and protected evidence

- Installed APK bytes match the tested debug APK SHA-256:
  `9e5321c785dce334ad090fedc15b9c73da051eb35561879405f1e05f6ea07192`.
- Android-test APK SHA-256:
  `a107981cb2354146f25fbc3811da55d3ff534663c906d01a5c6deca49757a17a`.
- A read-verified 313,802,752-byte backup covers 403 database, preference,
  calibration, match, and other user files. The existing loose artifact store
  was excluded and left untouched. No phone instrumentation was run.
- The post-install inventory still contains all 403 files with none added or
  missing. All 402 non-Firestore-cache files are byte-identical, including owned
  cards/teams, calibration profiles, preferences, and saved matches. The Firestore
  cache database is the sole changed file.
- Final build, unit summary, emulator output, pre/post-install inventories, and
  installed APK provenance are retained privately at
  `/home/sean/overdex-device-backups/2026-11-07-hp-locator-stage-profiling/`.
- Both APKs used for the rejected benchmark were copied to the desktop and
  hash-verified before removing only the task-created temporary phone benchmark
  directory. Its sources and measurements remain available for comparison.
