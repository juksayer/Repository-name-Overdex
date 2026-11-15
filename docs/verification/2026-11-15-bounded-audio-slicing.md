# Bounded retained-audio windows

Replay's retained-audio selection now includes up to 64 earlier clips near
source-timed changes in accepted species identity, in addition to up to 64
ending clips. The selection reports potentially exhausted bounds; an identity
is a search cue, not proof of an entry or a cry. It accepts eligible original
playback clips up to 16 seconds and verifies each clip's hash and metadata.
Mono audio is scanned in 1.2-second windows with 600 ms overlap. Fragment
positions are translated back to the original clip clock and article ID. Existing
onset clustering prevents overlapping windows from counting the same cry twice.
These bounds do not claim full coverage of all appearances or all audio.

A four-second window initially exhausted JVM heap with the full reference pool;
the smaller windows passed the saved-archive check. Synthetic tests cover a
cry crossing the overlap and verify original provenance and no duplication.
The Vaporeon archive was checked using the production loader with its existing
competitor and confidence guards. Neither cry was accepted and no final KO
association was created. The existing living-switch control must remain a veto.
No archive or original audio is rewritten; tests and logs are on the backup
drive in `2026-11-15-moondrop-0931-review/`.

The earlier full run exposed a Seed Bomb fixture failure: an unclassified
positive post-cry HP reading did not veto the link. The same failure occurred
on the unchanged baseline. The association now withholds a definitive link when
such a source-timed, geometrically plausible reading is present; it does not
classify that reading as confirmed live HP or change the displayed HP. Explicit
low-confidence classified readings retain their existing separate treatment.
Focused tests with both saved archives and living-switch controls passed after
this change. The Vaporeon cries and finishing Incinerate still need stronger
accepted independent evidence; no KO association was fabricated.

Final verification: 3,075 unit tests, zero failures/errors and seven skipped,
including both recorded external archive checks; debug build passed. The build
was installed and launched on the connected Moondrop. This establishes neither
phone recovery speed nor live-match accuracy. The workspace contained a separate
pre-existing uncommitted `battle_haiku.json` edit, which was not changed or
staged by this work; the workspace-built APK includes that current asset.
