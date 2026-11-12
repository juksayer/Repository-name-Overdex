# Calibration screenshot coverage and crop inventory

Source audit: November 9, 2026; charged-impact HP addition November 10. This is a collection/checklist document, not an
app change or a claim that a detector is accurate. Use one collection per actual
phone and game-display layout. **Start with the 17-scene collection plan below**;
a screenshot is reusable for many boxes. Add a frame when a target is missing or
obscured. Short recordings remain necessary to evaluate brief or moving events.

## What the counts mean

| Inventory | Current count | Meaning |
| --- | ---: | --- |
| `CalibrationRegion` entries | 49 | Includes `NONE`, which is not a crop |
| Current Match Calibration choices | 40 | The actual `matchRegions` cycle, in the order listed below |
| Hidden enum entries with stored rectangles | 8 | Six legacy entries and two active post-match support regions |
| Stored rectangles | 48 | 38 in `BattleCalibration`, plus 10 in `TeamSelectCalibration` |
| Declared named crop purposes | 50 | 40 `BattleCropContracts`, plus 10 `TeamSelectCropContracts`; some share a box, and some are not registered captures |
| Additional named dynamic text views | 2 | Player/opponent HP effectiveness strips located relative to a measured HP bar |

Thus “about 50 crops” is reasonable for **named purposes**, but does not mean
50 independently adjustable rectangles or 50 required screenshots. Witnesses
can share geometry without sharing their testimony. The two dynamic text views
bring the audited set to 52 named views; this is not a count of simultaneous
readers or of PNGs retained each frame.

The authoritative UI audit is [`MatchCalibrationScreen.kt`](../../app/src/main/java/com/example/overdex/ui/screens/MatchCalibrationScreen.kt):
`matchRegions`, `getReadableName`, `activeRegion`, and `updateCalibration`.
There are no current `regionActions` or `selectRegion` declarations in this
screen. The complete enum is [`CalibrationRegion.kt`](../../app/src/main/java/com/example/overdex/ui/components/CalibrationRegion.kt).
Geometry and named purposes are defined in
[`BattleCalibration.kt`](../../app/src/main/java/com/example/overdex/data/BattleCalibration.kt),
[`TeamSelectCalibration.kt`](../../app/src/main/java/com/example/overdex/battle/observation/TeamSelectCalibration.kt),
and [`BattleObservationContracts.kt`](../../app/src/main/java/com/example/overdex/battle/observation/BattleObservationContracts.kt).

For the source-verified list of actual readers, shared inputs, fixed child
geometry, hidden controls, audio/sensor readers and registration fields, see
[Reader calibration inventory](reader-calibration-inventory.md), checked
November 10, 2026. A declared crop or stored rectangle alone is not proof that
a corresponding recognizer is running.

## Image quality rules

The [November 10 reviewed Moondrop collection](../verification/2026-11-10-moondrop-reference-collection.md)
provides 21 starter calibration references and a separate 36-image, 18-type QTE
library. Its coverage table records remaining grade-text, effectiveness,
switch-timer, hand and opponent-recipient examples. Unapproved historical bundled
images are retained as fixtures but no longer offered for calibration.

- Keep the **original whole captured frame**, with its actual width/height,
  orientation, status bar, and navigation area. Use the same phone and the same
  game/system display layout as the profile being adjusted. A matching shape
  alone does not establish the phone's identity.
- Do not resize, stretch, crop out bars, add a phone frame, or use a desktop
  screenshot of a video player for coordinate calibration. Preserve such images
  as explanatory references if useful. An original frame extracted from a
  recording must retain the recording's actual frame geometry and provenance;
  do not silently promote a differently sized recording to the phone's profile.
- A raw Android Studio screen capture can be valid. Leave optional framing and
  transformations off. Android's [screen-capture documentation](https://developer.android.com/studio/debug/am-screenshot)
  explicitly warns that choosing artwork for a different device can stretch the
  screenshot to that frame's dimensions.
- Show Pokémon GO directly. Exclude the ODX-Fi replay, gallery/viewer controls,
  notification shade, dialogs from other apps, and overlays covering the target.
  A hidden target is unavailable evidence, not a failed or absent game element.
- Use static landmarks for badges, species strips, exit control, and button
  labels. **HP boxes are search territories** across the battlefield, not boxes
  to tighten around one pose. QTE grades need vertical bounce allowance; QTE
  glyphs and the hand need motion allowance. Do not chase a transient camera
  angle by repeatedly moving a fixed saved crop.
