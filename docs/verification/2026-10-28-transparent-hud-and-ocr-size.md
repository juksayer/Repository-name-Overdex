# Transparent move area and OCR size evidence — October 28, 2026

## Observable changes

- Only the opponent sprite header paints the badge material. The move list and
  estimated-energy row below it have a fully transparent background, including
  their padding. The saved badge seam, outer width, side padding, right alignment,
  long-press movement, and prebattle controls retain their existing behavior.
- Sprite cells are 36 dp high, down from 44 dp. Sprites fit into 36 dp without
  overflowing into the move text. Source PNGs and cached transparent-edge trimming
  remain intact.
- Move names, confirmation checks, hazard spans, and estimated energy have a
  tight white drop shadow: 0.5 dp offset and 0.75 dp blur. Their colored foreground,
  8 sp monospace font, 10 sp line height, and readiness colors are preserved.
  This gives contrast without drawing another panel over game text.

## Text-source evidence

The reader already maps word bounds back to original captured pixels, compares
glyph height with a bounded field-specific profile, and combines small size with
HUD position and colored-ink evidence before filtering a word. It does not
identify or classify font families. Differing font appearance helps make the
overlay visually distinct, but is not a measured font-classification signal.

Previously, any HUD intersection disabled profile learning for the entire crop.
Now clean, vocabulary-confirmed native-color words wholly outside that overlap
can train the profile even when other words in the crop were rejected as HUD
text. Any overlap or missing geometry in a retained training word prevents
learning when HUD bounds are present. Raw text, word decisions, timing, archive
schemas, OCR pass counts, and the existing conservative filtering gate remain.

Retained examples at a 672-pixel frame width contain HUD words around 11–12 px,
entry announcements around 28–35 px, and charged-move announcements around
25–26 px. Multiple game text sizes are legitimate: size is supporting evidence,
not an unconditional reason to discard an announcement or partial reading.

## Verification

- Full unit suite: **1,277 tests / 211 suites**, zero failures/errors/skips.
- Debug app and Android device-test APKs built successfully.
- Two new unit regressions cover mixed game/HUD word geometry and refusal to
  learn from overlapping or unknown training geometry.
- The existing HUD compositor/drag test now checks that an empty pixel beside
  the move list equals the underlying backdrop and expects the shorter sprites.
  It compiled but was not executed; no instrumentation touched a personal phone.
- Installed on the idle Moondrop (`MDPH00124112500414`) and launched Overdex.
  All **17** saved calibration/profile, HUD-layout, and current-team files match
  their pre-install hashes. The Pixel was not updated.
- `git diff --check` passed.
- APK SHA-256:
  `621d86f182df66c1c8eee72dc9e265b3bf2e82e905c66f04b1d9a3ce28b78658`.
- Build/install logs, setting snapshots, and comparisons:
  `/home/sean/overdex-device-backups/2026-10-28-transparent-hud/`.

No battle was played during verification. Actual composited HUD readability,
white-shadow filtering, and announcement recognition need checking in the next
recorded match. Transparent background preserves uncovered game pixels; it cannot
recover strokes hidden by the HUD's own labels or sprites.
