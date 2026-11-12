# Player terminal coverage and stable team-count support

Status: the production pixel/sequence path misses the reviewed player endings.
An independent count-expiry defect is repaired; this does not resolve the missing
visual evidence. Fresh live opponent acceptance and player-side recognition work
both remain outstanding.

## Actual production-reader scan

Read the existing November 11 `190345` and `124455` recordings at their original
PTS, selecting at most 20 frames per second. Resize with INTER_LINEAR to 672×1493
and use the current factory player territory (0,522)–(417,1463). These integer
bounds follow `BattleCropResolver`'s floor/round rules. The compiled production
`TerminalParticleFrameMeasurer` receives exact ARGB crops through the host pixel
callback; `TerminalCollapseSequences` then consumes those numeric outputs.

| Recording | Selected frames | Frames with a candidate cluster | Frames with bright components | Ordered collapse sequences |
| --- | ---: | ---: | ---: | ---: |
| 190345 | 3,137 | 18 | 566 | 0 |
| 124455 | 2,803 | 3 | 624 | 0 |

The `190345` video includes two final player charged KOs: Gourgeist following
Ninetales's Weather Ball around 170 s, and Camerupt following Stunfisk's Mud Bomb
around 358 s. The `124455` recording includes the final player Gourgeist departure
around 195–197 s and living-recall/camera controls. Absence of a qualified sequence
is a known missed-detection case, not evidence that these Pokémon survived.

The scan bypasses phone attention/overlay gates. It proves a pixel/sequence
coverage limitation even when these selected video frames are supplied; it does
not measure phone cost or prove how a denser live feed would behave. Video
compression and sparse source timestamps remain limits. All original sources and
archives are unchanged.

## Perspective diagnosis

Exact source frames show large foreground sparkles followed by a collapse close
to the left edge. The production reader emits no four-point cluster during either
final charged interval. An independent diagnostic retains individual candidates
before grouping, rather than treating the lack of a group as no particles.

For the Gourgeist finish, the main bright component at video 170.555 s has area
5,688 pixels and density about 0.39. The later peak at 170.687 s has area 21,377 and
density about 0.39. Both fail the current compact-flash shape constraints; the
larger peak also exceeds the area ceiling. These values describe decoded,
capture-sized video pixels, not new live observations or a qualified sprite mask.

Diagnostic variants enlarged the particle area ceiling from 1,000 to 4,000,
the rim from 3 to 6 pixels, and the grouping radius from 0.14 to 0.28 or 0.42
source widths. They still do not provide four qualifying points during these
finish windows. No variant is installed. Merely increasing the crop or one
threshold is not a demonstrated repair, and this study does not authorize
equating a few bright objects with a faint.

The first archived final announcement also contains only `Ninetales used
Weather`; it has a generic charged-use cue but no final named
`ChargedMoveEnergySpent` record. Better visual measurements alone would therefore
not make that original archive satisfy the named-hit requirement. The second
archive does retain Stunfisk's named Mud Bomb. Preserve these as distinct missing
inputs rather than filling either from a human video label.

## Count-expiry correction

The final charged resolver previously discarded a last-Pokémon count after
30 seconds. That conflicts with the event-driven reader's instruction to sleep
after a stable count until an appropriate battle event wakes it.

The second archive's last published independent player count is one at Match
+114.912054238 s, Article `cd67d8f0-e95c-4185-8d76-fd46c23519a2`. At the reviewed
final sparkles, using the second video's approximate GO alignment at 211.063 s,
that source is about 31.9 seconds old. Even a future correctly measured collapse
would have faced an unrelated age rejection.

`TerminalChargedFaintResolver` now allows that original count to remain supporting
history within the same Match. It still requires the named opposing hit, correct
identities, qualifying source-ordered visual sequence and matching result, and
rejects resets, contrary counts, new phases, switches and survival evidence.
The old reading is not republished, assigned a newer time, used as a new current
count, or converted into zero. See TEAM-15 in the battle rulebook.

A regression extends an existing last-Pokémon appearance by 60 seconds without
adding a count reading: the supported terminal conclusion survives; a reset or
contradictory surviving count still prevents it. A second regression appends the
actual player-video measurements only to a test copy of the second archive.
Because the visual sequence is unqualified, it creates no final Camerupt faint
or zero-HP endpoint. The original source-time HP and unknown terminal HP remain
unchanged. This explicitly records the remaining coverage gap.

## Verification and Pixel installation

The final focused archive checks passed all 16 tests. The subsequent full
`:app:testDebugUnitTest :app:assembleDebug` run passed 2,859 tests across 376
suites, with zero failures, errors or skips. `git diff --check` also passed.
Reports and the build log are retained with the study below.

Installed the resulting debug APK on Pixel 4 (`99261FFAZ0017J`) after confirming
that Droidball and MediaProjection were stopped. Installation and a cold launch
completed successfully. The installed APK SHA-256 matches the built APK:
`ae6e0ce15bc2c5f63e736354ade141f07b3cde44b452e312b78b957e049c68ac`.
All 102 checked files under `files/match_archives`, `files/battle_records`,
`files/calibration_profiles`, `files/calibration_reference` and `shared_prefs`
had unchanged hashes after installation and launch; none were added or removed
in those locations. This check does not cover every private database or establish
live battle accuracy. No capture session or battle was started for the user.

## Reproduction and next recognition work

Sources, hashes, selected frame PTS, raw numeric output, compiled reader
provenance, Java sequence runner, diagnostic scripts and reports are retained in
[/home/sean/overdex-device-backups/2026-11-12-player-terminal-controls](/home/sean/overdex-device-backups/2026-11-12-player-terminal-controls).
`player-factory-scans.json.gz` retains the 5,940 numeric samples as a regression
resource. Synthetic sequential publication IDs and video-relative wall clocks
are test transport metadata, not original phone capture metadata.

The next visual change needs player-perspective evidence of an owned collapse,
including the foreground's large/spread-out effects and edge visibility, tested
against living switches, entry, camera changes, QTE and Shadow aura. A new dense
live sequence can establish whether the brief core was absent from these sparse
recordings or is systematically rejected. Missing pixels, a loss screen or an
estimated lethal hit alone must not supply that sequence.

Follow-up: the [foreground appearance feasibility study](2026-11-12-player-appearance-contraction.md)
finds both reviewed final charged endings without relying on an empty bar or
bright core, but also exposes living-switch and bright-attack counterexamples.
It remains an offline proposal with explicit visibility/ownership limits.
