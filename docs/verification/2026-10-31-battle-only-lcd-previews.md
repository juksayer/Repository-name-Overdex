# Battle-qualified Directory LCD previews — October 31, 2026

## Problem and behavior

The LCD previously chose the newest raw crop in each territory across the entire
Droidball session. Navigation-only live sources could displace a preceding battle,
and post-result blanks could replace useful battle images. In the latest saved
Moondrop battle (`9d820530-23f9-4424-bb56-10d7e9085a33`), the selected player-name
crop was captured at +160.069 seconds, after the result's source time of +159.186.

The preview now selects existing raw crops within source-time battle windows.
Separate battle cues establish the window; a field does not have to recognize its
own target. A mispositioned or unreadable crop therefore remains available for
calibration inspection. A lone bar-shaped patch cannot establish a battle.
Matches without GO can qualify through corroborated battle evidence. End, stop,
and navigation boundaries prevent later menu crops from replacing battle samples.
Delayed testimony can revise the selection using capture time, not arrival order.

Announcement samples prefer a nearby charged-move or entry cue. Other slots use
their newest eligible battle crop. The seven slots belong to one Match, although
their individual source frames can differ. The header says **Battle crops** with
the newest selected capture time. A missing saved record says **Not saved**;
missing or invalid bytes for a recorded crop still say **Image unavailable**.

## Durable raw samples

Sparse HP PNG captures already run independently of bitmap-free numerical HP
measurements. Compact export previously dropped these uncited raw records, so
numerical HP history survived without an image for the LCD.

Compact export now uses the same metadata-only selection as the LCD and retains
up to seven inspection records per Match, one per preview slot. Existing cited
evidence and its transitive references remain intact. The package writer includes
and verifies the original referenced artifacts and deduplicates shared image
paths. There is no additional screen capture, OCR, image encoding, or inferred HP
rendering. Calibration profiles and recorded crop rectangles remain unchanged.

## Validation

- Full JVM suite: **1,581 tests across 235 suites**, zero failures, errors, or skips.
- Debug application and instrumentation APKs built successfully with the bundled JBR.
- Disposable API 34 emulator: **OK (8 tests)** for exact raw PNG decoding, all
  seven compact archive samples surviving removal of loose artifacts, LCD fit and
  original colors, distinct missing-media states, and directory registration.
- Focused regressions cover delayed source-time evidence, mixed legacy clocks,
  navigation-only live sessions, failed target recognition, GO-missed battles,
  stable ties, compact-to-cold selection equivalence, transitive evidence closure,
  over 2,048 navigation crops, and over 8,192 post-terminal noise observations.
- Moondrop `MDPH00124112500414`: installed the update with Droidball stopped.
  Reopening the Directory Tree restored the actual latest saved battle. Its blank
  player-name tile changed to **Victreebel**, scenery changed to **Get Ready!**,
  and the selected capture header moved from **11:36:17** to **11:36:11**.
  Old HP tiles correctly read **Not saved**; the old archive contains no HP PNG rows.
- All **17** checked calibration/team/layout profile and preference files remained
  byte-for-byte unchanged. All **four** application database tables retained the
  same rows and values. No new live battle was performed for this change.

Installed APK SHA-256:
`f662b4f391e8bf8a1bed407a7b7c5f5aed7d057b8c51167c00adf236da19462e`.

Local screenshots, build and test logs, and user-data comparison evidence are in
`/home/sean/overdex-device-backups/2026-10-31-battle-lcd/`, outside the repository.

## Boundaries

Old compact archives cannot recover PNGs already omitted from the archive and
artifact store. They can continue to show **Not saved** for HP. Newly retained
samples address future saves. HP previews show the recorded observation territory,
which can be wider than the measured bar. Raw source pixels remain untinted and
are not reconstructed using current calibration or successful OCR output.

These are calibration inspection samples, not a reconstructed screenshot of one
instant or a score of calibration accuracy. A source crop can still be blank or
covered while the surrounding battle context is valid.
