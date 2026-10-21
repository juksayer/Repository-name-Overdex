# Trainer Comms: replay sharing and group conversations

Direction agreed October 20, 2026. This checkpoint records the plan; it does not
implement or deploy the sharing service.

## Product home

Use **Profile → Trainer Comms** for trainers to exchange messages, match recordings,
clips, compilations, and coaching feedback. Support conversations with more than
one trainer, including coaches. Future tournament coordination should use the same
conversation and membership system.

Do not revive Trainer Timeline as a separate screen. Review the legacy Shared
Timeline's useful sharing duties and migrate them into Trainer Comms before
retiring its remaining navigation and storage. Match Sight is independently
removed in this checkpoint; the Reality Timeline remains the battle evidence
record and is not a social feed.

## Existing implementation

- [ChatMessage](../../app/src/main/java/com/example/overdex/model/ChatMessage.kt)
  supports text, system messages, and shared Pokémon records. It has no match or
  clip attachment type.
- [FirebaseChatTransport](../../app/src/main/java/com/example/overdex/data/FirebaseChatTransport.kt)
  derives a conversation ID from two trainer IDs. Group membership and attachment
  transfer are not implemented. Its current send path starts a Firestore write
  without awaiting completion, so successful delivery also needs explicit handling.
- [Replay Editor](../verification/2026-10-20-replay-editor.md) already saves clips,
  ordered compilations, notes, and independent source snapshots locally. Those
  files are not yet a portable sharing package.
- Existing archive import/export and Replay are the foundations for opening a
  shared `.odxmatch`; do not create a second battle-record format inside chat.

## Proposed implementation order

1. **Conversation foundation.** Give conversations stable IDs and explicit member
   lists. Preserve existing direct-message history while adding group names,
   membership, and trainer/coach conversations. Show pending, sent, and failed
   delivery accurately; keep a failed send available for retry.
2. **Share and open a match.** Select an existing `.odxmatch` in Trainer Comms,
   upload it once, and attach a reference to the message. Track name, format,
   size, hash, sender, and source match ID. Show transfer progress and open a
   validated received recording in the existing Replay workflow. Enforce
   conversation membership for messages and file access; test with two accounts
   and a group before calling it ready.
3. **Share clips and compilations.** Package clip boundaries, notes, compilation
   order, and the required source snapshots together. A recipient must be able
   to play the selection without the sender's local files. Preserve original
   testimony and keep coaching annotations separate from battle facts. Prefer
   replay data for normal sharing; include retained images/audio only when chosen
   and available. This does not imply video export is already implemented.
4. **Coaching and tournaments.** Add feedback linked to clips/events. Later connect
   tournament participants, announcements, scheduling, and organization to these
   conversations. Tournament-specific structure remains a separate feature to
   design, rather than a reason to restore the old Timeline UI.

## Boundaries

- The current step is the source checkpoint, not implementation of this entire
  plan. No messages are sent and no cloud configuration is changed by recording it.
- Sharing a recording must not rewrite its source evidence or silently add it to
  the recipient's own battle statistics.
- Keep local playback available after download and make missing attachments or
  sources explicit. Do not report a mocked/local send as delivery to a trainer.
- Registration remains paused until the user chooses to resume it. Battle
  recognition and energy accuracy remain tracked in the
  [current feature status](../reference/current-feature-status.md).
