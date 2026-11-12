# Event-driven reader attention — November 9, 2026

**Implemented and tested; not installed on either physical phone.** This slice
reduces repeated assignments after a reader has resolved its signal. It does not
establish live CPU savings or repair every missed faint/end transition. The
[user policy and follow-up boundaries](../plans/2026-11-09-reader-attention.md)
remain explicit. GO still defines Match Start; attention is not a new event.

## Observable changes

- Choose Your Team takes its first coherent ten-aperture snapshot immediately,
  then once every four seconds. Team entry closes capture. Obsolete queued
  pre-battle reads check their assignment when admitted and before publication.
  Original crop evidence is retained.
- Confirmed types and species stop routine rereading. Relevant appearance,
  switch, faint and charged-move cues reopen bounded assignments; unresolved
  species retain recovery. Old reads cannot close a newer appearance's check.
- Resource assignments finish on accepted count testimony rather than PNG
  preservation. Shields reopen for charge/QTE/switch/faint; Poké Balls reopen for
  the affected switch/faint/zero-HP cue. Red HP does not start repeated ball reads.
  Inactive cards reopen for player switch/faint. Initial assignments still work
  from independent battle evidence when GO was missed.
- Decision-timer capture/reading runs in bounded charged/faint windows. An old
  timer's disappearance cannot close a new assignment before its own numeric
  timer has been seen. Switch-dial crops get a short player-appearance window;
  pixels remain neutral evidence rather than proof of a voluntary switch.
- Heavy stat-sound matching is restricted to charged/QTE attention and effect
  tails. Volatile history remains available for finite recovery probes. Match
  End stops continuous matching; already-requested finite probes may finish.

The approved departure scheduling hint uses the **existing HP locator result**,
not another pixel/geometry pass. It requires a fresh nonzero prior bar, repeated
absence, a normally positioned bar on the other side, observable prior bounds,
and a bounded cooldown. Default overlapping search bands use existing candidate
geometry to rule out an unassigned compatible prior bar. It opens only that
side's ball-count check and publishes no faint or recall testimony.

It abstains during charged-camera motion and requires normal-bar reseeding. The
immediate post-charge Incinerate KO with no fresh ordinary low bar and no zero
reading therefore remains outside this hint's coverage. Missing all transition
cues can still leave a resolved identity stale until the next permitted cue.

## Verification

| Check | Result |
| --- | --- |
| Focused scheduling/recovery tests | 95 tests; zero failures, errors or skips |
| Full debug unit suite | 2,297 tests; zero failures, errors or skips |
| Debug app and Android test APK builds | Successful |
| Disposable API 34 emulator | `OK (6 tests)`; instrumentation output inspected for failure markers |
| Diff whitespace check | Passed |
| Physical phone installation/live battle | Not performed |

The six native checks cover four-second coherent captures and immediate restart,
retirement at shared model admission, retirement during an in-flight party read,
independent stalled fallback/button handling, queued-frame conflation without
deleting raw evidence, and restart cancellation/fresh anchors. They use retained
Moondrop Team Select pixels and controlled text responses. They are **not a
positive OCR accuracy or live battle acceptance test**.

An initial native run exposed a test-method return-type definition error before
any checks ran. The test was corrected to return Unit, rebuilt, and all six
selected checks then passed. No production change was needed for that failure.

Private evidence, exact tested Kotlin snapshots, logs, result manifests and
immutable APK copies are retained at:
`/home/sean/overdex-device-backups/2026-11-09-event-attention/`.
The app APK SHA-256 is
`ba4eb7ab405c97d4cb1b1cb416f6e1498b84e8d50c7af017bece025160341d6c`.
No commit or push was made for this slice.

## Acceptance still required

On new recordings from each phone, compare reader admission counts, HP frame
throughput, source-to-Timeline delay, and missed/extra Fast Moves, faints and
endings. Include a charged move followed immediately by a fast-move KO, ordinary
fast-move final KO, concession during a charged move and back-to-back matches.
Do not infer a throughput or accuracy improvement from the scheduling tests.

White/black game transitions, actual recall/exit observation, the strict
last-Pokémon-plus-recall music gate, ending-reason projection, source-timed shield
questions and separate player disagreement notes remain follow-up work. Existing
result-music behavior is unchanged. Player answers must not edit match records.