- Prefer clear text, both complete team badges, and unobscured non-Shadow art
  for the initial geometry pass. Keep Shadow-obscured examples separately as
  negative/availability checks. Weather, particles, camera movement, and a
  foreground Pokémon can also obscure the opponent's hand.
- Record phone/profile, original filename, source dimensions, game/Android
  versions, display density/font scale, navigation/status-bar layout, scene ID,
  targets covered, and any occlusion or uncertain provenance. Keep originals
  unchanged. Identical phone hardware can have different game layouts over time.

## Initial set: 17 scenes per device

The IDs below are **collection slots**, not assertions that an existing file
already supplies the scene. Tick a slot only after inspecting the original.
More than one slot may use the same image if every required target is visible.

| Slot | Capture | Why it is useful / acceptance detail |
| --- | --- | --- |
| S01 | Choose Your Team, with all three cards/names and Use This Party visible | Covers all ten Team Select rectangles: league badge, league text, restrictions, button, three sprites and three name strips. Include the longest visible name/restriction rather than clipping the row. |
| S02 | VS disc during the actual VS transition | Calibrates the disc and immediate surround, separate from trainer portraits or countdown. |
| S03 | A clear numeric opening countdown glyph, preferably 3 | Checks glyph height and initial inactive-card shading. The same territory must contain 2 and 1; extra frames are useful validation rather than new boxes. |
| S04 | Complete GO glyph | Checks both letters; a crop that fits a narrow numeral can still clip GO. |
| S05 | Unobscured normal battle, with both badges, both charged controls and both inactive cards | Main reusable image for names, types, balls, shields, broad HP territories, charge controls/labels, both bench halves and exit geometry. Prefer dual-type combatants and fully expanded ready controls; avoid notification/gallery overlays. The optional touch-coordinate strip may be checked here if intentionally enabled and not covering another target. |
| S06 | Player damaged by a fast move, with effectiveness text above the player's bar | Tests player HP search/low-red geometry and the dynamic player effectiveness strip. It supports an opponent attack; it is not the charged-button effectiveness label. |
| S07 | Opponent damaged by a fast move, with effectiveness text above the opponent's bar | Tests opponent HP search/low-red geometry and its separate dynamic effectiveness strip. Also useful at a different bar height/camera pose. |
| S08 | NICE grade with QTE type glyphs in view | First sample of grade bounce and experimental type-icon territory. Keep the whole execution view; one glyph does not validate every type. |
| S09 | GREAT grade, preferably at another bounce position | Checks that the grade box contains its vertical movement and broader text. |
| S10 | EXCELLENT grade, including its widest/highest readable position | Checks long lettering and motion margins. Together S08–S10 are geometry samples, not proof that all QTE trajectories fit. |
| S11 | Incoming charged attack / shield choice with a readable nonzero decision timer | Checks the decision dial, incoming-attack announcement and shield resources. Timer/overlay layouts differ from ordinary combat. |
| S12 | Immediately after a voluntary switch, with inactive-card shade/dials visible | Checks the shared bench territory in its cooldown state and changed badges. Do not add a fictitious independent switch-timer Draggy Box. |
| S13 | Player replacement choice after a faint, with the decision timer visible | Checks faint-decision layout separately from shield choice. The replacement dialog is not the two small normal-battle inactive cards. |
| S14 | Opponent send-out, with open hand/ball and entry announcement visible if possible | Checks the experimental hand territory and a positive `Go, <name>!` announcement. If the hand and toast are not visible together, add the missing frame rather than declaring the slot fully covered. |
| S15 | YOU WIN result text | One positive example for the current shared Match Outcome region. Keep the full phrase, not only the avatar. |
| S16 | GOOD EFFORT result text | Checks the other result layout in that same shared region. A win alone cannot validate loss coverage. |
| S17 | Post-match screen showing BATTLE and PARTY together, with surrounding menu support | Covers two active post-match support crops even though their separate controls are currently hidden from the Match Calibration cycle. This is not an arbitrary GO menu. |

If 20 images are convenient, prioritize **S18: one unlocked centered charged
move** (derived center name/effectiveness and control-rim validation) and
**S19: departure during final hit/shrink/disappearance**, with a clear hand/HP
area, then **S20: a Shadow-obscured departure/send-out** (availability check).
If the empty-bar frame cannot be caught, keep a short recording; a missing
still cannot establish the event, and a hand cue cannot distinguish faint from
switch. If S05 cannot
show both long move names/effectiveness labels clearly, use those extra slots for
the missing control state first. A Get Ready/used-move toast is another useful
reuse/extra frame for the same Announcement territory, not a new calibration
field. All 18 type-specific QTE paths and complete hand/faint sequences require
additional samples or recordings beyond this initial geometry set.

