# Task List — Flattening Boot Sequence and Main Menu

- [x] Refactor `MainMenuScreen.kt` to flatten layout hierarchy
    - [x] Change outer Column modifier to `fillMaxWidth()`
    - [x] Remove intermediate `Column` wrappers
    - [x] Remove artificial paddings and spacers between boot and menu
- [x] Verify continuous transcript behavior during boot
- [x] Verify menu visibility immediately after boot completes
