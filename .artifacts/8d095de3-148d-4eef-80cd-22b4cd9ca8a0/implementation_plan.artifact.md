# Implementation Plan — Match Sight Bootstrapping (Brick A)

Establish the first developer-facing diagnostic surface for **Match**. This brick creates the destination for Match diagnostics without modifying the Battle architecture or introducing new publication mechanisms.

## User Review Required

> [!IMPORTANT]
> This brick is strictly a presentation change. No new `Match` state will be introduced, and no new publication mechanisms (like `StateFlow<Match?>`) will be added to `PokedexViewModel`.

### Presentation Invariant

Match Sight is a read-only diagnostic surface.

It presents existing Battle state only.

If no Battle state is available, Match Sight presents the absence of diagnostic information.

It never fabricates or derives information.

## Proposed Changes

### Core Model & Navigation

#### [NEW] [InstrumentCommand.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/model/navigation/InstrumentCommand.kt)
- Move the `InstrumentCommand` sealed interface from `InstrumentTree.kt` to this new file to clarify ownership.
- Add `data object OpenMatchSight : InstrumentCommand` to the interface.

#### [MODIFY] [InstrumentTree.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/model/navigation/InstrumentTree.kt)
- Remove the `InstrumentCommand` definition (it now lives in its own file).
- Update the default instrument tree initialization (if it were here, but it's in `PokedexViewModel.kt`).

#### [MODIFY] [PokedexViewModel.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/PokedexViewModel.kt)
- Update the `instrumentTree` initialization to include `ActionNode("sight", InstrumentCommand.OpenMatchSight)` under the `battle` directory.

### Developer UI

#### [MODIFY] [ResearcherModeScreen.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/screens/ResearcherModeScreen.kt)
- Add `MATCH_SIGHT` to the `ResearcherFocus` enum.
- Add a "LAUNCH MATCH SIGHT" button in the `ResearcherModeOverlay` under the "SIGNAL OBSERVATORY" section.
- Wire the button to an `onLaunchMatchSight` callback.

#### [NEW] [MatchSightScreen.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/ui/screens/MatchSightScreen.kt)
- Create a new Composable screen for Match diagnostics.
- The initial implementation displays a diagnostic placeholder:
    - Header: `MATCH SIGHT`
    - Status: `No active diagnostics.`
    - Context: `Waiting for Battle...`

### Application Integration

#### [MODIFY] [MainActivity.kt](file:///home/sean/AndroidStudioProjects/Overdex/app/src/main/java/com/example/overdex/MainActivity.kt)
- Handle `InstrumentCommand.OpenMatchSight` in the `pendingCommand` collection logic to navigate to `"match_sight"`.
- Add `composable("match_sight")` to the `NavHost`.
- Pass `onLaunchMatchSight = { navController.navigate("match_sight") }` to `ODXFiShell` and `ResearcherModeOverlay`.

## Future Growth

Future Match Sight bricks shall consume additional Match state only after that state exists through independently approved Battle bricks.

They shall not require new Match state or ownership changes.

## Verification Plan

### Automated Tests
- N/A.

### Manual Verification
1. **Navigation**: Open the Developer Menu and verify "LAUNCH MATCH SIGHT" exists and functions.
2. **Terminal Navigation**: Verify `battle/sight` exists in the instrument tree and navigates correctly.
3. **Stability**: Ensure `MatchSightScreen` loads and displays the placeholder without crashing, regardless of whether a match is active.
4. **Non-Interference**: Verify that starting and stopping a match (via normal observation) is unaffected by the presence of the Match Sight screen.
5. **Read-Only**: Confirm no UI elements in Match Sight attempt to modify application or battle state.
6. **Exit Behavior**: Verify that leaving Match Sight returns to the previous screen without affecting Battle state.

## Definition of Done

The implementation is complete when:
- Match Sight is reachable through developer navigation.
- Match Sight loads without requiring an active Match.
- Match Sight displays only placeholder diagnostics.
- No Battle architecture has changed.
- No publication mechanisms have been introduced.
- Existing Battle behavior is unchanged.
