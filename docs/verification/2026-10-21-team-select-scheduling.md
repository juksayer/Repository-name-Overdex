# Team Select scheduling and emulator verification

October 21, 2026, CDT. Canonical checkout: `/home/sean/AndroidStudioProjects/Overdex`,
branch `odx-recovery`. The user requested emulator validation in place of
reconnecting the Moondrop. No phone was modified during this follow-up.

## Problem and change

The preceding phone check preserved valid button/badge pixels promptly, but
published Team Select acceptance **27.012 and 29.943 seconds after their source
frames**. The party reader serially processed league text, cards, restrictions,
and finally the button. Clipped text could delay the decisive button, and new
frames accumulated behind unfinished OCR. Three roster-name readers also
competed for OCR before the surface was accepted, even though their results
could not yet be published.

- Publish the **Use This Party** crop immediately after the league badge.
- Read each party aperture independently, keeping its newest pending crop.
  All OCR still enters the existing single model scheduler. Button recognition
  uses its transient priority; routine fallback text keeps normal priority.
- Keep measurements for up to 32 capture times, associating delayed text with
  its original visual anchors. The existing two-second source-time agreement
  rule still applies. This fixes a second problem exposed by the loaded emulator:
  newer visual results previously displaced the support for a queued label.
- Derive acceptance time from the newest cited source, regardless of which
  reader finishes last. Restart cancellation cannot publish an abandoned read.
- Retain roster-name crops while seeking the surface; start their OCR after
  acceptance. Independent sprite matching remains available beforehand.

The color-only overworld rejection remains in force. Every original crop
article and artifact remains available. Coalescing affects pending recognition
work, not the preserved ledger. No capture region or saved team was changed.

## Source fixture

The fixture is an unchanged, hash-verified 672 × 1493 published frame from
`2026-10-21_15-55-17_99fd2f35-d4ec-4c92-9f3f-b4a0b2fda39c.odxmatch.zip`.
Its SHA-256 is
`11588730a733215a1c0a41c0c96c5167e4711b426f9ac636f12eb776cb2e97dd`.
The test includes the actual corrected button calibration and the still-clipped
league/restriction apertures. Provenance and calibration are saved under
`app/src/androidTest/assets/team_select/oct21/`.

## Validation

- Full normal-build JVM suite: **1,023 passed**, zero failures/errors/skips,
  across 182 test classes. Debug APK and instrumentation APK assembled.
- Four Android 14 emulator tests passed: stalled fallback cannot block the
  button/badge; pending crops coalesce while all 15 source articles remain;
  restart rejects old anchors; real OCR and sprite matching identify the party
  through three attempts while other OCR readers compete.
- Four new JVM cases cover delayed labels, source-time separation, newest
  supporting timestamp, and bounded measurement retention. The ten existing
  surface-contract tests also passed, including overworld negatives.
- A second real-OCR run disabled the competing active-species/announcement
  workers. It passed all three scan attempts as well.

| Emulator condition | Attempt | Party accepted | Correct three-slot roster |
| --- | ---: | ---: | ---: |
| Competing OCR | 1 | 17.402 s | 17.402 s |
| Competing OCR | 2 | 21.814 s | 21.815 s |
| Competing OCR | 3 | 21.132 s | 21.152 s |
| No competing battle OCR | 1 | 3.948 s | 3.969 s |
| No competing battle OCR | 2 | 3.317 s | 3.358 s |
| No competing battle OCR | 3 | 3.026 s | 3.026 s |

All six attempts retained slot 1 **Raichu (Alolan)**, slot 2 **Raichu**, and
slot 3 **Obstagoon**, with one party acceptance per attempt and no invented
MatchStarted event. Timing begins when the test offers preserved crop testimony;
it excludes screen capture, PNG preservation, model warm-up, and HUD rendering.
The loaded case continuously requests two-pass active-species reads from two
workers plus transient announcement reads. These are diagnostic conditions,
not a measured copy of the phone's workload or an old/new speed comparison.

## Emulator model limitation and normal APK

The existing API 37 emulator ran instrumentation but lacked its optional OCR
module. An isolated Android 14 Google APIs emulator, `Overdex_API34_Check`
(`emulator-5556`, x86_64, 1080 × 2400, two cores), also failed Google's module
download, including an explicit ModuleInstall request. An early real-OCR attempt
with an eight-second test timeout exposed the source-correlation issue; it was
not counted as a pass. The final diagnostic allows a slow emulator enough time
to finish and reports measured latency rather than asserting a phone-speed target.

For real-image testing only, an external Gradle init script added Google's
[bundled Latin OCR model](https://developers.google.com/ml-kit/vision/text-recognition/v2/android)
(`com.google.mlkit:text-recognition:16.0.1`). Repository dependencies were not
changed. The normal APK was rebuilt afterward without that script and checked
to contain no bundled OCR native library.

Normal APK: `app/build/outputs/apk/debug/app-debug.apk`, 208,887,780 bytes;
SHA-256 `01e1aeab8bd30c450d774dde6fd8b20a02a478a58df867b73531090092c89cde`.
Logs, the emulator-only APK, the init script, and a machine-readable result
summary are retained in
`/home/sean/overdex-device-backups/2026-10-21-team-select-emulator/`.

The emulator establishes scheduling, cancellation, evidence correlation, and
recorded-image recognition behavior. **It does not establish the new Moondrop
scan time.** The loaded measurements still show substantial shared-OCR waiting.
Real-device timing and native badge/HUD handoff during a battle remain open.
