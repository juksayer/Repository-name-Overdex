# Review of existing second Brutal Swing fills

## Source custody

The Moondrop's latest internal records on inspection remain the 14:44:55 match
`6b18a6fe-32c8-4aa0-8d38-33ff755f6324` and 14:53:07 match
`99fe41cb-a61f-40bd-994a-e6d7a6301a59`. Both are already backed up. Their archives
verified 365 and 280 artifacts respectively. Neither compact archive retains a
`PlayerChargeMoveControlsCrop`; this does not establish that the live witness did
not run or that no loose crop remains on the phone.

A newer 15:19 compact export of the second match was copied to the existing backup
as `latest-export.odxmatch.zip`: 2,363 Articles, 285 verified artifacts, same Match ID.
It also lacks full controls crops. The first match therefore has no directly
reviewed fill-height imagery in these archives. No phone data was deleted or altered.

The full screen recording retained for the second match supplies the visual evidence.
The user's statement that Brutal Swing was filled twice is contextual testimony,
not a substitute for independent measurement. No new test is needed for this review.

## Findings

Previous selected stable frames support approximately 80, 65, and 85 energy under
the experimental height/layer model (see `2026-10-23-charge-fill-height.md`). The
80-energy checkpoint exceeds two recorded 35-energy Brutal Swing costs. This is
consistent with the user's double-fill report, not proof of a general layer model.

Additional sampling inspected 21 frames at video times 82–112 seconds, then eight
nearby frames at 85.5–86.9 seconds. At 85.738, 86.014, and 86.113 seconds, both
button separators yield an intersection around 74.3–75.4 energy using the original
manually located centers and +/-2px separator tolerance. The count ledger instead
gives 90 energy throughout each approximate alignment interval (video +10.553 to
10.976 seconds relative to archive record start).

The surrounding images show an interaction with Brutal Swing around 85 seconds,
followed by both controls disappearing by 87 seconds. A bright press effect and
touch ring are visible. This is not a settled post-attack checkpoint. The raw
separator readings are retained; the apparent 75-energy result is not accepted as
an exact counter correction.

Possible explanations remain unseparated: button press effects, fill animation,
display debit before announcement-based expenditure, faulty layer interpretation,
or counted-event errors. The source-time alignment is approximate. Do not silently
select one explanation or alter recorded Fast Move counts/spends to force agreement.

## Consequence for reconciliation

Continue recording independent height readings during transitions, but require a
settled visible-button interval before using them to challenge stored energy.
Treat button press/disappearance and pending charge expenditure as transition
context. Two buttons agreeing is useful corroboration, but not sufficient proof
that the display is stationary or that the layer model is correct.

This review does not implement that reconciliation gate or enable automatic energy
correction. The live witness remains experimental measurement-only. The archived
ledger and replay have not been changed.

## Evidence

- Original video and match archives remain under
  `/home/sean/overdex-device-backups/2026-10-23-charge-video-99fe41cb/`.
- `second-fill-audit/` and `second-fill-transition/` contain source frames with
  PTS/time base, hashes, raw separator readings, all layer hypotheses and joint ranges.
- `second-fill-audit/contact.png` presents charge-button crops from the broad scan.
- Small numeric evidence is tracked in
  `charge-fill-height/99fe41cb-second-fill-review.json`.

Documentation/numeric review only; no Android source or APK changed.
