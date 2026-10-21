# October 19 afternoon match review

Device: Moondrop MD_PH_001 (`MDPH00124112500414`). All five internal archives were copied read-only and passed manifest/artifact verification. Original phone archives and saved teams were not edited.

The user confirmed deliberately leaving all player Pokemon idle in the 11:12 match. Other counts below are recorded events, not ground-truth attack counts. The controlled two-phone match has not been performed.

| Recording | Result | Player Fast Moves recorded | Opponent Fast Moves recorded |
|---|---|---|---|
| 11:02:46 | WIN | Abomasnow: 6, Camerupt: 4 | None: 2, Sneasel: 3, Sealeo: 2, Vaporeon: 2 |
| 11:06:24 | WIN | Abomasnow: 16, Gourgeist: 7, Camerupt: 3 | Combusken: 6, Magcargo: 4, Flareon: 3 |
| 11:10:25 | WIN | Abomasnow: 20 | Plusle: 6, Electrode: 2, Jolteon: 4 |
| 11:12:31 | LOSS | 0 | Sneasel: 23 |
| 15:23:38 | WIN | Abomasnow: 19, Camerupt: 6 | Combusken: 10, Magcargo: 4, Flareon: 4 |

## Findings and corrections

- The confirmed idle match records **zero player Fast Moves and zero player generated energy**. It nevertheless briefly replaced configured Camerupt Incinerate with Rock Smash from recipient-area visual rhythm alone. That rhythm now remains supporting evidence/provisional candidates; it cannot certify a move identity. A saved player move needs repeated impact evidence or independent measured families before contradictory cadence replaces it. Independent raw articles remain intact.
- The same idle match identifies Sneasel Feint Attack by border cadence and then by repeated reference-audio matching. The regression preserves that identification.
- Bare `Gourgeist` OCR previously produced a false mismatch against `Gourgeist (Average)`. Matching national ID plus base name now reports compatibility without confirming an unseen form. Multiple configured copies remain ambiguous. The user reports an XL size label; the saved configuration was not rewritten or treated as authoritative form evidence.
- The latest match published 27 charged-button name readings, all blank, and no charged-button effectiveness observations. Name crops are saved at y≈1892 on the 1080×2400 display; the actual names are around y≈2300 in retained battle frames. Saved calibration/profile bytes were preserved. These two boxes still need to cover the bottom labels.
- Button OCR selected a crop before waiting for the shared reader, then discarded it as stale when its turn arrived. Effectiveness checks were idle-only and could starve throughout combat. Both now receive routine queued turns, replacing a waiting sample with the newest crop at admission. Species/result priority remains in the shared scheduler; optional pointer diagnostics remain idle-only.
- A ready charged button pushes its name lower. The runtime reading margin now includes up to 65 extra physical rows on a 2400-row display, bounded by the saved crop height and frame edge, with actual crop geometry retained. OCR input is enlarged at least 2×. These operations do not rewrite calibration.
- Real OCR read `NOT VERY EFFECTIIVE` where the gesture bar crosses the text. The resolver accepts one damaged letter in `EFFECTIVE` only when the complete distinguishing prefix (`NOT VERY` or `SUPER`) remains. Ambiguous/more damaged fragments remain unresolved. A charged-button matchup hint is never proof of a Fast Move being performed.
- A legible complete move-name line now survives a damaged effectiveness line below it (recorded example: `SEED BOMB` / `CIDER EFFECTIVE`). Matching still requires a unique legal move. Complete names can use the existing explicit OCR lookalike alphabet plus a single E/F substitution (recorded `FARTH POWER`). Two different legal names in one reading remain ambiguous; truncated names are not completed by this button matcher. A selected button and a later visible move announcement remain separate required evidence for executing a charge move.

## Remaining measurement limits

- The 11:06 match has no recorded MatchStarted/countdown article. The other four have a recorded or derived start.
- All five have a result (four wins, one loss). Three lack a raw final-faint witness even though a result is present; result evidence and inferred replay KO must remain distinct.
- First species publications range from 632 to 2404 ms after their cited observation timestamp. This does not measure latency from the first visible appearance on screen. Species side assignments in these five archives are consistent with the observed teams.
- Strong opponent identities include Sneasel Feint Attack (cadence + audio), Flareon Fire Spin (border + motion/effectiveness), and Jolteon Volt Switch (border + effectiveness). Plusle Quick Attack and Combusken Ember include explicit same-duration/higher-energy assumptions. Electrode Spark was visual-only and must not count as a confirmed identity under the new rule.
- All retained WAVs contain nonzero signal. Archives contain 48 kHz stereo capture and 8 kHz mono analysis clips. Non-silence proves audio data exists, not that every reference match is correct.
- Review statistics and exact archive hashes are in `2026-10-19-afternoon-match-review.json`. Source-time and publication-time distinctions are retained.

## Validation

- 919 unit tests passed with zero failures; debug and instrumentation APK builds succeeded.
- Six tests passed on the Moondrop, including real button images, four readers queued behind a busy OCR lane, pointer/label handling, and archive round-trip checks.
- Installed APK SHA-256: `003763943ed7637a296a9d532cee5c23b599de3160bcce250a5af381aedfcedf`.
- All nine calibration profiles and both saved calibration preference files are byte-identical to the pre-install backup.
- App launched successfully. A new real battle is still required to measure the changed live readers. Previously archived conclusions were not rewritten.
