# Ownership Audit Implementation Plan

Perform a comprehensive audit and refactoring of the "Ownership" model in Overdex to align with the core philosophy and terminology established in [Philosophy.md](file:///home/sean/AndroidStudioProjects/Overdex/DexDox/Philosophy.md) and [FutureIdeas.md](file:///home/sean/AndroidStudioProjects/Overdex/DexDox/FutureIdeas.md).

## Goals
- **Terminology Alignment**: Transition from `OwnedPokemon` to `Specimen` throughout the codebase.
- **Metaphor Consolidation**: Transition from `My Collection` to `My Binders`.
- **Architectural Cleanup**: Resolve inconsistencies between UI screens and backing models.

## Proposed Changes

### 1. Model & Data Layer Refactoring
Rename all instances of "OwnedPokemon" to "Specimen" to reflect the "Specimen" identity for user-owned Pokémon.

#### [MODIFY] [Specimen.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/model/Specimen.kt) [NEW] (Renamed from `OwnedPokemon.kt`)
- Rename data class `OwnedPokemon` to `Specimen`.
#### [MODIFY] [SpecimenEntity.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/data/local/SpecimenEntity.kt) [NEW] (Renamed from `OwnedPokemonEntity.kt`)
- Update table name to `specimens`.
- Rename fields and extension functions (e.g., `toDomain`, `toEntity`).
#### [MODIFY] [SpecimenDao.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/data/local/SpecimenDao.kt) [NEW] (Renamed from `OwnedPokemonDao.kt`)
- Update DAO methods to use `Specimen` and `SpecimenEntity`.
#### [MODIFY] [PokedexDatabase.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/data/local/PokedexDatabase.kt)
- Update to use `SpecimenEntity` and `SpecimenDao`.

### 2. ViewModel Refactoring
Rename `MyCollectionViewModel` to reflect the "Binder" or "Specimen" context. Given the philosophy, `BinderViewModel` seems appropriate as it manages the "relationship" with Pokémon.

#### [MODIFY] [BinderViewModel.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/BinderViewModel.kt) [NEW] (Renamed from `MyCollectionViewModel.kt`)
- Rename class and update internal logic to use `Specimen`.

### 3. UI Refactoring
Consolidate screens and update terminology.

#### [DELETE] [OwnedPokemonDetailScreen.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/screens/OwnedPokemonDetailScreen.kt)
- This is redundant as [SpecimenDetailScreen.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/screens/SpecimenDetailScreen.kt) already exists and follows the new naming.
#### [MODIFY] [BindersScreen.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/screens/BindersScreen.kt) [NEW] (Renamed from `MyCollectionScreen.kt`)
- Update UI to use "Binder" terminology.
#### [MODIFY] [SpecimensScreen.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/screens/SpecimensScreen.kt)
- Ensure it uses the new `Specimen` model and `BinderViewModel`.
#### [MODIFY] [AddSpecimenWizard.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/screens/AddSpecimenWizard.kt) [NEW] (Renamed from `AddOwnedPokemonWizard.kt`)
- Rename and update to use `Specimen`.

### 4. General Cleanup
- Update all imports and variable names across the project (including `MainActivity.kt` and `PokedexViewModel.kt`).

## Verification Plan

### Automated Tests
- Since there are no existing unit tests for these specific DAOs/ViewModels visible in the file list, I will perform a full project build to ensure no broken references.
- `gradlew assembleDebug`

### Manual Verification
- Deploy to device/emulator.
- Navigate to "My Binders" (formerly "My Collection").
- Ensure existing specimens are listed correctly.
- Add a new specimen via the wizard.
- View and edit specimen details.
- Verify that "Owned" terminology is gone from the UI, replaced by "Specimen" or "Binder" concepts.
