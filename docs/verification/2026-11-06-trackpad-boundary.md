# LCD trackpad boundary — November 6, 2026

## Problem and behavior

Starting a drag in the LCD left that contact attached to the trackpad after the
finger crossed into the shell or CRT. Compose retains the original hit path;
GlassShield did not receive the escaped contact. The old trackpad handler kept
using its outside coordinates and could mistake an exit/re-entry for a tap.

DesktopTrackpad now accepts only contacts and movement inside its measured pad
rectangle. The first outside sample cancels the entire contact. It continues
consuming that contact through release, with no more movement or activation,
even if the finger comes back inside. Delivered historical samples are checked
as well as the latest position. Lifting permits a new contact normally.

GlassShield, the shell layout, and other modules' controls are unchanged. Fresh
direct CRT input remains blocked, while valid LCD drags and taps still work.

## Verification

Two new emulator regressions failed against the previous APK before testing the
fix: outside movement changed the pointer, and a below-slop exit/re-entry opened
the hovered destination on release. Three added DesktopTrackpadDeviceTest cases
now cover all four edges, movement over real CRT and instrument button bounds,
re-entry, release, below-slop cancellation, delivered batched touch history,
and recovery on a fresh contact.

The history test injects root coordinates and a latest position distinct from
the preceding event. Compose discards a native move whose latest point is
unchanged, even if it contains history; the initial test shape therefore never
delivered the excursion. Correcting the test required no production change.
The corrected history case returned **OK (1 test)** independently before the
final broader UI run.

Final production sources passed the full unit suite and debug packages:

```text
:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --max-workers=2
BUILD SUCCESSFUL
263 suites: 1,835 tests, 0 failures, 0 errors, 0 skipped
```

The AndroidTest APK was rebuilt after the history test correction. The final
API 34 emulator run returned **OK (39 tests)** across trackpad, shortcut
customization, desktop, boot, directory, calibration previews, search, and
settings/summary returns. The emulator was stopped afterward.

## Moondrop acceptance

The unlocked Android 14 Moondrop had no active Droidball service before its
in-place update. Installed production APK SHA-256:

`6b3150a24b1cb50c1979ba81f5c3748ec99d4f5afc89d3fe28c620460e450dd0`

A single native four-second swipe began inside the LCD and continued upward
across the CRT. The pointer stopped at the trackpad boundary. CRT screenshots
at 1, 2, and 3.2 seconds and after release are byte-identical in the measured
CRT region. A new valid LCD drag moved the pointer. A separate touch on the CRT
did nothing. A fresh LCD tap opened Current Team; B returned home with trackpad
mode still enabled. The saved Incinerate team was present and unchanged.

Separate `input motionevent` shell processes were not accepted as one reliable
continuous gesture; their inconsistent snapshots were excluded. The single
native swipe and emulator's retained contact tests provide the boundary check.

Private screenshots, backups, and the preservation report are in
`/home/sean/overdex-device-backups/2026-11-06-trackpad-boundary/moondrop/`.
All 337 protected files remain: none added or removed, with 336 byte-identical.
The only changed file is Firestore's database, whose changed tables are limited
to `targets` and `target_globals` query metadata. Cards, Room databases, teams,
calibration, preferences, and recorded battle data are unchanged. No real-phone
instrumentation, data clearing, or archive cleanup occurred.

The Moondrop is left on home in trackpad mode, Droidball off. This is launcher
gesture acceptance; it does not claim Pixel/tablet or battle recognition results.
