# Calibration reference audit — November 9, 2026

The user reported boxes appearing too high on one reference and too low after
cycling to another, and requested an inventory before collecting an official
device-specific screenshot suite. This is an image/source audit and capture
checklist, not a change to saved calibration or the calibration viewer.

See the [capture checklist and complete region inventory](../reference/calibration-capture-checklist.md).

The user subsequently identified the affected path as **Moondrop, cycling bundled
references**, and clarified that 816 × 1808 and 672 × 1493 are actual screenshot
file dimensions. They are compatible shapes for the phone's 1080 × 2400 display;
their pixel dimensions alone are not a reason to reject them.

## Confirmed current-layout mismatch

Three full-frame PNGs were retained in the real November 9 17:11:23 Moondrop
archive. They were copied intact for inspection, with their source Articles,
crop metadata, archive paths and hashes preserved in
`direct-moondrop-frames/provenance.json` under the private audit root. Frame 1 is
ordinary battle at 672 × 1493; frame 2 is Team Select; frame 3 has the Battle
Setup overlay and is not a clean Team Select reference. These are retained
direct capture frames, not screenshots of Replay or of a desktop player.

Comparing frame 1's unobscured left badge and exit glyph with legacy bundled
examples establishes a field-layout difference after normalization:

| Landmark | Current direct frame, 672 × 1493 | Legacy PNG example, 1080 × 2400 | Legacy celluloid example, 816 × 1808 |
| --- | ---: | ---: | ---: |
| Left badge top | 88 / 1493 = 0.05894 | 226 / 2400 = 0.09417 | 170 / 1808 = 0.09403 |
| Left badge bottom | 164 / 1493 = 0.10985 | 347 / 2400 = 0.14458 | 261 / 1808 = 0.14436 |
| Exit dark-glyph vertical centroid | 223.954 / 1493 = 0.15000 | 444.120 / 2400 = 0.18505 | 334.663 / 1808 = 0.18510 |

The tabulated legacy examples are `Screenshot_20260825-165744.png` and
`celluloid-shot0010.jpg`. The bounded comparison also includes
`Screenshot_20260814-130929.png`. Those three files now have field-specific
incompatibility classifications for their tested top-field/name placements;
other legacy files remain provisional. Measurements and methods are in the
private `moondrop-bundled-comparison.md` and `moondrop-bundled-landmarks.csv`.

This corresponds to approximately **84 pixels higher on the current 2400-high
layout**. The two tested legacy resolution groups agree closely with each other;
their different pixel sizes do not account for the mismatch. The direct frame
also shows charged-move names **below** the controls, while tested legacy
examples put them **above**. A single fixed name crop cannot fit both layouts.

This establishes incompatibility for the tested old top-field/name placements,
not a uniform correction for all crops or a conclusion about the capture tool
that produced them. Team Select, other battle phases and every remaining target
still require separate checks. The direct frame has Overdex overlays over part
of the opponent field; only unobscured areas may serve as placement examples.
It is not a wholly clean official recognition-training image.

## What the current files establish

- The supplied Pixel folder contains 37 supported image names: 34 Pokémon GO
  frames at 1080 × 2280 and three 1080 × 2400 ODX-Fi Replay screenshots. A folder
  name does not establish that all its pictures show GO on that phone.
- Two of the 34 GO frames show gallery/player controls. The existing catalog
  correctly excludes those two from the bundled reference pack. The other 32
  have bundled JPEG counterparts with unchanged dimensions; catalog source and
  asset hashes were checked.
- The bundled library has 60 images: those 32 Pixel references, seven older
  1080 × 2400 PNGs and 21 older 816 × 1808 JPEGs. Hardware provenance and capture
  viewport for the older groups require verification before they become an
  official device suite. Their aspect ratio alone is insufficient.
- The supplied Pictures examples include desktop/player-window captures and
  annotated Replay mockups. These preserve useful visual evidence, but their
  whole-image coordinates are not a GO phone calibration frame.
- The Pixel GO pictures were taken during recorded playback. They remain
  provisional geometry candidates until stationary landmarks are compared with
  an equivalent direct capture of live GO. A usable rectangle-placement example
  is not automatically a clean OCR/color-recognition example: Pointer Location,
  gallery controls, game effects and other overlays can obscure individual crops.

Original files were not renamed, moved, resized, edited or deleted. Private file
metadata, hashes, classifications and a candidate shortlist belong under
`/home/sean/overdex-device-backups/2026-11-09-calibration-suite-audit/`.
The inventory covers 115 image entries across the supplied and bundled groups;
it is not a declaration that all 115 are usable calibration references.
`capture-scene-worksheet.csv` supplies 17 blank scene rows for a new device set;
`crop-placement-worksheet.csv` supplies the 39 current menu targets. Both leave
the device, source filename and completion status for actual review rather than
inventing coverage from a filename.

## Viewer and coordinate audit

The current screenshot and purple rectangle fill the same container and use
matching centered Fit geometry. The header reduces the available space for both
together. This read-only source review found no persistent independent image/
rectangle inset offset; it does not replace reproduction with the specific pair
that exhibited the user's mismatch.

Relevant limitations are confirmed in current code:

1. `CalibrationScreenshotLibrary.matchesScreen` checks aspect ratio with a 0.5
   percent tolerance. **This screen means compatible shape, not verified device,
   game content or capture origin.** An edited/resized image can pass it. The
   816 × 1808 group also falls within the Moondrop's shape tolerance.
2. `loadCalibrationSamples` loads the latest capture, user image entries and all
   image files in `battle_samples`. Catalog labels are metadata; the loader is
   not an approved-reference whitelist and does not extract the phone viewport
   from a desktop screenshot.
3. Directional calibration adjustments use a normalized 0.005 step, about 11.4
   vertical pixels at 2280 pixels high or 12 at 2400. This can overshoot a thin
   element; the live guide's finer controls are a different interface.
4. Droidball removes buffer row padding, without subtracting assumed status or
   navigation bars. References must retain equivalent full-frame boundaries.
5. HP bars, charge-camera framing, QTE grades and trainer gestures can move in
   valid images from one device. HP boxes are search territories. A static
   calibration should not be repeatedly moved to chase those animations.

Compare stationary landmarks first: team-badge geometry, exit icon and full
display boundaries. Common displacement suggests different image framing or
game layout; movement confined to animated elements needs phase-specific review.

## Capture source policy

Android Studio origin alone is not a rejection reason. Its screenshot editor can
add a device frame, and Android explicitly documents that selecting the wrong
frame stretches the screenshot. An untouched full-display export is a different
case from a framed export or a desktop screenshot of the device window.
[Android Studio screenshot documentation](https://developer.android.com/studio/debug/am-screenshot).

For the official suite, use original full-display PNGs from the phone screenshot
function or a direct device screen capture, without a decorative frame, cropping,
rotation or nonuniform scaling. Record phone identity, original dimensions,
navigation/status-bar layout, game version and scene. Preserve the originals and
their hashes. Android documents raw PNG capture directly to the workstation.
[Android Debug Bridge screenshot documentation](https://developer.android.com/tools/adb#screencap).

## Validation boundary

The checklist and file inventory are preparation for calibration, not a validated
new recognition corpus. A photograph's dimensions do not prove correct landmarks,
and a static frame cannot establish send-out/recall/faint timing or accuracy.
Short source sequences are still needed for transient-event validation. No app
code, saved profiles, phone settings or battle records were changed; no build,
installation or live recognition-speed claim is part of this audit.
