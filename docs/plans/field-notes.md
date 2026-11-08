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

## Next slice: personal writing and trainer exchange

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
- Reuse the existing Trainer Comms identity/linking infrastructure after auditing
  its current capabilities. Define access, offline behavior, duplicate/revision
  handling, and what happens after unlinking before implementing exchange.
- A local save is not a delivered submission. Show transfer state and failure
  honestly. Sharing remains an explicit trainer action; no automatic broadcasts.

Composition and trainer exchange require their own implementation and validation.
The first slice does not publish notes, send messages, or add cloud access rules.
