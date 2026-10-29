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

The successful artwork test verifies the production Canvas pixels. It does not
validate native-window movement, GO foreground timing, animation performance,
or live battle recognition. Full launch/return and Team Select/tap checks still
need an unlocked phone. Also check Stop during departure and during a field
session: capture must stop at once, with no delayed GO launch or surviving window.
No new battle was played by the agent.
