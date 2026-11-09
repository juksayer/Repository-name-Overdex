# ODX-Fi case, button and bezel colors — 2026-11-08

Programs → Settings → ODX-Fi colors now opens an appearance picker in the shared
shell. Case, Buttons and Bezels have independent presets and RGB sliders on the
LCD. The shell itself previews the draft. Save applies the choice throughout
the app and persists it; B/system Back discards the draft. Original colors
restores all three draft defaults, which become durable when saved.

The original paint values are case `#2E7D32`, buttons `#444444`, and bezels
`#2A732E` (the old translucent black bezel over the green case). Both the CRT
surround and lower control assembly use the bezel choice. Button legends and
etched/status labels choose light or dark ink for readability. Status LED signal
colors, screen colors, shell dimensions, CRT GlassShield, calibration and battle
observations retain their existing roles.

The picker suspends the CRT effect for literal color comparison; this does not
change the filter preference used by other screens.

Up/Down selects the part, Left/Right adjusts the selected channel by five,
Select cycles RGB channels, A cycles presets, and the Start-position Save button
saves. Touch controls belong to the LCD. The fixed part selector and Save/Original
colors footer remain reachable while the palette and sliders scroll.

`ODXFiAppearancePreferences` uses its own private preference file, canonical
opaque RGB values, independent fallback for malformed fields, and a durable
write result. MainActivity updates the global palette only after that write
succeeds, off the UI thread. A failed save retains the draft and exposes Retry
through Save; it does not close the editor. Drafts survive activity recreation.

Validation:

- Full unit suite: **2,168 tests**, zero failures/errors/skips. Includes six
  appearance-preference tests and a settings/return-selection navigation test.
- Debug app and instrumentation APKs build successfully.
- Disposable API 34 emulator: **five new picker tests passed** on the final
  build. These use actual LCD/physical-button touches, a non-preset RGB drag,
  durable save/reopen, cancel, failed-save retry, draft reset, rendered paint
  pixels and unchanged hardware bounds. Screenshots of default and customized
  paint were visually inspected after disabling CRT filtering in the picker.
- **Fourteen existing** Overdextop and Settings/Match Return Android checks
  passed, including CRT GlassShield, LCD input, navigation and hardware layout.
- In the full app, opened Programs → Settings → ODX-Fi colors with LCD and
  hardware controls, saved custom paint, cold-restarted MainActivity, and
  verified both stored values and the actual colored case/buttons/bezels on
  the main launcher. No fatal startup log entries were present.
- The Pixel 4 and Moondrop were not visible over USB during this task. No user
  phone was installed, instrumented, or modified. Physical-device acceptance
  remains pending. The disposable emulator was stopped after verification.

Evidence files include `confirmed-build-and-unit-tests.txt`,
`confirmed-color-picker-instrumentation.txt`,
`color-picker-and-shell-instrumentation.txt`, `full-app-restart.json`,
`odxfi-colors-default.png`, and `odxfi-colors-custom.png`. The first combined UI
run caught a test-only assumption that a swipe must land exactly at channel 255;
the test now checks channel independence and exact persistence of the actual
non-preset color selected by the physical drag.

Local evidence:
`/home/sean/overdex-device-backups/2026-11-08-odxfi-colors/`.
