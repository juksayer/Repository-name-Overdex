# Directory and LCD review — November 5, 2026

The trainer clarified the restoration target with the October 29 Match Summary
screenshot: a tall CRT above a compact LCD between two vertical button columns.
The enlarged keyboard console and the control-cross trial are both superseded.

- Fixed 170 dp bottom console, with 56 dp columns and 36 dp high buttons.
- Left: Up, Down, Left, Right. Right: A, B, SELECT, START/contextual STOP.
- Opening the keyboard keeps the same physical shell geometry, including in
  binder mode. QWERTY, local swipe recognition, and existing keyboard scrolling
  remain available inside the smaller LCD.

The spacing refinement removes the idle swipe-instruction line and
reduces its fixed suggestion strip from 44 dp to 24 dp, returning that space to
the letter rows without moving the shell or controls. Actual suggestions and
matching feedback remain in the strip.

The trainer requested one change at a time, with review before the next change.
The next accepted slice combines the **Phosphor branches** study with the
outline folder/file icons from **Instrument rails**:

- Green monospace rows, connected branch rails/elbows, purple active ancestors,
  a quiet selection background, and open/closed folder icons.
- Density refinement: use the selected Instrument mono (Share Tech Mono)
  typeface, bundled locally, and 26 dp minimum headings with 3 dp vertical
  padding. Keep 16 sp letters and 20 sp line height; wrapping and larger text
  can increase the row height rather than clip it.
- Structural labels are lowercase with Pokémon/Droidball name exceptions.
  Custom binder names and inline species/move choices preserve their spelling;
  internal paths and saved identities do not change.
- Right opens a closed folder; Right again enters its first child. A has the
  same folder behavior and activates files. Right never activates a file.
- Left closes the selected open folder and its descendants. Otherwise it returns
  to the parent and closes every child-folder branch at the level being left,
  including nested expansions. The parent remains open. B follows Left after
  an active editor has handled its own Back action.
- Left from another top-level entry returns to the first entry and clears open
  branches without launching anything. Selection alone does not open folders.
- Keyboard, registration and calibration controls retain input priority.

This slice implements the directory appearance and navigation below. The LCD's
contextual content, sounds and shell geometry are separate review decisions.
The following requested changes remain pending and can be implemented in
separate small slices:

1. Put keyboard instructions in a real How-to section; its final location or
   directory entry has not been chosen. The idle swipe instruction is removed
   by the spacing refinement above. Preserve actual word suggestions,
   loading/failure feedback and gesture geometry.
2. Show a selected folder's child preview in
   the LCD; leaf selection can show the page's purpose or useful instructions.
   Preserve the inline Add Pokémon instructions that the trainer likes. Keep
   calibration crop review/editing reachable when changing the default LCD.
3. Remove fake default LCD status, previous battle data, `idle`, `---` and shield
   counters when a module supplies no context. Preserve meaningful unknown
   editable fields, actual replay data, real errors and availability reports.
   The ODX-Fi manages collections/records; live battle presentation is Droidball.

An initial directory/LCD prototype was set aside before verification when the
trainer requested incremental review. Its patch and new LCD test are retained
privately under `/home/sean/overdex-device-backups/2026-11-05-directory-pending/`.
It is not a validated or installed implementation. MainActivity's contextual LCD
wiring and any How-to entry were not implemented in that prototype.
