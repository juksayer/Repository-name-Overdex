# Implementation Plan — Git #270: Package Import & Package Declaration Audit

Audit the entire project to resolve stale imports, correct package declarations, and eliminate wildcard imports following the relocation of core models and data classes. This plan prioritizes architectural integrity and build stability over aggressive automation.

## User Review Required

> [!IMPORTANT]
> This audit involves a comprehensive sweep of approximately 230 files. To maintain stability, changes will be applied in logical batches (Core Models, Data, Battle, UI) with intermediate builds and strict verification rules.

> [!WARNING]
> **No Automatic Cleanup**: Unused imports will only be removed if the compiler explicitly flags them as unused *after* a batch build succeeds.

## Proposed Changes

### Audit Strategy
1.  **Generate Inventory (Read-Only)**: Identify known mismatches and problematic imports before any modifications. (Treat as a starting point, not an exhaustive list).
2.  **Package Declaration Verification**: Ensure every file's `package` statement matches its physical directory path. This is the foundation for all subsequent import resolutions.
3.  **Wrong/Stale Imports**: Correct imports that reference old package locations (e.g., core models that moved from root to `.model`).
4.  **Unused Import Cleanup**: Manually remove compiler-confirmed unused imports following a successful batch build.
5.  **Wildcard Elimination**: Replace `import com.example.overdex.*` and other package-level wildcards with explicit imports.

### Batch Order
1.  **Core Models**: `com.example.overdex.model`
2.  **Data Layer**: `com.example.overdex.data`
3.  **Battle Domain**: `com.example.overdex.battle`
4.  **UI Components**: `com.example.overdex.ui`
5.  **Root & Activity**: `com.example.overdex`

## Starting Project Health Snapshot

### Known Package Declaration Mismatches
- `ui/components/CalibrationRegion.kt`: Declares `package com.example.overdex` (expected `com.example.overdex.ui.components`).
- `data/BattleObserver.kt`: Declares `package com.example.overdex` (expected `com.example.overdex.data`).

### Known Stale Imports
- Multiple references to `com.example.overdex.AnchorRegion` (moved to `.model`).
- Multiple references to `com.example.overdex.BattleCalibration` (moved to `.data`).

### High Wildcard Volume
- `MainActivity.kt`, `BattleMemory.kt`, `GuidedObservationPipeline.kt`.

## Verification Plan

### Per-Batch Verification
- **Build Success**: Run `./gradlew :app:compileDebugKotlin`.
- **Error Stability**: Ensure the total error count does not increase following an update.
- **Package Integrity Check**:
    - Every file in the batch has a package declaration matching its physical directory.
    - Every imported type resolves successfully.
    - No imports remain commented out as placeholders.

### Final Verification
- **Clean Build**: Execute `./gradlew clean :app:assembleDebug`.
- **Manual Launch**: Verify the app launches and core features (Calibration, Battle Observation) function correctly.
