# Exact faint-history caching — 2026-11-12

Follow-up to [processing measurements](2026-11-12-processing-bottleneck-measurement.md).
Scope: reuse identical pure resolver results within one Match. No recognition
thresholds, raw observations, capture clocks, event ordering, HUD appearance,
archive timeouts or drain semantics are changed. No install or commit.

## Implementation

`FaintHistoryCache` is Match-owned, synchronized and bounded to 16 entries and
8,192 retained input rows. Its key includes resolver kind, side and the complete
ordered immutable observation values. It copies list membership and compares
values after hashing; equal hashes alone do not match. The resolver namespaces
are team losses, verified HP faints and terminal charged faints. Oversized inputs
bypass storage; least recently used entries are evicted when necessary. Exceptions
are not cached. Eviction changes only how often the existing resolver runs.

The live HUD, Fast Move, cadence, energy, charge and forecast consumers retain
independent histories, source maps, filtering, conflicts and recovery state. Their
existing pure resolver calls are memoized. No consumer reuses another consumer's
mutable live tracker. Match release clears retained cache entries. Results are
read-only projections under the existing immutable evidence contract.

Rebuild callbacks now count actual resolver computations, not cache lookups.
The existing internal HP history request counter retains its old meaning. Cache
hit/miss, eviction and retained-row counts are exported out of band with the
processing diagnostics. No diagnostic testimony is added.

## Experiment

Inputs remain the two immutable Pixel ZIPs from the previous slice. New reports
are under the 1 TB backup volume:
`/mnt/overdex-backup/archives/overdex-device-backups/2026-11-12-pixel-noon-review/processing-cache-profile/`.
Baseline `processing-profile/` reports are preserved.

Each match has an uncached warm-up and cached warm-up, three cached bursts paired
with three fresh uncached controls, three cached paced runs and an instrumentation-
off control. Every comparison checks raw payloads, capture/publication clocks and
evidence references, and derived content/source clocks/order after UUID normalization.

Pacing still uses the nondecreasing capture-time envelope in custody order;
original custody-arrival cadence was not archived. Native capture/OCR/audio,
phone contention and database lookups are outside this desktop experiment.
Total stage CPU is serial-pipeline CPU, not total process CPU. Cache lookups
copy/hash/compare ordered inputs and may wait briefly for another consumer under
the cache lock. Fresh controls always follow their cached burst, so JIT and
run-order effects remain limitations; small stage differences are not isolated
causal measurements. Forecast is asynchronous and may still be processing at the existing inline drain
boundary. Its cache statistics vary; inline rebuild counts are reported separately.

```sh
OVERDEX_PROFILE_INPUT=/mnt/overdex-backup/archives/overdex-device-backups/2026-11-12-pixel-noon-review \
OVERDEX_PROFILE_OUTPUT=/mnt/overdex-backup/archives/overdex-device-backups/2026-11-12-pixel-noon-review/processing-cache-profile \
OVERDEX_PROFILE_REPEATS=3 JAVA_HOME="$PWD/.jbr" ./gradlew \
  -I /tmp/overdex-profile-rerun.gradle :app:testDebugUnitTest \
  --tests '*MatchProcessingArchiveProfileTest'
```

The init script disables test-result up-to-date reuse, as documented in the
baseline report. Do not run concurrent heavy builds during the experiment.
Use `tools/summarize_match_processing.py` for stage/backlog tables and
`tools/compare_faint_cache_profiles.py REPORTS BASELINE` for paired comparisons.

## Verification and results

Fresh uncached/cached burst medians, three repetitions each:

| Match | Uncached serial CPU | Cached serial CPU | CPU reduction | Uncached drain | Cached drain |
| --- | ---: | ---: | ---: | ---: | ---: |
| Blanche | 4,726.230 ms | 1,574.423 ms | 66.7% | 5,798.956 ms | 2,467.160 ms |
| Great League | 397.335 ms | 310.748 ms | 21.8% | 456.443 ms | 365.564 ms |

