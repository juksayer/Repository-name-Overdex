# Current feature status and remaining work

Reviewed October 20, 2026; Trainer Comms updated after checkpoint `474bd5f`.
This includes local untracked source. Source inspection establishes implementation
boundaries; it does not establish live recognition accuracy.

## Where to find the plans

- [Battle priorities](../plans/battle-priorities.md): current implementation order
  agreed October 21, ranked by benefit before, during, and after battle. This
  supersedes the historical roadmap's ordering, not its retained source material.
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
  coordination. The conversation foundation is implemented; match and clip sharing
  remain planned. See the [Comms verification](../verification/2026-10-20-trainer-comms-foundation.md).
- `DexDox/unfinished.mds/` and `.artifacts/`: historical outlines and generated
  work plans, not a current prioritized backlog.

## Match Sight retired

The user requested removal on October 20. The screen only displayed static
"No active diagnostics" and "Waiting for Battle..." text. It had no reader or
Timeline consumer. Removed its screen, navigation command and route, shell
callback, and entries/focus targets in Signal Observatory and Researcher Mode.
Signal Observatory, Timeline Viewer, Match Calibration, and Replay remain.

## Important unfinished or partial features

October 21 follow-up: [Team Select form preservation](../verification/2026-10-21-team-select-forms.md)
now retains accepted sprite forms, checks within-species ambiguity, and uses the
player form for reference/type lookup. The first Moondrop roster distinguished
shiny Alolan Raichu and ordinary Raichu together; the user reported immediate
recognition and correct move lists. The slot-swap/rescan check also passed,
including ordinary Raichu's move list in its new first slot. The October 22
[player form handoff](../verification/2026-10-22-player-form-handoff.md) now binds
those forms to battle appearances in inference and Replay, with recorded and
emulator checks passing; physical battle retention still needs validation.
This does not complete opponent-form recognition, owned-card form storage, or
form-specific artwork.

| Area | What exists | Remaining work and evidence |
| --- | --- | --- |
| Type and regional-form identification | Species OCR, active-type badge matching, reference species/moves, and configured teams. | Preserve form identity through owned cards, recognition, inference, and replay. Fix and validate badge glyph scaling before using it to eliminate moves. The [paired-match review](../verification/2026-10-20-paired-self-match-review.md) documents Alolan Raichu treated as ordinary Raichu and incorrect type matches; the current [type matcher](../../app/src/main/java/com/example/overdex/battle/observation/PokemonGoTypeIconMatcher.kt) still uses the inspected normalization. [OwnedPokemon](../../app/src/main/java/com/example/overdex/model/OwnedPokemon.kt) carries species ID but no explicit regional-form field. |
| Fast Move counts and energy | Independent HP/pulse/rhythm/visual/audio witnesses, fused uses, candidate move inference, measured accounting, and a separate live forecast. | Establish count accuracy during attacks, deliberate inactivity, switches, QTEs, and connection stalls using paired recordings. Do not treat forecasted attacks as observed events. Visual effects currently supply transient measurements/cadence, not a recognized move type: see [LiveActiveHpBarWitnesses](../../app/src/main/java/com/example/overdex/battle/observation/LiveActiveHpBarWitnesses.kt). Audio reference matching exists; it is not a missing pipeline. |
| Charged-move ownership and spending | Announcement interpretation, player controls, charge sequences, and energy-spend derivation. | Resolve damaged announcements and mirror-match actor ambiguity without assigning the wrong side. The [paired-match review](../verification/2026-10-20-paired-self-match-review.md) records missed spend events on one phone and ambiguous Obstagoon Cross Chop. These remain validation targets, not proof that every current recording fails. |
| QTE type recognition | Live Draggy Box, saved calibration, automatic bounded samples, and preserved QTE crop references. | Add and validate the classifier that turns those samples into cited type testimony. The current [archive selector](../../app/src/main/java/com/example/overdex/battle/archive/MatchArchiveArticleSelector.kt) explicitly preserves QTE samples without a classifier; [PokedexViewModel](../../app/src/main/java/com/example/overdex/ui/PokedexViewModel.kt) wires their capture. Do not confuse active-type badge matching with QTE-icon recognition. |
| Replay editor | Named clips, whole-clip notes, independent source snapshots, ordered compilations, reordering, and bounded in-app playback. | Event-search supercuts, event-attached coaching notes, editing existing clip in/out points, continuous draft autosave, external compilation sharing, and video rendering/export. See [editor verification and remaining work](../verification/2026-10-20-replay-editor.md) and [ReplayEditorScreen](../../app/src/main/java/com/example/overdex/ui/screens/observatory/ReplayEditorScreen.kt). Draft persistence currently covers the Open Matches detour, not every edit before process death. |
| Confidence and OCR review | Nullable article scores, honest "Not scored" display, method-specific matching scores, and an offline crop review desk. | Measure correctness on held-out, human-labeled matches/devices before calling scores probabilities. Review labels are separate evidence, not automatic ML Kit retraining. See [confidence review](../verification/2026-10-20-confidence-and-ocr-review.md); the [correction follow-up](../verification/2026-10-20-ocr-corrections-and-calibration.md) found crop position problems rather than a reason to blindly add text aliases. |
| Assisted registration — paused | One adjustable field layout, explicit field reads, cry assistance, draft review, and saving an owned card. | Automatic scrolling/layout learning and automatic classification of manually confirmed image fields. [GuidedRegistrationController.readField](../../app/src/main/java/com/example/overdex/data/registration/GuidedRegistrationController.kt) still asks the user to confirm image icons. Keep the user's pause in effect while battle reliability is prioritized. |
| Trainer Comms sharing | Required email/password account; discovery by 12-digit GO code; direct and named group text conversations (up to eight trainers); saved drafts/history and pending/sent/failed delivery with idempotent retry; preserved read-only legacy history. | `.odxmatch` transfer, portable clips/compilations, participant management, invitations/blocking/reporting, and a two-phone production conversation acceptance test. See the [sharing plan](../plans/trainer-comms-sharing.md) and [foundation verification](../verification/2026-10-20-trainer-comms-foundation.md). |

