# HP observation pipeline diagnostics

November 7, 2026. Implementation, automated checks, and Moondrop installation are
complete. The first [real Sierra recording](2026-11-07-sierra-hp-review.md) now
supplies live stage measurements and preserves all 660 accepted HP readings.
This record does not claim a measured live speed improvement or completed Fast
Move recognition.

## Purpose and scope

Recent archives showed gaps between accepted HP geometry readings and delays
between a source frame and its Timeline Article. Those gaps alone could not
separate capture-side work, frames the HP reader never received, disabled
interpretation, locator failures, or delayed Custody publication. This slice
records those stages independently before selecting a performance fix.

The diagnostic does not change the capture rate, calibration, HP acceptance
rules, Fast Move conclusions, energy accounting, or Replay. Existing geometry
Articles keep their original source wall and monotonic times. No additional
source images or per-frame diagnostic Articles are retained.

## Measured stages

| Stage | Measurement and owner |
| --- | --- |
| Source receipt and visual publication | `DroidballService` preserves the existing image-callback receipt time, then assigns a separate publication time after bitmap conversion and badge work. Each successfully accepted live-feed frame gets a stream ID and sequence number. Rejected emissions consume no sequence number. A bitmap-free latest-publication snapshot remains readable even when the HP consumer receives no further frames. |
| HP reader receipt | `LiveActiveHpBarWitness` records receipt time, original source time, publication metadata, and whether interpretation is enabled. Disabled frames count separately and do not become locator failures. |
| HP location | `LiveBattlefieldHpTracker.measureFrameWithDiagnostics` returns the existing accepted measurements plus a factual outcome for each side. The diagnostic times that call and counts `FOUND`, `INVALID_CALIBRATION`, `OUT_OF_ORDER_FRAME`, `NO_CANDIDATE`, `OBSCURED_CANDIDATES`, `UNASSIGNED_CANDIDATES`, or `RECOVERY_SCAN_THROTTLED`. These statuses do not assert a battle phase or why an unseen bar disappeared. |
| Custody submission | Immediately before an accepted geometry measurement enters Custody, the diagnostic records side, original source time, and submission time. |
| Actual Timeline append | `Match` records the geometry's completion time only after `RealityTimeline.append` returns, using the live player/opponent HP source IDs. The Timeline remains an uninterpreted ledger. Submission alone does not increment the recorded counter. |

Distributions separate source-to-publication, publication-to-reader,
source-to-reader, locator duration, source-to-submission,
submission-to-Timeline, and source-to-Timeline time. Received-source intervals,
receipt intervals, and accepted-source intervals expose different kinds of gaps.
Species changes, empty-HP resets, and disabled interpretation break accepted
continuity rather than creating a cadence across different appearances.

## Sparse summaries and interpretation limits

`HpObservationDiagnostics` maintains counters, fixed histograms, and a bounded
pending-submission map. It holds no Bitmap or growing per-frame sample history.
The pending map has a maximum of 512 entries; eviction, duplicate submission,
non-increasing accepted source times, and recording without a corresponding
submission are reported explicitly.

The live witness requests a `PERIODIC` summary every five seconds. Match-end
handling requests `MATCH_END`, and witness Stop requests `SESSION_STOP` even
when the five-second interval has not elapsed. Each
`HpObservationPipelineMeasured` Article has separate window and cumulative
statistics, pending count/oldest age, histogram bounds, and latest observed
producer stream/sequence. `HP_OBSERVATION_PIPELINE` is the source ID.

Important reading rules:

- A publication sequence is successfully accepted feed progress, not an Android
  capture callback count or proof any particular consumer saw the frame. The
  first known sequence establishes a baseline; unknown earlier publications are
  not added to a fabricated total. New capture streams establish fresh baselines.
- Sequence gaps bracketed by two enabled receives are reported separately from
  disabled and transition/unknown gaps. A skipped publication is not a missed
  attack, and its unobserved interpretation state is not reconstructed.
- Window stage counts can differ because one window's submissions can enter the
  Timeline in the next. Use cumulative counts and pending work when reconciling
  stages. Disabled receives do not enter the missing-bar denominator.
