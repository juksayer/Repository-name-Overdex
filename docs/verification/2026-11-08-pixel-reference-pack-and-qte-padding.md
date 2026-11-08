# Pixel reference pack and QTE progression motion allowance

November 8, 2026. Follows the device-isolation fix at `5a9b8f46`.

## Changes

The QTE progression box now spans normalized X .12–.88 and Y .23–.36. At the
Pixel's native 1080×2280 this is approximately X 130–950 and Y 524–821. Horizontal
coverage is unchanged; vertical height increases from .075 to .13. Compared with
the former default, this adds about 68 pixels above and 57 below. The visible
editable box is the actual capture region, with no hidden OCR-only padding.

The supplied Nice, Great and Excellent outlines are measured in the
[event inventory](../reference/2026-11-08-pixel-battle-event-samples.md). The new
box includes roughly ±64 pixels of deliberate vertical movement allowance
around their complete glyphs. This margin does not claim that three stills
establish the animation's full vertical excursion.

Only the exact prior factory region `.12,.26,.76,.075` migrates when reading
legacy preferences before version 2 or named profiles before schema 3. Old
working checkpoints receive the same narrow migration. This prevents a profile
switch or restore from reviving the previous factory size. All other regions
and custom QTE boxes remain exact. A deliberate current-version save at the
historical coordinates also remains exact. Reading does not rewrite profile
files or change saved timestamps; the next deliberate save writes the current
version. This is a specific QTE exception to the earlier general no-migration
rule, authorized by the request to broaden this crop.

The supplied folder contains 34 original PNGs, including one extra image not
attached in the message. The library includes 32 supplied frames as JPEG quality
94, 4:4:4 references at **unchanged 1080×2280 pixel geometry**, retaining the full
system-bar and pointer-location areas. These add about 14.6 MB of image assets.
The source PNGs remain unchanged in the supplied folder. The asset catalog
records source and derivative hashes, dimensions, descriptive event labels,
device label, exclusions and encoding. The Excellent gallery-overlay image and
extra Flamethrower gallery-overlay image are excluded from calibration choices;
Excellent remains an isolated OCR test fixture with its surrounding controls.

Local captures, selected folders and bundled images now supplement each other.
Choosing a custom folder cannot hide this Pixel pack. “This screen” still filters
using decoded full-frame dimensions, while device selection can show the pack
as Google Pixel 4. Foreign geometries remain read-only. Matching aspect alone
does not prove that game layout or density is identical.

White/black transitions, empty result scenery, medal progress and trainer-arm
motion are documented candidates. This pass adds **no new live witness** for
them, no outcome/KO inference from blank screens, and no badge-history writes.
Existing GO and outcome-text evidence remain authoritative. Native screenshot
filename times are not original battle-frame timestamps.

## Validation

- Full unit suite: **2,113 tests, zero failures/errors/skips**. Debug app and
  instrumentation APK builds succeed; `git diff --check` passes.
- API 34 disposable emulator: **OK (9 tests)** covering actual preference/profile
  migration, custom and historical-coordinate preservation, checkpoint restore,
  foreign-reference write protection and selected-folder/native-pack coexistence.
- The first real-pixel OCR attempt on API 34 stopped at its unavailable Google
  text module. Its Play Store package is a placeholder, and GMS could not bind
  its module downloader. The separate API 37 Google Play emulator has the real
  Play Store and a working native text module.
- Actual API 37 OCR reads Great and Excellent at both 1080×2280 and 672×1419.
  Nice at native size is read as **NIICE!**, so the existing strict replay grade
  parser rejects it. This is retained as a real recognition gap rather than
  weakening the assertion. The subsequent dedicated-QTE vocabulary fix and
  recorded-pixel regression are a separate change.
- Installed the tested APK, SHA-256
  `f54a4939a71110670ebc29a133f5b5fc8087084834f0a66039098a082c6123d2`,
  on both phones with no active capture. All **411 Moondrop / 91 Pixel** checked
  private files were byte-identical immediately after installation. Both apps
  resume their new main process with no fatal startup entries and capture stopped.
- Physical Pixel: Programs → match calibration → hold A shows **This screen
  (9:19), 33 screenshots**: its local capture plus 32 native Pixel references.
  Choose screenshot offers descriptive labels and Google Pixel 4 / 1080×2280
  metadata. Selecting the Nice reference and cycling A to QTE Progress Text
  displays **X129 Y524 W820 H296 / 1080×2280**, with the full word inside the
  expanded visible box. No directional adjustment, resize or save was performed.

Evidence, original failed Nice reading and physical screenshots are retained at
`/home/sean/overdex-device-backups/2026-11-08-pixel-reference-pack/`. Backups,
installed-APK identity and startup reports are in its `pre-install/` directory.
Still-image coverage and these checks do not establish the full animation's
extrema or live accuracy across all devices and move types.
