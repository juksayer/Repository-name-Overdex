# Retaining brief battle text during OCR contention

This change addresses the announcement/QTE admission gap identified in the
[evening final-hit review](2026-11-07-evening-final-hit-review.md). The saved
calibration already contains Get Ready, NICE and Camerupt's Rock Tomb banner.
The native reader was busy at other source instants, and the previous
latest-only pending slot allowed later blank scenery to replace those messages.

## Runtime behavior

`PersistedAnnouncementWitness` now inspects preserved announcement and QTE crops
with a small, non-semantic pixel probe before native OCR admission. Bright,
contrasting glyph-shaped rows can retain a candidate while the shared reader
is occupied. The probe cannot read a word, identify a move, award energy or
establish the absence of an event. Weak or failed probes keep the ordinary
latest-frame OCR fallback.

Each crop lane retains at most eight pending visual episodes, a latest fallback,
the in-flight reference and one separately captured retry. Source crops older
than ten seconds are discarded from scheduling, not removed from custody.
Identical geometry coalesces only within a continuous episode with at most a
1.5-second inter-frame gap; blank/weak/different content starts a new episode.
The first readable frame survives unless a later score improves by more than
0.05. A stronger replacement keeps its own capture time.

Selection still happens after the shared TRANSIENT reading turn is granted.
Every selected crop releases and reacquires that turn, preserving the scheduler's
normal-priority fairness. The queue stores artifact/Article references, never
an accumulating collection of Bitmaps or native OCR requests. Loaded Bitmaps
are recycled.

If a newer same-episode frame arrives during a failed/blank read, one retained
retry remains eligible even after the message vanishes. Successful reads discard
that held duplicate. Overflow eviction is not treated as a successful attempt:
a later capture of the evicted current episode may enter again. Expiry, overflow,
coalescing and selection kind are recorded in latency diagnostics.

Raw OCR, accepted testimony and diagnostics all retain the selected Article's
own perceived and monotonic clocks. No message is backdated to an older
candidate's clock. Stop cancellation prevents late native/probe callbacks from
publishing new testimony.

## Late evidence after a result

The first result summary remains prompt. Useful accepted text and typed evidence
that finish after it schedule a 300 ms debounced refresh of the same completed
Match archive, after accepted custody is published. Blank samples, raw OCR passes
and latency diagnostics do not perpetually postpone the save. Existing archive
write serialization is retained.

Late charge, species, countdown, GO and VS testimony cannot reopen a completed
Match's live HUD. Older/equal charged-phase source cues cannot overwrite a newer
charge boundary or clear cadence accumulated after it. These guards preserve
historical testimony; they do not turn publication order into capture order.

## Checks

- Full unit suite: **2,039 tests, zero failures, errors or skips** across 281
  suites. Debug APK and debug instrumentation APK builds pass.
- Queue/witness regressions cover an eight-second shared-reader wait, a later
  blank replacing the pending latest frame, independent announcement/QTE lanes,
  source-time/citation custody, per-crop admission, weak-probe fallback,
  cancellation, in-flight retries, bounds, expiry and overflow.
- Archive policy/snapshot tests cover late finishing text and its cited pixels,
  wrong-Match exclusion, unchanged original source clocks and initial snapshot.
- An existing legacy witness test's guessed subscription sleep was replaced by
  an explicit subscription/callback handshake after a CPU-load race appeared
  during the full suite. Production legacy witness behavior was not changed.
- The probe was also run against six exact private RGB crops from the evening
  battle: Get Ready 0.920, Rock Tomb 0.927 and NICE 0.874; three companion blank
  controls score zero. Committed fixtures retain only derived monochrome glyph
  geometry, with provenance hashes; private screenshots are not project assets.
- Android native OCR checks require the actual ML Kit model. The API 34
  `google_apis` emulator failed both checks at dependency preflight, before
  reading any pixels: its `com.android.vending` is LicenseChecker, not a working
  Play Store, and the optional `vision.ocr` download service is missing. Network
  validation and Google HTTPS connectivity work. This is recorded as a failed
  environment check, not skipped or simulated OCR success.
- A fresh disposable API 37 Google Play emulator has the real model available
  and passes **`OK (2 tests)`**, with no skips. Existing Pixel 8 emulator data
  was not used or changed. The direct native reader retains Get Ready, Rock Tomb
  and NICE exactly; all three blank controls return blank raw and threshold
  readings. Measured positive-crop reading times are 393, 188 and 205 ms,
  respectively. These are emulator measurements, not phone latency promises.
- The second Android test uses the production artifact store, pixel probe,
  witness and shared native reader. Both crop lanes wait behind a two-second
  blocker while distinct readable frames are followed by blank frames. All
  three messages are subsequently read as `TEXT_KEYFRAME`, with their exact
  source clocks and crop/raw-OCR citations. A waiting normal-priority turn also
  runs. The test's capture clocks are explicitly synthetic; they do not assign
  timestamps to the old loose source frames.

Debug APK SHA-256:
`d7cfb3500b914f41215565d715b440309c3968468ef66dd16b0d5a751e43490a`.

Private fixtures, exact source hashes and diagnostic reports remain under
`/home/sean/overdex-device-backups/2026-11-07-evening-final-hits/`.

## Moondrop installation

The Moondrop reconnected after the emulator checks. On physical device
`MDPH00124112500414` (1080 × 2400), the pre-install checks found no running
Overdex service, no active MediaProjection, and an unlocked, lit screen.

A private 330,431,488-byte backup preserves settings, databases, calibration,
teams and match archives, excluding only the loose artifact store. The app was
stopped before taking the consistent final backup. All 407 file hashes match
the device. Backup SHA-256:
`199c871125506b7141b058f72add0ebccb0df6b3ae8d2bad3c9e39301e910493`.

The replacement installation succeeds, and the installed base APK hashes
exactly to the tested build above. Before reopening the app, all 407 saved files
still have the same hashes, with no missing or added files. MainActivity then
cold-launches successfully (3,494 ms), remains the resumed activity, and its
process log contains no fatal crash. No new Droidball session or GO battle was
started for this check.

## Acceptance boundary

No original Match archive or loose source frame was rewritten. Their missing
exact finishing-impact and zero-HP/faint timestamps remain unresolved; a loose
file's modification time is not a capture clock. This change improves retention
for future announcement/QTE readings and does not itself repair terminal HP
acquisition or infer the missing final KO.

A real battle on the updated phone remains necessary to measure queue age,
probe cost, retained keyframe coverage and semantic recognition under live load.
The disposable test emulator was stopped and removed after validation; the
pre-existing emulator definitions and data were preserved.