- Histogram p50/p90/p99 values are inclusive bucket upper bounds, not exact
  percentiles. The overflow bucket is displayed as exceeding the last finite
  bound. Invalid negative durations are counted separately.
- Terminal summaries are point-in-time snapshots. A reader callback or accepted
  Custody record already in flight can finish after the snapshot. Stopping and
  draining preserve that testimony; they do not rewrite the terminal summary to
  claim every stage had already completed.

The regular Article log and Match archive viewer render these supplied facts.
The viewer's expanded payload includes window and cumulative details, miss
reasons, timing bounds, and limitations. Neither formatter derives attacks,
energy, Match Start, or battle outcomes from diagnostic counts.

## Archive and lifecycle handling

The archive mapper preserves the typed summary and its bounded statistics. The
compact archive selector retains it as ordinary non-artifact testimony; sparse
diagnostics do not require extra PNGs or audio files.

Result-triggered archival now waits for already accepted evidence, including
the Match-end diagnostic, to finish publication before taking its snapshot.
It keeps the Match worker available while the surrounding recording session is
still open. A subsequent Stop refreshes the same archive after its final flush
and drain; it does not create another Match entry. The worker is released after
successful terminal export, while the ledger remains available to readers.

An unexpected `DroidballSignal.Stopped` now follows the normal observer-stop,
automatic-stop testimony, diagnostic flush, and archive-drain path instead of
skipping observer teardown. The per-match signal collector is cancelled during
Stop, preventing queued frame/next-match signals or the service's final Stop
signal from starting another archival path. This covers a delivered service-stop
signal; abrupt process death cannot run a graceful flush and remains a separate
checkpoint-recovery boundary.

## Validation

- Full unit suite: **1,877 tests, zero failures, errors, or skips**. Debug app and
  Android-test APK builds also passed. Two new fake-clock test fixtures were
  corrected to measure their reporting interval from receipt time; the final
  full run passed with production reporting logic unchanged.
- API 34 emulator (`Overdex_API34_Check`, `emulator-5556`):
  `HpObservationDiagnosticsDeviceTest` returned **OK (2 tests)**. The tests
  generate their own bitmap HP rectangles and exercise the real live witness,
  Custody, Match, Timeline, summary scheduling, blocked append, normal result
  flush, and archive serialization/selection. Original source times survive;
  diagnostic counts produce no attacks, energy, or fabricated Match Start.
- Existing emulator navigation regressions returned **OK (12 tests)** across
  `SettingsAndMatchReturnDeviceTest`, `MatchSummaryNavigationDeviceTest`, and
  `MatchHistoryNavigationDeviceTest`. These checks cover existing controls and
  archive navigation; they do not simulate a physical projection shutdown.
- Installed with replacement, without data clear/uninstall, on the **Moondrop
  MD_PH_001 (`MDPH00124112500414`)** while Droidball was stopped. The installed
  APK's SHA-256 matches the tested build. The app opened to its existing home
  screen, and the checked crash buffer contained no Overdex crash.
- Before installation, backed up and read-verified 402 files from databases,
  preferences, calibration, saved matches, and other user files. The existing
  18 GB loose artifact store was excluded from this installation backup and left
  untouched; no phone instrumentation ran. After installation, 401 files were
  byte-identical. The remaining Firestore cache database changed only its two
  remote-snapshot timestamp columns, with all table row counts preserved. Owned
  collection/team databases, profiles, preferences, and saved match files were
  unchanged. Private evidence is under
  `/home/sean/overdex-device-backups/2026-11-07-hp-diagnostic/`.
- Debug APK SHA-256:
  `43ae29bb6d82276d9ad740f711a138207c993d16b92df61e65ad725a83760ebd`.
  Android-test APK SHA-256:
  `8cd283b2adb9d54a380e2e1274eb05a8cbc35cb27f14a9904f326e231cdbcf05`.
- Initial live acceptance: the Sierra recording preserves 38 summaries including
  match-end and Stop reports, with every accepted geometry submission recorded.
  The follow-up review identifies expensive locator elapsed time and unconsumed
  frames; internal locator profiling and a new validation battle after tuning
  remain necessary before claiming a recognition improvement.

The next tuning investigation is the measured HP reader/locator path. Shell
appearance remains deferred.
