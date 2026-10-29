# Droidball field size and icon alignment — October 29, 2026

The user's 1080×2400 Moondrop screenshots measured the Pokeball Plus backing
circle at X=935..1040 and Y=266..368. The subsequent 1080×2280 Pixel 4 screenshots
measured X=936..1039 and Y=268..365. Both horizontal centers are X=987.5; their
right-edge measurements differ by one pixel. Use the 105-pixel horizontal diameter
and 40-pixel right inset as the circular-body reference. The vertical endpoints
serve as approximate size evidence, not a request to move Droidball vertically.

`DroidballFieldGeometry` derives the canvas from screen width and the renderer's
0.746 outer-outline diameter. At 1080px width the canvas is 141px and its left
edge is 917px, placing the circular outline at approximately X=934.907..1040.093.
This measures the visible circle, not the transparent canvas or antennae. A shared
outline constant is also used by `DroidballBody`.

The service caches one physical canvas size per overlay and passes it to Compose;
the native window, drag/snap, travel and collapse calculations share that size.
The body scales from display width when the overlay is created, independently
of Android density. Travel padding retains the former one-third-canvas ratio,
including the scan beam. Arrival now begins at 1.5 body diameters outside the dock
so the visible circle starts fully offscreen with the new inset. Its two-body
skid endpoint and 2,333ms duration remain intact.

The shell's docked body, battle-HUD coordinates, saved calibration, body artwork
and panel controls retain their existing behavior. Left-edge snapping uses the
same inset. Expanding a panel retains its existing layout; collapsing restores
the compact dock after measurement.

## Verification

- Full unit suite: **1,417 tests / 225 suites**, zero failures/errors/skips.
  The seven maintained field-geometry tests cover the supplied circle bounds at
  720/1080/2160px widths, the real arrival starting pose, two-body travel,
  padding cancellation, both docks and repeated expansion/collapse.
- Debug app and Android-test APK builds passed.
- Installed on **Pixel 4** (`99261FFAZ0017J`) while idle. Its six critical
  calibration/profile, HUD-layout and current-team files retained their hashes.
- The user confirmed the Moondrop session was finished. By the time the idle
  notification was expanded, its service had already stopped; installation
  interrupted no active capture. Installed on **Moondrop** (`MDPH00124112500414`).
- A normal pre-flight launch through Android screen-share approval opened GO's
  overworld. Visual inspection showed the body aligned beneath the Pokeball Plus
  circle. WindowManager reported the collapsed frame **[917,350][1058,491]**,
  exactly 141×141px. Opening then closing the assistance panel returned to that
  same frame after layout. A screenshot is retained with the local device backup.
- The normal **Stop Session** control ended the check. No battle was played;
  this verifies presentation and native window placement, not recognition accuracy.
  Moondrop's 16 critical calibration/profile, HUD-layout and current-team files
  retained their hashes. Its final service query reported `(nothing)`.
- Pixel installation and supplied reference measurements are confirmed; a
  separate live field-overlay check on Pixel was not performed in this task.

Installed APK SHA-256:
`41b1ac9bec2763dd46d683d3b69e1e1572bece9611f2ad93075296071d7982ca`.

Local snapshots, build logs, hash manifests and Moondrop screenshot:

- `/home/sean/overdex-device-backups/2026-10-29-droidball-field-size-pixel/`
- `/home/sean/overdex-device-backups/2026-10-29-droidball-field-size-moondrop/`
