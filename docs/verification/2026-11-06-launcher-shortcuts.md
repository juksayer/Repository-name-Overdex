# Desktop and Programs shortcuts — November 6, 2026

## Behavior and scope

Programs → Settings → Shortcuts lists the 16 supported launcher destinations.
Independent Desktop and Programs checkboxes allow both, either, or neither.
These are existing entry points that do not require a selected match, Pokémon,
or other contextual argument. Hiding a shortcut does not delete program data.

Up/Down selects a destination, Left/Right selects its location, and A changes
that checkbox. The context LCD offers both checkboxes directly. In trackpad
mode, each CRT checkbox has its own measured target; hovering selects the
corresponding row and column, and a separate pad tap changes it. B returns
through Settings and Programs to home. Other modules keep their controls.

Programs remains a permanent desktop footer. Settings remains in Programs,
with its Programs checkbox marked fixed; it may also be pinned to the desktop.
An empty desktop and a Programs list containing only Settings remain recoverable.
Selection is retained by destination ID when filtering changes the grid, and
the grid supports more than two rows, including partial final rows.

HomeScreenPreferences saves only the known destination flags in the existing
home-screen preference file, and updates the displayed configuration only after
a successful durable write. Missing or malformed values receive the original
defaults: six desktop pins and all destinations in Programs. The existing home
style preference remains intact.

ODXFiShell, its dimensions, CRT/LCD aspect ratios, bezels, Droidball, LEDs, and
physical control columns are unchanged. No shell color picker, skin system, or
texture settings are implemented in this change. A shared appearance palette
with case colors followed by optional materials remains a proposed next slice.

## Automated verification

Final production sources passed the full unit suite and debug packages:

```text
:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --max-workers=2
BUILD SUCCESSFUL
263 suites: 1,835 tests, 0 failures, 0 errors, 0 skipped
```

Fourteen added unit checks cover configuration defaults and malformed values,
all visibility combinations, fixed Settings recovery, dynamic grid navigation,
selection after filtering, persistence, and preference isolation.

Explicit API 34 emulator instrumentation returned **OK (36 tests)**. Six added
LauncherShortcutSettingsDeviceTest checks cover:

- Physical controls, direct LCD toggles, independent trackpad checkbox targets,
  and the CRT touch shield.
- Both/either/neither placement and filtered entry presence.
- Many desktop pins, scrolling, partial final rows, and returning from Programs.
- A hidden selection cannot remain executable as the removed destination.
- Hiding every optional shortcut, reaching Settings, and restoring a pin.
- Both checkbox controls fitting within the real handheld LCD at 1.5× font size,
  including the disabled, checked Settings/Programs control.

The other 30 checks cover the existing desktop, trackpad, boot, directory,
calibration previews, search lifecycle, settings, and Match Summary returns.
The test APK was rebuilt after the sixth UI check was added; all 36 ran against
the final production APK. The emulator was stopped afterward. No instrumentation
ran on either real phone.

## Moondrop acceptance and data preservation

The unlocked Android 14 Moondrop had no active Droidball service before its
in-place update. Installed APK SHA-256:

`a986295099af82122c6c21fa2b73c2fe9a50b29ed676880b179b68775af39aaf`

Observed on the physical phone:

1. The original six shortcuts and existing shell remain visible after updating.
2. Programs → Settings → Shortcuts is reachable with the existing LCD and physical
   controls. Up/Down selects Tournaments; Left/Right and A set it to Desktop only.
3. Returning home shows a seventh Tournaments pin and a partial last row. The
   pointer follows the selected checkbox and return navigation when enabled.
4. After force-stopping and restarting Overdex, Tournaments remains on the
   desktop and absent from the Programs list. Persisted flags agree with both
   visible results. Scrolling the Programs list still reaches Settings.
5. Returning to Shortcuts restores Tournaments to its defaults: Desktop off,
   Programs on. All 32 saved flags match their defaults; the existing home style
   is unchanged. The phone is left on home in context mode, Droidball off.

Private screenshots and before/after data hashes are in
`/home/sean/overdex-device-backups/2026-11-06-shortcut-settings/moondrop/`.
All 337 protected files remain, with no additions or removals. Of those, 335 are
byte-identical. The launcher preference file contains the new 32 default flags;
all prior preference values are semantically unchanged. The only other changed
file is Firestore's database, whose changed tables are limited to `targets` and
`target_globals` query metadata. Room databases, cards, team settings,
calibration, and recorded battle data are unchanged. No archive/media cleanup
occurred.

This establishes launcher customization and restart persistence on Moondrop,
along with the emulator cases above. It does not establish Pixel/tablet
acceptance, a skin system, or battle recognition accuracy.
