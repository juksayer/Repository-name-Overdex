# Overdex Unfinished and Legacy-Code Inventory

**Status:** Reviewed working inventory

**Last validated against the working tree:** September 28, 2026

This document records cleanup candidates, intentional legacy systems, and known
feature gaps. It is evidence for a later decision, not an instruction to delete
files. Runtime ownership is documented in [PROJECT-MAP-v2.md](PROJECT-MAP-v2.md).

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
| [`GoodEffortWitness/kt.java`](app/src/main/java/com/example/overdex/battle/witness/GoodEffortWitness/kt.java) | Tracked, empty, zero-byte Java file inside a directory named for a Kotlin class. | Remove after confirming no external tooling expects the path. |
| [`witnessSpeciesName_logcat.logcat`](app/src/main/java/com/example/overdex/battle/witness/witnessSpeciesName_logcat.logcat) | Tracked 15,970-line, approximately 584 KiB diagnostic dump inside a production package. It is not compiled or referenced. | Preserve outside `app/src/main/java` if it still has forensic value, then remove it from the source tree. |
| [`ObservationPipelineDemo.kt`](app/src/main/java/com/example/overdex/battle/debug/ObservationPipelineDemo.kt) | The object and its `run` method have no call site. It assembles the older `BattleTimelineBuilder` path. | Move to a sample/validation source set or remove after its historical value is captured. |
| [`CaptureTemplateManager.kt`](app/src/main/java/com/example/overdex/CaptureTemplateManager.kt) | No production, unit-test, or instrumentation-test constructor call was found. Its only known external reference is an archived excavation validator. `getSummaryTemplate` and `getMovesTemplate` are deprecated. | Verify that no reflection or external automation loads it, then archive or remove the class as one unit rather than deleting only the two deprecated methods. |

## Legacy-active architecture requiring migration decisions

| Area | Evidence and present boundary | Decision still needed |
| --- | --- | --- |
| Three `Observation` models | [`model.Observation`](app/src/main/java/com/example/overdex/model/Observation.kt) is used by `ObservationExtractor`; [`model.observation.Observation`](app/src/main/java/com/example/overdex/model/observation/Observation.kt) is used by registration, general inputs, and older Witnesses; [`battle.observation.Observation`](app/src/main/java/com/example/overdex/battle/observation/Observation.kt) is used by battle workspace/debug paths. | Rename or migrate by responsibility. None is currently safe to delete merely because the names overlap. |
| Three Timeline families | [`battle.reality.RealityTimeline`](app/src/main/java/com/example/overdex/battle/reality/RealityTimeline.kt) is canonical for Match Articles. [`battle.timeline.BattleTimeline`](app/src/main/java/com/example/overdex/battle/timeline/BattleTimeline.kt) is used by tests, verification, and a demo. [`model.BattleTimeline`](app/src/main/java/com/example/overdex/model/BattleTimeline.kt) is used by the earlier `BattleMemory`. | Keep the Reality Timeline authoritative. Decide whether to migrate the remaining verification and BattleMemory callers or preserve those systems explicitly as legacy. |
| Retired Shared Timeline | [`SharedTimelineRepository.kt`](app/src/main/java/com/example/overdex/data/SharedTimelineRepository.kt) and [`SharedTimelineScreen.kt`](app/src/main/java/com/example/overdex/ui/screens/SharedTimelineScreen.kt) remain wired through `MainActivity`, even though the Shared Timeline product concept was retired. | Delegate any still-needed Trainer Comms/history duties, remove its navigation surface, then retire the repository and milestone-specific UI deliberately. |
| Debug observation recording | `ObservationRecorder`, `MatchRecording`, and `MatchSummaryCard` still have active callers in recognition, `PokedexViewModel`, and `TimelineViewerScreen`. | Decide whether this remains a supported diagnostic recorder or should be replaced completely by `.odxmatch` archive inspection. It is not orphaned today. |
| Older battle Witnesses | Classes under [`battle/witness/`](app/src/main/java/com/example/overdex/battle/witness/) use the general observation-input model; several are exercised only by tests while persisted-crop Witnesses carry the current Match record. | Audit each Witness against `PokedexViewModel` registration before moving or deleting it. Preserve one-Witness/one-signal behavior during migration. |
| Droidball session scope | `PokedexViewModel.startFreshMatch` constructs a new `DroidballSession` with every `Match`; the running capture service/deployment state spans consecutive Matches. | Introduce or rename the longer-lived assistance-session owner so the code expresses the established Droidball-session-versus-Match boundary. |
| Fast overlay ordering | `CropCaptureWitness` invokes `LiveOverlaySpeciesPipeline` after artifact preservation and Custody acceptance, while `Match` publishes the corresponding `RealityArticle` from a separate collector. | Feed the fast presentation lane from the published species-crop Article stream if strict Timeline-before-presentation ordering is required for every consumer. |

## Intentional fallback

| Item | Evidence | Keep-condition |
| --- | --- | --- |
| [`MockChatTransport.kt`](app/src/main/java/com/example/overdex/data/MockChatTransport.kt) | `ChatTransportFactory` returns it when Firebase initialization fails. | Keep while offline/development Trainer Comms needs a fallback. If production policy changes, replace the factory behavior before removing it. |

## Open feature and policy gaps

| Item | Current marker | Needed decision or implementation |
| --- | --- | --- |
| [`SpeciesObserver.kt`](app/src/main/java/com/example/overdex/battle/observation/SpeciesObserver.kt) | Domain mismatch TODO between `model.observation` and `battle.observation`. | Resolve as part of the Observation-model migration; do not add a fourth representation. |
| [`EditSpecimenScreen.kt`](app/src/main/java/com/example/overdex/ui/screens/EditSpecimenScreen.kt) | Notes field is deferred. | Define persistence and editing behavior before enabling the field. |
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
