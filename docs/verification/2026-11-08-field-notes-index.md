# Field notes searchable index

November 8, 2026. Follow-up to the [standalone journal](2026-11-08-field-notes.md).

The existing LCD keyboard and CRT index now search the accompanying lesson as
well as species names/numbers, titles and poem text. Every result includes a
short excerpt, with literal case-insensitive matches highlighted. Lesson matches
are labeled `lesson`. Long passages are cropped around their first match, retaining
the complete matching phrase and preserving supplementary Unicode characters.

Index results and opened pages display the permanent source note ID, for example
`entry 061-001`. A trainer can search that ID directly. Numeric species searches
also accept the familiar leading `#`. IDs remain independent of page position,
favorites and source ordering. Original notes, source files and attribution are
unchanged; the journal still uses the existing shell and controls.

The search helper preserves catalogue order and selects the first matching poem
line, otherwise the matching lesson. A species/title/ID-only match previews the
first poem line. Blank searches list the current all/favorites collection without
highlighting. Text search treats punctuation literally rather than as a pattern.

## Validation

Eight focused unit checks cover lesson-only matches, absent lessons, source
order, sibling IDs, Droidball's two numeric IDs, literal punctuation, original
UTF-16 highlight offsets, repeated matches and bounded excerpts near the end of
long lessons. Existing full-catalogue/favorite checks remain in the suite.

The new actual-assets Android check searches `ADAPTABILITY` through the LCD
keyboard, checks Poliwhirl's highlighted lesson and entry `061-001`, opens its
correct page, favorites it, then repeats the search in favorites. The same entry
ID must remain visible despite the page counter changing to 1/1. It also saves a
full-screen search preview for visual inspection. Existing page, Droidball search,
detail-return and CRT-protection checks remain in the selected test class.

- Full unit suite: **2,074 tests, zero failures/errors/skips**. Debug app and
  instrumentation APK builds succeed; `git diff --check` passes.
- API 34 disposable emulator: **`OK (3 tests)`**, covering the new lesson search
  and the existing page/favorites and Droidball/detail-return checks. The lesson
  test inspects actual styled text to confirm the matching word has a highlight
  background; merely including that word in an unstyled result is insufficient.
- Inspected the captured search screen: all three adaptability results show
  readable excerpts, highlighted matches and distinct permanent IDs. The selected
  Poliwhirl drives the LCD context inside the unchanged handheld shell.
- Installed on Moondrop and Pixel 4 after checking that neither had an active
  service or MediaProjection. Verified backups cover preferences, databases and
  files excluding the separately retained artifact store. All **411 Moondrop**
  and **91 Pixel** included files remain byte-identical after installation and
  before launch. Both app processes resume without fatal startup logs and with
  capture stopped. No physical-device instrumentation or favorite changes were
  performed. Detailed new result-layout inspection used the emulator.

The tested and installed app APK SHA-256 is
`fcf9de6731660b42874a335a23efad09a63b6acb1454916d08f48bc7fa45c715`.
Logs, the visual preview and data-preservation evidence remain outside Git at
`/home/sean/overdex-device-backups/2026-11-08-field-notes-index/`.
Large-font and tablet layout acceptance remain separate checks.
