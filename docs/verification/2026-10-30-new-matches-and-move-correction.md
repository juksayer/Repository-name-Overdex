# New Moondrop matches and move correction — October 30, 2026

Three previously unreviewed archives were copied from Moondrop
`MDPH00124112500414`; desktop SHA-256 values match device originals. Inspection
verified article counts and all declared artifact hashes:

| Match | Articles | Artifacts | Recorded result |
| --- | --- | --- | --- |
| `942e9091-a252-418c-ac38-2d60a80b7c3b` | 221 | 0 | No battle/result; session stopped |
| `d6b41423-8147-4ad3-bcbf-79b8015e8d7a` | 2,894 | 338 | LOSS, about 2.989 s after result source |
| `b252d3b6-fc02-4a48-8ae3-f138bbdce188` | 4,381 | 546 | WIN, 2.603 s after result source |

Both battle recordings resolve Golbat, Greninja and Victreebel in the player's
observed roster. Each contains only a GO countdown glyph, with MatchStarted
derived from that source. The first opponent team is Salazzle, Mimikyu and
Morpeko; the second is Forretress, Clodsire and Chandelure.

The result reader improvement has live support in both recordings; see
[the result queue verification](2026-10-30-result-reading-queue.md). Neither
archive establishes complete attack totals. Recorded Fast Move uses number 37
and 46 respectively, and the latter includes one unresolved player actor.

## Greninja correction and history

The user reports entering Hydro Cannon in error and identifies Night Slash as
the actual move. The newest match independently announces Greninja using Night
Slash at source +107.228, +130.810 and +201.214 seconds; charged-energy spending
is recorded for Night Slash. The saved configuration snapshot says Hydro Cannon
and Aerial Ace.

This is a correction to the current specimen's loadout, not a new specimen.
Greninja's owned-card ID is the same key used by historic battle snapshots and
specimen queries. Editing moves must retain that ID, its team position, binder
membership and existing history. Historic configuration testimony must continue
to record what was entered then; the correction does not rewrite old archives.
The card editor change and preservation tests are described in
[Current Team move editing](2026-10-30-current-team-move-editing.md).

The newest match also announces Victreebel using Leaf Tornado while the saved
configuration says Acid Spray / Leaf Blade. Its later Fast Move inference changes
from configured Razor Leaf to Magical Leaf. The charged announcement and the
Fast Move inference are different strengths of evidence. The user confirmed
Razor Leaf, Acid Spray and Leaf Tornado. The phone's Pokémon GO detail screen
also displays those three moves. The later Magical Leaf conclusion is therefore
an incorrect Fast Move inference; it must not replace the saved Razor Leaf.

## Presentation and calibration follow-up

The user reports the party overlay lingering across the countdown. The missing
3/2/1 observations support investigating that timing but do not alone prove visual
occlusion. League shield classifiers produced MASTER_LEAGUE and ULTRA_LEAGUE;
the preserved full frames actually show MEGA HALLOWEEN CUP: GREAT LEAGUE EDITION
and Max CP 1,500. The league classifier's color-only readings are wrong for this
seasonal layout. The source frames show that title and CP limit unobscured, but
Droidball's old separate body row overlaps the eligible-type icons. The setup
fix moves that body into the menu's existing header space. Seasonal badge/league
classification remains a separate observed issue; the new screenshot library
does not itself repair that classifier.

Original archives and their testimony remain unchanged. Private evidence,
checksums and a machine-readable review summary are under
`/home/sean/overdex-device-backups/2026-10-30-party-menu-and-calibration/`.

## Validation

The three coordinated changes (Current Team corrections, setup dismissal, and
calibration reference selection) passed 67 focused tests and the full 1,547-test
unit suite with zero failures/errors/skips. The debug APK built successfully.
An initial focused build was cancelled by a client disconnect after compilation;
the subsequent focused and full executions completed normally.

Installed with data preserved on idle Moondrop `MDPH00124112500414`, physical
1080 × 2400. No Droidball recording was active before replacement. Installed
APK SHA-256 equals the tested build:
`d3e00df889096b7e6dc581cbebbbbcc6bfa97cdb6b2ae2230739caf30789dd62`.

Physical checks saved both move corrections through the new editor, returned to
Current Team, and verified persisted card UUIDs/memberships/other fields and
owned-card count remained unchanged. The matching named shadow team was
explicitly replaced with this current lineup, retaining its saved-team ID;
unrelated named teams and the original archive hashes remain unchanged. Raw
past configurations continue to show the original entries.

Hold A opens the native screenshot library on the phone; This screen and All
filters were exercised, a named 1080 × 2400 reference was selected, and This screen
was restored with the profile still SAVED. New multi-device screenshot
packs and countdown dismissal during a fresh battle remain separate acceptance
checks. No battle was played or calibration profile saved during these checks.
