# Local field-note writing and syllable suggestions

November 8, 2026. Personal writing follows the standalone journal and searchable
index. This slice adds local drafts, saved personal entries and separate syllable
annotations. It does not claim to deliver a note to another trainer.

The existing ODX-Fi shell, protected CRT and LCD keyboard remain in use. Select
changes the writing keyboard between letters and punctuation/numbers; the latter
offers a case switch. Existing swipe suggestions are retained. Editing a poem
preserves any extra lines beyond the three initially offered. Back closes the
keyboard first, then returns to the journal while retaining the draft. Leaving
the whole journal completes the latest draft write before navigation.

The store uses an atomic, read-back-verified file in `no_backup/field_notes`.
Failed reads do not become an empty journal; failed/uncertain writes require a
reload. A recovered in-memory draft is persisted before retry reports success.
An edit keeps the entry ID, origin, creation time and favorites, advancing its
revision once. Deleting the currently edited entry cancels pending autosave and
clears that draft rather than resurrecting the deleted text.

Syllable annotations retain the target ID, revision, line index and original line.
They allow a count, pronunciation explanation or replacement line, without
silently rewriting the source or treating a count as authoritative. Personal
entries are not marked as verified catalogue material. Canonical haiku assets
and anonymous attribution are unchanged.

## Validation

- Full unit suite: 2,083 tests, zero failures/errors/skips, including nine new
  storage checks for atomic updates, revisions, draft clearing, corruption and
  uncertain-write recovery. Debug app and instrumentation APKs build.
- API 34 disposable emulator: **OK (9 tests)**. This includes existing page,
  favorites, searchable index, detail return and protected-CRT checks; actual LCD
  letters/punctuation/case; separate correction entry with an optional count;
  durable draft reopen, stable revisions/favorites, pending-draft deletion,
  recovered-draft retry and a real AtomicFile reopen.
- Reviewed captured writer and correction screens: text is readable and the
  original shell dimensions and controls remain intact. Initial typing checks
  exposed stale callbacks that replaced earlier letters. The editor now reads
  current fields/callbacks, and both typing checks plus the full selected Android
  group pass after that fix.

No physical-device instrumentation is used. Durable journal integration tests
assert a disposable emulator before changes and restore its complete journal and
relevant preferences after each test.

Logs and full-shell captures are retained outside Git under
`/home/sean/overdex-device-backups/2026-11-08-field-notes-writing/`.
Installation is deferred until the complete journal update is validated. Large
font, tablet and actual trainer writing remain physical acceptance checks.
