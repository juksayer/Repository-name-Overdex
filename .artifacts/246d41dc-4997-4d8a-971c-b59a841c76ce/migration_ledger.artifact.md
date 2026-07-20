# Migration Ledger

This ledger tracks identified architectural migrations. These changes have been evaluated and intentionally deferred to maintain development velocity while preserving the architectural roadmap.

| Legacy | Target | Priority | Risk | Trigger |
| :--- | :--- | :--- | :--- | :--- |
| `OwnedPokemon` | `Specimen` | Low | High | First database schema migration after v1 |
| `BattleMemory` | Split Memory/History | **Architectural Blocker** | Medium | Replay engine begins consuming timelines |
| `MyCollection` | `Binders` | Low | Medium | Binder pages and organization ship |
| `Confidence` (Hardcoded) | `Intelligence` (Inferred) | Medium | Medium | Confidence engine supports multiple observers |
| `BattlePreview` | `TestBattle` | Low | Low | HUD Workshop stabilization |
| `BattleHistoryRepository` | `ArchiveRepository` | Low | Low | Archive layer expansion |
| `LiveMovePanel` | `LiveMoveAnalysisPanel` | Low | Low | Intelligence UI refactor |

---

## Evaluation Notes

### BattleMemory (Split Memory/History)
**Status**: Architectural Blocker
**Reasoning**: This is a structural violation. `BattleMemory` is currently doing too much by owning both live state and chronological history. While deferred, it acts as a "blocker" because any meaningful work on the Replay Engine, Trainer Communications, or Battle History will require this separation to be resolved first.

### OwnedPokemon → Specimen
**Status**: Deferred
**Reasoning**: This rename touches the database, DAOs, ViewModels, and UI. While it is the most important terminology change for identity, the risk of data migration issues and broad refactoring noise is high. Best performed during the first mandatory schema migration post-v1.

### MyCollection → Binders
**Status**: Deferred
**Reasoning**: The "Binder" metaphor is intended to be much more than a list (pages, curation, slots). Until the UI supports these features, "Collection" remains an acceptable technical description. We wait until the feature actually deserves the name.

### Confidence Ownership
**Status**: Deferred
**Reasoning**: Moving confidence calculation from the data events to the intelligence layer requires a more robust analysis engine. Current hardcoded scores serve as placeholders to allow presentation development to proceed independently of the inference engine.
