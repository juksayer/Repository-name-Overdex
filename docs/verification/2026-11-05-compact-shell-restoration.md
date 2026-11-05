# Compact ODX-Fi shell restoration — November 5, 2026

## Requested behavior

The trainer clarified the revert target with the supplied October 29 Match
Summary screenshot. Restoring only the control-cross trial was insufficient:
the intended shell has a tall CRT and a compact LCD between two vertical
four-button columns, including while typing.

Recovered the dimensions from the shell immediately before `6e707e09`:

- Fixed 170 dp bottom console, 56 dp side columns, 36 dp high buttons.
- Left column: Up, Down, Left, Right.
- Right column: A, B, SELECT, START or the existing contextual action.
- Portrait and rotated binder typing keep the same physical shell geometry.

Removed the expanded typing dimensions and the branch that moved controls
below the LCD. Restored the full SELECT label. QWERTY, local swipe suggestions,
keyboard scrolling, compact LCD padding, touch ownership, CRT input preview,
stable content composition, and binder pixel rotation remain intact.

## Verification

- All 1,719 JVM tests in 253 suites passed, with no failures, errors, or skips.
  The count is two lower because obsolete expansion-specific sizing tests were
  replaced by three fixed-layout invariants instead of five sizing cases.
- Debug and instrumentation APK assembly passed.
- API 34 emulator instrumentation: `OK (16 tests)` in 97.865 seconds. Covered
  compact portrait/binder geometry, actual letter entry, navigation to the
  scrolled END key, finishing input, retained CRT content, standalone swipe
  behavior, registration navigation, and owned-Pokémon directory behavior.
- Separate read-only source review found no blocking scope or geometry issues.
- `git diff --check` passed.

APK SHA-256:
`84c46dc11569055b04ee5c4bdb5843de0b7e41dc7f4af63d00d4cbe0a9335edb`.

## Moondrop

USB serial: `MDPH00124112500414`.

- Confirmed no running Overdex service or MediaProjection before updating.
- Backed up and verified 49 protected files before installation.
- Installed successfully; installed APK hash matches the build.
- No protected files were added or removed. Only the Firestore local cache
  changed. Card storage, preferences, calibration and registration contents
  remained unchanged; match archive inventories match exactly.
- Visually inspected the normal directory and BATTLE → Add Pokémon → Species →
  START. The tall CRT and fixed compact side-column console match the requested
  layout in both views.
- The phone entered its notification shade during the follow-up input check;
  stopped further UI interaction. Native input delivery was verified on the
  emulator, not claimed from this phone interaction. No card was saved.

Private backups, screenshots and native-test output:
`/home/sean/overdex-device-backups/2026-11-05-compact-shell/`.

## Practical limit

The smaller LCD has smaller letter targets. Its existing selected-row scrolling
keeps the bottom keyboard controls reachable; rotated binder typing is
particularly narrow. The trainer's finger-comfort assessment remains open.
This restoration does not claim comfortable swipe typing from geometry tests.

The remaining directory/LCD requests stay in the separate
[review queue](../plans/directory-and-lcd-review.md).
