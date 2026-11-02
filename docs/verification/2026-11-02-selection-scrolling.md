# Selected-row visibility — November 2, 2026

## Behavior

The shared `MoveSelectionStep` now connects its `LazyColumn` position to the
handheld navigation selection through `HandheldListSync`. A move below the
visible edge is revealed when highlighted; moving upward, reopening an already
equipped move, and reaching the final save/next action also reveal that row.

Both production callers use this component:

- `CurrentBattleTeamScreen`: editing Fast Moves and charged moves on the current
  team, and choosing moves while adding a member.
- `AddOwnedPokemonWizard`: Fast Move and charged-move selection during specimen
  registration.

The Current Team overview had the same missing connection, so its member rows
and lower actions now follow selection too. Its empty-team explanatory row is
accounted for separately from selectable rows. The existing shared sync effect
also observes list identity and item count, so asynchronously loaded content is
revealed even if the selected index stays unchanged.

These changes only move the viewport and expose the existing selection to
accessibility. They do not change the equipped moves or individual Pokémon
identity/history.

## Verification

Debug and Android-test APK builds passed. Android instrumentation on the API 34
`Overdex_API34_Check` emulator completed with **`OK (7 tests)`**:

- Four new `MoveSelectionNavigationDeviceTest` cases use deliberately short
  340 dp wide viewports. They check every move while navigating down and up,
  opening an equipped move far down the list, reaching `SAVE MOVES`, returning
  to the first move/member, reaching lower Current Team actions, and loading a
  team without another navigation tap. Bounds checks require the whole selected
  row to fit inside the list, rather than merely exist in composition.
- Three existing `RegistrationNavigationDeviceTest` cases also passed, covering
  species-browser navigation in both directions, held instrument-button input,
  and returning to typing after leaving keyboard focus.

The final full unit suite passed 1,620 tests across 242 suites; debug and test
APK builds passed. The update was installed on both physical phones with no
Droidball capture running. On the Moondrop, navigated BATTLE → Current Team →
Gastrodon → Edit Fast Move using the instrument buttons. The picker opened
with equipped Mud Slap (last of 17 rows) fully visible. Sixteen Up presses
revealed Hidden Power (Bug), and sixteen Down presses returned to a fully
visible Mud Slap. B canceled back to the directory without saving a move.
The shared component's charged-move/save-row path is covered by native tests.
Pixel 4 launch was checked; its empty Current Team was left untouched.

Critical settings and logical Pokédex/owned-card rows were preserved on both
phones. The recorded battle pipeline was not exercised in a new live match
following this update; that remains a separate audio/observation boundary.
