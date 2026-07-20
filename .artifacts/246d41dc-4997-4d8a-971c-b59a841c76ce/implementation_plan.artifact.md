# Implementation Plan: Conventional Workspace Tree

Refactor the Workspace Viewer (directory tree) to behave like a traditional file explorer (IDE-style), enabling simultaneous expansion of multiple branches and providing intuitive navigation.

## Proposed Changes

### [Component Name] Navigation & Tree Model

#### [MODIFY] [InstrumentTree.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/model/navigation/InstrumentTree.kt)
-   **Expansion Independence**: Refactor `navigateBack` and `executeSelected` to decouple selection from expansion.
    -   `A` (Select) will continue to toggle expansion for directories, but also execute actions for children.
    -   New `expand()` and `collapse()` methods to be triggered by D-pad Right and Left.
    -   `B` (Back) will now move selection to the parent node *without* automatically collapsing it, providing a more predictable "back" navigation.
-   **Recursive Projection**: Ensure `project()` remains the single source of truth for the visible flat list, correctly reflecting all currently expanded paths.
-   **Scroll Logic**: Keep `scrollOffset` but ensure it behaves correctly as the tree grows/shrinks.

#### [MODIFY] [PokedexViewModel.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/PokedexViewModel.kt)
-   Add `handleLeft()` and `handleRight()` methods that delegate to `instrumentTree.expand()` and `instrumentTree.collapse()`.

#### [MODIFY] [MainActivity.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/MainActivity.kt)
-   Wire `PokedexFrame`'s `onLeft` and `onRight` callbacks to `viewModel.handleLeft()` and `viewModel.handleRight()`.

### [Component Name] UI Rendering

#### [MODIFY] [RetroComponents.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/components/RetroComponents.kt)
-   **`DirectoryTree` Refactor**:
    -   Evaluate switching from `Column` to `LazyColumn` for smoother scrolling and efficiency.
    -   Refine the `revealCount` animation to only trigger on first boot or major state changes, avoiding the "rebuilding" flash when simply toggling a folder.
    -   Improve visual depth cues (e.g., vertical guide lines or consistent indentation).

## Verification Plan

### Manual Verification
-   **Multi-Expansion**: Expand "specimens", then navigate to and expand "battle". Verify both remain visible.
-   **D-pad Navigation**:
    -   Right Arrow on a folder should expand it.
    -   Left Arrow on a folder should collapse it.
    -   Left Arrow on a child should move selection to the parent.
-   **Stability**: Verify that siblings do not disappear when a folder is toggled.
-   **Scroll Persistence**: Ensure the selected item remains in the viewport even when the tree size changes.
