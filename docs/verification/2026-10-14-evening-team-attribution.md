# October 14 evening team and move attribution

## Recording evidence

Read-only review on Moondrop MD_PH_001, serial `MDPH00124112500414`:

| Archive start | Match ID | Articles | Verified artifacts | Result |
| --- | --- | ---: | ---: | --- |
| 19:25:08 | `278ee954-6908-4da1-a135-7d3ea3608724` | 168 | 4 | Setup and manual restart, no battle start |
| 19:25:43 | `9b0a299f-44bb-44b8-a0f6-f3af4e62c013` | 2,404 | 677 | WIN |
| 19:27:20 | `696b62f9-f6c0-4db4-9b57-c01f10597c60` | 4,404 | 1,209 | WIN |

Original device archives remain unchanged. Copies and hash inspections are in
`/tmp/overdex-review-20261014-attribution-19h/`.

Both battles recorded the configured team as Samurott / Ceruledge / Machamp.
The actual player badge crops clearly read Abomasnow, Camerupt, and (in the
second battle) Gourgeist. Three retained player strips were visually inspected.
These are successful OCR readings followed by incorrect side inference.

In the first battle, `Go, Camerupt!` at +40.62 s was assigned OPPONENT despite
the player badge confirming Camerupt shortly afterward. In the second battle,
entry announcements assigned Abomasnow (+20.87 s), Gourgeist (+44.13 s),
Camerupt (+77.88 s), and returning Abomasnow (+90.52 s) to the opponent.
The same recording inserted configured Samurott at GO and identified Fury Cutter
before the actual player badge corrected that identity to Abomasnow.
Times in this paragraph use archived perceived wall time relative to record start.

Gourgeist was identified with Incinerate from multiple cadence sources. Seed Bomb
was announced at approximately +63.27 s. Its lingering SUPER EFFECTIVE text at
+65.19 s was treated as fast-move evidence and changed the conclusion to Razor
Leaf. This is charged-impact evidence contaminating fast-move identification.

The newest archive also retains 15 `QteTypeIconsCrop` samples. This verifies raw
sample retention, not QTE type classification, which is not enabled yet.

## Changes

- Independent badge sightings establish trainer side, ahead of saved-roster guesses.
  A species present on both sides remains ambiguous in a shared announcement.
- Exclusion from a team requires three witnessed Team Select slots. A saved team
  alone does not prove the active lead or make every unlisted Pokémon an opponent.
  A contradictory player badge invalidates roster assumptions for this match.
- Entry text can wait briefly for independent badge confirmation. Its derived
  species article retains the entry timestamp and cites both observations.
  Older entry delivery cannot rewind current HUD species/types.
- Charged announcements are retried when an earlier captured badge is published
  late. Resolved charges cite the badge that establishes their trainer side.
- A source-timed three-second aftermath window excludes lingering charge
  effectiveness text from fast-move identification. Border, HP, motion and
  independently timed attack measurements continue. This is conservative around
  a charged impact, not a guarantee that all fast-move observations are accurate.
- Completed replay repairs the specific older roster-based entry errors using
  independent badge evidence and omits a contradicted configured opening lead.
  It also omits the charge-aftermath-derived fast-move conclusion. Original
  archived articles, including wrong conclusions, are retained unchanged.

## Validation

Focused unit run: 119 tests passed, including reduced fixtures from both evening
battles, mirror-match ambiguity, late badge publication, and Seed Bomb aftermath.

Full `:app:testDebugUnitTest`, `:app:assembleDebug`, and
`:app:assembleDebugAndroidTest` passed: **772 unit tests, zero failures/errors**.
The app was installed in place on the Moondrop, without uninstalling or clearing
data. Installed app bytes match the built APK:
`8aac8ecc0f7f60116a4a855355404af67227bdcc457c103d39ae358fddad6351`.

Both `TeamAttributionDeviceTest` tests passed on the physical Moondrop:

- Post-OCR routing through real `Match`/custody/witness/presentation code rejected
  a fabricated Samurott lead, waited for side evidence for Gourgeist, put only
  Sealeo in the opponent HUD, and attributed Seed Bomb to PLAYER. The resolved
  entry kept its original timestamp and both evidence references.
- Both full device archives loaded and kept the actual player species on PLAYER
  throughout the sampled battle interval. The latest battle at +50 s rendered
  Gourgeist left and Sealeo right. The screenshot was visually inspected at
  `/tmp/overdex-team-attribution-replay.png`.

An initial device test fixture omitted the charged moves required by the saved
team payload. Correcting the fixture to a valid team allowed the routing test to
run; no production change was needed for this test setup error.

All 11 calibration/profile/HUD-layout files were byte-identical before and after
installation, both device tests, and a normal app launch. The app opened normally
with no fatal exception in its current process. All three original archive files matched
their reviewed copies by SHA-256. Test replay serialization also left the original
articles unchanged. Logs are in `/tmp/overdex-team-attribution-final.log` and
`/tmp/overdex-team-attribution-device.log`.

A fresh live battle remains the acceptance check for capture and HUD recognition
latency. These checks verify recorded reconstruction and post-OCR routing; they
do not establish complete fast-move counts or recalculate archived energy history.
