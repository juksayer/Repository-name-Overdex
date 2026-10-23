# Species OCR attention before battle

October 22, 2026. Checkout: `/home/sean/AndroidStudioProjects/Overdex`, branch
`odx-recovery`. This completes the next bounded scheduling change after the
[Team Select queue work](2026-10-21-team-select-scheduling.md).

## Behavior

The two active-badge species pipelines previously sent menu scenery to urgent
OCR during `SEEKING_TEAM_SELECT` and `TEAM_SELECT_ACTIVE`. Failed recovery reads
competed with the party button and roster. They now keep a cheap, rate-limited
native-badge probe while the battle surface is unconfirmed. Recognition wakes on:

- A native badge found by the existing badge/row locator. HUD pixels and retained
  badge copies remain excluded. The probe only admits a crop; it does not testify
  that a species, countdown, or Match start occurred.
- A bounded species-check cue: entry announcement, cry, switch, faint, VS,
  countdown, or Match start. An expired cue or periodic recovery timer alone
  cannot keep menu OCR running.
- Independently established battle context, active battle, or calibration.
  Once in battle, ordinary periodic identity recovery remains enabled.

Manually opening the HUD is not itself confirmed battle context. Announcements
and cries retain their existing early paths; this change does not wait for GO.
Capture availability records when species crops are gated. At OCR admission,
disabled capture and abandoned observation attempts are checked again. Previously
preserved crops remain in the ledger even when their queued OCR work is skipped.
No saved calibration, configured team, move inference, or archive was rewritten.

## Recorded-image checks

Android 14 emulator `Overdex_API34_Check`, 1080 × 2400, two virtual cores, 1536 MB
guest RAM. As in the preceding verification, real OCR uses an external Gradle
init script to provision a bundled Latin model for the emulator only. The phone
build keeps its normal dependencies. Tests read existing curated October 16,
19, and 21 fixtures; no new game reference assets were required.

All **12 instrumentation tests passed**:

- Three new capture/admission tests: Team Select produces no urgent species
  request; the first native badges wake both readers without a countdown; an
  entry announcement wakes a readable name even without a complete badge;
  Ignore/Rescan prevents queued native OCR while retaining its source crops.
  In the native-badge case, both crops were preserved and both readers queued
  within 267 ms; opponent Sneasel identity arrived at 2,645 ms. These timings
  include the controlled admission check and are emulator diagnostics.
- Four Team Select scheduling tests retain the previous stalled-fallback,
  coalescing, and restart checks. Three real-image scan attempts now run with
  the production species pipelines receiving the Team Select frames, plus a
  continuous transient announcement OCR workload.
- Five existing admission/badge tests cover latest-pixel selection, successive
  Sneasel/Sealeo/Vaporeon recognition, displaced rows, and retained-image/HUD
  exclusion. In the queued-identity check, the newer Vaporeon replaced Sneasel
  before OCR admission and reached the HUD; the subsequent Sneasel switch also
  succeeded without reopening OCR from its own confirmation.

| Scan attempt | Party accepted | Correct three-slot roster |
| --- | ---: | ---: |
| 1 | 5.003 s | 5.085 s |
| 2 | 5.038 s | 5.164 s |
| 3 | 5.028 s | 5.070 s |

Every attempt retained **Raichu (Alolan), Raichu, Obstagoon**, in that order,
without active-badge crops, active-species OCR, or an invented Match start.
This remains a diagnostic workload: the announcement load is synthetic and
continuous; timings exclude capture, model cold start, and final HUD rendering.
The previous 17.4–21.8 s loaded test deliberately maintained two continuous
two-pass species workers. The new test replaces those with the real gated
pipelines; neither test is a before/after phone speed measurement.

## Remaining live boundary

Normal-build validation also passed: **1,025 JVM tests** across 182 classes,
zero failures/errors/skips; both the debug app and instrumentation APK assembled;
`git diff --check` passed. The normal app was rebuilt without the emulator init
script and checked for the absence of the bundled OCR libraries.

Normal APK: `app/build/outputs/apk/debug/app-debug.apk`, 208,887,780 bytes;
SHA-256 `d9c0510586aefb3df302d60f5f607a00a9351ad16d81ad7c682844f4717419d7`.

The new normal APK still needs a Moondrop check from menus through Team Select
and the first battle badges. Confirm native badge/HUD handoff, mixed-form active
identity, and source-to-HUD latency before the earliest plausible charged move.
This scheduling change does not finish active-slot/form retention or establish
new Fast Move accuracy. The phone was not modified in this emulator follow-up.

Logs and the test-only APK are preserved in
`/home/sean/overdex-device-backups/2026-10-22-species-ocr-attention/`.
