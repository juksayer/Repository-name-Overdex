# Sierra: first live HP pipeline diagnostic review

November 7, 2026. This is a review of the original Moondrop recording made after
the HP diagnostic installation. It establishes a measured throughput problem,
not a completed recognition or performance fix.

## Source and integrity

- Moondrop MD_PH_001, serial `MDPH00124112500414`; Droidball was already stopped
  when the read-only copy was made.
- Match `4e3ee980-3887-49cf-950e-ec23b3420759`, archive
  `2026-11-07_09-28-16_4e3ee980-3887-49cf-950e-ec23b3420759.odxmatch.zip`.
- 8,590,330 bytes; SHA-256
  `a7ca2afbc87242d441905741d2a24529ff3cd19c9b25ee10744bd1fc2655621d`.
  Local and phone hashes agree. ZIP structure, 3,149 Articles, and all 439
  declared artifacts verify. The phone copy and original Timeline are unchanged.
- Private source, extracted crops, analysis script, and structured window report:
  `/home/sean/overdex-device-backups/2026-11-07-sierra-review/`.

The configured shadow team is Golbat / Greninja / Victreebel, with Wing Attack /
Water Shuriken / Razor Leaf. Observed opponents are Pancham / Blastoise /
Houndoom. GO and a Win are recorded. The user identifies Sierra and reports that
the close battle came down to one Fast Move; the archive does not independently
establish the deciding blow. Sierra is visually present in the postbattle crop.

All relative times below use the archived GO **monotonic** timestamp, not the
wall-clock receipt or publication time.

## What the diagnostic establishes

The 38 summaries include `MATCH_END` and `SESSION_STOP`; their window counts and
histograms reconcile with the final cumulative snapshot.

| Stage | Final recorded measurement |
| --- | ---: |
| Observed publication sequence advances after the baseline | 2,242 |
| HP reader receipts | 909 |
| Enabled / disabled receipts | 646 / 263 |
| Completed locator passes | 646 |
| Player found / submitted / recorded | 340 / 340 / 340 |
| Opponent found / submitted / recorded | 320 / 320 / 320 |
| Pending, evicted, duplicate, or unmatched submissions | 0 each |
| Skips between enabled receive endpoints / sequence advances | 1,329 / 1,974 (67.33%) |

The actual archive contains the same 660 HP geometry Articles. Accepted geometry
is therefore not being discarded between submission and the Timeline. Sequence
skips describe unconsumed published frames, not a count of missed attacks.

| Timing | Samples | Mean | p50 bucket | p90 bucket |
| --- | ---: | ---: | --- | --- |
| Source to publication | 909 | 56.35 ms | (33, 50] ms | (100, 150] ms |
| Publication to reader | 909 | 31.61 ms | (8, 16] ms | (75, 100] ms |
| Source to reader | 909 | 87.96 ms | (50, 75] ms | (150, 250] ms |
| HP locator elapsed time | 646 | 232.46 ms | (150, 250] ms | (500, 1,000] ms |
| Player submission to Timeline | 340 | 362.48 ms | (150, 250] ms | (1,000, 2,000] ms |
| Opponent submission to Timeline | 320 | 371.84 ms | (150, 250] ms | (1,000, 2,000] ms |

Percentiles are histogram bucket ranges, not exact quantiles. Reader timings
include disabled frames. Locator timing includes scheduling and lock waits; it
does not prove CPU-only cost. All passes total 150.17 seconds of elapsed time,
with a 4.95-second maximum. Capture publication generally advances about 9–15
positions per second in enabled windows, while the HP reader receives about
2–6 frames per second. Selected both-bars-successful windows still average
166.98 ms per locator pass; these selected examples are not an unbiased score.

## Ending and coverage limits

The last accepted HP frame is at GO+131.968 seconds, showing Greninja near 30.69%
and Houndoom near 4.76%. The next preserved offensive sequence includes NICE!,
then `Greninja used Night Slash!` at +138.687. A black transition and Sierra's
postbattle scene follow, with the accepted Win at +157.213.

There is no retained terminal HP loss, zero bar, final faint, final-hit Article,
or identified final cry. The late missing-HP interval overlaps QTE and postbattle
scenes, so it must not be described as 25 seconds of missed ordinary combat.
There is no complete independent ordinary-play phase track in this archive.
Likewise, each `NO_CANDIDATE` outcome is a search fact, not proof that a visible
bar was missed or that a Pokémon was idle.

## Formatter correction

The measurement data is correct, but its formatter checked whether a new
publication baseline appeared inside each five-second window. Later windows
therefore printed `unknown` even when an earlier baseline and current progress
were known. For example, a window with 106 measured advances and no new baseline
had that valid number hidden.

Formatting now carries the known stream/cumulative baseline into each window.
Known positive progress and known zero progress remain numeric; genuinely
unknown progress remains unknown. No measurement, timestamp, or battle inference
is changed. Two regressions cover inherited progress and known zero progress.

Validation of the formatter correction and the separate Replay cause-label
change passed together: **1,883 unit tests**, with zero failures, errors, or
skips; debug app and Android-test APK builds succeeded. The API 34 emulator
returned **OK (4 tests)** across `HpObservationDiagnosticsDeviceTest` and
`ReplayStatChangeDeviceTest`.

The final APK was installed on the Moondrop with Droidball stopped, using
replacement without clearing data or running phone instrumentation. Its hash
matches the tested build:
`89e5ec8a34333320cea53fcbdac06c9bda16ebd05b5d4b02bc3c2cb1a886f8a5`.
A read-verified backup protects 403 user-data files, excluding the untouched
loose artifact store. After installation, all 402 non-Firestore-cache files are
byte-identical, including owned/team data, preferences, calibration, and saved
matches. The Firestore cache database is the only changed file. No file is
missing or newly added. Installation and synthetic tests do not establish a
live speed improvement; the HP observation algorithm was not tuned here.

## Next bounded performance task

Measure the locator's internal stages separately: waiting to enter the tracker,
local search, recovery search, pixel extraction, geometry assignment, and the
post-locator sampling work. Preserve recognition thresholds while establishing
which work consumes the time. Capture callback work and the additional
submission-to-Timeline delay remain separate targets. The current diagnostic
identifies where to investigate first; it does not justify guessing that one
specific pixel operation or calibration box caused every gap.
