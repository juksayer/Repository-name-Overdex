# Docked Droidball tilt and blink — October 29, 2026

This records the initial restoration checkpoint. The subsequent five-tap behavior
and its device checks are recorded in [Droidball tap progression](2026-10-29-droidball-tap-progression.md).

The previous `AndroidPokeballLogo` in `PokedexComponents.kt` still contained the
tap response, but replacing its shell call with `DroidballBody` left the existing
`isLogoInteractive` flag unused. Only the Directory Tree's main-menu shell opts
into that flag. The restored path draws the current body once and layers a local
acknowledgement onto its rotation and eyelids.

A tap gives a roughly 15-degree spring tilt, two 60 ms blinks, a short hold and a
600 ms return upright (2,320 ms total). A finite frame-clock sequence retains the
response independently of Animator duration scaling. Repeated taps restart it.
No frame loop runs at rest. The former logo and its source remain preserved.

Interaction requires the owning route, the existing opt-in and a DOCKED journey.
Changing route, journey generation or enabled state immediately discards local
pose and tap state. Travel always owns its own pose. This acknowledgement does
not publish journey/battle changes or invoke the travel sounds. Field taps keep
their assistance-panel behavior.

## Verification

- Full unit suite: **1,411 tests / 224 suites**, zero failures/errors/skips.
- Debug app and Android-test APK builds passed.
- Installed on idle Pixel 4 (`99261FFAZ0017J`). Editable app state was saved first.
- Actual `ODXFiShell` instrumentation: **OK (1 test)**. A real Compose tap produced
  changed body pixels for tilt, closed/open/closed eye pixels for both blinks,
  and an exact return to resting pixels. The same fixture checked disable/re-enable
  without stale replay, removed tap actions during departure, and clean rest after
  journey takeover.
- The isolated empty test Activity can draw over the lock screen. It did not
  unlock the phone, open trainer screens, launch GO or start capture. Journey
  transitions in the test are presentation-only and reset afterward.
- Android's animation settings were not changed. The test drove the frame clock;
  it does not establish a separate live check with system animations disabled.
- The Moondrop was disconnected, so its installation remains pending reconnection.
- All six calibration/profile, HUD-layout and current-team files retained their
  saved hashes. No active service remained after the check.

Installed APK SHA-256:
`897d5bc58aef20c7a33585edf9a15c36e610469d187ed520c78aad86bdf73ae1`.

Editable-state snapshots and logs:
`/home/sean/overdex-device-backups/2026-10-29-droidball-acknowledgement-pixel/`.
