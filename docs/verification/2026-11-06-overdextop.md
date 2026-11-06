# Overdextop home launcher — November 6, 2026

## Implemented scope

Overdextop renders six shortcuts and a programs menu inside the existing CRT.
Droidball, PWR/OBS/LINK, the bezel, LCD, and physical control columns retain the
shared ODXFiShell layout. The directory remains accessible through Explorer.
Programs and directory actions dispatch the same existing InstrumentCommands;
they do not create another card, team, history, or calibration store.

Arrows select, A opens, B returns, and Select opens programs. The LCD exposes
Open, Programs, and Explorer as direct-touch actions. Start retains Droidball
readiness/stop behavior. Programs → Settings saves an Overdextop/Directory home
choice; Directory → Home Screen opens the same choice. Absent or invalid saved
values default to Overdextop. A failed preference write leaves the current
displayed choice unchanged and reports the error.

The existing boot text clears, then shortcuts appear in order through the same
MainMenuScreen reveal. Controls remain unavailable until that reveal finishes.
Returning from a program preserves the desktop selection. Recovered-session
notices retain LCD precedence. Explicit crop previews select the real Calibration
directory row, avoiding an old Add Pokémon context hiding their LCD.

The Android LCD is **not a pointer trackpad yet**. This slice uses the existing
buttons and contextual LCD actions. The separate concept demonstration includes
trackpad behavior and is not proof of Android implementation.

## Automated verification

Final production sources passed:

```text
:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --max-workers=2
BUILD SUCCESSFUL
261 unit suites: 1,807 tests, 0 failures, 0 errors, 0 skipped
```

Ten new unit checks cover selection boundaries, menu/back behavior, command
routing, Explorer separation, and durable home preference success/failure.

Explicit emulator-only instrumentation on API 34 returned **OK (23 tests)**:

- OverdextopDeviceTest: 8 checks for shared boot/reveal, arrows/A/B, programs,
  Explorer, real GlassShield interception versus LCD touch, unchanged shell
  bounds, selected-row visibility, and usable LCD controls at large text sizes.
- MainMenuBootDeviceTest: 3 existing boot/reveal checks.
- DirectoryTreeNavigationDeviceTest: 2 existing directory checks.
- DirectoryLcdModeDeviceTest: 3 existing calibration/focus/save checks.
- DirectorySearchResetDeviceTest: 1 existing search-lifecycle check.
- SettingsAndMatchReturnDeviceTest: 6 existing settings/Start/summary checks.

The small-screen tests include 280 × 260 dp CRT content and 280 × 140 dp LCD
content with 1.5× text. The emulator was stopped after these checks. No
instrumentation ran on the physical phone.

## Moondrop acceptance

The unlocked Android 14 Moondrop had no active Droidball service before the
in-place update. Installed APK SHA-256:

`4c583ea79effe6ae10ccf2b133d45406cb622c739757410fa97b39fc217741f5`

Observed with the actual shell buttons and LCD:

1. Cold launch clears boot text and reveals Overdextop inside the original shell.
2. Right twice, A opens Current Team with the saved Incinerate team. B returns
   with Current Team still selected on the desktop.
3. Select opens programs. Down to Settings, A opens the home choice.
4. Choosing Directory survives a full app restart. Its Home Screen entry opens
   settings, and programs remain accessible while Directory is the saved home.
   B returns to Directory. Choosing Overdextop restores the desktop.
5. LCD Explorer opens the existing tree. Right opens/enters folders; Left returns
   to the parent. B returns to Overdextop from an unedited Add Pokémon branch.
6. Holding A on the desktop Droidball shortcut then opens real crop previews in
   the LCD, including the saved species/HP/resources/announcement targets. The
   inactive Add Pokémon context does not cover them. B exits without saving.
7. Another full restart retains Overdextop. The phone was left with all six
   shortcuts, programs, and LCD actions visible; Droidball remains stopped.

Private screen captures, a boot recording, and data-preservation checks are in
`/home/sean/overdex-device-backups/2026-11-06-overdextop/moondrop/`.
Before/after checks covered 336 existing protected files: no removals, and all
existing Room databases, owned cards, team settings, and calibration files were
unchanged. The new home preference is the only added protected file. Normal
Firebase auth/heartbeat metadata refreshed; Firestore changes were confined to
`targets` and `target_globals` query metadata. No archive/media cleanup occurred.

This validates the launcher and its shared navigation on Moondrop. It does not
claim Pixel/tablet acceptance, a live battle check, or changed recognition
accuracy.
