# Walkthrough — Match Sight Bootstrapping (Brick A)

I have successfully bootstrapped **Match Sight**, the first developer-facing diagnostic surface for the Battle module. This implementation establishes the destination for future Match diagnostics while strictly adhering to the architectural boundaries of Brick A.

## Changes

### Core Model & Navigation

#### [InstrumentCommand.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/model/navigation/InstrumentCommand.kt) [NEW]
- Created a dedicated file for `InstrumentCommand` to clarify ownership and separate it from tree logic.
- Added `OpenMatchSight` command.

#### [InstrumentTree.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/model/navigation/InstrumentTree.kt) [MODIFY]
- Removed the `InstrumentCommand` definition as it now resides in its own file.

#### [PokedexViewModel.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/PokedexViewModel.kt) [MODIFY]
- Added `ActionNode("sight", InstrumentCommand.OpenMatchSight)` to the `battle` directory in the instrument tree.
- Match Sight is now reachable via the terminal at `battle/sight`.

### Developer UI

#### [ResearcherModeScreen.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/screens/ResearcherModeScreen.kt) [MODIFY]
- Added `MATCH_SIGHT` to the `ResearcherFocus` enum.
- Added a new **LAUNCH MATCH SIGHT** button to the Researcher Overlay for quick access.

#### [MatchSightScreen.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/screens/MatchSightScreen.kt) [NEW]
- Implemented a clean diagnostic surface with a header and "No active diagnostics" placeholder.
- Designed as a read-only consumer of state, ready for future expansion.

### Application Integration

#### [MainActivity.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/MainActivity.kt) [MODIFY]
- Added the `match_sight` navigation route.
- Wired the `OpenMatchSight` command to navigate to the new screen.
- Integrated the `onLaunchMatchSight` callback into `ODXFiShell` and `ResearcherModeOverlay`.

## Verification Results

### Manual Verification
- **Terminal Navigation**: Verified that navigating to `battle/sight` in the instrument terminal successfully opens the Match Sight screen.
- **Developer Menu**: Verified that the "LAUNCH MATCH SIGHT" button appears in the Researcher Overlay and correctly navigates to the diagnostic surface.
- **Stability**: Confirmed the screen loads correctly without an active match and handles navigation state (BACK button/B key) as expected.
- **Read-Only Invariant**: Confirmed the screen is entirely passive and does not modify any application or battle state.

## Final State

Match Sight is now the canonical developer diagnostic surface for the Battle module. Future bricks can now expand this screen by displaying additional state already owned by `Match`, without needing to re-negotiate the UI architecture.
