# Directory and LCD review — November 5, 2026

Review outcome: the trainer requested reverting the control-cross trial.
The previous enlarged portrait keyboard arrangement has been restored. The
cross layout below describes the rejected trial, not the current layout.
All other requested changes remain pending separate review.

The trainer requested one change at a time, with a review before the next change.
The first slice is the enlarged **portrait typing controls only**:

- Left cross: Up above Left/Right, Down below.
- Right cross: START above SELECT/B, A below. Existing contextual START actions
  and labels remain meaningful.
- Keep the keyboard full width above the two crosses. Browsing and binder
  controls remain unchanged.

Do not fold the following requested changes into that preview. They remain
pending the trainer's review and can be implemented in separate small slices:

1. Remove the idle swipe instruction line. Put instructions in a real How-to
   section; its final location/directory entry has not been chosen. Preserve
   actual word suggestions, loading/failure feedback and gesture geometry.
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
