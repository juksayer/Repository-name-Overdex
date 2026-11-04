# Delete an owned Pokemon card from its active detail screen

## Report and behavior

The user could not find how to delete old test cards, such as a 9,000 CP
Bulbasaur. A delete action existed on an unused legacy detail screen; the active
binder opens SpecimenDetailScreen, which had no delete control.

The active detail LCD now offers EDIT CARD and DELETE CARD. SELECT also opens
confirmation. KEEP CARD is selected initially, so pressing A immediately cancels.
Down then A or the explicit confirmation LCD button deletes. B or system Back
cancels. The display identifies the selected specimen and explains that the card
is removed from all binders while recorded matches and saved teams stay.

Deletion targets only the selected owned ID and is awaited before returning to
the binder. Repeated input cannot duplicate a pending write or navigate away
through the detail controls. Errors remain visible with retry/cancel available.
Other specimens, match archives, battle records, and saved lineup snapshots are
unchanged. A saved lineup can still use its species/moves snapshot when a linked
card is missing; editing that card's moves requests a replacement.

The user did not request deletion of any particular real card, so no phone card
is deleted during development or validation. Tests use generated cards in
isolated databases.

## Verification

- Three Android detail tests use the production detail composable and actual
  shell, GlassShield, LCD and instrument buttons with isolated Room storage.
  They verify default A/Keep/B cancellation, one awaited delete with repeated
  input blocked, and visible write failure with cancellation.
- A fourth Android test deletes a generated card from a temporary persistent
  database and verifies another copy, another species and their team memberships
  survive reopening. No phone card is used for this destructive test.
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

On the Moondrop, navigated BATTLE -> BINDERS -> All Owned -> Camerupt with the
instrument buttons. EDIT CARD and DELETE CARD are visible on the LCD. SELECT
opens the confirmation with KEEP CARD highlighted. B cancels without leaving
the card; another B returns to its binder. After this check all 29 owned cards and all four Room
tables remain unchanged, as do the 315 compared user-data/calibration files.
A real deletion on the user's phone is deliberately not part of validation.
