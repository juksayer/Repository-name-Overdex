# Current feature status and remaining work

Reviewed October 20, 2026 against the working tree based on `1ba1c13`.
This includes local untracked source. Source inspection establishes implementation
boundaries; it does not establish live recognition accuracy.

## Where to find the plans

- [Original roadmap](../../DexDox/roadmap.md): historical milestone plan. Its
  "next" lists predate implemented teams, binders, archives, and replay.
- [Unfinished-code inventory](../../UNFINISHED_CODE_INVENTORY.md): cleanup,
  legacy architecture, and explicit TODOs. Entries retain their own review dates.
- [Architecture map](../../PROJECT-MAP-v2.md) and
  [navigation index](overdex-navigation-index.md): current ownership and entry points.
- [Verification reports](../verification/): dated results and remaining acceptance
  boundaries. A later report can supersede an older report's open issue.
- [Trainer Comms sharing plan](../plans/trainer-comms-sharing.md): the agreed home
  for match/clip sharing, group conversations, coaching, and future tournament
  coordination. This direction is recorded, not implemented by the checkpoint.
- `DexDox/unfinished.mds/` and `.artifacts/`: historical outlines and generated
  work plans, not a current prioritized backlog.

## Match Sight retired

The user requested removal on October 20. The screen only displayed static
"No active diagnostics" and "Waiting for Battle..." text. It had no reader or
Timeline consumer. Removed its screen, navigation command and route, shell
callback, and entries/focus targets in Signal Observatory and Researcher Mode.
Signal Observatory, Timeline Viewer, Match Calibration, and Replay remain.

## Important unfinished or partial features

| Area | What exists | Remaining work and evidence |
| --- | --- | --- |
| Type and regional-form identification | Species OCR, active-type badge matching, reference species/moves, and configured teams. | Preserve form identity through owned cards, recognition, inference, and replay. Fix and validate badge glyph scaling before using it to eliminate moves. The [paired-match review](../verification/2026-10-20-paired-self-match-review.md) documents Alolan Raichu treated as ordinary Raichu and incorrect type matches; the current [type matcher](../../app/src/main/java/com/example/overdex/battle/observation/PokemonGoTypeIconMatcher.kt) still uses the inspected normalization. [OwnedPokemon](../../app/src/main/java/com/example/overdex/model/OwnedPokemon.kt) carries species ID but no explicit regional-form field. |
| Fast Move counts and energy | Independent HP/pulse/rhythm/visual/audio witnesses, fused uses, candidate move inference, measured accounting, and a separate live forecast. | Establish count accuracy during attacks, deliberate inactivity, switches, QTEs, and connection stalls using paired recordings. Do not treat forecasted attacks as observed events. Visual effects currently supply transient measurements/cadence, not a recognized move type: see [LiveActiveHpBarWitnesses](../../app/src/main/java/com/example/overdex/battle/observation/LiveActiveHpBarWitnesses.kt). Audio reference matching exists; it is not a missing pipeline. |
| Charged-move ownership and spending | Announcement interpretation, player controls, charge sequences, and energy-spend derivation. | Resolve damaged announcements and mirror-match actor ambiguity without assigning the wrong side. The [paired-match review](../verification/2026-10-20-paired-self-match-review.md) records missed spend events on one phone and ambiguous Obstagoon Cross Chop. These remain validation targets, not proof that every current recording fails. |
| QTE type recognition | Live Draggy Box, saved calibration, automatic bounded samples, and preserved QTE crop references. | Add and validate the classifier that turns those samples into cited type testimony. The current [archive selector](../../app/src/main/java/com/example/overdex/battle/archive/MatchArchiveArticleSelector.kt) explicitly preserves QTE samples without a classifier; [PokedexViewModel](../../app/src/main/java/com/example/overdex/ui/PokedexViewModel.kt) wires their capture. Do not confuse active-type badge matching with QTE-icon recognition. |
| Replay editor | Named clips, whole-clip notes, independent source snapshots, ordered compilations, reordering, and bounded in-app playback. | Event-search supercuts, event-attached coaching notes, editing existing clip in/out points, continuous draft autosave, external compilation sharing, and video rendering/export. See [editor verification and remaining work](../verification/2026-10-20-replay-editor.md) and [ReplayEditorScreen](../../app/src/main/java/com/example/overdex/ui/screens/observatory/ReplayEditorScreen.kt). Draft persistence currently covers the Open Matches detour, not every edit before process death. |
| Confidence and OCR review | Nullable article scores, honest "Not scored" display, method-specific matching scores, and an offline crop review desk. | Measure correctness on held-out, human-labeled matches/devices before calling scores probabilities. Review labels are separate evidence, not automatic ML Kit retraining. See [confidence review](../verification/2026-10-20-confidence-and-ocr-review.md); the [correction follow-up](../verification/2026-10-20-ocr-corrections-and-calibration.md) found crop position problems rather than a reason to blindly add text aliases. |
| Assisted registration — paused | One adjustable field layout, explicit field reads, cry assistance, draft review, and saving an owned card. | Automatic scrolling/layout learning and automatic classification of manually confirmed image fields. [GuidedRegistrationController.readField](../../app/src/main/java/com/example/overdex/data/registration/GuidedRegistrationController.kt) still asks the user to confirm image icons. Keep the user's pause in effect while battle reliability is prioritized. |
| Trainer Comms sharing | Pairwise text/system/Pokémon messages and local Replay Editor clips/compilations. | Add group conversations, reliable delivery, `.odxmatch` attachments, and portable clips under Profile → Trainer Comms. The current transport uses two trainer IDs; it has no file transfer or group membership. See the [agreed sharing plan](../plans/trainer-comms-sharing.md). |

