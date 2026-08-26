# Instrument Directory Audit - ODX-FI

**Objective:** Document the current state of the ODX-FI Instrument Tree and verify its integration with the navigation system.

## Summary Table

| Current Entry | Path | Type | Destination | Status | Keep? | Notes |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **specimens/** | `/specimens/` | Dir | - | Functional | Yes | |
| ∟ search | `/specimens/search` | Action | `list` | Working | Yes | |
| ∟ collection | `/specimens/collection` | Action | `specimens/collection` | Working | Yes | |
| ∟ register | `/specimens/register` | Action | `add_pokemon_wizard` | Working | Yes | |
| **battle/** | `/battle/` | Dir | - | Functional | Yes | |
| ∟ sight | `/battle/sight` | Action | `match_sight` | Working | Yes | |
| ∟ preview | `/battle/preview` | Action | `battle_preview` | Working | Yes | |
| ∟ history | `/battle/history` | Action | `battle_history` | Working | Yes | |
| ∟ logs | `/battle/logs` | Action | `battle_log` | Working | Yes | |
| **observation/** | `/observation/` | Dir | - | Functional | Yes | |
| ∟ capture | `/observation/capture` | Action | `calibration` | Redirected | No | Redirects to system calibration; potentially obsolete. |
| ∟ calibration | `/observation/calibration` | Action | `calibration` | Working | Yes | |
| **trainer/** | `/trainer/` | Dir | - | Functional | Yes | |
| ∟ profile | `/trainer/profile` | Action | `trainer_profile` | Working | Yes | |
| ∟ timeline | `/trainer/timeline` | Action | `shared_timeline` | Working | Yes | |
| ∟ chat | `/trainer/chat` | Action | `private_chat` | Working | Yes | |
| **tools/** | `/tools/` | Dir | - | Functional | Yes | |
| ∟ probe | `/tools/probe` | Action | `accessibility_probe` | Working | Yes | |
| ∟ observatory | `/tools/observatory` | Action | `signal_observatory` | Working | Yes | |

## Navigation Implementation Trace

1.  **Directory Model**: The authoritative structure is defined in `PokedexViewModel.kt` within the `instrumentTree` initialization using `DirectoryNode` and `ActionNode` components.
2.  **Selection State**: Managed by the `InstrumentTree` class in `app/src/main/java/com/example/overdex/model/navigation/InstrumentTree.kt`. It tracks the `selectedPath` and `expandedPaths`.
3.  **Navigation Intent**:
    - **Physical Control**: `ODXFiShell` (onA) → `PokedexViewModel.handleA()` → `instrumentTree.executeSelected()` → emits an `InstrumentCommand`.
    - **Touch Interaction**: `MainMenuScreen` (onNodeSelected) → `MainActivity.kt` (via a hardcoded `when (node.path)` block).
4.  **Navigation Owner**: `MainActivity.kt`'s `NavHost` translates both `InstrumentCommand` emissions and explicit path-based requests into `navController` actions.
5.  **Workspace/Screen**: Final screens are defined as composables within the `MainActivity` navigation graph.

## Missing Capabilities

- **Match Calibration**: A functional screen (`MatchCalibrationScreen`) exists and is reachable through the **Researcher Mode** overlay, but it has no entry in the primary `InstrumentTree`.
    - *Proposed Location:* `/battle/calibration` or `/observation/match_calibration`.

## Dead/Obsolete Capabilities

- **`/observation/capture`**: This node is a legacy entry that currently redirects to system calibration. In the current architecture, "Capture" (template registration) has been largely subsumed or deferred by the unified calibration system.

## Ambiguous Capabilities

- **Timeline Viewer vs. Observatory**: The Researcher Mode UI refers to "LAUNCH TIMELINE VIEWER," while the Instrument Tree uses "observatory" (`/tools/observatory`). Both point to `SignalObservatoryScreen`. The terminology should be unified.
- **Touch/Hardware Divergence**: The `MainActivity` navigation logic for the Main Menu is split between `InstrumentCommand` (for hardware buttons) and a hardcoded path-match block (for touch). This creates a risk of desynchronization where a tree entry works via one input method but not the other.

## Verification of Visibility/Reachability

- All entries in the `InstrumentTree` are currently **visible** in the `MainMenuScreen` once expanded.
- All entries are **reachable** via physical controls (D-pad + A).
- Only a subset of entries (chat, profile, collection, search, history, logs, calibration) are **reachable** via touch in `MainActivity`'s current implementation.
