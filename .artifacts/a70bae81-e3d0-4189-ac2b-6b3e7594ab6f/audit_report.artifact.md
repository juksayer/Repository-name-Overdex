# Phase 1: Public Directory Migration Audit

This report audits the current navigation implementation against the target directory structure.

## Target Directory Audit

| Directory Path | Existing Command | Existing Route | Existing Implementation | Status |
| :--- | :--- | :--- | :--- | :--- |
| `/OVERDEX` | `OpenSearch` | `list` | `PokedexListScreen` | **Functional** |
| `/BATTLE/Roster` | `OpenCollection` | `specimens/collection` | `MyCollectionScreen` | **Functional** |
| `/BATTLE/Match` | `OpenBattlePreview` | `battle_preview` | `BattlePreviewScreen` | **Unfinished** |
| `/BATTLE/Tournament` | *None* | *None* | *None* | **Missing** |
| `/BATTLE/History` | `OpenBattleHistory` | `battle_history` | `BattleHistoryScreen` | **Functional** |
| `/OBSERVE` | `OpenCalibration` | `calibration` | `CalibrationScreen` | **Functional** |
| `/TOOLS/Accessibility Probe` | `OpenAccessibilityProbe` | `accessibility_probe` | `AccessibilityProbeScreen` | **Functional** |
| `/TOOLS/Timeline Viewer` | `OpenSignalObservatory` | `signal_observatory` | `SignalObservatoryScreen` | **Functional** |
| `/TOOLS/Match Sight` | `OpenMatchSight` | `match_sight` | `MatchSightScreen` | **Functional** |
| `/TOOLS/Match Calibration` | `OpenMatchCalibration` | `match_calibration` | `MatchCalibrationScreen` | **Functional** |
| `/TOOLS/Filter Settings` | *None* | *None* | `FilterSettingsOverlay` | **Functional** (Mechanism) |
| `/PROFILE` | `OpenProfile` | `trainer_profile` | `TrainerProfileScreen` | **Functional** |

## Implementation Details

### BATTLE / Match
- **Command**: `InstrumentCommand.OpenBattlePreview` exists but is not linked in the current `InstrumentTree`.
- **Implementation**: `BattlePreviewScreen` is defined as a "workshop for the Battle HUD design."
- **Gap**: Not present in the current menu structure.

### BATTLE / Tournament
- **Status**: **Missing**. No command, route, or implementation exists in the source code. It exists only in documentation (`Tournaments.md`, `roadmap.md`).

### TOOLS / Filter Settings
- **Implementation**: `FilterSettingsOverlay` (inside `PokedexComponents.kt`).
- **Mechanism**: Currently managed as a state-driven overlay within `ODXFiShell`, accessed via the **START** button during active observation.
- **Distinction**: Functional Filter Settings are distinct from the **Researcher Mode** CRT controls (managed by `ResearcherModeOverlay`).

### TOOLS / Timeline Viewer
- **Implementation**: `SignalObservatoryScreen` (inside `SignalObservatoryScreen.kt`).
- **Distinction**: This is the user-facing name for the Signal Observatory functionality. The screen internally uses the path `/signal_observatory/timeline_viewer/`.

### PROFILE
- **Current Path**: `/TRAINER/Profile` in `InstrumentTree`.
- **Target Path**: Root-level `/PROFILE`.
- **Implementation**: `TrainerProfileScreen`.

## Infrastructure Audit

### Navigation System
- **Command Dispatch**: `MainActivity` collects `pendingCommand` from `PokedexViewModel` and navigates via `navController`.
- **Menu Structure**: `PokedexViewModel.instrumentTree` defines the visible hierarchy.
- **Node Types**:
    - `DirectoryNode`: Groups children (e.g., `BATTLE`, `TRAINER`).
    - `ActionNode`: Executes an `InstrumentCommand`.

### Gaps Identified
1. **Missing Directory**: `TOOLS/` is completely absent from the current `InstrumentTree`.
2. **Missing Nodes**: `Match`, `Tournament`, and `Filter Settings` are not defined in the tree.
3. **Misalignment**: `PROFILE` is currently a child of `TRAINER`, which does not exist in the target directory.
4. **Command Gap**: No `InstrumentCommand` exists for `Tournament` or directly for `Filter Settings`.
