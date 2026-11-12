# Source-time faint attribution

Scope: assign observed team losses to the correct combatants. No new terminal
hit, zero-HP reading, final player faint, or outcome is inferred for this match.

## Recorded evidence

The regression match is `8c55c9c3-e7c2-40e7-80de-bd7cd529a6f6`, recorded on the
Moondrop on 2026-11-11. The unchanged archive contains 2,642 Articles and 303
artifacts. SHA-256:
`3d9acb465204b5d981a2df21bea56373fdf1de104c28abd44c30292ab24b55d4`.

The checked-in regression fixture contains 682 exact original Articles, including
all active HP observations, identities, team counts, original faint conclusions,
and supported attack evidence needed by Replay. Its adjacent provenance file
records the source archive. Fixture entries were compared field-for-field with
the full archive, including their original source and receipt timestamps.

The video `Screen_recording_20261111_124455.mp4` records live gameplay, not Replay.
It supports these expected attributions:

| Side | Combatant | Resolved state |
| --- | --- | --- |
| Player | Turtonator | Fainted |
| Player | Camerupt | Fainted |
| Player | Gourgeist | Final faint unresolved from archived evidence |
| Opponent | Gourgeist | Fainted |
| Opponent | Carbink | Conscious; supported attacks retained |

## Implementation

`TeamLossResolver` orders independent identity, count, and positive-HP observations
by their captured monotonic times. A count decrease remains a team loss even when
the responsible combatant is unknown. All possible actors between count readings
remain candidates, including the actor present at the later reading. Later
independent positive HP rules out a candidate; compatible single-loss assignments
must not faint the same species twice.

Repeated counts and noisy increases cannot manufacture another loss. Count jumps,
missing initial identity, contradictory identities, and conflicting counts remain
unresolved when they prevent a unique attribution. An initial identity gap can be
covered only when all three distinct team members are already known by the loss.
Species names with parenthetical forms use consistent base-species matching.

Live HUD faint flags are replaced from the current resolution, allowing a late
observation to clear a wrong fade without changing the active opponent or raw
resource measurements. The live witness retains the count image's capture clock
when later evidence permits a named conclusion.

Count-derived exclusions in Fast Move use, cadence, and energy tracking also
reconcile from the raw history. If a previously blocked return is later supported
by positive HP, those consumers retry the original identity with its original
clock. An old named faint conclusion cannot independently close the restored
actor. Legacy explicit non-count faints retain their prior behavior.

Replay uses the same resolver in an ephemeral projection. The original archive
and its public Article list remain intact. Resolved faints attach to their exact
earlier appearance; an animation fitted before a replacement has an explicit
`RECONCILED_TEAM_COUNT_LOSS` basis and original source evidence IDs. HP, resources,
actions, markers, and faint poses consume that same projection.

Adjacent bare/form name refinements share a knockout interval. Separately
configured forms and actual departures retain their distinct appearances.

## User clarification about size

The player clarified that this Gourgeist is XXL. Pokémon GO's observed label in
the recording is simply `Gourgeist`; it does not establish `Average` or another
size. The archived `Gourgeist (Average)` label came from the saved team reference
(`CURRENT_TEAM_CONFIGURATION`, with `ownedPokemon = null`), then propagated into
derived identity names. That reference is not independent size evidence.

Battle presentation uses the base Gourgeist/Pumpkaboo name rather than a
catalogue size suffix. Explicit specimen size remains separate metadata and is
shown only when supplied. Canonical catalogue lookup keys and the original
archive stay unchanged; XXL is not automatically translated into a catalogue
form. Internal default reference statistics must not become proof of exact size
or lethal damage in future inference work.

## Validation

The final implementation, including the size-label correction, passed the full
2,475-test unit suite with zero failures, errors, or skipped tests. The debug APK
build also passed. Validation used the complete original archive's 2,642 Articles
for Replay scene, faint-marker, supported-attack, display-name, and unchanged-source
assertions. Delayed and reversed observations, ordinary switches, repeated
counts, ambiguous losses, and later survival evidence are covered by the suite.

The final archive SHA-256 matches the original above. Whitespace validation also
passed. Build and test log: `/tmp/overdex-faint-size-final-validation.log`.

This verifies saved evidence interpretation and Replay scene state. It does not
establish new live capture accuracy or physical-device processing cost. No build
was installed as part of this step.
