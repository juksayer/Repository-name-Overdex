# Terminology Audit Report

This audit identifies where the Overdex codebase has drifted from its architectural language and philosophy as defined in [Philosophy.md](file:///home/sean/AndroidStudioProjects/Overdex/DexDox/Philosophy.md), [FutureIdeas.md](file:///home/sean/AndroidStudioProjects/Overdex/DexDox/FutureIdeas.md), and [Ownership.md](file:///home/sean/AndroidStudioProjects/Overdex/DexDox/Ownership.md).

## Executive Summary

The project has significant terminology drift in the **Collection** and **Battle** domains. The UI has begun adopting the "Specimen" and "Binder" metaphors, but the underlying data models and ViewModels remain anchored in legacy "OwnedPokemon" and "Collection" terminology. Additionally, `BattleMemory` is currently overreaching its architectural boundaries by owning historical data.

---

## 1. Collection Terminology

### Finding: OwnedPokemon

**Current Term**: `OwnedPokemon`

**Location**: `model/OwnedPokemon.kt`, `data/local/OwnedPokemonEntity.kt`, `data/local/OwnedPokemonDao.kt`

**Purpose**: Represents a specific Pokémon instance caught/owned by the trainer.

**Still Accurate?**: No

**Suggested Successor**: `Specimen`

**Reasoning**: [Philosophy.md](file:///home/sean/AndroidStudioProjects/Overdex/DexDox/Philosophy.md) and [FutureIdeas.md](file:///home/sean/AndroidStudioProjects/Overdex/DexDox/FutureIdeas.md) explicitly state that "OwnedPokemon" should be renamed to "Specimen". "Specimen" treats the Pokémon as a subject of study and part of an instrument's record, rather than just a digital asset.

---

### Finding: My Collection

**Current Term**: `MyCollection`

**Location**: `ui/screens/MyCollectionScreen.kt`, `ui/MyCollectionViewModel.kt`

**Purpose**: The top-level container for the trainer's specimens.

**Still Accurate?**: No

**Suggested Successor**: `Binders`

**Reasoning**: The intended metaphor is that the Pokédex documents Pokémon species, while "Binders" document the trainer's relationship with them. "Collection" is a generic Android/Gaming term; "Binders" reinforces the physical instrument identity.

---

## 2. Battle Terminology

### Finding: BattlePreview

**Current Term**: `BattlePreview`

**Location**: `ui/screens/BattlePreviewScreen.kt`, `presentation/preview/BattlePreviewData.kt`

**Status**: Rename Candidate

**Suggested Successor**: `HUDWorkshop` or `BattleSimulator`

**Reasoning**: The screen is currently used as a "workshop for the Battle HUD design" using deterministic mock data. It is not a "preview" of an actual upcoming battle, but rather a simulation environment for testing presentation.

---

### Finding: BattleMemory (History Ownership)

**Current Term**: `BattleMemory` (specifically the `battleHistory` property)

**Location**: `BattleMemory.kt`

**Status**: Architectural Drift

**Suggested Successor**: Move `battleHistory` to a `History` layer service.

**Reasoning**: According to [Ownership.md](file:///home/sean/AndroidStudioProjects/Overdex/DexDox/Ownership.md), **Memory** owns "Current battle state, working memory" while **History** owns "Battle timelines, ordered events". `BattleMemory` currently owns both the live state and the growing list of historical events, violating this separation.

---

### Finding: BattleHistoryRepository

**Current Term**: `BattleHistoryRepository`

**Location**: `data/BattleHistoryRepository.kt`

**Status**: Rename Candidate

**Suggested Successor**: `ArchiveRepository`

**Reasoning**: The repository returns `ArchivedBattle` objects. [Ownership.md](file:///home/sean/AndroidStudioProjects/Overdex/DexDox/Ownership.md) defines **Archive** as owning "Persistence, storage, long-term preservation". The term "History" should be reserved for the chronological ordering of events, while "Archive" handles the storage of completed sessions.

---

## 3. Observation & Intelligence Terminology

### Finding: Confidence in Memory

**Current Term**: `Confidence` (stored within `BattleEvent`)

**Location**: `BattleMemory.kt`, `BattleEvent.kt`

**Status**: Ownership Drift

**Suggested Successor**: Confidence should be calculated by `Intelligence` during presentation/analysis.

**Reasoning**: [Ownership.md](file:///home/sean/AndroidStudioProjects/Overdex/DexDox/Ownership.md) states that "Confidence belongs to Intelligence—not Observation". By hardcoding confidence scores into `BattleEvent` facts within `BattleMemory`, we are mixing "Observed Facts" with "Inferred Confidence".

---

### Finding: MatchupAnalysis

**Current Term**: `MatchupAnalysis`

**Location**: `model/MatchupAnalysis.kt`

**Status**: Accurate

**Reasoning**: Aligns perfectly with the **Intelligence** layer's responsibility to interpret facts and provide "Matchup analysis" and "Pattern recognition".

---

## 4. Presentation Terminology

### Finding: PokedexFrame

**Current Term**: `PokedexFrame`

**Location**: `ui/components/PokedexComponents.kt`

**Status**: Accurate

**Reasoning**: Correctly describes the "Handheld" identity and the physical "Frame" of the instrument. It manages hardware behaviors (buttons, CRT rendering) without owning the data it displays.

---

## Conclusion

The project's language is currently split between **legacy implementation** ("OwnedPokemon") and **vision documentation** ("Specimen"). While the UI screens have begun to adopt the new names, the internal architecture is still lagging behind. A coordinated refactor (when scheduled) should focus on aligning the core models with the "Specimen/Binder" and "Memory/History/Archive" boundaries.
