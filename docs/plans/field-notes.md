# Field notes

Direction agreed November 8, 2026. The first slice is a standalone journal of the
existing battle haiku, with local favorites and Pokémon/owned-card links. The
Pokédex keeps its existing haiku section. Desktop, programs and directory access
use the same destination and respect launcher visibility settings.
See the [implementation and acceptance record](../verification/2026-11-08-field-notes.md).

The CRT presents the journal and its searchable index. The LCD provides Pokémon
context and actions within the existing ODX-Fi shell. A quiet original paper
rustle accompanies an actual page turn; it is excluded from playback capture.
Favorites refer to stable note IDs, independently of owned cards, equipped moves,
and battle history. Bundled attribution remains `Unknown Author`.

The index searches species, titles, poem text and lessons. Results include a
matching excerpt with the literal search term highlighted, plus the permanent
entry ID. That ID also appears on the journal page and can be searched directly;
it does not change when the favorites filter changes the page counter.
See the [index follow-up checks](../verification/2026-11-08-field-notes-index.md).

## Personal writing and syllable notes

The local composer uses the existing LCD keyboard, with lowercase prose,
punctuation/numbers on Select, and a case switch. Title, poem lines, lesson and
author are editable. An unfinished draft is autosaved; returning to the directory
flushes the last keystroke before navigation. Notes require a title and one poem
line, without enforcing 5/7/5. Species comes from the selected journal page.

Saved personal entries have permanent IDs and revisions. Editing retains their
identity, creation time and favorites. A syllable suggestion is a separate local
annotation against an entry ID, revision, line number and original line; optional
count, pronunciation and replacement wording do not change the original poem.
The personal journal lives in Android's no-backup directory rather than cloud
backup. Bundled entries and the original source remain unchanged.

## Portable trainer exchange

The trainer's own haiku are stored locally. This is a personal notebook with
deliberate exchanges between linked trainers, rather than a public submission
queue. There is no inactive submit button in the first slice.

- Add, edit and save a personal note locally, associated with a Pokémon. Keep
  bundled notes separate from trainer-authored notes and preserve the source
  haiku asset.
- Linking with another trainer should allow reading that trainer's submitted
  notes. A recipient can explicitly save a copy, favorite it, or share it with
  another linked trainer.
- Saved and forwarded copies retain the original author, stable note identity,
  revision and origin. Forwarding must not claim new authorship. Distinguish
  saving a local copy from favoriting a remote note.
- File exchange uses an explicit local sharing list and an incoming preview.
  Drafts and annotations about private personal notes are excluded. A received
  copy is private by default; forwarding is another explicit sharing choice.
- Duplicate IDs/revisions are detected. Newer received revisions require an
  explicit save, and older/conflicting copies cannot replace saved writing.
  Authored notes cannot be overwritten by an incoming package.
- A local save is not a delivered submission. Show transfer state and failure
  honestly. Sharing remains an explicit trainer action; no automatic broadcasts.

The portable format is bounded, versioned UTF-8 JSON (`.odxnotes`). The system
file picker chooses where to export or which package to preview. Local writing
and file export do not publish notes, send messages, or add cloud access rules.
See [portable exchange validation](../verification/2026-11-08-field-notes-exchange.md).

## Nearby exchange acceptance

The existing QR partner repository holds one local linked trainer identity;
Trainer Comms is a separate Firebase-backed messaging system, not offline peer
transport. Nearby journal transfer must use its own foreground-only connection,
filter for that linked trainer, require matching authentication codes on both
phones, and feed the same package parser and explicit import preview. A transport
delivery is distinct from the recipient saving or favoriting a note. Closing or
backgrounding the exchange stops discovery, hosting and connections. No automatic
sharing follows linking, and unlinking does not erase already saved copies.
