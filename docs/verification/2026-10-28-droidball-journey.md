# Droidball journey — first implementation, October 28, 2026

## Observable behavior

A single shared Canvas body now belongs to either the ODX-Fi shell or the field.
The first pass adds focused eyes, two departure blinks, a grounded shadow, an
eject press, a green-to-white lower shell, volumetric highlights, small industrial
etches, a left departure, a right entrance/skid, friendly eyes, a short scan,
right-edge docking, and a reluctant return to the shell. The old floating bitmap
and separate upper/lower field images are no longer used by BattleOverlay. The
legacy recursive shell Droidball wrapper and second service body were removed;
no historical Jimball assets were retrieved or introduced.

The coordinator is presentation-only. Recording begins after screen-sharing
approval without waiting for decorative motion. Stop invalidates pending launch
and motion callbacks and tears capture down immediately. Leaving during departure
cancels the delayed GO launch. Missing/failed GO launch stops observation. Frame
pacing uses monotonic elapsed time rather than Animator duration scaling, and
static poses do not request ongoing animation frames.

Native travel uses a small moving window with drawing margin for antennae and
scan. Its empty path does not intercept game taps. It restores normal flags and
right-edge coordinates after travel, without saving HUD calibration. The anchored
battle HUD remains body-free. Field/team editing and registration retain their
separate existing paths.

The display gate starts closed. Accepted Team Select, a body tap, or an explicit
HUD request grants permission. VS/countdown/battle/result can select a layout
without exposing a never-permitted panel. Information may skip the rest of an
entrance. Collapsing actually hides the panel; useful background observation
continues. Retained native-badge display also respects ownership and expansion.

## App-handoff boundary

Initial field entry uses successful explicit GO launch plus Overdex leaving the
foreground. This is not pixel-level confirmation that GO has finished drawing.
No broad foreground-app permission was added for animation. When returning to an
already-open battle through Recents, fresh dual native badges can restore field
ownership. Accepted Team Select must follow the current transit boundary. Late
generic OCR cannot itself restore the body over another app. Generation checks
prevent stale callbacks from reviving a stopped or superseded journey.

## Verification

- Full unit suite: **1,377 tests / 221 suites**, zero failures, errors or skips.
  New checks cover exclusive ownership, full trip ordering, stale callbacks,
  Stop during departure/arrival, reset, two blinks, eject/roll ordering,
  two-body-length skid, scan/settle ordering, material/return expressions,
  delayed-frame endpoints, compact-window geometry, and display permission.
- Debug application and Android-test APKs built successfully. Existing visual
  HUD fixtures now explicitly authorize their own test panel.
- Installed the debug app on the idle **Moondrop MD_PH_001**
  (`MDPH00124112500414`). Neither connected phone had an active Droidball service.
  Pixel 4 was not updated.
- Saved all editable app state before installation (284 files, including Room,
  preferences, calibration/reference images, lifetime records and replay-editor
  data). Immutable match media was inventoried and left untouched. Backup:
  `/home/sean/overdex-device-backups/2026-10-28-droidball-journey/`.
- After normal launch and after the isolated artwork test, all **17** calibration,
  HUD-layout and current-team files matched their prior hashes. Room database
  and WAL bytes were unchanged; database version 12, **24** owned cards, integrity OK.
- Isolated actual-renderer preview: **OK (1 test)** on Moondrop. It saved sixteen
  poses in a contact sheet. Initial attempts could not find Compose while the
  display was asleep. The successful run woke the screen and used a test-only
  empty activity allowed over the keyguard. It did not unlock the phone, expose
  trainer data, start capture, or run a battle.
- Preview output (generated, not versioned):
  `docs/verification/evidence/droidball-journey-20261028/droidball-preview.png`.
- APK SHA-256:
  `eebcfb51ad7d7de7264d58db1e20f496214443010f95e99e396cbb5adba5f569`.

The artwork test validates the production Canvas pixels separately from native
window behavior. The following unlocked-phone check extends that boundary; it
does not measure live battle recognition or sustained battle performance.

## Unlocked Moondrop check and resulting fixes

The user unlocked the Moondrop and authorized finishing the check. Native screen
recordings captured the shell departure, initial GO handoff, entrance/skid/scan,
closed field body, and return to the shell. One body was present through the
transfer. Initial GO loading was slow; its loading screen already had the closed
body, which is consistent with the documented app-handoff boundary.

Tapping the body opened the prebattle controls. Scan Team Select accepted the
actual party and resolved **Golbat, Greninja, Victreebel**, including Golbat's
nickname. No roster confirmation, move edit, or party-use action was submitted.
Closing the panel hid it. Stop from the shell and from the field removed the
service/window, and the shell displayed **START** again.

