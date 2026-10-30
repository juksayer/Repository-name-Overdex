# Calibration screenshot library: October 30, 2026

Match Calibration now catalogs screenshot dimensions from image headers off the UI thread. Its default **This screen** selection uses the current device's full physical display shape, including system-bar rows. Uniformly scaled captures and small capture-width alignment differences match; app-content height is not substituted for physical height. Portrait and landscape remain separate shapes.

Hold the physical **A** button to open the screenshot-library dialog. It offers:

- **This screen**, **All screenshots**, and each available screen shape.
- **Device folder** selections when the selected screenshot library has model-named subfolders.
- **Choose screenshot**, with filenames, dimensions and optional device-folder labels.
- **Choose screenshot folder**, using Android's existing persistent folder permission.

Long-pressing SELECT still cycles the currently filtered images. The library dialog includes the folder picker formerly opened directly by long-pressing A. These are native dialogs outside the CRT GlassShield; the shield and clean reference preview remain intact. The LCD includes **HOLD A: IMAGES**. An empty filter is safe and says how to select another shape. Images with unreadable dimensions remain available under This screen and All screenshots, labeled **Dimensions unknown**; their crop outlines are withheld instead of displaying false coordinates. Known images with a different shape are available only through a deliberate broader/manual selection.

References are fitted to the preview without stretching. Crop outlines and drag distances use that same fitted viewport, including letterbox margins. Choosing a reference/filter does not select, overwrite or migrate a saved calibration profile. Existing profile save/load rules remain unchanged. Matching the aspect ratio alone does not prove identical game layout or correct calibration.

## Preparing the new library

1. Take original, full-screen screenshots on each device. Keep status and navigation areas, original pixel dimensions and orientation. Hide Overdex overlays and pointer-location/tap indicators for clean references. Avoid cropping, resizing, adding borders or screenshots of another screenshot; those alter the geometry that the editor needs. A short recording can supply individual frames of brief countdown states.
2. Collect each relevant state: Team Select with league/restrictions/CP limit visible, VS, countdown/GO, normal play, charged move/QTE, choose-to-shield, replacement decision timer, and win/loss. Include one- and two-charged-move layouts where available.
3. Put them in one shared root folder with optional **one-level** model subfolders, for example:

   ```text
   Battle calibration/
     Moondrop/
       team-select.png
       countdown-3.png
       battle-one-charge.png
     Pixel 4/
       team-select.png
       battle-two-charges.png
     onn 10.1/
       team-select.png
   ```

4. Copy that folder to each testing device, then choose its root in Match Calibration. The folder names are user-supplied device labels, not verified hardware metadata. Filenames can be descriptive; no filename pattern or manifest is required. Root-level images also work but have no device label. Folders nested deeper than the model folder are not scanned.
5. Leave **This screen** selected for ordinary calibration, or select the desired model/shape to inspect another screen's images. Save a named profile only for the actual device being calibrated. A manual model filter does not change the detected device or activate another device's profile.

An emulator is useful for examining layouts at additional resolutions, but its captures should be labeled with the emulator/model and physical resolution used. A matching ratio does not replace real-device validation of capture bounds, display insets, density and Pokémon GO's responsive layout.

If no accessible custom images are found, the existing latest device capture and bundled-reference fallback remains available and is filtered in the same way. No new game screenshots or source assets were bundled in this change.

## Validation

Added focused coverage for Moondrop/Pixel/tablet screen-shape selection; scaled/aligned captures; orientation; unknown-dimension fallback; explicit device and all choices; grouping equivalent shapes; and fitted crop/drag geometry in horizontal and vertical letterboxing.

Coordinated focused checks passed: 67 cases. The full suite passed: 1,547 tests,
zero failures/errors/skips. Debug APK build and idle Moondrop installation passed,
with installed and tested hashes equal. See
[coordinated verification](2026-10-30-new-matches-and-move-correction.md#validation).

On Moondrop, opened Match Calibration through Signal Observatory using physical
controls; held A to reach the native screenshot-library dialog, observed This
screen (9:20) with 29 references, switched to All screenshots, selected a named
1080 × 2400 bundled reference, and returned to This screen. The reference fits
without stretching; its source-coordinate readout changes to the selected image's
dimensions while the profile remains SAVED. The chooser is reachable without
removing GlassShield. New custom model-folder packs, Pixel/tablet
physical filtering and saved-profile acceptance still await the user's captures.
