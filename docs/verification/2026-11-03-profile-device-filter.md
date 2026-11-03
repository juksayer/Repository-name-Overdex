# Filter saved calibrations to the detected device

Match Calibration already reads the manufacturer/model and full display bounds.
The profile dropdown now defaults to saved profiles with that same model string
and exact physical width/height. Capitalization and incidental whitespace do not
create a different identity; blank/unknown identity and invalid dimensions do
not establish a match. Equal aspect ratio, equal resolution on another model,
rotated dimensions, and app-content height are insufficient.

The Device Calibrations modal displays the matching count. Show Other Devices
is explicit, keeps matching entries first, and preserves saved order within
groups. Opening the modal resets this display filter. A missing device match
shows an actionable empty state instead of automatically selecting or adapting
another profile. The existing active selection, original boxes, confirmation
status, and working restore point remain unchanged by filtering.

This is device-metadata filtering, not proof that every crop is correct. Android
display settings, system bars, and game layout changes still require checking
actual targets. Similar-aspect profiles can be reviewed manually as starting
points; they are not automatically declared compatible. This change filters
profiles stored on the device and does not distribute a shared preset catalogue.

## Verification

- Nine new JVM cases cover case/whitespace, exact model/resolution boundaries,
  same-ratio differences, unknown dimensions/identity, stable ordering,
  preservation of confirmation metadata, and the no-match result.
- Full JVM suite: 1,649 tests across 246 suites, no failures, errors, or skips.
  Debug and debug instrumentation APK builds pass.
- API 34 emulator: `OK (2 tests)` for the real Device Calibrations modal. One
  checks default exclusion and deliberate inclusion of a foreign profile while
  preserving exact loading/restore behavior. The second checks a device with
  no matching profile and confirms that the UI does not create or select a
  replacement without the user's action. Test preferences/files are isolated.
- Moondrop was idle before replacement. Its readable settings/database backup
  and verification logs remain private under
  `/home/sean/overdex-device-backups/2026-11-03-profile-device-filter/`.
  The installed APK matches tested SHA-256
  `32320206bcc6f9536e4e51a5a3662647c8f15963b5895a6146503d374474da36`.
  Calibration files/selection remain byte-identical and the Pokédex/owned-card
  database retains identical logical contents after replacement.

No live-battle OCR accuracy or cross-device preset reuse is claimed by these
checks. The actual calibration still needs a visible target and an explicitly
kept working checkpoint.
