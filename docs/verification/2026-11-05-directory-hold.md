# Species-browser hold acceleration — November 5, 2026

The trainer reported that “hold to speed up” did not work in the directory's
Add Pokémon species picker.

## Cause and fix

Short taps used the current choices, but the held-repeat coroutine used
`rememberUpdatedState(::moveChoice)`. The generated local callable reference
captured a choices list; its inherited equality compared callable metadata,
not that captured list. Compose retained the initial callback, captured while
no picker choices were loaded. Held repeats therefore clamped selection back
to zero instead of advancing through the loaded list.

Replace that reference with a capturing lambda that refreshes the callback as
the current options change. The existing bounded acceleration curve, release
cancellation, keyboard focus rules, and short-tap behavior remain in place.

## Verification

- Added a full-shell regression using 200 species. Before the fix, a short Down
  tap reached choice 1, but a 2.5-second hold ended at choice 0 and failed the
  check. The same test passed after the callback change.
- Extended that regression to hold Up, stop after release in both directions,
  and hold again after switching to a different regional list.
- Final API 34 emulator run: `OK (9 tests)` in 65.898 seconds. Covers the new
  regression, owned-Pokémon save/restoration, standalone button holds, keyboard
  navigation, directory branch cleanup and selected-row scrolling.
- All 1,741 JVM tests passed across 255 suites, without failures, errors or skips.
- Debug and instrumentation APK assembly passed. `git diff --check` passed.

## Moondrop

- Reproduced the original failure on USB device `MDPH00124112500414`: a real
  five-second Down hold left Bulbasaur (#001) selected.
- Confirmed no active Overdex service or MediaProjection, then installed the
  update. Installed and built APK hashes match:
  `543621106610458065e2fb789501db0244da2f7e0ae5769b47829c9a84f8a09e`.
- On the updated build, a five-second Down hold advanced from Bulbasaur (#001)
  to Weepinbell (#070). A two-second Up hold returned to Mankey (#056).
  Screenshots shortly after release and later UI hierarchy captures agreed on
  the stopped selections. Left the unsaved species picker open for review.
- Compared 49 protected files before/after installation and checks: none added
  or removed, only the Firestore local cache changed. Owned cards, preferences,
  calibration, registration, and replay settings retained their contents.
  Match archive inventories match exactly. No card was chosen, created or saved.
- No physical-device instrumentation or Pixel installation was performed.

Private before/after logs, screenshots, hierarchies and backups:
`/home/sean/overdex-device-backups/2026-11-05-directory-hold/`.
