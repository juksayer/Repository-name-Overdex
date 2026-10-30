# Current Team move corrections — 2026-10-30

## Problem and resulting behavior

Current Team already opened its team-building wizard when a member was selected, but that wizard reset the species and moves. Correcting Greninja's Hydro Cannon entry to Night Slash therefore looked like replacing or rebuilding the Pokémon.

Selecting a Current Team member now opens explicit **Edit Fast Move**, **Edit Charged Moves**, and **Replace Pokémon** actions. The edit paths load the member's exact species/form and retain its existing choices. They reuse the current move selection screens and the ODX-Fi's A/B and directional controls; the CRT GlassShield stays in place. B cancels a move draft without saving.

Saving a move correction changes the same current-team member and, when linked, the same owned card. The slot, specimen UUID, species/form, nickname, CP, variants, teams/binder memberships, creation timestamp, and other card fields remain intact. A linked card with a mismatched UUID/species/form is rejected. Loading/saving and errors are visible in the LCD. Saving during an active battle is rejected.

## Battle history remains attached

`BattleRecordQuery` associates recorded events with `event.pokemon.ownedPokemonId`. `OwnedPokemonBattleSnapshot` freezes that specimen UUID in each match's configuration. The move correction uses `copy` on the existing member/card and retains that ID; it does not create another specimen or rewrite an archived configuration, event, or move name. New choices apply to the current/future team configuration. Past matches retain their recorded moves and evidence, including any originally mistaken entry.

Unlinked current-team members remain unlinked: correcting a move does not create an owned card or invent an association with past events. Named saved teams are snapshots; this correction changes the active team and the linked owned card, not unrelated named snapshots.

## Phone acceptance path

1. On the Moondrop, open **Battle → Current Team** in Overdex.
2. Highlight Greninja with Up/Down and press A.
3. Highlight **Edit Charged Moves** and press A. Existing charged moves are selected.
4. Highlight Hydro Cannon and press A to deselect it, then select Night Slash with A. Keep the desired second move selected, if any.
5. Highlight **Save Moves** and press A. The team reappears with Night Slash and a saved acknowledgement in the LCD.
6. Close/reopen Current Team and the linked owned card. Confirm moves persist while the card identity and team memberships are unchanged. Search an existing match/event for that specimen to confirm its prior history is still present.

Normal behavior: the same Greninja occupies the same team slot/card and retains its prior battle history. Abnormal behavior: new UUID/card, missing prior history, changed form, altered teammates, lost second move, or a silent failed save.

## Validation

- Added nine pure regression tests for correcting only the selected member, serialized team round trip, same-card updates, preserving old recorded history, removing the second charged move, unlinked members, stale drafts, exact form identity, legal move choices, and linked-card identity checks.
- Coordinated focused checks passed: 67 tests. Full unit suite passed: 1,547
  tests, zero failures/errors/skips. The debug APK built and was installed on idle
  Moondrop `MDPH00124112500414` with data preserved; its installed hash matches
  the tested APK. See [coordinated verification](2026-10-30-new-matches-and-move-correction.md#validation).
- On the phone, used the new member actions and physical controls to replace
  Greninja's Hydro Cannon with Night Slash, retaining Water Shuriken / Aerial Ace,
  and Victreebel's Leaf Blade with Leaf Tornado, retaining Razor Leaf / Acid Spray.
  Returned to Current Team and verified both corrected lineups.
- Compared the persisted Room rows before/after. Greninja changed only
  `chargedMove1` and `updatedAt`; Victreebel changed only `chargedMove2` and
  `updatedAt`. UUIDs, creation times, memberships and every other field are equal;
  total owned-card count is unchanged. Current-team slots, species, Fast Moves
  and owned IDs are unchanged. Original match hashes still match desktop copies.
- Separately used Saved Teams → Replace with current lineup → Save for the
  matching named shadow team. Its team ID and specimen IDs are retained; all
  other named teams remain unchanged. This was an explicit replacement, not an
  automatic overwrite by the move editor.
- A specimen-history regression proves old events still match the same UUID and
  keep their old recorded move names. The physical history search screen was not
  separately exercised; source archives themselves remain byte-for-byte intact.

## Touched implementation

- `app/src/main/java/com/example/overdex/ui/screens/CurrentBattleTeamScreen.kt`
- `app/src/main/java/com/example/overdex/ui/PokedexViewModel.kt` (`saveCurrentBattleTeamMemberMoves` only)
- `app/src/main/java/com/example/overdex/battle/team/CurrentBattleTeamMoveEdit.kt`
- `app/src/test/java/com/example/overdex/battle/team/CurrentBattleTeamMoveEditTest.kt`