For **S21**, added November 10, collect the receiving Pokémon's centered HP bar
during charged impact. Include a clear frame before and after damage when
available, and both player/opponent recipients. Different sprite heights and
camera poses can move the bar vertically. A shielded frame helps establish
geometry but may tint or obscure the interior, so it does not by itself validate
fill-color recognition. This scene calibrates the independent **Charged Impact
HP** territory; it does not replace S05–S07's ordinary battlefield HP examples.

## All 40 current Match Calibration choices

**Leaf** means a purpose-specific active reader/capture. **Shared** means an
aggregate/search territory used by multiple measurements or raw capture.
**Experiment** retains raw samples without a gesture/type conclusion.
**Reserved** is editable geometry with no registered production captor/reader
found in this audit. These categories describe current wiring, not priority.

`TeamSelectCalibration` fields below have the prefix `TS.`; all other fields
belong to `BattleCalibration`. Quoted labels match the current LCD strings.

| # / enum | Exact UI label → saved field | Kind and named crop purpose(s) | Initial scenes |
| --- | --- | --- | --- |
| 1 `COUNTDOWN` | Countdown → `countdownRegion` | Leaf: `CountdownGlyphCrop` | S03, S04 |
| 2 `TEAM_SELECT_LEAGUE_BADGE` | Team Select League Badge → `TS.leagueBadge` | Leaf: `TeamSelectLeagueBadgeCrop` | S01 |
| 3 `TEAM_SELECT_LEAGUE_TEXT` | Team Select League Text → `TS.leagueText` | Leaf: `TeamSelectLeagueTextCrop` | S01 |
| 4 `TEAM_SELECT_RESTRICTIONS` | Team Select Restrictions → `TS.restrictions` | Leaf: `TeamSelectRestrictionsCrop` | S01 |
| 5 `TEAM_SELECT_USE_THIS_PARTY` | Team Select Use This Party → `TS.useThisParty` | Leaf: `TeamSelectUseThisPartyCrop` | S01 |
| 6 `TEAM_SELECT_ROSTER_1` | Team Select Player Card 1 → `TS.rosterSlot1` | Leaf: `TeamSelectPlayerRosterSlot1Crop` | S01 |
| 7 `TEAM_SELECT_ROSTER_2` | Team Select Player Card 2 → `TS.rosterSlot2` | Leaf: `TeamSelectPlayerRosterSlot2Crop` | S01 |
| 8 `TEAM_SELECT_ROSTER_3` | Team Select Player Card 3 → `TS.rosterSlot3` | Leaf: `TeamSelectPlayerRosterSlot3Crop` | S01 |
| 9 `TEAM_SELECT_ROSTER_NAME_1` | Team Select Player Name 1 → `TS.rosterName1` | Leaf: `TeamSelectPlayerRosterName1Crop` | S01 |
| 10 `TEAM_SELECT_ROSTER_NAME_2` | Team Select Player Name 2 → `TS.rosterName2` | Leaf: `TeamSelectPlayerRosterName2Crop` | S01 |
| 11 `TEAM_SELECT_ROSTER_NAME_3` | Team Select Player Name 3 → `TS.rosterName3` | Leaf: `TeamSelectPlayerRosterName3Crop` | S01 |
| 12 `VS_SCREEN` | VS Screen → `vsScreenRegion` | Leaf: `VsScreenCrop` | S02 |
| 13 `ANNOUNCEMENT` | Announcement → `announcementRegion` | Leaf: `AnnouncementCrop`; entry, Get Ready, incoming and used-move text share it | S11, S14 |
| 14 `TRAINER_TEAM_INFO` | Trainer Team Info → `playerTeamInfoRegion` | Shared: native badge presence/retention; `PlayerTeamStatusCrop` is declared but its capture contract is not registered | S05 |
| 15 `OPPONENT_TEAM_INFO` | Opponent Team Info → `opponentTeamInfoRegion` | Shared: native badge presence/retention; `OpponentTeamStatusCrop` is declared but its capture contract is not registered | S05 |
| 16 `PLAYER_SPECIES_NAME` | Player Species Name → `playerSpeciesNameRegion` | Leaf: `PlayerActiveSpeciesTextCrop` | S05, S12 |
| 17 `OPPONENT_SPECIES_NAME` | Opponent Species Name → `opponentSpeciesNameRegion` | Leaf: `OpponentActiveSpeciesTextCrop` | S05, S14 |
| 18 `PLAYER_SHIELDS` | Player Shields → `playerShieldsRegion` | Leaf: `PlayerShieldsCrop` | S05, S11 |
| 19 `OPPONENT_SHIELDS` | Opponent Shields → `opponentShieldsRegion` | Leaf: `OpponentShieldsCrop` | S05, S11 |
| 20 `PLAYER_POKE_BALLS` | Player Poké Balls → `playerPokeBallsRegion` | Leaf: `PlayerPokeBallsCrop` | S05, S13, S19 if available |
| 21 `OPPONENT_POKE_BALLS` | Opponent Poké Balls → `opponentPokeBallsRegion` | Leaf: `OpponentPokeBallsCrop` | S05, S14, S19 if available |
| 22 `TRAINER_ACTIVE_TYPE` | Trainer Active Type → `trainerActiveTypeRegion` | Leaf: `PlayerActiveTypeIconsCrop` | S05, S12 |
| 23 `OPPONENT_ACTIVE_TYPE` | Opponent Active Type → `opponentActiveTypeRegion` | Leaf: `OpponentActiveTypeIconsCrop` | S05, S14 |
| 24 `TRAINER_HP` | Trainer HP → `trainerHpRegion` | Shared broad search: `PlayerHpEvidenceCrop`; bar/fill/border/motion/damage/artifact readers share measured geometry | S05, S06, S19 if available |
| 25 `OPPONENT_HP` | Opponent HP → `opponentHpRegion` | Shared broad search: `OpponentHpEvidenceCrop`; same independent HP readers for opponent side | S05, S07, S19 if available |
| 26 `CHARGE_MOVE_EXECUTION` | Charge-Move Execution → `chargeMoveExecutionRegion` | Shared/raw: `ChargeMoveExecutionCrop`; active preservation, no independent execution classifier found | S08–S11 |
| 27 `QTE_PROGRESS_TEXT` | QTE Progress Text (Nice / Great / Excellent) → `qteProgressTextRegion` | Leaf: `QteProgressTextCrop`, independently read from Announcement | S08–S10 |
| 28 `QTE_TYPE_ICONS` | QTE Type Icons → `qteTypeIconsRegion` | Experiment: `QteTypeIconsCrop`; bounded raw QTE samples, not classified type testimony | S08–S10 |
| 29 `EXIT_ICON` | Exit Icon → `exitIconRegion` | Reserved: `ExitIconCrop` declared; no registered capture or exit/concession recognizer found | S05 |
| 30 `TRAINER_CHARGE_MOVE_CONTROLS` | Trainer Charge-Move Controls → `trainerChargeMoveControlsRegion` | Shared: `PlayerChargeMoveControlsCrop`; left/right or confirmed center rims and fill sampled within it | S05, S18 if available |
| 31 `POINTER_COORDINATES` | Touch Coordinates → `pointerCoordinatesRegion` | Leaf/optional: `PointerCoordinatesCrop`; Android Pointer Location must actually be enabled | S05 if enabled |
| 32 `DECISION_TIMER` | Decision Timer → `decisionTimerRegion` | Leaf: `DecisionTimerCrop`; shield and replacement decisions | S11, S13 |
| 33 `PLAYER_CHARGE_MOVE_NAME_LEFT` | Player Charged Move Name - Left → `playerChargeMoveNameLeftRegion` | Leaf: `PlayerChargeMoveNameLeftCrop`; shares derivation of centered name | S05, S18 if available |
| 34 `PLAYER_CHARGE_MOVE_EFFECTIVENESS_LEFT` | Charged Button Effectiveness - Left → `playerChargeMoveEffectivenessLeftRegion` | Leaf: `PlayerChargeMoveEffectivenessLeftCrop`; shares derivation of centered effectiveness | S05, S18 if available |
| 35 `PLAYER_CHARGE_MOVE_NAME_RIGHT` | Player Charged Move Name - Right → `playerChargeMoveNameRightRegion` | Leaf: `PlayerChargeMoveNameRightCrop` | S05 |
| 36 `PLAYER_CHARGE_MOVE_EFFECTIVENESS_RIGHT` | Charged Button Effectiveness - Right → `playerChargeMoveEffectivenessRightRegion` | Leaf: `PlayerChargeMoveEffectivenessRightCrop` | S05 |
| 37 `TRAINER_INACTIVE_POKEMON` | Inactive Cards: Upper / Lower → `trainerInactivePokemonRegion` | Shared: upper/lower sprite and HP views plus initial/cooldown timer views, detailed below | S03, S05, S12 |
| 38 `MATCH_OUTCOME` | Match Outcome → `matchOutcomeRegion` | Leaf: `MatchOutcomeCrop`; current YOU WIN and GOOD EFFORT readers share it | S15, S16 |
| 39 `OPPONENT_TRAINER_HAND` | Opponent Hand / Poké Ball → `opponentTrainerHandRegion` | Experiment: `OpponentTrainerHandCrop`; manual bounded samples, no automatic recall/faint/send-out claim | S14, S19 if available |
| 40 `CHARGED_IMPACT_HP` | Charged Impact HP → `chargedImpactHpRegion` | Shared centered-recipient search territory: `ChargedImpactHpCrop`; phase-bounded geometry/fill/color measurements, independently saved from normal side HP | S21 |

