# Blanche: actual Replay playback review

The user's `Screen_recording_20261107_123431.mp4` records Overdex Replay of
Blanche's battle. It distinguishes the previous sampled rendering check from
the behavior visible during normal playback. The recording has 3,170 video
frames over 92.675344 seconds, with no audio track. Actual sequential frame
timestamps were used; the displayed Replay cursor has its own clock.

After an initial pause at Replay 00:38.4, playback advances at 1×. Its cursor is
approximately video time plus 37.55 seconds, with roughly 0.10-second display
uncertainty. Original media, native frame contacts, clock anchors and analysis
are retained privately under
`/home/sean/overdex-device-backups/2026-11-07-blanche-replay-video-review/`.

## What the recording establishes

| Video time | Visible behavior |
| --- | --- |
| 0 to about 6 s | Stale saved-team Golbat and its moves appear before observed Turtonator. |
| 23.210722 s | Sneasel is gray and fallen before Sealeo enters at 23.421000 s. |
| 34.507644 to 37.517611 s | The second charged sequence displays NICE, GREAT, NICE, then EXCELLENT. |
| 55.313344 to 55.522744 s | Sealeo falls before Vaporeon enters at 55.707644 s. |
| About 72 s | Accepted final NICE produces an unnamed charging hint. |
| 86.707811 s | WIN hides stale opponent HP and begins an upright withdrawal. |

Both intermediate faints from the previous fix are present. The falls are hard
to notice because a 550 ms animation deadline proportionally compressed the
normal long hold, leaving approximately 108 ms for rotation. Poké Balls also
remain red after each replacement until the later recorded resource reading.
The video does not establish that a faint was missing from this updated model.

## Bounded presentation repairs

- **Condensed faints:** retain the existing source and replacement clocks, but
  allocate short intervals to the fall rather than the ordinary long hold.
  Blanche's 550 ms interval now has 55 ms before falling, 385 ms rotating, and
  110 ms draining colour, followed by the existing 100 ms lying before entry.
  Ordinary 2.13-second choreography stays unchanged.
- **Fitted resources:** when the existing direct custody checks establish an
  outgoing identity and exactly one same-side ball decrement, present that
  proven remaining count with the fitted fall. Do not guess a count from a
  name, subtract another ball when the raw reading arrives, spend shields, or
  invent a final decrement from WIN. All original readings keep their clocks.
- **Charge grades:** overlapping accepted vibration patterns retain their raw
  pulses and haptic schedule. Visual grades advance only to a higher supported
  rank within the same charge; suppressed repeats neither reset sprite growth
  nor refresh the label's original time. New preparation/ordinary-play/identity
  boundaries reset the episode, so a subsequent charge can start at NICE.
- **Stale opener:** a side-located player badge of at least 0.90 confidence,
  within two seconds after GO and absent from the entire configured roster,
  can contradict an unsupported saved lead. A supported lead, scanned slot
  one, explicit entry, player faint/resource boundary, or roster-consistent
  quick replacement preserves the possible opening switch. The independent
  Turtonator observation fills this battle's opening; its source time remains.

These repairs operate on completed-archive presentation. They do not modify
live recognition, archived Articles, event custody or inferred attack counts.

## Remaining finishing-hit gap

The [original battle review](2026-11-07-blanche-replay-evidence.md) establishes
Earth Power and Vaporeon's collapse in the external battle video. The archive
has neither a completed final move, final HP loss, nor a final faint. Its last
HP samples are Camerupt 39.893618% and Vaporeon 49.206350% at record-relative
105.006991621 s; WIN arrives 19.19715 seconds later. The Replay recording shows
exactly that unresolved interval. NICE supports QTE progress, not an invented
move, energy spend, damage, zero HP or faint.

The next capture investigation remains source-timed charged-phase, announcement
and terminal-HP coverage. More presentation changes cannot recover testimony
that the archive does not contain. The external video has not been silently
inserted into the canonical match.

## Verification

All 1,971 unit tests pass, with no failures, errors or skips. The 23 new tests
cover both mirrored resource chains, rejected proof links, rewind and duplicate
decrement behavior, condensed and ordinary poses, stale-roster boundaries,
overlapping grade patterns, same-species repeated observations, separate charges
and archive immutability. A contradictory one-to-zero reading cannot authorize
a zero count on a replacement actor. The app and instrumentation APKs build.

`ReplayBlanchePlaybackDeviceTest` exercises the full 1,609-Article ZIP, current
species/move/type catalogue, actual `MatchReplayScreen` and LCD transport
callbacks. Frame-clock playback at 1× checks the opener, both visible falls,
both replacements, synchronized resources, the overlapping grade interval,
accepted GREAT/NICE text, and result-only withdrawal. It reports **OK (1 test)**.
Eight screenshots, semantics, resolver counts, actual OCR output and archive
hash verification are retained privately. The archive is unchanged.

This emulator could not download the optional ML Kit module within the bounded
60-second preflight. The strict OCR run therefore failed before playback;
it was not counted as a pass. An explicitly labelled `require_ocr=false` run
checks the real module once, calls the actual recognizer, retains its honest
empty result, and verifies the production fallback to recorded species
testimony. That run passed. Positive archived-crop OCR is **not validated** by
it, nor is live capture accuracy. The test's default remains strict, and records
`actualOcrModuleAvailable=false` for this fallback run. Module setup follows the
[official Google API](https://developers.google.com/android/guides/module-install-apis);
an acknowledged request is not treated as a completed download.

The final APK was installed on the stopped Moondrop and launched; its installed
hash matches the tested build. Readable before/after backups each have 405 files,
326,541,312 tar bytes. All 403 other files match byte for byte; only the Firestore
cache and installation profile marker changed. No files were added or removed.
Teams, owned cards, calibrations, preferences and match archives are preserved.
The loose artifact store was excluded from the backups and left untouched.
No phone instrumentation was run. The disposable emulator was stopped afterward.
