# Walkthrough - Brick XXX.1: Terminology Audit (Fact -> Signal)

This step initiates the terminology audit by retiring the term **Fact** in the context of the instrument's communication stream and replacing it with **Signal**.

## Changes

### 1. Architectural Vocabulary
- **[Terms of Ownership](file:///home/sean/AndroidStudioProjects/Overdex/DexDox/Terms%20Of%20Ownership)**:
    - Added **Fact** to the **Retired** section. The term is removed because it implies absolute certainty, which conflicts with the project's philosophy of modeling confidence.
    - Added **Signal** as a **Reserved** term for the **Instrument** module.

### 2. Sensing Layer Communication
- **[DroidballService.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/observation/DroidballService.kt)**:
    - Renamed the core sealed class from `DroidballFact` to **`DroidballSignal`**.
    - Renamed the publication API from `emitFact` to **`emitSignal`**.
    - Renamed the shared flow from `facts` to **`signals`**.

### 3. Consumption Layer
- **[CountdownObserver.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/battle/observation/CountdownObserver.kt)**:
    - Updated the observer loop to emit signals instead of facts.
- **[PokedexViewModel.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/PokedexViewModel.kt)** & **[BattleOverlay.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/components/BattleOverlay.kt)**:
    - Updated all call sites and flow collection to consume signals.

## Verification

### Brick XXX.2: Model Refinement (factsRecorded -> eventsWitnessed)
- **[BattleLifecycleAnalysis.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/model/BattleLifecycleAnalysis.kt)**:
    - Renamed `factsRecorded` to **`eventsWitnessed`**. This aligns the lifecycle analysis model with the "Witnessing" vocabulary.

## Verification

### Automated Verification
- **Gradle Build**: Successful. The project compiles cleanly with the new naming convention.

### Manual Verification
- **Instrument Deployment**: The overlay correctly receives and displays signals (`READY`, `OBSERVING`, and recognized countdown values).
- **Logcat Output**: Confirms the publication of signals across the instrument boundary.
