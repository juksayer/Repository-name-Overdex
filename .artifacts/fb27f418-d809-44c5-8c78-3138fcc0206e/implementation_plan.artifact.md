# Terminal Boot Sequence Refinement Implementation Plan

Refine the application boot sequence to ensure architectural correctness, remove placeholders, and improve the terminal-like transition to the main menu.

## Proposed Changes

### [Component Name] UI Screens

#### [MODIFY] [MainMenuScreen.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/screens/MainMenuScreen.kt)
- **Update Boot Entries**:
    - Remove placeholder "initializing confidence engine...".
    - Update move database count to `335` (matching `gamemaster.json`).
    - Standardize labels to resemble concise terminal status output.
- **Refine Transition**:
    - Remove `animateScrollTo` to eliminate "screen navigation" feel and dependency on Android's animation duration scale.
    - Remove the `height(viewportHeight)` constraint on the boot report section so it doesn't push the menu "below the fold" artificially.
    - Trigger `onBootComplete()` immediately after the final boot line has been appended to the terminal output, before any UI transition or scrolling logic.
    - Ensure the terminal output timing remains consistent using `delay()` (which is independent of animation duration scale).

## Implementation Notes
> [!IMPORTANT]
> Do not introduce fade, slide, or navigation animations as a replacement for the removed scroll animation. The menu should simply exist because the terminal has finished printing.

## Out of Scope
- Do not redesign the boot sequence.
- Do not add or remove boot stages beyond the specified placeholder removal.
- Do not modify boot timing except as required to remove the animated scroll.
- Do not introduce new visual effects or animations.

## Verification Plan

### Manual Verification
- Observe the boot sequence:
    - Verify that "confidence engine" is removed.
    - Verify move count is 335.
    - Confirm the transition to the main menu happens without a 2.2-second animated scroll.
    - Check that the menu items appear immediately after "overdex ready" (or the last boot line) in a way that feels like reaching a terminal prompt.
