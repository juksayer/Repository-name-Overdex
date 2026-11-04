# Recall a saved team from Choose Your Party

## Report and behavior

The user could not select Turtonator, Camerupt, and Gourgeist while choosing a
party. The Moondrop has one owned Camerupt card; Turtonator and Gourgeist are
entries in an older named saved-team setup, not individual collection cards.
The user confirmed this distinction. The saved three-Pokemon loadout and all
its move choices validate against current references.

Previously a Saved Teams row only opened a draft. Applying it still required
USE THIS TEAM after the long import and move controls. A saved row now explicitly
says USE THIS TEAM: selecting it loads the exact species/forms, checks every
move set, applies a complete valid lineup, and closes the panel. It does not
create collection cards or automatically borrow a same-species card. Existing
linked specimen IDs are preserved. A team with outdated moves remains editable
with a visible review message; an unavailable exact form is not substituted.
The manual editor also keeps its readiness count and USE THIS TEAM action
outside the scrolling move/import fields, so applying a team stays reachable.
Closing, rescanning, superseding a lookup, or entering battle prevents a delayed
lookup from changing the current team. A failed save leaves the loaded draft
available for retry. The saved Gourgeist form is preserved as recorded.

## Fresh match review

The November 4 13:14 Moondrop archive has 3,175 Articles and 539 verified media
artifacts. Team Select correctly read and the user confirmed Turtonator,
Camerupt, and Gourgeist, while configured moves still belonged to Golbat,
Gengar, and Drapion. All three differences were recorded. Applying the desired
saved team connects its move choices to the current lineup; saved loadouts with
null owned-card links do not gain links automatically.

The recording also provides live follow-up for the preceding type/audio repair:
124 of 127 published type readings match complete visible pairs. Three faded
Grimmsnarl frames still publish Dark alone after missing the Fairy disk. Those
source crops are retained privately for a separate missed-circle regression.
This is not calibration movement. All 55 audio windows use declared
observed-species legal pools; 52 contain both sides, with three switching
windows missing one side. No returned candidate is outside its declared pool.

The decisive YOU WIN result is recorded, but GO/MatchStarted is absent. Five
named charged moves and 30 Fast Move uses are archived. Actual attack totals
were not supplied, so these counts are not ground truth; close HP-derived pairs
and empty charge-button label reads remain separate review items. The historical
archive is unchanged. Its SHA-256 is
`79cbaf9c8760c2ae05f72e80178002cd289aa49a7fb715000b7951d091156164`.

## Verification

- Focused FieldTeamEditor suite: 24 tests pass, including six new regressions
  for the reported saved lineup, linked IDs, outdated moves, unavailable forms,
  battle-start races, and persistence failure/retry.
- The initial Android run exposed the existing below-fold manual apply action.
  After pinning the footer, all three field UI tests pass: scan all three move
  sets while the action stays visible, apply a saved lineup in one selection,
  and rescan without applying it.
- Final full JVM suite: **1,671 tests across 247 suites**, zero failures,
  errors, or skips. Debug APK build passes.
- Android API 34 isolated emulator: **OK (7 tests)** across the field editor,
  active detail confirmation, and exact-ID database deletion. No skips.
- Independent read-only review found no remaining blockers.
- Installed and launched the tested debug APK on idle Moondrop
  `MDPH00124112500414`. Installed SHA-256 matches the build:
  `24af8c0be34fd4e5ffabb9c610035bef0f5bc090762ea90b9d117a59eac249f3`.
- Before/after backups contain 337 files each. All 315 compared stable user-data,
  current-team, saved-team and calibration files are unchanged. All four Room
  tables have identical logical contents, including 29 owned Pokemon. The newest
  match archive is unchanged. No test instrumentation runs on the user's phone.

Private backups, test/build logs, source review and device screenshots are under
`/home/sean/overdex-device-backups/2026-11-04-cards-and-new-match/`.

A real Team Select/battle after installation remains the acceptance boundary for
service-window dismissal and configured-move continuity. The new actions are
validated on Android UI; the historical archive is not rewritten.
