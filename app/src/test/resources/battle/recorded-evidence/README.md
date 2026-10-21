# Recorded HP and shield regression fixtures

These ten images are byte-for-byte copies of the local diagnostic captures used
by `BattlefieldHpTrackerTest` and `OpponentBattleResourceCounterTest`. They are
committed test inputs; the complete device-review folders remain local.

`manifest.json` records each original relative path and SHA-256. Copying these
fixtures changes neither the pixels nor the measurements asserted by the tests.
The original local evidence is retained under `docs/verification/evidence/`.

- `2026-10-12-1546`: camera movement, clipped Sealeo HP bounds, central charged
  recipient ambiguity, disappearing shield badges, and visible spent shields.
- `2026-10-16-hp-outline`: a white Abomasnow sprite overlapping its HP outline.
- `2026-10-16-camerupt-hp`: orange Camerupt artwork next to the HP outline.
