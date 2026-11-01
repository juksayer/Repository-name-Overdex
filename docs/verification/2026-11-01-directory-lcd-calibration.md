# Directory LCD calibration and QTE progression box

Implemented two feature slices on `odx-recovery`: a separate QTE progression
calibration region/capture, and source-backed position editing of the seven
Directory LCD crops. See the [usage guide](../reference/directory-lcd-calibration.md).

## Behavior

The new Match Calibration target is **QTE Progress Text (Nice / Great /
Excellent)**. The default `(x=.12, y=.26, width=.76, height=.075)` contains the
progression line in the retained NICE/EXCELLENT source frames. Legacy preferences
and named profiles round-trip it independently; older profiles default only this
new field. A dedicated crop is retained at 500 ms intervals during BATTLE_ACTIVE.
This adds capture, not grade OCR testimony or a new replay-grade inference.

Down after the final visible Directory row selects the first LCD crop. Tapping
any crop also selects it. A opens a position-only draft; arrows nudge one source
pixel and either source/preview image can be dragged. A/Save commits, B/Cancel
abandons the draft, and B while selecting returns Directory focus. Registration,
keyboard and interruption messages retain priority over the normal crop LCD.

Editing loads a verified complete opponent-HP source frame, or the separately
identified saved battle reference. Arbitrary frame paths, transparent partial
atlases and cropped-size PNGs cannot establish surrounding source pixels.
Historical crop provenance/pixels remain unchanged. The source is loaded on
entry and frozen through the draft; arrow presses do not decode new images or
start OCR. Save checks the original profile and target before applying a
single-region copy to the latest calibration. A canceled conflicting draft
reloads calibration; repeated Save cannot enqueue duplicate writes.

## Automated checks

- Focused geometry/navigation/region checks passed while iterating.
- Final full unit suite: **1,613 tests in 240 suites**, zero failures, errors or
  skips.
- Debug and debug-test APK builds passed.
- API 34 disposable emulator instrumentation: **OK (17 tests)**. Includes raw
  LCD rendering, actual drag revealing pixels outside the original crop,
  one-pixel arrow movement, cancel, duplicate-save protection, conflict refresh,
  disabled-during-save cleanup, source fidelity, compact archive previews,
  legacy preferences and named-profile persistence.

The emulator was shut down afterward. No instrumentation ran on the user phone.

## Moondrop check

Installed with replacement while no Droidball service was running; cold launch
succeeded. Installed APK SHA-256 matched the tested APK:
`4a3ca47ff2933a5020615831aac56abaceff1e7e015d70b941e95d4faab826a3`.
No fatal exception was present for the installed app process.

From the collapsed Directory, pressed Down five times to move through Profile
and into the first LCD crop. A opened its editor with source dimensions
672×1493. Right then Down changed X22/Y148 to X23/Y149 while retaining 156×32.
The preview updated and the readout showed Unsaved. B discarded the draft; a
second B restored the Directory selection. Opened Intueor → Match Calibration
and cycled A to progression target 27/38; its independent outline/readout was
visible. Returned to Directory without saving any calibration adjustments.

Private pre/post critical-state backups were readable. All **31 preference and
profile files** were byte-identical, including calibration/team settings. The
species database (1,026 rows) and owned-Pokémon database (27 rows) were logically
identical. The Firestore cache refreshed an existing remote document and target
metadata; it is not included in the unchanged-database claim. No cache was
restored over the running app.

## Acceptance boundaries

No new battle was recorded for this change. Live progression crop timing and
recognition accuracy are not established by these checks. New clean screenshot
packs on the Moondrop, Pixel and tablet still await user capture. Current saved
references can contain the HUD and pointer-location strip that were visible when
recorded; new reference screenshots should hide both. Historical LCD previews
can still show older calibration positions after an edit; they are labeled
battle crops rather than being rewritten as new evidence.
