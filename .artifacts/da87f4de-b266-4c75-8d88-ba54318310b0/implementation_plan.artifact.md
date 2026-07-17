# Implementation Plan — Flattening Boot Sequence and Main Menu

Identify and remove the layout constraints causing the Main Menu to be pushed below the visible viewport after the boot sequence completes. The terminal should behave as one continuous transcript.

## Proposed Changes

### [Component] UI — Main Menu Layout

#### [MODIFY] [MainMenuScreen.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/screens/MainMenuScreen.kt)
- **Continuous Transcript**: Remove the conceptual and structural separation between "boot report" and "interactive menu." Everything will be rendered as a single stream of terminal output.
- **Flatten Hierarchy**: Remove intermediate `Column` wrappers for sections. All terminal lines, headers, and menu options will be direct children of the primary scrollable `Column`.
- **Natural Sizing**:
    - Change the outer Column's `Modifier.fillMaxSize()` to `Modifier.fillMaxWidth()`.
    - Eliminate any explicit or inherited height constraint that prevents the terminal from naturally sizing to its content.
- **Tighten Spacing**: Remove large bottom paddings (32.dp) and redundant spacers that were artificially separating the boot sequence from the system check and menu.

## Verification Plan

### Manual Verification
1.  **Boot Transition**: Launch the app (or clear session state). Observe the boot sequence reveal.
2.  **Menu Visibility**: Verify that the "modules" section appears immediately following the final boot line, well within the visible terminal area.
3.  **Terminal Interaction**: Verify that menu navigation works as expected and items are laid out naturally as part of the transcript.
4.  **No Transitions**: Ensure there are no animated scrolling or screen-sliding transitions during the reveal.

### Automated Tests
- N/A (Manual visual verification is required for layout regressions).

## Out of Scope
- Do not introduce automatic scrolling to compensate for layout issues. The fix must be structural (layout-driven).