## Smaller gaps and deferred product decisions

| Area | Current source evidence | Next decision |
| --- | --- | --- |
| Owned-card detail | [SpecimenDetailScreen](../../app/src/main/java/com/example/overdex/ui/screens/SpecimenDetailScreen.kt) renders `PlaceholderContent()` for Timeline, League Eligibility, and Notes. Battle History and team memberships have real wiring. | Implement or remove these unfinished sections. Notes also need a persisted field/editor; binder restrictions already exist and are a separate feature from this placeholder. |
| Pokédex artwork | [PokemonDetailScreen](../../app/src/main/java/com/example/overdex/ui/screens/PokemonDetailScreen.kt) has a long-press TODO. | Choose an actual action, or remove the obsolete TODO. It is not evidence that the Pokédex itself is unfinished. |
| Android backup/restore | The manifest enables backup; [data_extraction_rules.xml](../../app/src/main/res/xml/data_extraction_rules.xml) and [backup_rules.xml](../../app/src/main/res/xml/backup_rules.xml) remain sample templates. | Define treatment of calibration, owned cards, teams, archives, caches, and machine/device-specific state, then test restore. No backup behavior was changed in this review. |
| Tournaments | The old roadmap lists them; `OpenTournament` remains a command with no producer or handler found. | Future organization should build on Trainer Comms conversations and membership. Tournament structure itself is not implemented. |

## Maintenance, not new features

- **Git checkpoint repaired:** at the start of this review, required production
  source, test fixtures, and assets remained untracked after `1ba1c13`. For example,
  tracked `ArchiveDirectoryManager` calls untracked `MatchArchiveRetention`, and
  tracked `FastMoveSoundMeasurement`/`FastMoveCadenceInference` use untracked
  `FastMoveReferenceMatcher`. The subsequent checkpoint tracks those dependencies
  and verifies an exported staged tree: 959 passing unit tests, app APK, and
  device-test APK. Ten images required by existing tests are now curated fixtures;
  raw diagnostics remain local. See [checkpoint validation](../verification/2026-10-20-source-checkpoint.md).
- **Shared Timeline migration:** still has a repository, screen, and MainActivity
  route. The user's October 20 direction puts sharing inside Profile → Trainer
  Comms, including groups, coaches, and future tournament organization. Migrate
  useful duties there before retiring the old surface; do not revive a separate
  Trainer Timeline. This checkpoint only removes Match Sight.
- **Legacy models:** overlapping Observation/Timeline families and the diagnostic
  recorder remain. Migrate actual callers rather than deleting names that look
  redundant. See the [legacy inventory](../../UNFINISHED_CODE_INVENTORY.md).
- **Unused older SpeciesObserver:** its TODO only logs an observation instead of
  publishing it. No construction/call site was found in `app/src` or `tools`.
  The working species path is `LiveOverlaySpeciesPipeline` / persisted Witnesses;
  do not diagnose live species failures from this unused class.
- **Small cleanup candidates:** malformed `simpleCurrentState.kt.kt`, source-tree
  diagnostic logs, and the unused `ObservationPipelineDemo` remain listed in the
  inventory. They were not deleted. The earlier inventory's empty
  `GoodEffortWitness/kt.java` is already absent and is no longer a pending item.
- **Test-only TODOs:** `PokemonRepositoryTest` still has unused fake-DAO methods
  implemented with `TODO()`. These are test-double gaps, not production methods.

## Suggested order

1. Source/assets checkpoint completed and validated from an exported Git tree;
   future work can build on that baseline.
2. Correct type/form identity, then validate Fast Move counts and energy with the
   controlled paired matches, including deliberate idle periods.
3. Add QTE type testimony and improve ambiguous charged-move attribution.
4. Extend the replay editor into searchable supercuts and event annotations.
5. Resume assisted registration when the user chooses; keep release policy and
   legacy cleanup as explicit tasks rather than mixing them into battle fixes.

This ordering is a recommendation, not authorization to implement all items.

## Match Sight removal validation

- Removed all Match Sight references from production/test source; refreshed
  `CODEBASE_INDEX.md` and checked the edited documentation's local links.
- Full `:app:testDebugUnitTest`: 959 tests, zero failures/errors/skips.
- `:app:assembleDebug`: successful. Build log:
  `/tmp/overdex-match-sight-retirement.log`.
- No new tests were added for the removal of the static screen. The existing
  suite and Kotlin compilation cover its deleted call sites and navigation types.
- No phone installation or device navigation check was performed in this task.
- No other feature was implemented or retired by the removal. The subsequent
  checkpoint and its validation are documented separately.
