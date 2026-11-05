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
The following requested changes remain pending and can be implemented in
separate small slices:

1. Put keyboard instructions in a real How-to section; its final location or
   directory entry has not been chosen. The idle swipe instruction is removed
   by the spacing refinement above. Preserve actual word suggestions,
   loading/failure feedback and gesture geometry.
2. Simplify directory presentation: lowercase labels with the requested
   Pokémon/Droidball name exceptions; remove `+` and trailing `/`; show branch
   elbows/open hierarchy. Presentation changes must preserve internal paths,
   saved names and individual Pokémon identities.
3. Directional tree navigation: Right expands a selected folder; Left collapses
   the current folder or returns toward its parent. From any top-level entry,
   Left returns selection to the top of the directory without launching it.
4. Show a selected folder's children in the CRT hierarchy and child preview in
   the LCD; leaf selection can show the page's purpose or useful instructions.
   Preserve the inline Add Pokémon instructions that the trainer likes. Keep
   calibration crop review/editing reachable when changing the default LCD.
5. Remove fake default LCD status, previous battle data, `idle`, `---` and shield
   counters when a module supplies no context. Preserve meaningful unknown
   editable fields, actual replay data, real errors and availability reports.
   The ODX-Fi manages collections/records; live battle presentation is Droidball.

An initial directory/LCD prototype was set aside before verification when the
trainer requested incremental review. Its patch and new LCD test are retained
privately under `/home/sean/overdex-device-backups/2026-11-05-directory-pending/`.
It is not a validated or installed implementation. MainActivity's contextual LCD
wiring and any How-to entry were not implemented in that prototype.
