# October 12: battle HP, resources, faint attribution, and replay energy

Registration work is paused at the user's request. Existing registration changes,
owned cards, teams, and calibration data were preserved.

## Recorded evidence

Reviewed the latest two Moondrop archives, `2026-10-11_12-24-13.odxmatch.zip`
and `2026-10-11_12-31-54.odxmatch.zip`. They contain six and one canonical
Fast Move uses respectively. Their 38 and 19 recorded HP measurements are all
from the opponent's HP bar; neither has player HP measurements. Thus they lack
the recipient-side HP evidence needed to count the opponent's attacks reliably.
Neither contains a recorded `MatchEnded`.

A retained October 10 battlefield image reproduced the player HP-reader failure.
The saved box is tight: the real bar occupies over 90% of its width, and adjacent
white Pokémon artwork joins the bar's outline in the horizontal scan. The reader
previously rejected this geometry. It now locates the actual endcaps near the
crop edges while retaining fill, aspect, and rectangular-outline requirements.
The two real image fixtures and their source bounds are in
`app/src/test/resources/battle/hp/`. No calibration coordinates were moved.

## Changes

- Added an independent player shield crop, calibration entry, witness, typed
  testimony, archive mapping, and replay handling. Older profiles default to
  mirroring their saved opponent shield region. Missing counts remain unknown.
- Entry, species changes, and charged-move announcements open a three-second
  resource observation window. Two agreeing resource frames confirm a changed
  count using the first frame's timestamp; repeated ball snapshots retain fresh
  timestamps for distinguishing a switch from a faint.
- Both sides now have a Poké Ball faint tracker. It rejects stale count frames
  and impossible count increases. A delayed faint for Sealeo does not clear
  Vaporeon's current species/move context. Fast-move impact audio is not used as
  faint-cry corroboration.
- Named pending Fast Move uses survive a switch and can be resolved later
  against their original species. Motion, charge-button shade, and unclassified
  artifacts alone do not award exact Fast Move energy.
- Replay has three Poké Balls and two shields for each side. Unknown counts use
  outlines; spent resources are dimmed. Backward seeking recomputes their state.
- Replay energy is separated by side and species, retained across switches,
  capped at 100 before spending, and cleared for the fainted combatant only.
  Duplicate energy derived from the same canonical use is counted once. The LCD
  labels it `RECORDED ENERGY` with `~`: missed attacks or missed charged moves
  can make the actual balance differ in either direction.
- Opponent sprites are 10% smaller. Reduced outer padding and an added central
  gap increase the separation between combatants. Player sprite sizing is unchanged.
- `BATTLE → Match → Match Summary` now opens the current Timeline summary, whose
  LCD exposes `OPEN RECORDED MATCHES`. It previously opened the legacy battle
  screen and showed only `No active battle record` after a restart.

## Verification

- Full `:app:testDebugUnitTest`: 526 tests, 102 suites, zero failures/errors/skips.
- `:app:assembleDebug`: successful. `git diff --check`: clean.
- APK SHA-256: `bfb538f5cb5f8b70d892e031361b7f1c8c46828e3b54d00135d9c1da867c22cf`.
- Installed in place on Moondrop `MDPH00124112500414`; no uninstall or data clear.
- Before/after byte comparisons confirmed six preference files unchanged:
  battle HUD layout, capture templates, current battle team, selected calibration
  profile, battle calibration, and Team Select calibration.
- On the phone, used the instrument buttons to open the corrected Match Summary
  route, the recorded-match browser, and the newest archive's replay.
- At 48.5 seconds: player Camerupt on the left, opponent Sealeo on the right,
  player balls 3, opponent balls 2, opponent shields 2, player shields outlined
  because the old archive has no player shield measurements.
- At 85.8 seconds: opponent Vaporeon remains alive with one opponent Poké Ball.
  Sealeo's delayed faint does not cross out Vaporeon. LCD drag seeking in both
  directions, tap play/pause, and scrolling to the energy display worked.
- No AndroidRuntime errors were reported for the installed app's process during
  this replay check. Screenshots and logs are under
  `/tmp/overdex-battle-20261012/` (temporary local evidence).

## Remaining acceptance boundary

This verifies recorded-frame HP recognition, deterministic inference/accounting,
archive compatibility, and replay presentation on the actual phone. It does not
prove complete live Fast Move counting, correct live move identification, or
complete shield/faint capture. The old archives cannot supply attacks they never
recorded. A new controlled battle should include a known player Fast Move, a
short idle interval while the opponent attacks, a shield use, and a faint/switch.
Compare both sides' HP measurements and canonical use counts with the gameplay
before treating energy as reliable advice.