## Shared and derived views: no extra independent Draggy Boxes

- **Inactive cards:** one outer rectangle is bisected horizontally at its
  midpoint. `PlayerInactiveUpperSpeciesSpriteCrop` and
  `PlayerInactiveUpperHpBarCrop` receive the upper half;
  `PlayerInactiveLowerSpeciesSpriteCrop` and
  `PlayerInactiveLowerHpBarCrop` receive the lower half. Separate witnesses
  interpret the same half; they are not four independent adjustable boxes.
  `TrainerInactiveTimerOverlayClearanceCrop`, `InactiveMatchStartTimerCrop`, and
  `SwitchLockoutTimerCrop` use the full outer territory. Keep both cards inside,
  with the middle divider between them.
- **One centered charged move:** `PlayerChargeMoveNameCenterCrop` and
  `PlayerChargeMoveEffectivenessCenterCrop` are derived from the corresponding
  saved left/right pair by [`PlayerChargeControlLayout.kt`](../../app/src/main/java/com/example/overdex/battle/observation/PlayerChargeControlLayout.kt).
  A saved moveset alone does not establish centered layout; live rim evidence
  must confirm it. There is no separate center enum/control.
- **HP effectiveness:** `PlayerHpEffectivenessTextCrop` and
  `OpponentHpEffectivenessTextCrop` are dynamically placed above the tracked bar
  by [`LiveHpEffectivenessTextWitness.kt`](../../app/src/main/java/com/example/overdex/battle/observation/LiveHpEffectivenessTextWitness.kt).
  They are distinct from charged-button effectiveness and have no independent
  calibration fields. Broad HP coverage must allow the actual bar's movement.