Two live defects were found and fixed:

1. Expanding the panel had overwritten the compact body's dock with the panel's
   left edge. Compact dock coordinates now remain separate from panel alignment;
   the measured-size callback reconciles resizing without writing calibration.
   Three successive real open/close cycles restored the same right-edge body
   position. The red shell bounds after the cycles were x=956–1058 on the
   1080-pixel-wide capture, matching the original right-edge placement. Tests
   also exercise left/right manual dock choices and repeated resize cycles.
2. Pressing Stop while Android still owed a foreground-service promotion could
   cancel the pending service and crash with
   `ForegroundServiceDidNotStartInTimeException`. A small startup gate now queues
   that cancellation, acknowledges foreground startup with a lightweight
   notification, and stops before projection, audio, sensors or overlay setup.
   Active capture still stops directly. The repeated phone check logged
   `Cancelled startup: foreground acknowledged; capture was not opened`, kept
   Overdex in front, and left no active service or delayed GO launch. No new
   AndroidRuntime exception occurred on the updated APK.

Final checks: **1,386 unit tests / 222 suites**, zero failures/errors/skips; debug
app and Android-test APK builds successful. Both fixes are installed on Moondrop.
The 16 calibration/profile, HUD-layout and current-team files included in the
live-fix backup retained their exact hashes; all **24** owned cards remain, with
SQLite integrity OK. Final APK SHA-256:
`991906dce34eef221c7b07c3cb34435e936a8d48b5c6418bd8af49d54acaf27b`.

Live evidence is under
`docs/verification/evidence/droidball-journey-20261028/live/`; the short deployment
excerpt preserves the source timestamps after trimming the permission wait.
Editable-state snapshots and final verification JSON are in the backup's
`live-fixes/` folder. These navigation checks created prebattle/test-session
records with no Match Start or battle result. No actual battle was played.
The final service query reports `(nothing)`, and the shell shows START. Pixel 4
was not changed. Android animation-scale-OFF behavior and sustained battle frame
performance were not measured during this check.

## Visible departure correction and slower entrance

The user's follow-up identified that departure could appear as a disappearance.
The earlier general launch check did not establish that the complete left roll
was visible. Its clock started before synchronous capture/witness setup, while
the navigation delay started afterward: setup could consume the entire pose
sequence before a shell frame was available.

Departure now progresses from delivered shell frame timestamps, starting at zero
and capping each step at 50 ms. A journey-owned clock preserves progress across
shell recreation and ignores duplicate/older frame timestamps. Hidden field
compositions cannot advance it. Only completion of that visible progression
permits the Activity to claim the GO handoff; Stop/Home can still cancel before
the claim. Capture starts immediately as before. Horizontal translation now sits
outside binder rotation. Arrival runs at 90% speed: 2,333 ms instead of 2,100 ms.

- Full suite: **1,394 tests / 223 suites**, no failures, errors or skips. Debug
  application and instrumentation APK builds passed. New checks cover stalled
  first/subsequent frames, duplicate/older frames, fractional timing, exact
  endpoints, stale generations and Stop after the last frame before handoff.
- Installed on the idle Moondrop. A native screen recording shows the lower shell
  becoming white, rotation and leftward travel through intermediate positions,
  the entire body leaving the left edge, and then the GO entrance and right dock.
  Closeups at source times 4.518, 4.620, 4.709, 4.829 and 4.943 seconds show the
  progressively departing body; it is gone by 5.064 seconds. This directly
  verifies the reported visual defect, unlike the stationary pose sheet.
- Returning to Overdex and pressing Stop removed the service. A second launch
  interrupted by Home 700 ms after accepting capture stayed on the Android home
  screen, removed the service, and did not open GO afterward. No new app crash
  appeared; the crash-buffer entry at 21:05 belongs to the previously fixed build.
- All 16 calibration/profile, HUD-layout and current-team files retained their
  hashes. Editable-state snapshots are in the backup's `visible-roll/` folder.
  These were prebattle navigation checks; no battle, team edit or confirmation
  was performed. Pixel 4 was not updated.
- Generated evidence: `live/visible-roll.mp4`, `live/left-roll-detail.png`,
  `live/visible-roll-excerpt.mp4` (trimmed with original frame timing), and
  `live/roll-home-cancel.mp4` under the evidence directory above.
- Installed APK SHA-256:
  `188ae93978679e438a2434b977d2bef45b368ea87433dfa7112f170a92aa8302`.
