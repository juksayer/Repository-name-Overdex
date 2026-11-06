# Desktop navigation and LCD trackpad — November 6, 2026

## Behavior and scope

The existing ODXFiShell, CRT/LCD dimensions, bezels, Droidball, LEDs, and physical
control columns are unchanged. Changes are inside the existing home CRT and LCD.
The proposed desktop name is removed from visible labels: the CRT says `home`,
and the home-screen choice and current-style label say `desktop`.

On the home launcher, Select or the LCD's trackpad/context control switches
input modes. Relative LCD drags move a pointer over actual measured CRT targets.
Hover selects; a separate tap opens the item under the pointer. A drag release
does not activate anything, and a new finger contact does not warp the pointer.
Trackpad mode retains Programs and Context above the pad. Context mode retains
Open, Programs, and Explorer. The physical arrows/A/B remain available; Start
retains its Droidball behavior. Other modules keep their existing controls.

Pointer coordinates are normalized to the CRT content viewport. Target rectangles
come from actual layout, clipped by scroll containers and the viewport, rather
than guessed device coordinates. Physical selection centers the pointer and
follows a selected row through BringIntoView. A real drag releases that alignment.
Only new movement during the current CRT attachment changes hover selection;
resizing, toggling, or returning from a module cannot replay an old movement.
Departure and surface changes release old targets. Failed home-preference saves
retain the current targets so the user can retry.

The cursor overlay is decorative for accessibility. Giving its full-viewport
canvas a label hid the real shortcut labels beneath it; the final build retains
the underlying labels while the cursor is visible.

## Automated verification

Final production sources passed the full unit suite and both debug packages:

```text
:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --max-workers=2
BUILD SUCCESSFUL
262 suites: 1,821 tests, 0 failures, 0 errors, 0 skipped
```

Fourteen new pointer unit checks cover relative motion, invalid input, edge
clamping, clipping, stale geometry, resize, surface changes, and deferred physical
selection alignment.

Explicit API 34 emulator instrumentation returned **OK (30 tests)**. Seven new
DesktopTrackpadDeviceTest checks cover actual gestures, separate contacts,
drag-release safety, hover/tap routing, GlassShield interception, mode switching,
physical buttons, scrolling to the last program, and CRT remount/reattachment.
The other 23 checks cover desktop boot, small/large-text layouts, directory,
calibration, search lifecycle, and settings/summary returns.
After the decorative-cursor accessibility correction, the full unit suite and
both packages passed again, and the 15 launcher/trackpad interface checks returned
**OK (15 tests)** on the final installed build.

Mode-switch snapshots compare exact CRT and LCD bounds, preserving their size
and aspect ratio, along with Droidball, LEDs, branding, and button positions.
The emulator was stopped afterward. No instrumentation ran on the real phone.

## Moondrop acceptance and data preservation

The unlocked Android 14 Moondrop had no active Droidball service before its
in-place update. Installed APK SHA-256:

`b7ccc4724616af170363c9250d40b576878f0fc703dc1c79161cf4e6075f9e63`

Observed on the physical phone:

1. The home screen and settings no longer display the proposed name.
2. Select enables the LCD pad and CRT pointer. Thirteen measured header/control
   bounds match the pre-update capture exactly in context and trackpad modes.
3. Two horizontal LCD drags move from Droidball to Current Team. Both releases
   leave the home screen visible. A separate LCD tap opens the existing team.
4. B returns to the same shortcut with trackpad mode still active and its pointer
   correctly centered. The saved Incinerate team is unchanged.
5. Programs remains accessible in trackpad mode. Physical Down scrolls to Settings
   and the pointer follows the final visible row; a pad tap opens Home Screen.
6. Select restores context controls. Settings shows `desktop`, `directory tree`,
   and `current: desktop`. B returns through Programs to home, and Select enables
   trackpad mode again. The phone was left there for user testing, Droidball off.
7. On the final build, Android's accessibility hierarchy retains all six shortcut
   labels with the pointer visible. Drag/tap opens the saved team and B returns.

Private screenshots, the gesture recording, before/after hashes, and the hardware
bounds report are in
`/home/sean/overdex-device-backups/2026-11-06-lcd-trackpad/moondrop/`.
All 337 protected files remain: no additions or removals, with 336 byte-identical.
Existing Room databases, cards, team settings, calibration, and preferences are
unchanged. The only changed file is Firestore's database, with table differences
confined to `targets` and `target_globals` query metadata. No archive/media cleanup
occurred.

This is launcher-level trackpad acceptance on Moondrop. It does not establish
Pixel/tablet acceptance, trackpad support in every module, or battle recognition
accuracy. Pointer feel and sensitivity remain appropriate for user feedback.