- **HP submeasurements:** border pulses, cadence, filled fraction, damage ticks
  and recipient artifacts share the live measured bar/frame hub. They are not
  additional saved crop rectangles. See [`LiveActiveHpBarWitnesses.kt`](../../app/src/main/java/com/example/overdex/battle/observation/LiveActiveHpBarWitnesses.kt).
- **Badge relocation and ready-button text:** capture may relocate a badge view
  from native name-row geometry or retain a bounded lower margin for a growing
  charged-button label. This preserves real provenance without rewriting the
  saved boxes. Do not use those runtime recovery rules as a reason to omit the
  proper full-size badge/ready-button examples.

## Remaining nine enum entries, explicitly accounted for

These are absent from the current 40-choice Match Calibration cycle. A readable
label or `when` branch alone does not make a target reachable in that cycle.

| Enum | Label / stored field | Current status and relevant scene |
| --- | --- | --- |
| `NONE` | No crop; fallback branch may return enemy-name geometry | Sentinel only; do not count or calibrate it |
| `ENEMY_NAME` | Enemy Name / `enemyNameRegion` | Legacy calibration UI/readers; current production species readers use purpose-specific player/opponent strips. S05 is reference only for this old field. |
| `HP_BAR` | HP Bar / `hpBarRegion` | Legacy calibration field; no current production HP consumer found. Use Trainer HP/Opponent HP instead. |
| `TEAM_ICONS` | Team Icons / `teamIconsRegion` | Legacy calibration field; no current production consumer found. Current ball/shield crops are independent. |
| `MOVE_BANNER` | Move Banner / `moveBannerRegion` | Legacy witness classes refer to it, but current registered phrase readers consume Announcement. S11 is an old-field reference only. |
| `YOU_WIN` | You Win / `youWinRegion` | Legacy standalone witness field. Current registered result phrase readers use shared Match Outcome (S15). |
| `GOOD_EFFORT` | Good Effort / `goodEffortRegion` | Legacy standalone witness field. Current registered result phrase readers use shared Match Outcome (S16). |
| `BATTLE_PARTY_TABS` | Post-Match Battle / Party Selector / `battlePartyTabsRegion` | Active `BattlePartyTabsCrop` capture/reader in RESULT phase, but no separate current Match Calibration choice. Collect S17 and record this access gap. |
| `OUT_OF_BATTLE_MENU` | Post-Match Menu Support / `outOfBattleMenuRegion` | Active `OutOfBattleMenuCrop` support capture in RESULT phase, paired with the tabs observation; hidden from the current choice cycle. Collect S17 and record this access gap. |

