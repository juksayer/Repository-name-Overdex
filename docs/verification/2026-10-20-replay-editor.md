# Replay editor — first usable slice

## Entry points and controls

Directory Tree: **BATTLE → Match → Replay Editor**.

In a recorded match, choose **Open Replay**, seek to the desired start,
select **Mark Start**, seek forward, and choose **End + Save**. Supply a
name and optional notes. The existing article-based archive marks also open
the naming dialog.

Replay's Left/Right buttons select Play/Pause, Mark Start, End + Save, and
Clip Library. A performs the selected action; Up/Down step one second.
The LCD can still be dragged to seek. Clip boundaries use the actual cursor
time, including fractional seconds. The mark controls display that time and
the saved start together.

The editor's CRT displays clips and compilation order. Up/Down select an
item; Left/Right select an LCD action; A performs it; B returns. Touch
actions stay on the LCD. Clip names and notes use a standard text dialog.

Add clips to a compilation, including clips from different recordings.
Reorder using Move Earlier/Move Later or long-press and drag the LCD rows.
Remove From Order removes an entry from the draft while retaining its
source clip. Save Compilation stores a named editorial document. Preview
automatically advances between bounded clips and pauses at the final end.
Speed changes carry between clips. Clip notes can be edited from the library.

A draft survives the Open Matches detour. Leaving an unsaved compilation
offers Keep Editing or Discard Changes. Current in-memory edits are not yet
continuously autosaved across arbitrary process death.

## Evidence and persistence

`ReplayExcerpt` remains backward compatible with the original excerpt format.
New optional fields hold creation time, notes, and recovered presentation
identities. Exact in/out timestamps and original article references remain
separate from the source articles.

Internal files:

- `replay_excerpts/*.odxexcerpt.json`: named clip definitions.
- `replay_sources/<sha256>.json.gz`: complete source Timeline snapshot.
- `replay_compilations/*.odxcompilation.json`: ordered clip references.
- `replay_compilation_draft.json`: draft saved before opening other matches.

Snapshots retain every original article, including pre-clip state and later
identity testimony. Rendering evaluates the complete source model at the
clip cursor. Recovered crop identities remain a separate presentation
input. Original matches and battle statistics are unchanged.

Source snapshots are streamed, compressed, content-addressed, atomically
published, and checked by SHA-256 when read. Multiple clips of one archive
share a snapshot. PNGs and audio files are not copied. The 128 MiB
uncompressed source limit is checked before publishing a new clip and
while reading a source.

New clips remain usable after the original archive ages out of newest-50
retention. Older reference-only excerpts protect their source Match IDs
from cleanup until an exact matching source is recovered. Recovery requires
both the original Match ID and original archive hash; a different export
of the same match is not silently substituted.

## Validation

Canonical checkout: `/home/sean/AndroidStudioProjects/Overdex`.

Full `:app:testDebugUnitTest` and `:app:assembleDebug` succeeded using
the checkout's `.jbr`: **959 tests, zero failures/errors/skips**.

Eight new `ReplayLibraryTest` checks cover source-independent reopening,
preserved starting HP state, shared source snapshots, multiple independent
match clocks, repeated clip entries, ordering, boundary rejection,
missing-source rejection, legacy recovery/retention protection, corruption,
renaming, recovered identities, draft persistence, and bounded playback.

Moondrop `MDPH00124112500414`, model `MD_PH_001`, 1080×2400:

- Installed with `adb install -r`; no uninstall or connected test runner.
- Opened the editor through the physical Directory Tree controls.
- Saved an eight-second clip from
  `c12270c6-6aa9-449d-8a4e-a0667fa52936`, including a note.
- Reopened it with the battle state populated at the clip start.
- Kept the compilation draft while opening another archive.
- Saved a six-second clip from
  `7f0cd358-8731-4fa8-9b9f-d3db2d0ea088`.
- Added both to one compilation. Verified physical reordering and
  long-press LCD drag reordering by inspecting the resulting order.
- Saved **Replay editor check**, restarted through an app update, reopened
  it, and observed playback advance from 1/2 to 2/2 and stop at
  `FINISHED`, `00:06.0 / 00:06.0`.
- Visually checked the mark controls with the current time and start time
  visible together.
- Compared all ten calibration files and both pre-existing excerpt files
  against the pre-install backup: byte-for-byte unchanged.
- Verified both new source snapshot hashes. Their sizes are 356,364 and
  374,953 bytes, approximately 714 KiB combined.

One automated navigation sequence ran before the startup navigation settled
and entered pre-flight, selecting a saved Turtonator team. The final settings
comparison caught this. With observation stopped, the app was stopped and
only `current_battle_team.xml` was restored from the pre-install backup,
then verified byte-for-byte. This restores the user's Raichu/Spark current
lineup and leaves the saved-team definitions intact. Navigation checks after
a cold start must inspect the settled Directory Tree before sending taps.

Final APK SHA-256:
`bd8b219df40c2b640b9250bce61a6942c489a991639c23101c5cffb2626004e0`.

Backups, saved clip definitions/source copies, and screenshots:
`/home/sean/overdex-device-backups/2026-10-20-replay-editor/`.
Build output: `/tmp/overdex-replay-editor-final.log`.

## Remaining editor work

This slice saves and plays in-app compilations. Event-search supercuts,
event-anchored coaching annotations, revising an existing clip's in/out
points, continuous draft autosave, external compilation sharing, and video
render/export remain follow-up work. Current notes belong to the whole clip.

These checks validate editorial persistence, controls, and playback
boundaries. They do not establish new accuracy for live battle observation,
species identification, Fast Move counting, or energy inference.
