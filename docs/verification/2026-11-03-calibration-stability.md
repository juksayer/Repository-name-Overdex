# Device calibration restore points and target-backed LCD examples

## Observed problem

The Moondrop's 07:50 and 10:20 recorded battles both use 672 × 1493 source
frames from the same 1080 × 2400 display. The native badge/name rows occupy the
same position in the retained complete battle frames. These recordings do not
demonstrate a physical-size or capture-aspect change. Pointer Location is
currently off; the recordings do not establish its earlier toggle history.

The profile used during the newest battle placed the name strips below the
actual names. SpeciesBadgeTextLocator recovered nearby text at different source
Y coordinates; TeamBadgeCropLocator similarly relocated resource captures.
Those per-frame measurements never save the calibration profile. The selected
profile was saved again after the battle, with badge boxes about 84 physical
pixels higher. Those newer boxes fit the retained badge image. Existing data
does not identify which editor/action saved the changes.

The old fixed announcement crop often contained background and clipped QTE
progress text. The new match has no MatchStarted or typed early charged-move
cue, so it cannot validate the earlier QTE-reader change. This work does not
invent that missing timing or rewrite the archive.

The new archive and earlier comparison archive were copied without modifying
the phone originals. All 400 and 435 declared artifacts passed hash/size checks.
Private comparisons, source manifests, and test logs are retained under
`/home/sean/overdex-device-backups/2026-11-03-calibration-stability/`.

## Calibration controls

Match Calibration's external DEVICES/Start control opens Device Calibrations.
The modal offers a saved device/profile dropdown with model, physical resolution,
save time, and explicit confirmation status. Selecting an existing profile
loads battle and Team Select rectangles exactly, without rescaling them or
automatically launching Save As for a deliberately selected different device.

Keep As Working stores an explicit user-confirmed snapshot of both halves.
Ordinary auto-saved adjustments and Save As over the same name preserve that
restore point. Restore Working recovers both halves. Old profiles load as
unconfirmed; saving a file or reading one target cannot certify a whole profile.
The additive stored checkpoint remains absent until the user explicitly keeps
one. No current user profile is automatically marked working by this update.

Holding Select opens an explicit screenshot chooser instead of silently cycling
the reference. The selected filename/dimensions remain visible. Different-device
and different-reference-aspect warnings explain what will be edited. Crop
controls do not act behind an open modal. A running-session notice explains
that saved changes require restarting observation; existing witnesses retain
their loaded calibration until then.

## Directory LCD

Historical examples prefer crops actually cited by a reader for that field.
Species, Poké Ball, and announcement evidence follows explicit custody links;
configured team information or merely nearby events cannot establish a target.
HP evidence requires matching side, source dimensions, exact source-frame time,
and measured bar geometry contained in the historical crop.

Unverified/missing examples remain available and are labeled Review, with an
amber correction prompt using the existing select → A → position editor path.
A missing target is not automatically called incorrect calibration: the full
reference may simply show a different battle moment. Read describes historical
reader evidence, not approval of today's saved profile or perfect OCR accuracy.

Historical HP tiles enlarge the measured bar with a BitmapPainter subrectangle
of the original bitmap, labeled Bar detail. This leaves the broad search region,
source pixels, and provenance unchanged. Current calibration inspection still
projects the full saved territory. Selecting a tile projects today's rectangle
into that tile only; it does not replace every historical example with one
reference frame that may lack the other targets.

## Verification

- Full JVM suite: 1,640 tests across 245 suites, no failures, errors, or skips.
- Debug APK and debug instrumentation APK builds pass.
- API 34 emulator: 13 native checks pass for the saved-profile modal, exact
  cross-device loading, explicit working checkpoint, restoration of both halves,
  review/correction navigation, unchanged source geometry, HP detail pixels,
  original seven-tile layout, and the existing saved QTE calibration control.
- The isolated native profile test uses private temporary preferences/files;
  no instrumentation runs on the user's phone. An initial isolated test needed
  an ActivityResult owner; the existing QTE UI check needed to dismiss the
  first-device Save As modal before using the now-blocked background controls.
- Moondrop manual checks cover the real Directory review prompt/position editor
  and the DEVICES modal/dropdown. Merely opening them does not change a crop,
  activate another profile, or create a working checkpoint.
- Installed the final tested APK on the idle Moondrop. Its installed hash
  matches `0453ca7031caba92422d3f842dc817555ce7ac792b3bc633e8779273ebf7b18d`.
  All 12 named calibration files and the active selection are byte-identical
  before/after. Both SQLite databases have identical logical contents across
  all nine tables; a database file checkpoint changed its physical bytes.
  The readable private pre-install backup remains available. No user crop was
  moved, profile activated, or working checkpoint created during the check.

These checks validate persistence, presentation, and evidence selection. They
do not establish live OCR accuracy for every field, verify every stored device
profile, or confirm all 18 QTE patterns. New inventory work should use an
explicitly chosen reference and a checked, restorable device calibration.
