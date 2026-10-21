# Trainer Comms: replay sharing and group conversations

Direction agreed October 20, 2026. The source checkpoint is complete.
Step 1 is implemented: account-based direct/group conversations, trainer-code
discovery, saved history, and acknowledged delivery. Email/password sign-in and
membership rules are deployed to the existing Firebase project. Match and clip
attachments remain steps 2 and 3.

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

## Current implementation

- [TrainerCommsScreen](../../app/src/main/java/com/example/overdex/ui/screens/TrainerCommsScreen.kt)
  is accessible from Profile regardless of legacy partner linkage. Its CRT displays
  history; the LCD and physical buttons operate conversations and the composer.
- [TrainerCommsServices](../../app/src/main/java/com/example/overdex/comms/TrainerCommsServices.kt)
  requires a Firebase email/password account. Email is private. Trainers discover
  one another using their **12-digit Pokemon GO trainer codes**, preserving leading
  zeroes and displaying three groups of four. Codes can be rerolled; conversations
  belong to the stable account ID, not the mutable code.
- A trainer code is self-reported. Overdex enforces uniqueness of the claim, but
  cannot verify ownership of the Pokemon GO account. Passwords are handled by
  Firebase Authentication and are never stored in the conversation/profile store.
- [CommsRepository](../../app/src/main/java/com/example/overdex/comms/CommsRepository.kt)
  persists separate account/conversation histories, drafts, and an outbox. Pending
  means unacknowledged; sent means saved online, not read by every recipient. Failed
  or interrupted sends retain their IDs for idempotent retry. There is no mock
  transport fallback on this route.
- Groups support up to eight registered trainers. Names and membership are fixed
  at creation in this first version. Member management, leaving/blocking/reporting,
  invitations, pagination/retention and production-scale abuse controls are later work.
- Old `overdex_chat_prefs` records are retained intact and copied once into a
  read-only history for the first account used on that device. Old records lack
  recipient and delivery metadata, so those fields remain unknown. Legacy pair-ID
  cloud documents remain denied; the former transport and ChatScreen are no longer
  routed from MainActivity. Shared Timeline has not been deleted.
- [Replay Editor](../verification/2026-10-20-replay-editor.md) already saves clips,
  ordered compilations, notes, and independent source snapshots locally. Those
  files are not yet a portable sharing package.
- Existing archive import/export and Replay are the foundations for opening a
  shared `.odxmatch`; do not create a second battle-record format inside chat.

## Implementation order

1. **Conversation foundation — implemented.** Give conversations stable IDs and explicit member
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

- Account creation and cloud messaging require network access. Cached history and
  drafts remain readable for the signed-in account while offline. Signing out hides
  that account’s cached history. No real messages were sent during development;
  integration tests use disposable local emulator accounts only.
- Sharing a recording must not rewrite its source evidence or silently add it to
  the recipient's own battle statistics.
- Keep local playback available after download and make missing attachments or
  sources explicit. Do not report a mocked/local send as delivery to a trainer.
- Registration remains paused until the user chooses to resume it. Battle
  recognition and energy accuracy remain tracked in the
  [current feature status](../reference/current-feature-status.md).
