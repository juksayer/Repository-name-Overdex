# Profiled Moondrop match and bounded HP acquisition

November 7, 2026. The first new recording after the
[internal HP profiler](2026-11-07-hp-locator-stage-profiling.md) identifies the
expensive stage. The user then requested midpoint-first acquisition and an
internal species-specific location record. This report separates the original
recording from the subsequent implementation and its acceptance boundary.

## Source evidence

- Moondrop MD-PH-001, API 34, USB serial `MDPH00124112500414`.
- Match `962c11a7-9b88-442f-9893-31157d71732f`, archived at 10:36:23 local time.
- Original ZIP: 5,905,383 bytes; SHA-256
  `76381a68e5c54daea58c4752e6ef8bf9df694db11c376481026583c8c8bea7ab`.
- All 184 declared artifacts validate. The compact archive contains 1,380
  Articles and 23 HP diagnostic summaries. Original phone and desktop archive
  hashes agree; neither Timeline nor source media was rewritten.
- Supporting video: `Screen_recording_20261107_103616.mp4`, 816 × 1808,
  H.264, 120.1294 seconds, 2,389 frames, variable rate averaging 19.8869 FPS.
  It has **no audio track**; the other tracks contain Android metadata.
- The archive separately retains 21 WAV clips: 20 contain nonzero samples and
  one is silent. Seventeen of 20 sound measurements report audible signal;
  five accept a reference match. Signal presence is not identity ground truth.

Private evidence, analysis scripts, native frames, audio levels and detailed
reports are retained under
`/home/sean/overdex-device-backups/2026-11-07-profiled-match-review/`.
Personal battle media is not added to the repository.

## What the profiler establishes

All **207 accepted HP measurements** reached the Timeline: 113 player and 94
opponent. Final pending submissions, duplicates, unmatched records and evictions
are zero. This proves preservation after acceptance, not detection of every
visible HP change. The enabled reader skipped 697 of 996 bracketed publication
sequence advances (69.98%); those are feed gaps, not missed-attack counts.

Across 300 locator passes:

| Measurement | Result |
| --- | ---: |
| Mean complete locator elapsed | 272.227 ms |
| Mean current-thread CPU | 86.142 ms |
| Mean monitor admission elapsed | 0.056 ms |
| Geometry share of locator CPU | 92.23% |
| Wide recovery passes | 167 / 300 |
| Recovery share of locator CPU | 77.02% |

Search stages include their pixel/geometry sub-work; these totals overlap and
must not be added. Elapsed minus current-thread CPU includes off-thread-clock
time such as preemption or waits; it does not identify a specific scheduler or
GC cause. Monitor contention is not the dominant measured cost.

Four positively matched source images establish video minus record-relative
time in the conservative interval **4.79–4.89 seconds**. Diagnostic window
record+26.688–31.698 maps to video31.478–36.588 and is visibly ordinary Fast Move
play. Its 12 passes find 11 player and 12 opponent bars, with 12 local and two
recovery searches. Locator CPU averages 61.83 ms, including 55.84 ms geometry
(90.32%); monitor admission averages 0.00675 ms. Geometry remains the principal
CPU target even in this positively qualified ordinary interval.

## Independent timing and unresolved capture gaps

The archive has no accepted countdown glyph or `MatchStarted`. All archive
relative times use `MatchRecordStarted` monotonic time `129760477490810` as an
explicit record origin, **not GO**. The video independently brackets GO between
30.2138 and 30.2954 seconds. No Article is retroactively invented from that video.

The video shows four player charged sequences: two Brutal Swings and two Dragon
Pulses. Earliest visible transition brackets are 38.8021–38.8706,
50.3546–50.4306, 71.5702–71.6216 and 86.1744–86.2622 video seconds. The archive's
single Get Ready belongs to the second actual sequence. Named charged-move
announcements are absent. The initial You Win appears around video100.08; the
archive accepts the later result after rewards. These are existing recognition
gaps, not fixes delivered by the acquisition change.

Countdown digit 2 visibly masks the middle of Plusle's otherwise full HP bar,
explaining an apparent pre-GO ~20% fill fragment followed by near-full HP. This
must not be labeled real damage or used as ordinary-play training ground truth.
Timer-clearance support occurs while the countdown is still visible and is not
GO testimony. Compact exports omit uncited crop/audio Articles, so missing raw
crops alone cannot distinguish no capture from an unsuccessful reader. No full
checkpoint remained and relevant device logs had rolled out of their buffers.

## Midpoint-first acquisition

`LiveBattlefieldHpTracker` now tries a bounded search near each unlocated side's
calibrated vertical midpoint before full-band recovery. The initial window uses
the central third of that crop's height, at least 48 pixels when available, plus
a horizontal end-cap margin clipped to the source screen. A tracked side keeps
its existing position-following window. A side/species reset returns only that
side to acquisition; resizing clears old locks as before.

If fewer than two sides are assigned and the existing 250 ms source-time recovery
limit allows it, the same source frame still receives the full-band search.
Partial narrow results remain provisional until that recovery completes: a
narrow-only false width cannot establish a home lock that rejects the real bar
in the final wide pass. Recovery eligibility and exact-boundary behavior remain
unchanged. The local timing stage now includes initial bounded acquisition as
well as subsequent tracking. An inspected narrow miss is `NO_CANDIDATE`; a
throttled wide search can be identified separately from its absent stage call.

This is deliberately a **local search prior**, not global candidate equality.
Two valid bars inside the narrow windows can be accepted without inspecting an
unrelated third candidate outside them. Existing per-candidate shape/fill,
side ordering, centered-bar continuity and obstruction rules still apply.
Calibration, original timestamps, attack inference and energy rules are unchanged.

The user's broad crop spans species from their observed low Stunfisk position to
high Galarian Zapdos position. Those are user observations, not measured universal
extrema. Actual current 672 × 1493 crop midpoints are player723.5/opponent622;
the retained normal frame at record+43.472 measures player592–609 and
opponent678–696. A midpoint-only rule would miss the player. The new fallback is
essential; this recording does **not** establish a performance improvement from
the new policy.

Species-location evidence is collected separately by a read-only archive analysis
utility. Device/source dimensions, exact available crop provenance, species/form,
side, observed extents, modal bins and cited Articles remain associated. Unknown
battle phase is not relabeled ordinary home, and stale/conflicting identity is
excluded. These records are reviewable hints, not automatically applied device
calibrations or production species presets.

## Validation

All **1,917 unit tests pass**, with no failures, errors or skips. The debug app
and Android-test APKs build successfully. Thirteen acquisition regressions cover
centered/off-center discovery, provisional lock rejection, species/reset/resize,
camera movement, ownership, obstruction and existing recorded-source pixels.
The diagnostic tests retain cache, old-frame, source-clock and recovery-boundary
checks with the new search order. Emulator and installation results follow in
the subsequent color/occlusion validation.
Fresh battle acceptance still requires a recording of the new build, comparing
ordinary-play recovery frequency, geometry CPU and accepted readings. No live
accuracy or throughput improvement is claimed from a build or generated fixture.
