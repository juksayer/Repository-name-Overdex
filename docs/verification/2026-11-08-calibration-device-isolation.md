# Keep calibration references on the correct device geometry

November 8, 2026. The user reported Moondrop match screenshots appearing on the
Pixel and causing vertical crop misalignment. Investigation found permissive
selection and editing paths, rather than proof that either phone's latest local
reference file had been copied to the other.

## Evidence and cause

- Both APK installs include the same sample library: seven 1080×2400 PNGs and
  twenty-one 816×1808 JPEGs. These are Moondrop-shaped; they are not Pixel 4
  calibration evidence merely because the files are present on the Pixel.
- The pre-update local references are distinct. Moondrop's saved PNG is
  672×1493, matching its 1080×2400 screen after downscaling. Pixel's is 672×1419,
  matching its 1080×2280 screen. Their hashes differ. Pixel's saved profile is
  labeled Google Pixel 4, 1080×2280; Moondrop's saved profiles are 1080×2400.
- Match Calibration's default filter already rejected known foreign shapes,
  but accepted unknown dimensions as matching. All/Shape/Device selection could
  expose foreign references, and its warning explicitly allowed adjustments to
  keep changing the current device's calibration.
- The directory LCD chose live/archive crop evidence without a current-screen
  check. Its editing background also lacked an independent display-compatibility
  gate. A foreign archive could therefore supply a misleading reference.
- No app-level screenshot/profile synchronization path was found. Other source
  paths are the common APK samples and the trainer's selected Android folder.

## Change

“This screen” now requires known compatible dimensions. Foreign, unknown,
undecodable and still-loading references are read-only. Successful decode must
agree with both the source header and current display shape. All crop writes use
that central guard, including hardware controls, resizing, LCD dragging and Team
Select regions. Foreign profiles can be reviewed without activation. Saving,
applying and confirming profiles require the appropriate current-device identity
and reference. The existing 0.5% aspect tolerance is retained for legitimate
uniform downscaling and rounding.

The latest local device capture now stays available even when a trainer has
selected a screenshot folder. Previously, any nonempty folder replaced that
fallback entirely; a folder of foreign images could hide a valid local Pixel
reference. “This screen” filters the combined sources, while “All screenshots”
keeps foreign images available strictly for review.

The directory feed filters crop provenance before selecting a live or archived
match, skips newer foreign-only archives, and validates full-frame pixels as
well as provenance. The legacy saved reference is accepted only if its decoded
shape matches the current full display. Unknown geometry does not grant edit
permission. No compatible reference produces an uncaptured/record-on-this-device
state, rather than a prompt to correct unrelated crops.

The LCD editor separately checks frame geometry and active-profile compatibility
against the actual manufacturer/model and maximum window bounds. Save rechecks
the device, profile and original crop so a stale editing session cannot replace
changed settings. Application content insets are not used as physical height.

Saved screenshots, archives, named profiles and crop positions are preserved.
This prevents further edits from incompatible evidence; it does not guess a
vertical correction for crops that may already have been adjusted incorrectly.
Those should be reviewed against a compatible Pixel recording/reference.

## Validation

- Full unit suite: **2,106 tests, zero failures/errors/skips**. New geometry,
  unknown-reference, decode and archive-selection regressions pass.
- Debug and instrumentation APK builds succeed. `git diff --check` passes.
- API 34 disposable emulator: **OK (29 tests)** on the final APK. Four profile/
  reference checks include real Pixel/Moondrop PNG headers and foreign-image
  adjustment/save protection. The remaining checks cover the directory frame
  loader, feed, LCD, editor and LCD mode controls, including foreign/unknown
  geometry and foreign active profiles. The prior 28-test run also passed before
  the always-available local-reference follow-up.
- App-wide lint was run and reports **3 errors, 187 warnings and 8 hints**. The
  errors are `MissingPermission` in `RegistrationCry.kt:32` and API-31 splash
  attributes in `values/themes.xml:5–6`. Both files are byte-identical to the
  pre-journal baseline `340e133a`; no lint error is in this change. Lint is not
  claimed to pass. The optional Wi-Fi/Bluetooth hardware declaration was added
  afterward and the APKs rebuilt, avoiding an optional-feature availability
  restriction; no runtime source changed after the full unit run.

Physical-device instrumentation is not used. Pre-update references and
verification evidence are retained outside Git at
`/home/sean/overdex-device-backups/2026-11-08-calibration-device-isolation/`.
Final installation and startup results are recorded below.

## Phone installation and visual check

The final tested APK, SHA-256
`7b92ae080bfba630bf0b2d7b1248de9974601ae77125907eb2eb00d0584f47ef`,
is installed on both the Moondrop and Pixel 4. Both had no active Droidball
service or MediaProjection before installation. Private files were backed up
before updating; all 411 Moondrop and 91 Pixel files in the checked inventory
were byte-identical immediately after installation, including `no_backup`.
The separately retained artifact store was excluded from that inventory and
was not altered. Installed APK hashes match the tested APK.

Both phones launch the new main process, resume MainActivity, retain the same
process through startup, and show no fatal entries in the new process's startup
log. Droidball and MediaProjection remain stopped.

On the physical Pixel 4, Programs → match calibration displays Google Pixel 4,
1080×2280, and “Reference: Latest device capture · 672×1419.” Holding A opens the
library with “This screen (9:19)” selected and exactly one compatible screenshot:
the local device capture. The Moondrop-shaped library samples are not offered
under this filter. The reference image is the Pixel's Gardevoir/Gabite battle,
visually distinct from the Moondrop reference. No movement, resizing, profile
activation or crop save was performed during this check.

Screenshots and selector hierarchy are retained in the calibration evidence
folder above. Backup, install and startup provenance are retained at
`/home/sean/overdex-device-backups/2026-11-08-journal-calibration/pre-install/`.
This verifies reference selection on the Pixel; it does not certify every
existing crop position or replace a fresh live-battle calibration review.
