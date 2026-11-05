# One input preview while typing — November 5, 2026

The user clarified that the LCD keyboard's top line duplicated information on
the CRT. It was not a second confirmation inside the LCD. This slice removes
the shared LCD prompt and its spacer, reclaiming that row for the keyboard.

## Behavior and scope

- Existing CRT field values remain the input preview for registration, owned
  card editing, team names, battle filters and binder editing.
- Pokédex search, owned-binder search, directory species search and Trainer
  Comms lacked a separate CRT query/draft preview. Their sole preview now appears
  on the CRT while the keyboard is visible. It follows the binder rotation.
- CRT content has a stable composition call site when the preview appears or
  disappears. Opening the keyboard must not dispose a binder's remembered card
  selection or the directory editor state.
- Binder-library species search brings its query row into view when typing
  begins. The ordinary CRT GlassShield and interactive LCD remain intact.

This change does not resize the shell, control columns or key-size ceiling, and
does not add QWERTY or swipe recognition. The removed row was 9 sp text plus a
2 dp spacer; no fixed 30–50 pixel gain is claimed across devices. Larger input
layouts remain in the [input experiment plan](../plans/audio-position-and-lcd-input.md).

## Validation

- Full JVM suite: **1,704 tests across 250 suites**, zero failures, errors or skips.
- Debug app and Android-test APKs built successfully in the same invocation.
- Explicit isolated emulator installation and instrumentation on
  `Overdex_API34_Check`, API 34, 1080×2400 at density 420: **OK (6 tests)**.
  `RegistrationNavigationDeviceTest` covers list selection, held controls,
  END/touch resume, and the new binder preview/state regression.
  `DirectoryOwnedPokemonDeviceTest` covers directory controls, typing, a single
  successful save, failed-save restoration and cancellation.
- The new binder regression sets a remembered card selection before opening
  the keyboard, types, ends input and closes the keyboard. Selection and query
  survive throughout, with exactly one preview while visible. It detects the
  conditional content-wrapper reset found during independent review.
- Directory regression checks that the single typed species query is displayed
  above the LCD controls, and remains visible after END.
- Actual app visual check: directory → OVERDEX → SELECT/search → touch R, A, I
  → END. The rotated CRT shows `SEARCH: RAI`; the LCD shows all four keyboard
  rows without a repeated query header. END returns to the binder display.
- `git diff --check` passed. Independent source review found no remaining
  introduced issue after the composition-stability correction.

The emulator initially displayed a System UI ANR before app installation;
closing that system dialog restored the launcher. It did not recur during the
successful tests. Logs and screenshots are retained separately from source at
`/home/sean/overdex-device-backups/2026-11-05-keyboard-space/`.

App APK SHA-256:
`618b73100aa9f0b4ed6599169cc00a937248dceba6598d62e8dbd73730cf21d2`.
Android-test APK SHA-256:
`92b221cc57177c051c0e308a8efc9af137c3311942e573626df24649005f000a`.

Neither physical phone was connected during this check; no phone install or
capture interruption occurred. Physical typing comfort, long text at enlarged
font scales and each screen's full workflow still require phone acceptance.
Trainer Comms draft placement was source-audited; no account was created and no
message was sent during validation.