The older [`CalibrationScreen.kt`](../../app/src/main/java/com/example/overdex/ui/screens/CalibrationScreen.kt)
still has a separate routed legacy list containing eight choices (including
Countdown and Opponent Shields). Its presence is not evidence that its legacy
boxes drive the current readers. The live registration authority is
[`PokedexViewModel.kt`](../../app/src/main/java/com/example/overdex/ui/PokedexViewModel.kt),
with native badge geometry also consumed by
[`DroidballService.kt`](../../app/src/main/java/com/example/overdex/battle/observation/DroidballService.kt).
The post-match pair's actual confirmation rule is in
[`PersistedOutOfBattleMenuWitness.kt`](../../app/src/main/java/com/example/overdex/battle/observation/PersistedOutOfBattleMenuWitness.kt).

## Current library triage and limits

The November 10 [reviewed Moondrop collection](../verification/2026-11-10-moondrop-reference-collection.md)
now contains 67 original 1080 × 2400 PNGs (66 unique images): 31 non-QTE and
36 QTE images covering all 18 types. Its 21-image core includes the added
`supereffectivetext.png`, which supplies **S07 opponent-HP effectiveness text
placement**. It does not establish the source attack, Fast Move versus charged
move, cause, or timing. The corresponding player-side placement in S06 remains
missing. QTE grade reveals, voluntary-switch card shading/dials, normal-field
hand sequences, and a clear centered opponent recipient also remain collection
targets. The initial static exit-landmark comparison covers 15 applicable
images within the original 30 non-QTE images, not the later 31-image set.

The user located the mismatch on the **Moondrop while cycling bundled references**.
The mixed Pixel folder below is a separate inventory finding, not an explanation
of that reported path. Both 816 × 1808 and 672 × 1493 are actual source-image
dimensions and are compatible shapes for the Moondrop; resolution alone does not
establish whether a reference is suitable.

Comparison with a retained direct 672 × 1493 frame from the November 9 Moondrop
match confirms a layout mismatch in tested legacy references. Current battle
badges and the exit control are about 84 pixels higher when expressed on a
2400-pixel-high display. Current charged-move names are below their buttons;
the tested older PNG/JPEG examples place names above them. Do not use those
older top-field/name placements to recalibrate the current layout. This is not
a uniform offset correction for every field. See the
[reference audit](../verification/2026-11-09-calibration-reference-audit.md).

The separate candidate-image audit reported 37 supported image filenames in
`/home/sean/Desktop/pixel4calibrationscreenshots`: 34 Pokémon GO images at
1080 × 2280 and three ODX-Fi replay references at 1080 × 2400. Two GO images
with gallery overlays should be excluded from clean coordinate calibration.
A direct header/count check also found three extra readable duplicate files
whose names end in `(101).`; those unsupported extensions account for 40
physical files versus 37 supported image names. Do not rename, import or delete
them as part of this read-only checklist audit.

Older references mix 1080 × 2400 and 816 × 1808 source geometry with unverified
hardware provenance. Keep them as separately labeled references until their
source is established; neither shape nor a folder name proves they are raw
Moondrop captures. The candidate CSV is maintained by the separate image audit;
its inclusion/exclusion findings do not imply the 17 scene slots above are
already complete on either phone.

Static calibration can establish territory. It cannot establish a faint, recall,
forfeit, reverse entrance sequence, or all QTE motion from one still. Preserve
short source recordings for those experiments and treat Shadow-obscured motion
as unavailable evidence. No app code, device settings or calibration profile is
changed by this document.

The private audit folder also contains a fillable 17-row
`capture-scene-worksheet.csv`, a 39-row `crop-placement-worksheet.csv`, the
per-image classifications and a provisional Pixel candidate shortlist:
`/home/sean/overdex-device-backups/2026-11-09-calibration-suite-audit/`.
