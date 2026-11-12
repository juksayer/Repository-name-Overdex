# Pixel raw-publication investigation — November 12, 2026

Input: [first cache trial](2026-11-12-pixel-cache-trial.md), Match
`09cb2cae-7620-4be8-a27b-dc4fd75fe7dd`. Source diagnostics and immutable archive
remain on the 1 TB backup drive under `2026-11-12-pixel-cache-trial/`.

## What is measured

Raw publication: 2,914 invocations, 32.085 seconds cumulative elapsed,
7.697 seconds thread CPU. Average 11.011 ms elapsed / 2.641 ms CPU per
invocation. The 24.388-second difference is not all computation: it can include
lock waits, preemption, GC and scheduling. These counters do not distinguish those
causes. This stage is 27.34% of cumulative serial-stage elapsed time.

| Payload | Calls | Elapsed total | CPU total | Largest elapsed invocation |
| --- | ---: | ---: | ---: | ---: |
| CropCaptured | 1,298 | 12.423 s | 3.185 s | 82.9 ms |
| RawTestimony | 530 | 6.129 s | 1.410 s | 106.2 ms |
| ActiveHpBarMeasured | 471 | 5.966 s | 1.429 s | 104.9 ms |

The stage was broader than its label suggests. In order it performs:
1. `realityTimeline.append(article)`.
2. HP timing callback for eligible HP measurements only.
3. `cueSpeciesChecks(article)`.
4. `_articles.tryEmit(article)` to the general article stream.

CropCaptured bypasses the HP callback and reaches the early-return path of species
cues. Its cost therefore narrows the investigation to append/broadcast plus small
branch and instrumentation overhead. It is not evidence that HP diagnostic locking
is the general bottleneck.

## Code evidence and hypotheses

`InMemoryRealityTimeline.append` adds to an ArrayList under one lock. It does not
serialize to disk, rebuild battle history, or render the HUD. Full/tail snapshot
readers copy under the same lock, so contention remains possible; list growth
occasionally allocates. There is no proof yet that the lock dominates.

The general article stream is a MutableSharedFlow with replay 4,096, extra capacity
4,096 and DROP_OLDEST. `tryEmit` does not await every listener's recognition result,
but delivery can still involve shared synchronization and waking collectors.
Numerous witnesses collect this general stream and filter for their own payloads.
Collectors run in several contexts: the Match's dedicated serial dispatcher,
Dispatchers.Default, and the view model's main-thread scope. Their recognition
cost is not automatically inline in tryEmit; scheduler/lock interactions require
measurement. Static collector call sites do not establish the runtime subscriber
count. Broadcast fan-out is the leading hypothesis, not a measured cause.

## Added diagnostic boundaries

The old raw-publication interval is now four adjacent, non-overlapping intervals:

- `raw_publication.timeline_append`
- `raw_publication.hp_timing`
- `raw_publication.species_cues`
- `raw_publication.article_broadcast`

They reuse the existing bounded counters, CPU/elapsed histograms and out-of-band
export. No extra testimony, new worker, delivery filter, scheduling change or
recognition rule is introduced. The existing reporting scripts group the prefix
back into raw_publication for historical comparisons. Detailed stage keys remain
in exported JSON; do not sum a parent total and these children together.

The injected-delay test verifies append waiting is charged only to append, CPU
stays separate, all four intervals occur once, and the original raw clocks,
evidence references and completion are preserved. Existing instrumentation
on/off equivalence and drain tests remain in place.

## Next decision

A separate Pixel test using the new instrumented APK is required. Current phone
build and installed diagnostic flag remain unchanged by this investigation.
If append dominates, split lock wait from lock-held snapshot/append work next.
If broadcast dominates, measure active subscriber counts and evaluate narrower
notification lanes while preserving every accepted timeline article and existing
witness delivery guarantees. If all intervals have large elapsed/CPU gaps,
investigate phone scheduling/GC pressure rather than assuming a slow algorithm.
Do not remove observations or defer canonical publication based on this report.

Validation complete: 12 focused diagnostic/drain tests passed; full unit suite
2896 passed, 1 opt-in archive experiment skipped, zero failures/errors
(384 suites). Debug build and report-aggregation compatibility check passed.
APK saved on the 1 TB drive under `pixel-cache-trial/publication-breakdown/`:
`overdex-publication-breakdown-debug.apk`, SHA-256 `ba8fd3099f23866e9e16f4b65fcd478e7dc084d57b278e08374be7eb5c106cae`.
Not installed; no commit. No new battle performance claim is made from these tests.
