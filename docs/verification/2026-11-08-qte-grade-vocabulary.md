# Preserve native QTE OCR while resolving the observed NIICE alias

November 8, 2026. Follows the Pixel reference pack and crop allowance in
`440ccb24`.

## Trigger and behavior

The actual Google Play ML Kit recognizer read the supplied native-size Pixel
Nice frame as `NIICE!`. Great and Excellent were read exactly at both native
1080×2280 and uniformly scaled 672×1419 sizes. The strict replay grade parser
rejected `NIICE!`, exposing a real recognition gap rather than a crop-coverage
failure.

The dedicated QTE progression reader now repairs only the complete observed
token `NIICE` or `NIICE!`, case-insensitively with optional outer whitespace, to
`NICE` or `NICE!`. Both the QTE source identity and its specific crop contract
must match. This is not nearest-word matching. Other misspellings, arbitrary
prose, extra exclamation marks and general announcements are unchanged. Already
valid Nice/Great/Excellent text is unchanged.

Native `_OCR` testimony still contains `NIICE!` in its raw and accepted native
reading fields. A separate `_VOCABULARY_RESOLUTION` article cites the capture
and native OCR, recording original/accepted text and the basis
`KNOWN_QTE_GRADE_DUPLICATED_I`. Accepted QTE testimony cites that resolution as
well as its original evidence. Every article retains the source capture's wall
and monotonic time. Timeline custody and replay's strict grade parser are
unchanged.

The recorded-pixel Android regression uses the actual supplied Nice/Great
reference JPEGs and the review-only Excellent fixture. It passes each through
the production crop resolver, retained PNG artifact, real ML Kit witness,
archive mapper and replay parser at both sizes. The test clocks exercise
custody; screenshot creation times are not reconstructed battle timestamps.
It requires a disposable emulator with a working native Google text module and
cannot run on a user's phone.

## Validation

- Full unit suite: **2,117 tests, zero failures/errors/skips**. Four new checks
  exercise the exact alias, production witness custody chain, both source/crop
  guards and unknown-text preservation. Debug and instrumentation APKs build.
- API 37 Google Play disposable emulator: **OK (3 tests)**, covering six actual
  native OCR readings. Nice, Great and Excellent all reach the strict replay
  parser at both 1080×2280 and 672×1419. The native Nice reading remains `NIICE!`
  in retained OCR testimony, while the cited accepted result is `NICE!`. Its
  capture-sized reading is already `NICE!` and does not need the repair. Great
  and Excellent are exact at both sizes. Source clock/citation assertions pass.
- The separate **9 calibration checks** passed on API 34 before this text-only
  follow-up. Calibration geometry, library assets, profile handling and replay
  parsing were not changed by this follow-up.
- `git diff --check` passes. No instrumentation ran on either real phone.

The final APK SHA-256 is
`a568ee7554229fd5a9ad910e1429e4aa791db17e30e4e9407ab6da8d2164617d`.
Evidence is retained at
`/home/sean/overdex-device-backups/2026-11-08-pixel-reference-pack/`: final OCR
JSON, original failed Nice JSON, both emulator logs, and the full unit/build
summary. This proves these six recorded readings, not every animation frame or
all future OCR aliases. Phone installation/startup results are recorded below.

The exact final APK is installed on both Moondrop and Pixel 4. Fresh verified
backups preceded replacement, with no active Droidball service or MediaProjection.
All **411 Moondrop / 91 Pixel** checked private files and inventory entries were
identical immediately after replacement. Both installed base APK hashes match
the tested final build. Both apps resume their new process, retain the same PID
through the startup check and have no fatal entries in the new process's log;
capture remains stopped. Provenance is retained in `pre-vocabulary-install/`
under the evidence folder above. Physical screenshot-library/crop UI proof from
the preceding change remains applicable; this follow-up changes only the
dedicated text reader and its regression checks.
