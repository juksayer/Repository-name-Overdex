# October 24 Pixel review: HUD artwork blocked badge recovery

## Recordings and user context

Four new Pixel 4 archives were copied to
`/home/sean/overdex-device-backups/2026-10-24-pixel-review/` and passed archive
structure and referenced-artifact verification. Phone originals remain intact.
The Moondrop had no newer match at the start of this review.

The user confirmed continuous attacking, Dragon Breath on Dragonair, and Charm
on Gardevoir. They also confirmed observation began late in the newest match.
Its missing opening is not proof of missed countdown recognition; starting
energy and HP must remain unknown unless later evidence establishes them.

| Archive prefix / local filename time | Articles / verified artifacts | Observed context | Start / result Articles |
| --- | --- | --- | --- |
| `c5693262`, Oct 23 21:16:41 | 2,377 / 348 | Turtonator, Dragonair versus Combusken, Magcargo, Flareon | Start and win |
| `5ba33d2a`, Oct 23 21:22:11 | 3,061 / 451 | Turtonator, Dragonair, Gardevoir versus Steelix, Togedemaru, Mr. Mime | Neither |
| `9c8a9b11`, Oct 24 07:20:15 | 2,553 / 353 | Gyarados versus Wimpod, Anorith, Scolipede | Start and win |
| `8952313d`, Oct 24 07:23:38 | 4,094 / 574 | Gardevoir, Turtonator, Dragonair versus Azumarill, Forretress, Turtonator | Neither; user confirms late observation |

Archive SHA256, in table order:

```text
51705b226015f2b418c2da8e63b9962031ea9a34032a5432284a67f03049d626
7486eb213d6bd14f27ebfeed6084417c966ca2d0aa7119e00d252d4e7f86a166
ac45b3d2414ebd97f714e230f7b2a7a2a3a139b1d243d6dcba6a15be48a97b09
4c0478c55720663074eb8170b3b8fc65c07c4d86dfeb8e22f763fe68f7271d3c
```

All four contain audio signal evidence. The Pixel's installed APK matched
`b1879ea11b6115df050d35fe2a0353f049730f49391b23cdacadb6b8182f8c2f`,
the October 23 Incinerate-fusion build. These matches do not validate that
specific fix: none supplied the trusted Incinerate identity needed to exercise
its longer fusion interval. The Moondrop still had the earlier build.

## Demonstrated crop-selection failure

Today's matches had almost no opponent-ball testimony despite readable native
badges in retained source frames. The Rocket match recorded only one count of
three; the newest match recorded no opponent-ball counts. A retained frame also
shows the newest match's win screen, although no result Article was produced.
Missing resource updates can leave the resource-driven result watcher unopened.

The saved Pixel ball assignment maps to `(544,165)-(660,201)` in a 672 x 1419
frame. In these recorded cases, this is our HUD's sprite-card row. The native
Poké Ball row is above it, around `(544,123)-(660,159)`. This is capture-time
recovery evidence, not a request to shift or overwrite the user's saved boxes.

`TeamBadgeCropLocator` previously validated candidate ball/shield shapes without
the HUD exclusion. Our cards passed that check, so the locator kept the assigned
row instead of recovering from the native name row. The downstream resource
witness correctly excluded the HUD and could not produce a count.

The locator now uses the same crop-relative HUD footprint and resource-counter
visibility rules as the witness, for both the original and recovered candidate,
on both sides. Real exposed caps still qualify with partial lower-edge overlap.
The retained-copy exclusion and all saved calibration values remain unchanged.
No new reader, OCR pass, sampling frequency, or image retention was added.

Three recorded examples demonstrate false HUD counts of 2, 0, and 0 where the
native rows show 1, 2, and 3 remaining balls. Pixel crops, source hashes and exact
rectangles are in `app/src/test/resources/battle/badge-location-20261024/`.
HUD footprints in these tests are explicitly manual annotations, not recovered
capture metadata. Full-frame originals remain in the desktop backup. The Android
test restores only the badge band to its original full-frame coordinates.

## Separate unresolved findings

- Dragonair is correctly identified as Dragon Breath in the first recording,
  with audio corroboration. In the newest, three HP changes around 155.448,
  156.900 and 158.360 archive seconds led to an incorrect Iron Tail inference.
  Their roughly 1.455-second interval describes the observed damage updates,
  not necessarily the true attack cycle. User confirmation establishes Dragon
  Breath. A sparse damage series must not alone eliminate faster legal moves.
- Gardevoir's Charge Beam label was an energy-preference assumption, not a
  witnessed identity. The user confirmed Charm. Neither that assumption nor
  elapsed time should be treated as recorded attacks or exact energy.
- The saved team in all four archives is Swampert/Quagsire/Obstagoon, different
  from the observed teams. Saved-team knowledge therefore cannot supply these
  actual players' equipped moves. This review did not overwrite the saved team.
- Gyarados has a single centered Twister button in the Rocket match; the current
  left/right charge crops miss its circle. None of its 128 fill samples yielded
  a usable experimental fill measurement. Single-button geometry needs support.
- Gyarados's first inferred identity briefly changed from Dragon Breath to
  Waterfall. Closely spaced HP fragments and border pulses also make its attack
  count suspect. Continuous attacking is not an exact hand-labelled count.
- Compact exports omit uncited crops, so missing PNGs do not prove a witness
  stopped. Thirty-five retained full frames were separately copied for this
  review; their filesystem modification times are not attack timestamps.

No historical Article, match result, attack count, or energy ledger was rewritten.

## Validation

The four new unit regressions fail before the fix and pass afterward. The full
unit suite passes **1,074 tests**, with no failures, errors or skips. The debug
app and instrumentation APKs build. The API 34 emulator reports **OK (1 test)**
for the recorded-frame Android regression, which exercises actual crop contracts,
name-row location, HUD exclusion and native resource counts on all three cases.

APK SHA256: `d2ac3d26dc18e2a3fe18a3df4cca07eed4cb39a1c0a1979dc818b13c0aac50ba`.

Installed in place on Pixel 4 (`99261FFAZ0017J`) and Moondrop
(`MDPH00124112500414`) after confirming neither had an active Droidball service.
Both installations succeeded and MainActivity resumed. Package update times are
October 24 08:18:37 and 08:19:06 respectively. Before/after hashes match for saved
battle calibration, Team Select calibration, current team, HUD layout and selected
calibration profile. No uninstall, data clear, or historical archive rewrite was
performed. The Moondrop also receives the preceding Incinerate late-flash fix.

This is recorded-frame and startup validation, not a new live battle. A subsequent
battle remains the acceptance check for restored faint/result behavior. Dragonair
cadence ambiguity and centered charge-button measurements remain separate work.
