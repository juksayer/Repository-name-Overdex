# Pixel cache trial — November 12, 2026

Device: Pixel 4, 99261FFAZ0017J. Installed APK verified in the preceding install:
`4ebe3fa846c98258f1e1ccf1cb4238fa86ff5b60befc3d930fac3cf8e51d9af0`.
Match: `09cb2cae-7620-4be8-a27b-dc4fd75fe7dd`, captured starting 14:26 local.
Archive and diagnostics copied intact to the 1 TB drive under
`2026-11-12-pixel-cache-trial/`. Device originals retained.

## Outcome and trainer report

Trainer reports a win by opponent concession during the final charged move execution.
Archive contains accepted `YOU WIN!` text and `MatchEnded(WIN)`. This supports
WIN; the concession mechanism is trainer testimony, not independently established
by these two records. Do not convert this report into a final KO or rewrite the archive.
Only the player Turtonator faint is explicitly recorded in this archive.

## Processing

All 2,914 accepted testimony records completed; no active sequence at export.
Cache: 547 hits, 320 misses, no oversized bypasses. Zero retained entries at
export is consistent with Match release clearing storage; cumulative hit/miss
counts remain available. The existing HP diagnostics peak at 17 pending readings
and 3.368 seconds oldest tracked age. Final HP pending count is zero.
This is a different match from earlier tests, not a controlled performance comparison.

Cumulative stage totals across the entire match (not per-event latency):

| Stage | Invocations | Elapsed seconds | Thread CPU seconds |
| --- | ---: | ---: | ---: |
| raw_publication | 2914 | 32.085 | 7.697 |
| cadence | 2914 | 19.696 | 4.373 |
| energy | 2914 | 14.311 | 3.153 |
| fast_move | 2914 | 12.828 | 3.268 |
| charge_announcement | 2914 | 9.562 | 2.142 |
| faint_reconciliation | 2914 | 6.001 | 1.229 |
| player_information | 2914 | 5.652 | 1.131 |
| hud_resources | 2914 | 4.360 | 1.190 |
| identity_projection | 2914 | 3.167 | 0.838 |
| hud_references | 2914 | 2.356 | 0.445 |
| raw_preparation | 2914 | 1.791 | 0.429 |
| legacy_interpretation | 2914 | 1.200 | 0.294 |
| attack_and_cues | 2914 | 0.964 | 0.208 |
| crop_delivery | 2914 | 0.948 | 0.191 |
| match_start | 2914 | 0.933 | 0.191 |
| charge_qte | 2914 | 0.882 | 0.194 |
| identity_preparation | 2914 | 0.639 | 0.193 |

Raw publication is now the largest measured stage. Its elapsed time includes
append-side work and the HP append-boundary diagnostic callback; this instrument
alone does not isolate subscriber cost, lock contention or scheduling. Next
measurement should separate those components before changing the publication
contract. These numbers do not prove that caching eliminated live delays.

## Charged move positions

Saved configuration: Turtonator Brutal Swing / Dragon Pulse; Camerupt Rock Tomb /
Earth Power; Gourgeist Shadow Ball / Seed Bomb. `ownedPokemon` is null for each
configured team slot, so this archive does not identify a particular owned card.
The configuration also calls Gourgeist `(Average)` and gives it Incinerate; treat
these as saved inputs, not facts verified from gameplay. Do not infer size.

Charge label OCR is largely blank. One accepted label reads Seed Bomb in slot 1;
this disagrees with the configured Gourgeist order, but one frame does not meet
the current repeated-label confirmation rule. Turtonator has no legible charge
label in this archive. Announcement inference reports Dragon Claw, which is not
proof of button placement and requires separate OCR review before trusting it.

PlayerBattleInformationInference checks repeated move labels for membership in
the configured move set, not slot equality. FieldTeamEditor records moves in
selection order; owned cards supply chargedMove1 then chargedMove2. Thus the saved
order is important and can disagree with the phone. The affected card surface
and correct order are requested from the trainer before changing saved data or
presentation behavior. No production changes, build, install or commit in this review.