Burst drain ranges: Blanche uncached 5,748–6,150 ms versus cached 2,362–2,617 ms;
Great League uncached 408–663 ms versus cached 260–501 ms. Individual Great League
ranges overlap. Use the repeated medians and deterministic reconstruction counts,
not the fastest individual run, to interpret the improvement.

Inline resolver input rows fall from 3,904,238 to 786,603 for Blanche (79.9%) and
319,716 to 65,036 for Great League (79.7%). Every cached burst has 5,643 hits for
Blanche and 905 for Great League. Forecast completion changes the Blanche miss
count at drain, so that count is not a deterministic whole-match metric. No run
exceeds either storage limit and no input needs an oversized bypass.

Blanche's first HUD faint reconstruction remains roughly unchanged (882 → 867 ms
median CPU). Its later Fast Move stage falls 950 → 165 ms, cadence 937 → 147 ms,
energy 906 → 145 ms, and charge announcement 881 → 100 ms. Great League's first
faint stage increases 50 → 59 ms while later stages save more than that overhead.
Stage numbers are medians separately and need not add to the median total.

The historical baseline has a much higher Great League CPU median than the fresh
controls. Therefore the historical comparison is retained in the report but is
not used as the headline cache improvement.

All 12 measured cached runs, six fresh uncached burst controls, four warm-ups
and two instrumentation-off controls passed raw/derived equivalence checks.
All 6,345 directly accepted records map successfully; 178 previously derived
archive articles remain excluded as inputs.

| Match | Paced runs | Highest sampled pending | Highest sampled oldest age | Sustained paced backlog |
| --- | ---: | ---: | ---: | --- |
| Blanche | 3 | 4 | 4.360 ms | None |
| Great League | 3 | 4 | 1.008 ms | None |

Sampling is once per second and can miss short transients. The cached bursts
also have no interval meeting the pre-existing three-consecutive-samples backlog
definition: Blanche drains in less than three seconds, rather than its baseline
5.7–6.7 seconds. This does not mean a burst has no pending queue. It intentionally
loads the queue immediately. No new sustained backlog interval was observed.
The long real-Pixel delay still does not reproduce under desktop pacing.

## Recommendation and acceptance boundary

Keep the bounded cache and test this build on the Pixel with out-of-band
processing diagnostics enabled for a separate trial. The duplicate reconstruction
cost is measured and reduced. The first changed faint-history reconstruction is
still the largest Blanche stage; lookup/copy/hash costs remain too. A larger
incremental resolver or scheduling change would require separate measurement and
correctness work. Do not infer that the phone's Vaporeon HUD delay is solved from
these desktop results.

Validation complete:

- Initial focused checks: 32 tests passed (cache, live HP, revised faint exclusion and drain).
- Final focused checks: 32 tests passed (8 cache, 13 recorded terminal charged faint, 6 timing diagnostics, 5 drain).
- Full unit suite: 2896 tests across 384 suites; 2895 passed, 1 opt-in archive experiment skipped, zero failures/errors. The archive experiment passed separately with its inputs enabled.
- Debug build succeeded; `git diff --check` passed.
- Both source ZIP SHA-256 checksums match the baseline.
- APK saved as `processing-cache-profile/overdex-faint-history-cache-debug.apk`, SHA-256 `4ebe3fa846c98258f1e1ccf1cb4238fa86ff5b60befc3d930fac3cf8e51d9af0`.
- Reports, XML test results, logs and the APK are on the 1 TB volume. No installation or commit.

Cache regression coverage includes exact-value reuse, hash collisions, changed
geometry/confidence/source validity, side/order separation, conflicts, late
identities, repeated counts, resets, exceptions, eviction/oversized inputs,
concurrent consumers, and supported terminal collapses delivered forward,
backward and shuffled. Recognition rules remain unchanged.
