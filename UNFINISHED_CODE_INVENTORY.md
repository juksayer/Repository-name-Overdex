# Overdex Unfinished and Legacy-Code Inventory

**Status:** Reviewed working inventory

**Last validated against the working tree:** September 28, 2026

**Focused updates:** October 11, 2026 — pre-battle team editing and replay HP
were rechecked; October 10 registration and species checks remain listed below.
Other entries retain their earlier review date.

**October 20 focused review:** Match Sight was removed at the user's request;
its screen had no diagnostic implementation. Explicit TODOs, specimen-detail
placeholders, and the unused `SpeciesObserver` were rechecked. The
[current feature-status review](docs/reference/current-feature-status.md)
separates implemented features, unfinished work, and the historical roadmap.

This document records cleanup candidates, intentional legacy systems, and known
feature gaps. It is evidence for a later decision, not an instruction to delete
files. Runtime ownership is documented in [PROJECT-MAP-v2.md](PROJECT-MAP-v2.md).

**October 23 release boundary:** Accessibility Probe's service, manifest declaration
and configuration now live in `app/src/debug`; release menus, focus order and
navigation exclude the probe. The shared diagnostic event model/manager remain
in source, but no release accessibility service receives events. Timeline Viewer
and Match Calibration are unaffected. See the
[first-release checklist](docs/plans/first-release.md) for the current release scope.

## Classification

| Classification | Meaning |
| --- | --- |
| **Cleanup candidate** | No caller was found, or the file is malformed/generated debris. It can be removed after a focused verification. |
| **Legacy-active** | A newer architecture exists, but current code still calls this path. Migrate callers before removal. |
| **Intentional fallback** | The code exists for a defined degraded or development mode. |
| **Feature gap** | A user-visible behavior or platform policy remains undecided or unfinished. |
| **Archive** | Deliberately preserved project history. Exclude it from builds and current architecture maps; do not treat it as dead source. |

## Confirmed cleanup candidates

| Item | Evidence | Recommended action |
| --- | --- | --- |
| [`simpleCurrentState.kt.kt`](app/src/main/java/com/example/overdex/simpleCurrentState.kt.kt) | Malformed double extension. `ExplainedValue` and `DerivationStatus` have no references outside this file. | Remove it in a dedicated cleanup change, or move it to an archive if its design still has documentary value. |
| [`witnessSpeciesName_logcat.logcat`](app/src/main/java/com/example/overdex/battle/witness/witnessSpeciesName_logcat.logcat) | Tracked 15,970-line, approximately 584 KiB diagnostic dump inside a production package. It is not compiled or referenced. | Preserve outside `app/src/main/java` if it still has forensic value, then remove it from the source tree. |
| [`ObservationPipelineDemo.kt`](app/src/main/java/com/example/overdex/battle/debug/ObservationPipelineDemo.kt) | The object and its `run` method have no call site. It assembles the older `BattleTimelineBuilder` path. | Move to a sample/validation source set or remove after its historical value is captured. |
| [`SpeciesObserver.kt`](app/src/main/java/com/example/overdex/battle/observation/SpeciesObserver.kt) | October 20: no constructor/call site found in `app/src` or `tools`. Its domain-mismatch TODO only logs recognized text. | Retire separately after checking any external callers. The current live/persisted species Witnesses are different implementations; this TODO is not a missing step in their publication path. |

## Retired in this review

**Match Sight — October 20, 2026:** removed the static screen, navigation command
and route, shell callback, and both Observatory/Researcher menu entries and focus
targets. It displayed waiting text without consuming diagnostics. Signal
Observatory, Timeline Viewer, Match Calibration, and Replay remain available.

The previously listed empty `GoodEffortWitness/kt.java` is already absent from
the working tree as of October 20; it is no longer an outstanding cleanup item.

## Legacy-active architecture requiring migration decisions

| Area | Evidence and present boundary | Decision still needed |
| --- | --- | --- |
| Three `Observation` models | [`model.Observation`](app/src/main/java/com/example/overdex/model/Observation.kt) is used by `ObservationExtractor`; [`model.observation.Observation`](app/src/main/java/com/example/overdex/model/observation/Observation.kt) is used by registration, general inputs, and older Witnesses; [`battle.observation.Observation`](app/src/main/java/com/example/overdex/battle/observation/Observation.kt) is used by battle workspace/debug paths. | Rename or migrate by responsibility. None is currently safe to delete merely because the names overlap. |
| Three Timeline families | [`battle.reality.RealityTimeline`](app/src/main/java/com/example/overdex/battle/reality/RealityTimeline.kt) is canonical for Match Articles. [`battle.timeline.BattleTimeline`](app/src/main/java/com/example/overdex/battle/timeline/BattleTimeline.kt) is used by tests, verification, and a demo. [`model.BattleTimeline`](app/src/main/java/com/example/overdex/model/BattleTimeline.kt) is used by the earlier `BattleMemory`. | Keep the Reality Timeline authoritative. Decide whether to migrate the remaining verification and BattleMemory callers or preserve those systems explicitly as legacy. |
| Shared Timeline migration | [`SharedTimelineRepository.kt`](app/src/main/java/com/example/overdex/data/SharedTimelineRepository.kt) and [`SharedTimelineScreen.kt`](app/src/main/java/com/example/overdex/ui/screens/SharedTimelineScreen.kt) remain wired through `MainActivity`. The October 20 direction puts sharing in Profile → Trainer Comms. | Migrate useful duties into group conversations, match/clip sharing, and coaching; retire the old surface deliberately. Future tournament coordination uses Trainer Comms too. See the [sharing plan](docs/plans/trainer-comms-sharing.md); do not revive a separate Trainer Timeline. |
| Debug observation recording | `ObservationRecorder`, `MatchRecording`, and `MatchSummaryCard` still have active callers in recognition, `PokedexViewModel`, and `TimelineViewerScreen`. | Decide whether this remains a supported diagnostic recorder or should be replaced completely by `.odxmatch` archive inspection. It is not orphaned today. |
| Older battle Witnesses | Classes under [`battle/witness/`](app/src/main/java/com/example/overdex/battle/witness/) use the general observation-input model; several are exercised only by tests while persisted-crop Witnesses carry the current Match record. | Audit each Witness against `PokedexViewModel` registration before moving or deleting it. Preserve one-Witness/one-signal behavior during migration. |
| Droidball session scope | `PokedexViewModel.startFreshMatch` constructs a new `DroidballSession` with every `Match`; the running capture service/deployment state spans consecutive Matches. | Introduce or rename the longer-lived assistance-session owner so the code expresses the established Droidball-session-versus-Match boundary. |

