# Field notes journal

November 8, 2026. A standalone journal now presents the existing battle haiku,
with local favorites and links to Pokémon and owned cards. Personal composition
and linked-trainer exchanges remain the [next slice](../plans/field-notes.md).

## Implemented behavior

- Desktop, Programs and the root directory all open `field_notes`. Existing
  shortcut visibility preferences survive; the new shortcut defaults visible in
  both launcher locations and can be hidden independently.
- The CRT shows complete poems, lessons and attribution in a journal. The LCD
  shows the species sprite and actions, including separate links to each matching
  owned specimen. These links use the unfiltered database stream and individual
  card IDs; a binder's retained search cannot hide a specimen.
- Left/Right turns pages; Up/Down selects LCD actions; A executes; B returns;
  Select toggles the current note's favorite; Start switches all/favorites.
  The LCD search action opens the existing custom keyboard and a CRT index.
  After finishing typing, Up/Down selects a result and A opens it. Long journal
  pages have LCD scroll actions. GlassShield continues to block direct CRT input.
- Stable note IDs identify favorites independently of text, revisions, cards,
  equipped moves and battle history. Commits are serialized and published only
  after persistence succeeds, including cancellation during an in-flight write.
  Loading and saving failures are visible rather than an apparently empty book.
- Pokémon details return to the same journal entry and reading position. Type,
  move and region actions show actual Pokédex results while retaining the journal
  beneath them; evolution chains propagate the originating destination. Existing
  binder links without an origin retain their default behavior.
- A 380 ms original procedural paper rustle accompanies actual page turns. Edge
  presses, recomposition and returning from details do not play it. The player is
  released with the screen and excluded from playback capture. Its source is
  `scripts/generate_field_notes_page_turn.py`.

The haiku asset and source documents are unchanged. All 1,026 source species and
1,030 notes retain their exact text and `Unknown Author` attribution, including
intentional line-count differences, sibling notes and four Droidball notes.
Droidball source ID 1026 maps to app catalogue ID 10001. The existing Pokédex
haiku section remains available. Shell geometry, LEDs, Droidball and controls
were not repositioned.

## Validation

- Final unit suite: **2,066 tests, zero failures/errors/skips**. New checks cover
  full source coverage/order/attribution, invalid IDs, retry/cancellation,
  independent and concurrent favorites, failed commits, cancellation consistency,
  launcher visibility migration and navigation dispatch.
- Debug app and instrumentation APKs build successfully. `git diff --check`
  passes. The procedural audio is mono 44.1 kHz, 16-bit PCM, 0.38 seconds.
- The API 37 emulator's Espresso runner fails before UI execution because
  `InputManager.getInstance` is no longer available. This is a test-framework
  compatibility failure; UI acceptance uses the existing API 34 emulator.
- API 34 emulator: all **11 targeted checks pass across the initial run and one
  corrected test-selector rerun**. This includes six existing shortcut-settings
  checks, real SharedPreferences disk/reopen persistence, two journal checks and
  two actual navigation-stack checks. The first journal search check initially
  matched both the D key and the D drawn inside END; excluding END's descendants
  corrects the test without changing production code. The rerun reports
  `OK (1 test)`; the other ten passed in the initial run.
- Journal checks exercise Left/Right and sound-call boundaries, the custom LCD
  keyboard, all four Droidball notes, Select/Start favorites, removing the final
  favorite, CRT touch rejection, and detail return. Navigation checks include
  evolution chains from both the journal and existing binder routes.
- Moondrop (1080×2400) and Pixel 4 (1080×2280): replaced the app with the tested
  APK, verified all 502 included private files unchanged before launch, and
  confirmed new MainActivity processes without fatal logs or active capture.
  Opened the journal using physical shell buttons and inspected screenshots on
  both devices. Poems and attribution fit within the existing CRT; the LCD
  remains inside its bezel. On Moondrop, Down scrolls the owned-card choices into
  view; A opens the existing CP 9000 Bulbasaur card, B returns to the journal,
  and Right changes to Ivysaur's page. No card was edited or deleted.

The installed debug APK SHA-256 is
`edfd0444383c4eee0ac99c08485618798741a270bd8b3e2724681198426faf53`.
The test-only selector change does not change that app APK. Physical preference
for the page sound, long reading sessions, font scaling and tablet layouts remain
trainer acceptance checks, not claims established by compilation or emulator tests.

## Device data protection

Before the update, both phones had no active Overdex service or MediaProjection.
Overdex was stopped and `shared_prefs`, `databases` and `files` were copied,
excluding the separately retained `files/artifacts` store. All **411 Moondrop**
and **91 Pixel** included files match their device SHA-256 hashes. Backups and
installation evidence remain outside Git at
`/home/sean/overdex-device-backups/2026-11-08-field-notes/`.

No messages are sent, public submissions created, or trainer access rules changed
by this slice. Trainer-authored notes are not yet editable or exchangeable.