### October 21 battle follow-up

The [Raichu review and notification update](../verification/2026-10-21-raichu-and-notification-controls.md)
adds Start, Stop, Next match, and return-to-Overdex actions to the existing
Droidball notification. It also records a remaining mixed-form battle gap:
Team Select recognizes both Raichu forms, but bare battle text does not identify
which same-name slot is active. The update blocks selecting the ordinary slot's
configured Fast Move by name alone. The October 22 handoff below adds player
appearance binding; live mixed-form acceptance remains open. Actual type crops
in this match contain a menu button or team-card content,
so crop placement and non-icon rejection both need validation. Result OCR and
victory music agreed on WIN. League/achievement medal references were located,
but a summary-image result trigger has not been enabled.

Starting Droidball in the overworld no longer qualifies badge-colored scenery
for retention. Badge caching requires independently established battle context,
excludes HUD overlap, and resets by match/observation attempt. Early HUD requests
keep navigation controls accessible; Ignore/Rescan reset the attempt. Live
validation also exposed false Team Select acceptance from terrain colors: party
acceptance now requires page-specific text with supporting visual evidence, and
league classification waits for that acceptance. A type-icon guess alone no
longer opens the battle HUD. The saved Use This Party aperture was widened through
Match Calibration without moving any other region. The reconnected phone check
now confirms both Raichu forms and Obstagoon and passes Ignore/Rescan recovery
within one session. That phone build took about 28–31 seconds from Scan.
The [scheduling follow-up](../verification/2026-10-21-team-select-scheduling.md)
removes serial fallback blocking, coalesces pending reads, correlates asynchronous
anchors by source time, and defers roster-name OCR until surface acceptance.
Four emulator tests and all 1,023 JVM tests pass. Recorded-image scans took
3.0–4.0 seconds without competing battle OCR and 17.4–21.8 seconds under continuous
OCR contention in the emulator-only bundled-model build. These are not a phone
speed comparison: Moondrop latency and native badge/HUD handoff remain unverified
on the new build.

The [October 22 species-attention follow-up](../verification/2026-10-22-species-ocr-attention.md)
keeps active-badge OCR out of menu scans until a native badge, recent battle cue,
or established battle context admits it. Early announcements and cries remain
available; a manually opened HUD alone does not admit species OCR. Twelve
emulator tests pass, including pre-countdown wake-up, Ignore/Rescan, and existing
switch/occlusion regressions. Three recorded Team Select attempts with continuous
announcement OCR took 5.0–5.2 seconds and retained both Raichu forms in their slots.
These are emulator diagnostics; the new phone response time remains open.

The [player form handoff](../verification/2026-10-22-player-form-handoff.md) preserves
the scanned lead's form and configured moves through bare badge reads, scopes
later label evidence to its original appearance, and separates player forms in
energy/faint tracking and Replay charge buttons. The saved Raichu fixture now
replays Alolan Raichu with Spark. All 1,041 JVM tests and one emulator integration
test pass. A same-name switch needs entry and slot/form evidence; no opponent
form is inferred from the player's roster. Physical live validation, active-type
crop quality and form-specific artwork remain open.

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
