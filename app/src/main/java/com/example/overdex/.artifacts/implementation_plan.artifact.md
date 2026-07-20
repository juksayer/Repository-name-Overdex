# Battle Preview Image Capture Plan

This plan outlines the process of capturing high-fidelity screenshots for each battle preview scenario to document the HUD development.

## Proposed Changes

1.  **Instrumented Capture Loop**:
    *   Temporarily modify `MainActivity.kt` to allow switching between `BattlePreviewData` scenarios and set `battle_preview` as the start destination.
    *   Deploy the application.
    *   For each scenario (`mewtwoDemo`, `missingMovesDemo`, `shadowDemo`, `faintedDemo`):
        *   Update the state in `MainActivity.kt`.
        *   Deploy/Refresh.
        *   Capture the screen using `adb shell screencap`.
        *   Pull the image to the `.artifacts` directory with a descriptive name.
2.  **Cleanup**:
    *   Restore `MainActivity.kt` to its original state (start destination `main_menu`, using `mewtwoDemo()`).

## Verification Plan
*   Verify the existence and quality of the generated PNG files in the `.artifacts` directory.
*   Update the `walkthrough.artifact.md` to reference these physical files.
