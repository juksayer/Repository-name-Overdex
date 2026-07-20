# Workspace Viewer Refactor Task List

Refactor the Workspace Viewer into a conventional expandable tree structure.

- `[x]` Refactor `InstrumentTree.kt` model
    - [x] Decouple `navigateBack` from expansion state
    - [x] Simplify `executeSelected` for directory toggling
    - [x] Remove manual viewport/scrollOffset logic
- `[x]` Refactor `RetroComponents.kt` UI
    - [x] Transition `DirectoryTree` to `LazyColumn`
    - [x] Implement `LazyListState` sync for D-pad selection
    - [x] Preserve "Ownership of Time" reveal animation
- `[x]` Update `PokedexViewModel.kt` and screens
    - [x] Adjust state collection for the full tree projection
- `[ ]` Verification
    - [ ] Test multi-expansion
    - [ ] Test B navigation (move to parent without collapse)
    - [ ] Test selection scroll persistence
