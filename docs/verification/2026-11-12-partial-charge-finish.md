# Terminal KO support from a partially read charged-move announcement

The first match in `Screen_recording_20261111_190345.mp4` has independent final
incoming-phase and used-move observations, but the exact move name was clipped.
Requiring a fully named move and its reference energy cost unnecessarily blocked
the otherwise corroborated player Gourgeist finish.

## Original evidence

The unchanged first-match archive fixture contains:

| Relative to Match Start | Original source | Observation |
| --- | --- | --- |
| 139.663845 s | ATTACK_INCOMING_WITNESS | Incoming charge phase |
| 146.685900 s | ANNOUNCEMENT_WITNESS | `Ninetales used Weather` |
| 146.685900 s | CHARGE_MOVE_USED_ANNOUNCEMENT_WITNESS | Typed Used cue from that capture |
| 147.274134 s | ANNOUNCEMENT_WITNESS | `Ninetales used Weather B- r` |
| 155.787738 s | GOOD_EFFORT_WITNESS | Accepted loss result |

The incoming cue is Article `8a31887b-3bd7-4c94-a7d4-1932de5767a0`.
The first partial announcement is `0b803bc5-6c5e-4d59-9fdd-c5388e5e1aa8`;
its typed Used cue is `82303c29-d132-4e8a-9355-9b5c207223d5`.
These preserve original capture clocks despite delayed recognition. The raw
text and its typed Used cue share a source and are not treated as two attacks.

## Change

`TerminalChargedFaintResolver` retains its fully named-charge path and adds a
separate qualified partial-announcement path. It requires an exact match between
the announcement's performer and the independently identified opposing actor,
a same-clock native typed Used cue, and the latest independently witnessed
incoming/Get Ready direction within the existing 20-second phase budget.
Contradictory simultaneous performers or directions remain unresolved.

The existing final-count, correctly owned contraction/clear sequence, result,
phase, continued-play, positive-HP, switch and survival checks remain required.
The shared resolver cites the additional evidence and retains the clear frame's
source time. Replay now restores the typed Used cue to the same input vocabulary
used by live tracking.

This creates no named charged-move event, energy spend, exact damage amount,
native zero-bar reading or ball-count observation. A supported KO may provide
an inferred depleted HP endpoint; earlier measured HP remains unchanged.

## Recorded checks and limits

The existing recorded appearance fixtures are added only to explicitly
test-enriched archive copies. The first Gourgeist ending resolves at
Match Start +150.836066666 s using approximate video/GO alignment, also checked
at ±1 second. Forward, reversed and shuffled publication orders agree for live
history, faint tracking and Replay. The original archive without appearance
measurements remains unresolved; serialized original and enriched inputs are
unchanged, and the count of named charged-move/energy events does not increase.

Missing Used/direction evidence, stale or opposite direction, wrong or absent
performer, non-native source and conflicting same-clock claims are rejection
cases. The outgoing Cramorant archive is a useful additional boundary: deleting
its named charge does **not** make its stale Get Ready sufficient. A separately
labeled test-only fresh Get Ready exercises the outgoing partial-proof branch;
it is not described as evidence actually captured in that recording.

Fresh live-battle acceptance remains pending. This change does not repair the
separate missing final Fast Move or replace the appearance pixel reader.

Build/test and installation evidence is retained in
[/home/sean/overdex-device-backups/2026-11-12-partial-charge-finish](/home/sean/overdex-device-backups/2026-11-12-partial-charge-finish).

## Build and Pixel installation

The final verification passed 2,873 unit tests across 379 suites, with zero
failures, errors or skips, and `:app:assembleDebug` succeeded. On resume on
2026-11-12, the APK and the four changed source/test file hashes still matched
`build-validation.json`; the saved XML results were re-counted before installation.

Installed with data-preserving replacement on Pixel 4 `99261FFAZ0017J` only.
The connected Moondrop was not modified. Droidball and MediaProjection were idle
before replacement. Installed APK SHA-256:
`ec5b42f02e0d88480f25e4ece4cc8e07c9d1e799cb3fc3a1a743545d9fc6ddd3`.
The installed package hash matches the verified APK, the main activity returned
`Status: ok`, the home screen was visually inspected, and the launch log check
found no AndroidRuntime/FATAL/Overdex ANR entries.

A fresh readable backup protects all 109 files in the selected archive, record,
calibration, preferences and database directories. After installation and launch,
108 files remain byte-identical, with no missing or added files. This includes
all 32 archives, 51 battle records, three calibration/reference files, all 16
preferences files and six of seven database files. The remaining Firestore
database differs only in its `targets` and `target_globals` tables: listening
sequence, snapshot and query-resumption metadata changed on launch. Every other
table, including stored documents, has identical schema and row-content hashes.
The loose artifact store was not included in this bounded backup; no device data
cleanup or instrumentation tests were performed.

Evidence includes `pixel-resume-preinstall.json`, `pixel-install-status.json`,
`pixel-install-data-check.json`, `pixel-install-database-check.json`, the launch
log checks and `pixel-after-install.png` in the proof directory above. The Pixel
was left on the Overdex home screen for the user's test. A fresh recorded battle,
especially a last-Pokémon charged KO without a visible empty outline, remains the
acceptance check; installation success does not establish recognition success.
