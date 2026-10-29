# Directory LCD captured-crop previews — October 29, 2026

## Result

The Directory Tree's normal LCD now displays seven raw crop previews grouped as
species names, HP crops, Poké Balls (player left, opponent right), and a full-width
announcement crop. Images preserve their original colors and aspect ratios. The
header shows the latest capture's device-local time, not the current clock.
Per-image accessibility descriptions retain each image's own capture time.
Missing crop records say **Not captured**; recorded crops whose image is absent,
corrupt, or has inconsistent dimensions say **Image unavailable**. No image is
reconstructed from OCR or today's calibration.

Registration details, its keyboard, and interrupted-observation notices retain
LCD priority. The existing CRT, bezel, and hardware-button dimensions are unchanged.

## Evidence and lifecycle

- `DirectoryCropPreviewFeed` selects existing `CropCaptured` Articles by capture
  time, preferring monotonic time where available. It never mixes Matches in one
  displayed state.
- Species crop placement includes the actual adaptive locator's recorded bounds.
  HP previews are the calibrated observation territory, not a reconstructed
  dynamically tracked bar rectangle.
- Only changed artifact/provenance pairs are decoded. The feed polls at most
  once per second while subscribed; lifecycle-aware collection stops when the
  Activity is no longer started. It adds no battle capture, OCR, or image encoding.
- Cold launch scans archive metadata for the newest useful battle, excluding
  navigation-only recordings. It verifies only the selected local/ZIP images,
  without importing every artifact or changing the archive. A changed archive
  list allows asynchronous checkpoint recovery to supply a newer battle.
- A newer live source takes priority over slow archive loading. Published bitmaps
  stay independently owned and are not recycled when collection stops.

## Validation

- Eight new JVM tests passed for capture-time ordering, seven-slot/Match isolation,
  menu exclusion, selected artifact verification, lazy/cancelled collection,
  decode reuse, delayed archive recovery, and archive/live races.
- Full JVM suite: **1,446 tests across 228 suites**, zero failures, errors, or skips.
- Debug app and instrumentation APKs built successfully using the bundled JBR.
- Pixel 4 `99261FFAZ0017J`: **OK (5 tests)** for three LCD display tests and two
  existing directory registration tests. Checks include all seven image regions
  fitting a 240 × 130 dp LCD, exact source colors, fit margins, and honest states.
- Pixel 4: **OK (2 tests)** for real PNG decoding. Larger-frame artifacts use the
  exact recorded rectangle; full-frame images remain usable after cancellation;
  standalone crops are not cropped twice; dimension mismatches stay unavailable.
- Both Pixel 4 and Moondrop `MDPH00124112500414` installed and displayed the new
  LCD on the real Directory Tree. The source images and labels fit the existing
  shell on both resolutions.
- Saved calibration/team/layout checks remained byte-for-byte unchanged:
  **6 Pixel files and 16 Moondrop files**.

Installed app APK SHA-256:
`a58d84e4190b09dfbae5e099b5038c5e4844e60e072c6fa8ee2f4a4580f316d9`.

Local device evidence, screenshots, and test logs are in
`/home/sean/overdex-device-backups/2026-10-29-directory-crop-lcd/` and are not
committed to the repository.

## Acceptance boundary

This validates preview selection, image decoding, presentation, and existing
directory editing. No new live battle was played for this change. The older
compact archives on both phones omit uncited HP crop Articles, so their restored
HP slots correctly show **Not captured**. New in-memory battle evidence can supply
those slots; this change does not add extra HP-image retention to compact exports.
Blank or covered source crops remain visible as captured and do not by themselves
prove that calibration is incorrect.
