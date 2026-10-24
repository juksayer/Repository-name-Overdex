# Result OCR latency, October 24

## Recorded failure

Moondrop match `f843fe0c-23a5-4fc2-8981-6acc8292e827` is preserved under
`/home/sean/overdex-device-backups/2026-10-24-retained-balls/`.
The preceding retained-badge fix did not address this separate late result.

Times below are seconds after the record began, using Article wall timestamps:

| Source frame | Crop published | OCR published | Raw reading |
| --- | --- | --- | --- |
| 144.016 | 144.319 | 206.353 | `YOU WIN!` |
| 144.445 | 144.710 | 145.707 | `iNIAA NOA` |
| 150.062 | 150.421 | 196.650 | `1/4` |
| 151.197 | 151.424 | 191.263 | `1/4` |
| 159.108 | 159.424 | 161.536 | `D` / `4/6` |

Both first two preserved PNGs visibly contain upright, clear `YOU WIN!`. The
second was read promptly but incorrectly. The first produced the correct reading
62.337 seconds after capture. Publication of other Articles during this interval
continued promptly; saved species diagnostics around the first result show
backend waits of 111–1,005 ms and native work of 434–795 ms, not a minute-long
global publication pause.

The result worker selected its crop before waiting for the shared OCR backend.
Its backlog alternates newest and oldest captures: a brief result can be stranded
between older blank candidates and newer reward screens. Compact archives omit
uncited blank reads, so this archive cannot establish the complete queue depth
or divide that 62 seconds exactly between queueing and native OCR.

## Change

- Select the next preserved crop after urgent OCR admission. A result arriving
  during other text work can take the current turn. Older source crops remain
  available and keep their fair turns.
- Keep the native reading. If it contains at least three unresolved letters,
  retry that same source once with near-white lettering rendered dark on white,
  with padding. Blank readings and numeric reward counters do not incur a retry.
- Keep both raw readings, treatment names, word geometry, original crop
  reference, and source time. A garbled reading is not translated into a result;
  the existing conservative phrase resolver must accept actual OCR output.
- Record result-reader queue wait, capture-to-admission age, native read time,
  processing time, pass count, and pending count. Blank diagnostics do not add
  PNG retention to compact exports.

The shared ML Kit instance, priority fairness, capture region/cadence, result
capture gate, phrase vocabulary, and source-timed `MatchEnded` contract remain
unchanged. The original Match archive is not rewritten.

## Validation

Recorded PNG fixtures and provenance are in
`app/src/androidTest/assets/battle/oct24/`. They include the two win crops, seven
non-result/reward crops, and an earlier Pixel `GOOD EFFORT!` crop.

Device checks cover both outcome phrases, rejection of reward screens, unchanged
source pixels, a result arriving behind an occupied OCR backend and 81 blank
captures, one `MatchEnded`, and source-time/evidence continuity through RESULT.

- Full unit suite: **1,095 tests**, zero failures, errors, or skips. Debug APK and
  Android test APK build succeeded.
- Moondrop `MDPH00124112500414`: **OK (5 tests)** across the new two recorded-frame
  checks, both October 12 outcome-chain checks, and the October 13 competing-OCR
  check.
- The failure reproduced on the same phone: native-color input returned
  `iNIAA NOA`; isolated white text returned `YOU WIN!`. The initial unpadded
  diagnostic retry took 173 ms. The final production two-pass reading, including
  padding/geometry, took 785 ms in the final run.
- The busy-backend/81-blank-backlog case entered RESULT **503 ms after backend
  release**, using the newest result crop. Both native and treated raw readings
  survived; one MatchEnded inherited the selected crop's original timestamps.
  The initial run took 602 ms. These are synthetic-queue recorded-frame checks,
  not live-match recognition latency. The test intentionally backdates source
  frames; its roughly 10-second capture age is not an OCR performance measure.
- Existing competing-OCR checks: result reads **447–708 ms**, species reads
  **435–595 ms**, all correctly resolved. Their result fixture uses the native
  single-pass path; the new two-pass path is covered by the separate checks.

## Install and data preservation

Installed in place on the Moondrop at **11:37:11 CDT**, October 24. APK SHA-256:
`60b78fdf8f24927ee2a4df2fb9514223b9b8412f2cf493dbf8c350b8d338a35d`.
Overdex resumed into the Directory Tree, with the LCD idle and no Droidball
session running. No AndroidRuntime error was present for the launched app process.
The Pixel was disconnected and was not updated.

Before instrumentation, the complete app-private directory was copied and its
tar structure verified: **9,007,542,272 bytes**, 104,549 members, 104,523 regular
files. Backup, hash, verification report, test outputs, preference comparisons,
recorded timing table, and startup screenshot are under
`/home/sean/overdex-device-backups/2026-10-24-outcome-latency/`.
All five calibration/team/HUD/profile preference hashes matched before and after;
owned/team preference files and the complete match-index inventory also matched.

## Live acceptance still needed

Record a normal battle on the updated Moondrop and leave the result visible
briefly. Check both the preceding retained-badge faint fix and prompt transition
to the result state. Inspect the new result timing diagnostics and evidence chain.
The old archive's measured delay is established; a live latency improvement is
not yet established by these recorded-frame checks.
