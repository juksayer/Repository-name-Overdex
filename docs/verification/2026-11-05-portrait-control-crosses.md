# Portrait typing control crosses — November 5, 2026

**Reverted after review:** the trainer requested restoring the previous ODX-Fi
shell controls. The observations below record the trial and are historical;
they do not describe the currently restored portrait arrangement.

## Scope

This checkpoint implements only the enlarged portrait typing controls. The
trainer requested one change at a time, followed by review.

- Left cross: Up at the top, Left/Right in the middle, Down at the bottom.
- Right cross: START at the top, SELECT/B in the middle, A at the bottom.
- The LCD keyboard stays full width above both crosses.
- Normal portrait typing targets are 48 dp square; the desired console height
  is 430 dp. Short viewports reduce control height while preserving LCD space
  and the CRT input preview. Even the synthetic 230 dp viewport retains
  positive control and LCD space.
- Contextual START labels/actions, hold behavior, and the existing B-owner
  protection remain intact. Ordinary browsing and binder layouts are unchanged.

The directory presentation/navigation, swipe instruction line, contextual LCD
content, and placeholder cleanup remain pending in the
[review queue](../plans/directory-and-lcd-review.md).

## Automated checks

- Debug app and instrumentation APK assembly passed.
- All 1,721 JVM tests in 253 suites passed, with no failures, errors, or skips.
- API 34 emulator instrumentation: `OK (17 tests)`. This covers portrait cross
  geometry and touch callbacks, binder geometry, swipe typing, registration
  navigation, and owned-Pokémon directory behavior.
- The first instrumentation run encountered a System UI ANR dialog that caused
  two swipe-test setup timeouts. The geometry tests passed on that run. After
  closing the system dialog, the unchanged APK passed all 17 tests.
- A separate read-only review found no scope or mapping blockers.
- `git diff --check` passed.

App APK SHA-256:
`15425738af79f1f4e0cf001dfff186fd4dbd06ac946e1018a88f9c7641b6aad3`.

## Moondrop check

Device: Moondrop, USB serial `MDPH00124112500414`.

- Confirmed no running Overdex service or MediaProjection before installation.
- Installed the update successfully; installed APK hash matches the built APK.
- Compared 49 protected files before and after installation. No files were
  added or removed. Only the Firestore local cache database changed; owned-card
  storage, preferences, calibration, registration, and replay settings retained
  their contents. Match archive inventories match exactly.
- From the directory, opened BATTLE → Add Pokémon → Species, then START.
- Visually confirmed the full-width portrait keyboard and both cross mappings.
- Tapped P on the LCD and saw `SEARCH: P` in the CRT, then removed it using the
  LCD backspace. No card was created or saved.
- Left the empty portrait keyboard open for the trainer's comfort review.

Private backups, screenshots, and test output:
`/home/sean/overdex-device-backups/2026-11-05-portrait-cross-controls/`.

This verifies installation, layout, and input delivery. The trainer's assessment
of typing comfort and preferred control placement remains the review boundary
before the next queued change.

## Revert verification

The trainer requested reverting the shell trial. Restored both production files
and their two tests to the behavior of `34cb372d` (apart from removing two
pre-existing trailing spaces). The prior two control rows and 390 dp portrait
typing panel are restored; the earlier full-width QWERTY keyboard and local
swipe recognition remain.

- All 1,721 JVM tests passed; debug and instrumentation APK assembly passed.
- Installed on the Moondrop and visually confirmed the prior portrait rows via
  BATTLE → Add Pokémon → Species → START. No card was created or saved.
- All 49 protected files remain present. Only the Firestore local cache changed;
  match archive inventories match exactly.
- Installed APK SHA-256 matches the build:
  `c44ffe58ed024ae11fad6a32f3a7c1581c51c891f9693f507c7ed95c4dd49739`.
- No new emulator instrumentation run was required for this restoration. The
  17-test result above applies only to the now-reverted trial.
- Backup and restored-layout screenshot:
  `/home/sean/overdex-device-backups/2026-11-05-revert-control-crosses/`.
