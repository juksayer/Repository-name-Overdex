# Reduce repeated live battle presentation work

Approved scope: preserve immediate evidence publication while reducing work that
does not help acquire the next battle observation. This is the first performance
slice following the November 9 Timeline review. It does not postpone accepted
Articles until result or Stop.

## Why this boundary

The canonical `RealityTimeline` already appends to a locked mutable list. Live
species recognition, charged-impact HP windows, faint/resource attention, audio
cues, and result acquisition depend on prompt publication of its Articles.
Recovery checkpoints already serialize new tails every 15 seconds, and complete
ZIP publication follows completion/Stop. Those contracts stay in place.

The earlier Moondrop Rocket recording contains 2,849 Articles. Its final HP
pipeline diagnostics report 783 locator passes averaging 215.01 ms elapsed and
97.70 ms thread CPU, and average submission-to-Timeline delays of 170.67 ms for
player HP and 207.79 ms for opponent HP. These measurements predate the latest
updates. They identify work and delay worth investigating; they do not attribute
all contention to presentation. Broad HP recovery, crop encoding/storage, and
native OCR remain separate candidates for subsequent measurement.

## Changes

`OpponentEnergyForecast` keeps a disposable projection at its last processed
semantic event. New events enter stable source-time order when they arrive.
Display ticks advance only the active balance in temporary values; they do not
replay all prior events or mutate historical energy. Historical insertion, changed
move knowledge, backwards event cutoffs, and visibility changes affecting an
already-processed interval invalidate the projection. Ordinary frame extensions
after the processed boundary reuse it. A late identity can still revise a
completed battle's display without restarting its clock.

`FastMoveCadenceInference` caches the cadence/effectiveness candidate ranking.
Relevant inputs invalidate it even when they produce no derived Article. Plain
HP measurements and unrelated diagnostic Articles reuse the ranking. Audio
preference eligibility still advances with source time, so unrelated newer
frames can expire a weak preference and delayed old frames cannot restore it.
Match skips forecast/HUD candidate publication when the snapshot is unchanged.

The compatibility history keeps a separate incremental semantic-event view.
The shell reads its count directly, so raw measurements do not trigger a
full-history filter or invalidate that count. The complete mixed history remains
immediately available to its viewer. Verbose `BATTLE_TIMELINE` formatting is
guarded by Android's log-level check before reflection/string formatting.
Explicit formatting and retained diagnostic Articles remain available. For a
deliberate verbose device investigation, the channel can be enabled with
`adb shell setprop log.tag.BATTLE_TIMELINE DEBUG` and returned to normal with
`adb shell setprop log.tag.BATTLE_TIMELINE INFO`.

No input timestamps, immutable Articles, raw evidence references, witness
availability, capture rate, or acceptance rules are changed by this slice.

## Verification

All 2,235 unit tests passed with zero failures, errors, or skips. Debug application
and Android test APK builds passed. Eight new forecast checks compare cached and
cold projections under late/equal-time events, backwards queries, switches,
faints, partial recordings, charge interruptions, changed knowledge, visibility
confirmation/recovery, result, and reset. Six candidate checks exercise input
invalidation and source-time audio expiry. Two history checks cover preservation
and actual Compose snapshot invalidation.

Deterministic workload assertions establish reduced repeated work, not a phone
frame-rate claim:

- After 102 semantic events are processed, 1,000 advancing display ticks with
  ordinary live visibility extensions add no forecast builds or event replays.
  One subsequent semantic event is applied once.
- Across 1,000 unrelated HP/diagnostic inputs, candidate presentation performs
  one ranking and reuses the same immutable snapshot.
- A raw history burst preserves every record while the shell's semantic-count
  observer stays quiet; an actual semantic event still notifies it.

An initial focused run passed 417 of 418 checks. The failing new fixture supplied
zero charged moves to a team-slot constructor that requires one or two. The
fixture now supplies Body Slam; no production acceptance rule or cache assertion
was relaxed. The final full suite includes that corrected test.

Seven Android checks passed on the disposable API 34 emulator (`OK (7 tests)`):

- Two new workload checks verify immediate Match/Custody publication of 512 raw
  HP measurements before result, deliberate out-of-order source time, exact
  envelope/reference preservation through archive serialization, quiet semantic
  count observation during raw bursts, candidate/forecast reuse, and late charge
  reconciliation behind an already-processed hit.
- Three existing charged-impact checks preserve a single empty frame through the
  archive, keep it independent of a stalled normal locator, and avoid invented
  Fast Move evidence on the charged-camera/normal-return boundary.
- Two existing HUD checks verify weak-audio versus confirmed-move labels and
  charge-text protection/native window bounds. The UIAutomation screenshot from
  the audio-label check was blank despite passing Compose assertions; it is
  retained privately and is not used as visual appearance proof.

The tested application APK SHA-256 is
`173598abe7922cf7509318228a2a00a4261d40cba533be8603bb80fbbef82a25`.
The Android test APK SHA-256 is
`aba9b696836249b9db5e70790ab54b9057a2ea2340f21f89225aa50afe301e73`.

The Pixel 4 was connected with capture stopped. A readable, hash-verified
274,621,952-byte pre-update backup contains 99 private files, including its
database, preferences, calibration profiles and match archives. The loose
artifact store is excluded from that backup/inventory. All 99 files were
byte-identical after replacement and before first launch; the installed base
APK hash matches the tested application. MainActivity resumed, no fatal runtime
entry was observed, and capture remained stopped. The custom announcement crop
also remained exact after startup. No instrumentation ran on the phone.

The Pixel's pre-existing custom announcement Y .3158333 remains too low for the
recorded entry/move toast and still needs a deliberate profile correction, as
described in the preceding charge/result verification. This performance update
does not silently replace it. The Moondrop was absent and did not receive this
performance build.

Logs, source/build hashes, native workload results, and update/startup/calibration
provenance remain under the private directory
`/home/sean/overdex-device-backups/2026-11-09-timeline-performance/`.
A fresh recorded battle on an updated phone is still required to measure
delivered HP frames, skipped publications, Fast Move coverage, and latency.
Synthetic workloads do not establish those live results.
