# QTE calibration guide and replay icon placement — 2026-10-14

## Requested behavior

- The growing charged-move glyph sits forward of the performer, toward its opponent, and behind the sprite in draw order. It rises with the sprite and launches from that same forward position. Player and opponent placements mirror each other.
- Battle HUD exposes **QTE BOX**. The live guide starts in **VIEW ONLY**, allowing QTE touches through its interior. **ADJUST** enables dragging; **MOVE / SIZE** selects position or size adjustments. Arrow taps move one physical pixel, with accelerated repetition while held. The control panel can also be dragged.
- **SAVE & CLOSE** persists only `qteTypeIconsRegion`, loading the current calibration before updating it. **CANCEL** discards the draft. Both named profiles and the older preference-backed calibration persist the new region.
- CRT Match Calibration includes **QTE Type Icons**, using the same normalized rectangle. Full display dimensions are used without subtracting the status bar. Existing saved boxes are not migrated.

## Scope and evidence boundary

`BattleCropContracts.qteTypeIcons` defines the proposed territory. No QTE icon classifier or type testimony is activated by this guide. Its default central box is a starting point for the user's live measurements, not validation that all 18 moving type patterns cross it. The outline is thin, unfilled, and not secure-window blacked out. The calibration controls remain visible in whole-screen captures while open.

## Verification

- Full unit suite: **753 passed**, zero failures/skips. Debug application and instrumentation APK builds passed.
- Installed with `adb install -r` on the **Moondrop MD_PH_001**, serial `MDPH00124112500414`, Android 14. No MediaProjection session was active at install time.
- `ReplayChargeTypeDeviceTest`: all **3 tests passed**, including real recorded Abomasnow Weather Ball (Ice), player/opponent forward placement, growth, throw direction, one icon per charge, and reverse scrub. Phone screenshots inspected: Abomasnow partly obscures the snowflake as requested; Galvantula partly obscures its Bug glyph.
- `LiveQteCalibrationDeviceTest`: both **2 tests passed**. HUD launch, exact screen-space outline position, transparent interior, pass-through taps, adjustment-mode touch interception, one-pixel move/size nudges, Save/Close and Cancel were exercised in real WindowManager windows. CRT calibration exposes the new region. The save callback is isolated in the UI test; the unit tests cover the real named-profile/preferences persistence.
- The first guide test injected a tap before Android applied the window-flag transaction. Waiting for the window update in the test resolved it; production code did not change for that test timing issue.
- All **11 calibration files** matched byte-for-byte before install and after the phone checks. No species/HP box was shifted or overwritten.
- APK SHA-256: `eea674dfdc3411cd34e009101c7f0e657fc6b9bd66515eb6b46424125ccf6a41`.

The remaining acceptance check is live Pokémon GO QTE coverage across the user's type samples. The phone tests validate overlay geometry/touch behavior, not recognition accuracy or coverage of all 18 type patterns.

Device evidence and build logs: `/tmp/overdex-qte-box-20261014/`.
