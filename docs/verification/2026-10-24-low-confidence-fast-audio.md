# Show a favored Fast Move without calling it confirmed

The user requested an explicit low-confidence indication when audio favors one
candidate but cannot confirm it. The Battle HUD now adds **Favored · low confidence**
under that candidate, aligned right. Other remaining moves stay visible. A later
confirmed identity replaces the label with the existing checkmark.

## Evidence and presentation boundaries

- The live audio witness retains plausible comparisons at most once per 1.5 seconds,
  alongside its existing accepted matches. Each has a preserved 1.2-second WAV,
  reference scores, source/time and `referenceMatchAccepted = false`. Failed artifact
  preservation cannot create a measurement. The archive viewer calls these
  low-confidence, unconfirmed comparisons with side unassigned.
- The lower retention floor is spectrum similarity 0.85 and changing-spectrum
  similarity 0.65. These are heuristic comparison floors, not probabilities or
  validated accuracy levels. The strict identity thresholds are unchanged.
- A presentation preference requires two non-overlapping windows favoring the
  same move. Both active species and their move pools must be known; that move
  must be legal on only one side. All legal alternatives must have reference
  measurements, with a spectrum lead of at least 0.015 and a higher changing-spectrum
  score over the other legal candidates. Shared moves, ties, missing references
  and an illegal catalogue winner do not receive a preference.
- The preference expires after six seconds without supporting evidence. A conflicting
  retained comparison, an entry check, a changed combatant, QTE or reset clears it.
  Old/overlapping windows cannot become repeated corroboration. The existing
  battle-start and QTE exclusions apply before preference selection.
- This is presentation state with supporting Article IDs. It does not emit
  FastMoveIdentified, narrow the candidate set, earn a confirmation checkmark,
  change the energy forecast's candidate set, add attacks, or award energy.

The worker, analysis cadence and catalogue search cost are unchanged. Additional
comparison retention is bounded to at most about 0.77 MB of PCM per minute, plus
WAV headers and reference metadata. Actual retention depends on plausible scores,
deduplication and processing capacity; this is not continuous audio storage.

## Checks

The full unit suite passes **1,090 tests**, with no failures, errors or skips.
Debug app and instrumentation APKs build. New checks cover overlapping windows,
expiry, conflicts, both-side switches, shared moves, missing references, QTE,
later confirmation, retaining the original candidate alternatives and real PCM
preservation with rejected reference status. The UI check uses the actual Battle
HUD at its 415-pixel width to exercise the label, alternatives and later checkmark.

The retained Dragonair clip was also compared using the new rate limit. Eight
plausible windows would be retained, but none produces a side-resolved low-confidence
preference under these rules: competing catalogue/other-side sounds remain too
close. Across all 60 offline windows Dragon Breath still outranks Iron Tail, as
documented in the [audio review](2026-10-24-late-start-fast-audio.md). That pairwise
finding does not establish which combatant produced the mixed sound. The UI test
demonstrates the presentation using supplied comparison state, not recognition
accuracy on a live battle. A fresh recording remains necessary for acceptance.

The API 34 emulator reports **OK (1 test)** for the HUD check. A cold-boot System
UI not-responding dialog initially dimmed the screenshot; after dismissing it and
repeating the check, the label and Iron Tail alternative fit without clipping.
The later confirmed state shows the checkmark and removes the low-confidence label.

APK SHA256: `961d5a0a980ce8d1ee4e41b58a69b043d6952f909e42dc03f1bb52e7851addf0`.
Installed in place on Moondrop `MDPH00124112500414` after confirming no Overdex
service was running. Package update time is October 24, 2026, 09:21:39;
MainActivity launched successfully. Before/after hashes match for battle and
Team Select calibration, current team, selected calibration profile and HUD layout.
This update also includes the sparse-impact and late-start listening fixes.

Pixel `99261FFAZ0017J` rejected the preceding update with
`INSTALL_FAILED_INSUFFICIENT_STORAGE` (approximately 1 GB free). Its configuration
hashes also remain unchanged; no uninstall, data clear or match deletion was used.
After the user freed space, the Pixel reported 2.6 GB available and no active
Overdex service. The same tested APK installed in place successfully on October
24, 2026, at 09:27:32. MainActivity launched and resumed. Fresh before/after hashes
match for both calibrations, current team, selected calibration profile and HUD
layout. Both phones now have this update. No new live battle was run.
