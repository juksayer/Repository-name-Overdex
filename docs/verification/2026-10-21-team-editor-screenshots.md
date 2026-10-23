# Team & Moves screenshots

October 21, 2026, `odx-recovery`.

## Report and cause

After confirming correct Raichu forms and move lists, the user shared a
[screenshot](/tmp/codex-clipboard-224f5484-8af4-4b6f-abc9-7b1fdab9fa40.png)
with a black rectangle where Team & Moves should be. They clarified that they
want to see the overlay itself in screenshots, not see the game through it.

`DroidballService.setFieldEditorWindow(true)` enabled `FLAG_SECURE` for that
window. Android's [window documentation](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#FLAG_SECURE)
specifies that this flag prevents screenshots. The ordinary Battle HUD already
omits it.

## Change and validation boundary

- Team & Moves no longer adds the secure-window flag. Its appearance, opacity,
  position, focus and touch behavior stay the same.
- Visual frame publication still stops while the editor is open, clears its
  replay cache on transitions, and waits 250 ms after closing. Screenshots of
  the editor can be shared without turning our own move list into GO testimony.
- Assisted-registration windows use separate flag policies and are outside this
  change.
- Debug APK build and full unit suite passed: **1,004 tests, zero failures,
  errors or skips**. Log: `/tmp/overdex-team-editor-screenshots.log`.
- Updated the Moondrop (`MDPH00124112500414`) with `adb install -r` after checking
  that Droidball and MediaProjection were stopped. Installation succeeded;
  package update time was October 21, 2026, 13:31:09. MainActivity accepted the
  launch intent. No instrumentation suite was run on the user's phone.
- Live screenshot acceptance passed on the Moondrop. The user's October 21
  screenshots at 13:34 and 13:35 show the editor's text and controls, with no
  black rectangle, while selecting slots 1 and 2 respectively:
  [Alolan Raichu](/tmp/codex-clipboard-630f206b-9e30-4535-ac6c-8b700e1c1a6b.png),
  [ordinary Raichu](/tmp/codex-clipboard-cb1bc3b3-dda0-4a45-b3a2-f689f94e3baf.png).
  The screenshots also confirm the distinct Fast Move lists described in the
  [form verification](2026-10-21-team-select-forms.md).
- The subsequent 13:40–13:41 slot-swap test produced the new roster order and
  ordinary Raichu's move list in slot 1; see the form verification above.
  Frame-publication timing and exclusion of editor text are code-checked here,
  not measured from the supplied still screenshots.
