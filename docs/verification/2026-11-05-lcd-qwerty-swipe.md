# Expanded LCD typing and domain swipe — November 5, 2026

The user wants typing to be faster while retaining the ODX-Fi appearance. They
chose an in-LCD keyboard over Android's separate input-method window. This
follows the [duplicate-header fix](2026-11-05-keyboard-lcd-space.md).

## Resulting interaction

Typing expands the lower console, using actual available height:

- Portrait: target 390 dp console, full-width LCD and two rows of 48 dp
  instrument buttons beneath it. The temporary Select label is `SEL`.
- Binder: target 420 dp console with 48 dp side controls, increasing the
  quarter-turned keyboard's logical width. Rotation remains 90 degrees.
- Short viewports cap growth to retain a CRT preview. The keyboard still
  scrolls when all rows cannot fit. Ordinary browsing retains the 170 dp
  console and 56×36 dp controls.
- Letter keys can grow to 48 dp high, with larger letter labels and thinner
  LCD frame padding. The CRT remains protected by GlassShield.

QWERTY is the default letter page. ABC remains selectable; token switching and
returning from numeric/move fields preserve that choice. Whole-move choice
grids, numeric fields, all 18 type tokens, END, B and instrument navigation
remain available. The CRT content call site stays stable across typing changes.

Swipe input uses the locally bundled Pokémon and move catalogue. Slide through
letters, lift, then tap one of up to three suggested canonical names. The strip
scrolls horizontally for longer choices. A swipe alone enters nothing. Choosing
a suggestion appends that label through the existing field callback, without
silently replacing existing text or adding a space. Ordinary tap input handles
arbitrary nicknames and messages; this is not a general-language swipe engine.

`TerminalSwipeVocabulary` reuses the existing GameMaster loader, including all
move entries rather than only moves in a current legal pool. Loading and
recognizer construction/comparison run off the UI thread. Gesture coordinates
and letter positions share the keyboard's local space before shell rotation.
The recognizer compares ordered path shape, endpoints, length and letter
waypoints; it normalizes accents, punctuation and adjacent repeated letters
while retaining the original spelling in suggestions.

Canceled, multi-pointer, invalid and unmatched traces insert nothing. A no-match
message leaves tapping available. Pointer ownership suppresses incidental key
clicks only once a drag begins. New touch input or a controller input revision
invalidates old suggestions/comparisons; END can release focus while retaining
the search keyboard without retaining stale choices.

## Verification

- Full JVM suite: **1,721 tests across 253 suites**, no failures, errors or skips.
- Debug app and Android-test APK builds succeeded.
- Emulator: `Overdex_API34_Check`, API 34, 1080×2400, density 420. Final native
  result: **OK (16 tests)** across `TerminalSwipeKeyboardDeviceTest` (8),
  `KeyboardPanelGeometryDeviceTest` (2), `RegistrationNavigationDeviceTest` (4)
  and `DirectoryOwnedPokemonDeviceTest` (2).
- Focused pure checks cover layout choice persistence, custom numeric/move
  pages, viewport sizing, noisy/distinct/ambiguous traces, punctuation and
  repeated letters, unrelated-path rejection, invalid coordinates, and cached
  vocabulary construction including unreferenced moves.
- First native run reproduced a cancellation defect: Android's consumed
  synthetic release was mistaken for a completed swipe. It offered a candidate
  but inserted no text. The handler now checks cancellation before consuming
  events and always clears its trail in `finally`.
- Native regressions include actual pointer taps and drags, explicit suggestion
  choice, cancellation and second-finger rejection, unmatched gestures, direct
  instrument input/END invalidation, a real rotated-keyboard gesture, expanded
  geometry and return to browsing, existing binder selection, directory typing,
  save-once and canceled/failed-save restoration.
- Actual app visual checks: OVERDEX → search shows the enlarged rotated LCD
  with all QWERTY rows and side controls; guided registration → CHOOSE SPECIES
  → Search/Select shows the full-width portrait LCD, CRT query and both button
  rows. The initial `Tap to type` hint changes to the swipe hint when the local
  vocabulary is ready. Screenshots are retained privately. The emulator's
  startup System UI ANR was dismissed before manual navigation; no app crash
  was observed during these checks.

App APK SHA-256:
`ba372f62b154706682d10296bd0546790a798b5e384182ac87a6d4ef8ca45559`.
Android-test APK SHA-256:
`64a71d9f108c8afdd4cc35622408b85b88fc7d58a8247d3c77321acb0c0dde15`.

Private build logs, native output and screenshots:
`/home/sean/overdex-device-backups/2026-11-05-lcd-swipe/`.

Neither physical phone was connected during the initial emulator checks.
Ideal/noisy path tests and emulator pointer checks demonstrate the
bounded behavior; they do not establish real-finger speed or recognition rates.
Phone comfort, large font scales, longer names and recognition tuning remain
acceptance work. No battle inference, recorded Articles or original audio were
changed by this feature.

## Moondrop installation later November 5

The user reconnected the Moondrop. It was unlocked, with no Droidball service or
MediaProjection running. Replacement installation succeeded; the installed APK
hash matches the app hash above.

A readable 2,932,224-byte backup protects 49 files containing databases,
preferences, calibration profiles/reference images, registration state and replay
edits. After installation and launch, no protected paths are missing or added.
All protected files retain their hashes except Firestore's local cache. The
51-entry match archive inventory is unchanged. The source recordings were not
deleted or copied as part of this settings backup.

Physical navigation: Directory → OVERDEX → Search opens the expanded rotated
QWERTY keyboard with all rows, side controls, CRT query and ready swipe hint.
The phone is left there with an empty query for the trainer's comfort test.
No recording was interrupted and no phone instrumentation was run. Real-finger
speed, gesture recognition rates and the portrait keyboard still need trainer
acceptance; this is an installation and visible-layout check.

Private backup and before/after evidence:
`/home/sean/overdex-device-backups/2026-11-05-keyboard-install/`.

Primary implementation references:
[Compose gesture ownership and consumption](https://developer.android.com/develop/ui/compose/touch-input/pointer-input/understand-gestures),
[Android input-method windows](https://developer.android.com/develop/ui/views/touch-and-input/creating-input-method).