## Rechecked boundaries

| Area | Current behavior |
| --- | --- |
| Pre-battle team editing | Droidball exposes manual Team Select rescanning and a field editor for species and moves. Saved lineups are selectable directly from pre-flight. Explicit team revisions are preserved as configured-slot articles; replay and move inference use the revised lineup. Fresh Team Select OCR on a live screen still requires device acceptance. |
| Replay HP | First entrants start visually full until a valid measurement; this is a presentation assumption, not new testimony. Returning species retain their earlier HP. Countdown samples and short, thick false-bar detections are rejected in replay. Live tracking quality still needs improvement when sparse measurements leave the bar assumed full. |
| Registration templates | `CaptureTemplateManager` is active in screenshot calibration, screenshot reading, and Droidball registration alignment. These callers now share one normalized field layout with explicit box reads and `SAVE CARD`; legacy keys are retained and the duplicate fast-move alias is read only for migration. Automatic layout learning and icon classification remain unimplemented. It is no longer a cleanup candidate. |
| Live species ordering | `LiveOverlaySpeciesPipeline` preserves the crop and waits for its Timeline article before OCR testimony. Accepted species pass through Custody to the Timeline; `Match` updates the HUD from that same testimony. A recorded-frame device test verifies both sides, referenced source articles, the opponent HUD species, and its move lists. Fresh live-match latency remains a separate acceptance check. |

## Intentional fallback

| Item | Evidence | Keep-condition |
| --- | --- | --- |
| [`MockChatTransport.kt`](app/src/main/java/com/example/overdex/data/MockChatTransport.kt) | `ChatTransportFactory` returns it when Firebase initialization fails. | Keep while offline/development Trainer Comms needs a fallback. If production policy changes, replace the factory behavior before removing it. |

## Open feature and policy gaps

| Item | Current marker | Needed decision or implementation |
| --- | --- | --- |
| [`SpecimenDetailScreen.kt`](app/src/main/java/com/example/overdex/ui/screens/SpecimenDetailScreen.kt) | October 20: Timeline, League Eligibility, and Notes still call `PlaceholderContent()`. Battle History and teams are wired. | Define and implement these sections; notes also need persistence and editing. Binder restrictions are already implemented separately. |
| [`PokemonDetailScreen.kt`](app/src/main/java/com/example/overdex/ui/screens/PokemonDetailScreen.kt) | Artwork long-press behavior is unimplemented. | Choose the action—download, share, inspect, or none—before adding gesture affordance. |
| [`data_extraction_rules.xml`](app/src/main/res/xml/data_extraction_rules.xml) | Android backup include/exclude policy remains the template TODO. | Decide which archives, calibration, credentials, caches, and artifacts may enter device backup. |
| [`PokemonRepositoryTest.kt`](app/src/test/java/com/example/overdex/data/PokemonRepositoryTest.kt) | Test-double methods use `TODO()` for calls the test does not expect. | Replace with explicit failure messages or complete fakes if future tests exercise those methods. These are test gaps, not unfinished production methods. |

## Intentional archive material

| Location | Status |
| --- | --- |
| [`DexDox/Ontology(old).md`](DexDox/Ontology%28old%29.md) | Preserved earlier ontology. Keep labeled as historical. |
| [`DexDox/Archive Dox/Excavations/`](<DexDox/Archive Dox/Excavations/>) | Preserved excavated source and validators. It is outside the application source sets. |
| [`DexDox/unfinished.mds/`](DexDox/unfinished.mds/) | Unfinished documentation outlines. Review for useful material, but do not confuse them with runtime source. |

## Validation procedure

Before changing an entry from **legacy-active** to **cleanup candidate**:

1. Search production, unit-test, instrumentation-test, and navigation call sites.
2. Check reflection, manifest declarations, serialization names, and external validation scripts.
3. Build and run the focused tests for the affected path.
4. Remove or migrate one responsibility at a time.
5. Regenerate [CODEBASE_INDEX.md](CODEBASE_INDEX.md) and update this inventory with the evidence used.

This inventory should change when evidence changes. Dates and line counts are
snapshots; call-site classification is the durable part.
