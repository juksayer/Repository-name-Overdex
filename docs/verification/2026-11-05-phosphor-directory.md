# Phosphor directory and directional navigation — November 5, 2026

The trainer selected the Phosphor branches study, requested the outline
folder/file icons from Instrument rails, and specified that returning to a
parent must close the other open folders at the child level being left.

## Result

- Directory rows use green monospace text, connected dim rails and elbows,
  purple active ancestors, and a quiet selection background. Folder icons show
  open/closed state; files and existing binders have distinct outline icons.
- Display labels omit `+`, `-`, and trailing `/`. Structural labels are
  lowercase with Pokémon/Droidball exceptions. Custom binder names and inline
  species/move choices preserve their spelling. Node names, internal paths,
  cards, and battle identities remain unchanged.
- Right opens a closed nonempty folder. Right on an open folder enters its first
  child. A does the same on folders and activates files; Right never activates
  a file. Selection alone does not expand a folder.
- Left recursively closes a selected open folder. Otherwise it returns to the
  actual parent and closes all child-folder branches at the level being left,
  including their descendants. The parent remains open. B mirrors this once an
  active editor has handled its own Back action.
- Left from another top-level entry returns to the first directory entry and
  clears expansions without launching anything.
- Parent and rail calculations use visible depth/preorder rather than splitting
  path strings, preserving custom names containing `/` and synthetic choices.
- Registration, keyboard and calibration inputs retain priority. The compact
  shell, keyboard geometry, LCD crop review, and GlassShield are retained.

LCD contextual content, placeholder cleanup, sounds, and a How-to home remain
separate items in the [review queue](../plans/directory-and-lcd-review.md).

## Verification

- Debug and instrumentation APK assembly succeeded.
- All 1,741 JVM tests passed across 255 suites: no failures, errors, or skips.
  Includes 16 directory-navigation and 6 directory-presentation tests.
- API 34 emulator: all 21 directory, owned-Pokémon editor, crop editor, crop LCD,
  keyboard geometry and registration checks passed in the 30-test run. New
  checks exercise real shell buttons, sibling cleanup, folder state, blocked CRT
  touches, file activation, and full-row scrolling at 1.5 font scale in a narrow
  viewport.
- The same run had one failure in the unchanged swipe-keyboard group: the
  second PIKACHU suggestion timed out. That exact test then passed alone:
  `OK (1 test)` in 5.277 seconds. A subsequent 9-test keyboard run passed the
  repeated swipe but timed out in the rotated gesture test, which passed in the
  initial run. These intermittent gesture timeouts remain unresolved; this is
  not a claim that the full 30-test run passed.
- During subsequent visual review the emulator displayed a System UI ANR.
  Dismissed it and confirmed the actual main-menu styling and nested rails.
  No app crash or deterministic dependency on the directory change was found;
  the ANR is not proof of the gesture timeout's cause. Logs retain both failures.
- Independent read-only integration review found no blocking issue with the
  input fallbacks, sibling cleanup, custom names, or selection scrolling.
- `git diff --check` passed.

## Moondrop installation and data retention

- USB serial: `MDPH00124112500414`. No Overdex service or MediaProjection was
  active immediately before replacement.
- Installed successfully and launched the main activity. Installed and built
  APK SHA-256 match:
  `19b942571de841d19b5fccff3e83a1e3be5ed9f4ee9af2f003d0049a67da9633`.
- Compared 49 protected files before and after installation: none added or
  removed; only the Firestore local cache changed. Owned-card storage,
  preferences, calibration, registration, and replay settings retained their
  contents. Match archive inventories match exactly.
- The notification shade was open at the post-install screenshot. Left the
  physical UI alone rather than competing with possible trainer use. Phone
  interaction/comfort acceptance remains for the trainer; button/navigation and
  visual checks above were performed on the isolated emulator.
- No card was created or saved during visual review. No physical-device
  instrumentation or Pixel installation was performed.

Private backup, screenshots and test evidence:
`/home/sean/overdex-device-backups/2026-11-05-phosphor-directory/`.
