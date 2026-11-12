# Game readers and calibration coverage

Source-verified November 10, 2026, after the Charged Impact HP addition.
This inventories game-observation readers, their shared helpers and their source
geometry. It does not count archive/file/database readers, UI gesture handling,
or playback rendering as new ways of observing Pokémon GO.

The current Match Calibration selector exposes **40 choices**. Registration has
**16 additional field boxes in its own editor**. A class count, reader-output
count and box count are different: one reader can have two sides or three slots,
and several semantic readers can consume one preserved OCR result.

## Coverage labels

| Label | Meaning |
| --- | --- |
| Direct | A matching adjustable control is reachable in Match Calibration. |
| Shared | An adjustable source box exists, shared by several readers or by a helper. |
| Relative / fixed detail | The parent or detected object is adjustable, but the actual child window/offset is hardcoded and has no independent control. |
| Hidden | Saved geometry and editor branches exist, but the control is absent from the current selector. |
| Miswired | An adjustable box is used, but it is assigned to a different job. |
| N/A | No new screenshot pixels: audio, sensor input, or interpretation of existing testimony. |
| Raw only | Captures pixels for inspection; does not identify the event suggested by its name. |

## Gaps and the appropriate controls

| Gap | Affected views | Current situation | Recommended adjustment |
| --- | ---: | --- | --- |
| Post-match Battle / Party tabs | 1 | battlePartyTabsRegion is saved but its control is hidden | Expose Post-Match Battle / Party Selector. |
| Post-match menu support | 1 | outOfBattleMenuRegion is saved but its control is hidden; currently freshness support, not a menu-pixel classifier | Expose Post-Match Menu Support and describe its actual role. |
| Between-match VS rearm | 1 | NextMatchVsWatcher searches CountdownGlyphCrop, despite the separate VS box | Assign it to the existing VS Screen territory. This is a wiring correction, not a new box. |
| Effectiveness text above HP | 2, player/opponent | Text rectangles follow bars using fixed offsets and padding | Adjustable width/height/offset anchored to each detected bar. |
| Recipient visual effects | 2, player/opponent | Fixed territory around/below the bar; may extend outside the HP search box | Separate adjustable territories, preferably anchored to the recipient/bar, with matching visibility checks. |
| Inactive cards | 2 cards, with 4 HP/sprite windows | One outer box is split exactly in half; inner HP and sprite windows are fixed fractions | Independently adjustable upper/lower card placement and HP/sprite subregions. |
| Centered charged-button text | 2, name/effectiveness | Both rectangles are derived from the left/right pair | Optional independent center-name and center-effectiveness geometry. |
| Charged-button fill/rim targets | Left, right and center | Only the outer control area is adjustable; target positions/radii are fixed ratios inside it | Adjustable button centers/search windows within the control parent. |
| Inactive opening shade/dial detail | Upper/lower | Clearance reader examines the fixed card halves | Optional anchored shade/dial subregions if the card-level split proves insufficient. No cooldown-seconds reader is currently wired. |
| Registration screenshot page search | 1, outside battle | Explicit screenshot-import action performs whole-page semantic OCR | Optional adjustable page search area. Live requested registration field reads already use their selected boxes. |

These are **independent adjustment gaps**, not ten entirely unbounded running
readers. In particular, fixed child geometry already has an adjustable parent.
HP loss, cadence and border readers should continue to agree about the same
detected bar rather than use independently drifting rectangles.

## Pre-battle, identity, resources and text

Both sides and all slots are explicitly stated below; grouped rows do not mean
only one side or slot runs.

| ID | Reader / actual signal | Current control | Coverage / implementation |
| --- | --- | --- | --- |
| V01 | Team Select surface acceptance | Team Select League Badge, League Text, Restrictions, Use This Party, Player Card 1/2/3 | Shared inputs. PersistedTeamSelectPartyWitness combines the seven preserved views; it does not publish a separate restrictions value. |
| V02 | League classification | Team Select League Badge | Direct. PersistedTeamSelectLeagueWitness / TeamSelectLeagueShieldClassifier. |
| V03 | Selected roster names, slots 1/2/3 | Team Select Player Name 1/2/3 | Three direct strips. PersistedPlayerTeamRosterSlotWitness performs species OCR. |
| V04 | Selected roster artwork / form variant, slots 1/2/3 | Team Select Player Card 1/2/3 | Three direct crops. PersistedPlayerTeamRosterSpriteWitness / TeamSelectSpriteMatcher. |
| V05 | Opening 3 / 2 / 1 / GO glyph | Countdown | Direct. PersistedCountdownGlyphWitness, CountdownGlyphMatcher and bounded OCR. |
| V06 | Countdown-shaped cue requesting opening cry capture | Countdown | Shared. PersistedCountdownCryCueWitness reads the existing glyph crop; the subsequent audio has no screen rectangle. |
| V07 | Normal VS transition | VS Screen | Direct. PersistedVsScreenWitness. |
| V08 | VS detection between completed matches | Countdown, currently | Miswired. NextMatchVsWatcher resolves countdownGlyph rather than vsScreen. |
| V09 | Player active species-name OCR | Player Species Name | Direct. LiveOverlaySpeciesPipeline.player; bounded row relocation is a helper, not another identity source. |
| V10 | Opponent active species-name OCR | Opponent Species Name | Direct. LiveOverlaySpeciesPipeline.opponent. |
| V11 | Player active type icons | Trainer Active Type | Direct. PersistedActivePokemonTypeWitness.player / PokemonGoTypeIconMatcher / TypeIconPixelMatcher. |
| V12 | Opponent active type icons | Opponent Active Type | Direct. PersistedActivePokemonTypeWitness.opponent and the same matching helpers. |
| V13 | Player remaining Poké Balls | Player Poké Balls | Direct. PersistedPlayerPokeBallWitness / OpponentBattleResourceCounter utility. |
| V14 | Opponent remaining Poké Balls | Opponent Poké Balls | Direct. PersistedOpponentBattleResourceWitness.pokeBalls. |
| V15 | Player remaining shields | Player Shields | Direct. PersistedOpponentBattleResourceWitness.playerShields. |
| V16 | Opponent remaining shields | Opponent Shields | Direct. PersistedOpponentBattleResourceWitness.shields. |
| V17 | General announcement OCR | Announcement | Direct. PersistedAnnouncementWitness; raw and accepted text are retained separately. |
| V18 | Nice / Great / Excellent grade-band OCR | QTE Progress Text (Nice / Great / Excellent) | Direct. A separate PersistedAnnouncementWitness instance under QTE_PROGRESS_TEXT_WITNESS. |
| V19 | Get Ready phrase | Announcement and QTE Progress Text | Shared accepted OCR, no new crop. PersistedAnnouncementPhraseWitness.getReady. |
| V20 | Attack Incoming / Protect Shield / no-shields phrases | Announcement and QTE Progress Text | Shared accepted OCR. PersistedAttackIncomingWitness. No separate prompt-only box. |
| V21 | Shield-decision text fallback | Player Charged Move Name - Left/Right; derived center | Shared PlayerChargeMoveLabelRead testimony. BattleShieldDecisionTextEvidence / SHIELD_DECISION_TEXT_WITNESS. |
| V22 | Species used move phase phrase | Announcement | Shared accepted OCR. PersistedAnnouncementPhraseWitness.chargeMoveUsed. |
| V23 | Go, species entry identity and side | Announcement | Shared accepted OCR. PersistedPlayerEntrySpeciesWitness; side can remain unresolved. |
| V24 | Named charged-move performer's species and roster attribution | Announcement | Shared OCR plus roster/side evidence. PersistedAnnouncementSpeciesWitness. |
| V25 | Decision dial presence and numeric seconds | Decision Timer | Direct. PersistedDecisionTimerWitness / DecisionTimerTextReader; numeral segmentation is inside this territory. |
| V26 | Result OCR, ordinary and late-probe lanes | Match Outcome | Direct, shared geometry across both lanes. PersistedOutcomeTextWitness / OutcomeTextRecognizer. |
| V27 | YOU WIN result phrase | Match Outcome | Shared outcome OCR. PersistedOutcomePhraseWitness / MatchOutcomeTextResolver. The older youWinRegion is not the active source. |
| V28 | GOOD EFFORT result phrase | Match Outcome | Shared outcome OCR. PersistedOutcomePhraseWitness / MatchOutcomeTextResolver. The older goodEffortRegion is not the active source. |
| V29 | Post-match BATTLE and PARTY tab OCR | Post-Match Battle / Party Selector | Hidden. PersistedOutOfBattleMenuWitness reads BattlePartyTabsCrop / battlePartyTabsRegion. |
| V30 | Post-match supporting menu capture | Post-Match Menu Support | Hidden. OutOfBattleMenuCrop / outOfBattleMenuRegion. Current witness correlates its fresh capture arrival with tab OCR; it does not classify these support pixels. |
| V31 | Native player team-badge presence | Trainer Team Info, Player Species Name | Shared adjustable geometry. TeamBadgePresenceDetector in DroidballService and species admission. |
| V32 | Native opponent team-badge presence / retained-badge placement | Opponent Team Info, Opponent Species Name | Shared adjustable geometry. RetainedOpponentBadge is presentation, excluded from new game evidence. |

The visual registry is in [PokedexViewModel](../../app/src/main/java/com/example/overdex/ui/PokedexViewModel.kt).
Exact reachable labels and fields are in [MatchCalibrationScreen](../../app/src/main/java/com/example/overdex/ui/screens/MatchCalibrationScreen.kt).
Native service probes are in [DroidballService](../../app/src/main/java/com/example/overdex/battle/observation/DroidballService.kt).
The VS mismatch is visible in [NextMatchVsWatcher](../../app/src/main/java/com/example/overdex/battle/observation/NextMatchVsWatcher.kt).
Post-match behavior is in [PersistedOutOfBattleMenuWitness](../../app/src/main/java/com/example/overdex/battle/observation/PersistedOutOfBattleMenuWitness.kt).

## HP and visual Fast Move evidence

| ID | Reader / actual signal | Current control | Coverage / implementation |
| --- | --- | --- | --- |
| H01 | Player active HP outline and filled fraction | Trainer HP | Shared adjustable search territory. LiveActiveHpBarWitness / LiveBattlefieldHpTracker / ActiveHpBarMeasurer. |
| H02 | Opponent active HP outline and filled fraction | Opponent HP | Shared adjustable search territory; same locator and separate side ownership. |
| H03 | Player/opponent low-HP and terminal outline follow-up; literal fill color | Recently detected normal bar | Shared/attached. TerminalHpWatchPolicy / TrackedTerminalHpBarMeasurer; no independent detached rectangle. |
| H04 | Centered receiving Pokémon's charged-impact HP / fill color, either side | Charged Impact HP | Direct, separate from normal HP. ChargedImpactHpWatchPolicy / ChargedImpactHpBarMeasurer. |
| H05 | Player/opponent vertical HP motion cadence | Existing HP measurements | N/A for new pixels. PersistedActiveHpBarCadenceWitness reads measured bar Articles. |
| H06 | Player/opponent HP border appearance | Detected HP outline | Attached sampling. ActiveHpBarBorderAppearanceMeasurer follows the actual measured border. |
| H07 | Player/opponent repeating border cadence | H06 border appearance | No new territory. LiveActiveHpBarBorderCadenceWitness. |
| H08 | Player/opponent individual border pulses | H06 border appearance | No new territory. LiveActiveHpBarBorderPulseWitness. |
| H09 | Player/opponent persistent HP damage ticks | Existing HP fractions/history | No new territory. LiveActiveHpBarDamageTickWitness. |
| H10 | Effectiveness OCR above player's HP | Fixed relative strip above player bar | Relative / fixed detail; no independent box. LiveHpEffectivenessTextWitness.player. |
| H11 | Effectiveness OCR above opponent's HP | Fixed relative strip above opponent bar | Relative / fixed detail; no independent box. LiveHpEffectivenessTextWitness.opponent. |
| H12 | Fast Move effectiveness parsing | H10/H11 plus accepted Announcement/Outcome text | No new pixels. PersistedFastMoveEffectivenessWitness. |
| H13 | Visual change/color/centroid around player recipient | Fixed territory around player bar | Relative / fixed detail; no independent box. FastMoveRecipientVisualSampler / LiveFastMoveRecipientVisualArtifactWitness.player. |
| H14 | Visual change/color/centroid around opponent recipient | Fixed territory around opponent bar | Relative / fixed detail; no independent box. Same sampler / opponent witness. |
| H15 | Player/opponent visual artifact cadence | Existing H13/H14 artifact measurements | No new pixels. LiveFastMoveRecipientVisualCadenceWitness. This is not a move-type icon classifier. |
| H16 | Departure hint waking a ball-count check | Existing ordinary tracker outcome | No additional pixel search. HpBarDepartureCheckPolicy; disappearance alone is not a faint. |

The normal HP boxes govern the shared vertical battlefield search band; broad
recovery can span horizontally to include both caps during camera movement.
They should not be presented as a guarantee that all HP-related sampling is
clipped to each visible box's horizontal edges.

Effectiveness text currently adds at least 12 pixels or 35% of a bar width on
each side, with height at least 32 pixels or four bar heights. Recipient visual
sampling uses a 24 × 24 grid over a larger territory: half a bar width beyond
each side, a quarter bar width above, and at least 1.5 bar widths or 12 bar heights
below the bar. Those genuinely different pixels warrant their own visible,
adjustable anchored territories. They can extend beyond the HP search area.

Sources: [live HP readers and recipient sampling](../../app/src/main/java/com/example/overdex/battle/observation/LiveActiveHpBarWitnesses.kt),
[tracker search policy](../../app/src/main/java/com/example/overdex/battle/observation/BattlefieldHpTracker.kt),
[HP effectiveness crop geometry](../../app/src/main/java/com/example/overdex/battle/observation/LiveHpEffectivenessTextWitness.kt),
[terminal outline reader](../../app/src/main/java/com/example/overdex/battle/observation/TrackedTerminalHpBarMeasurer.kt),
[charged-impact reader](../../app/src/main/java/com/example/overdex/battle/observation/ChargedImpactHpBarMeasurer.kt).

## Charged controls, inactive cards and input text

| ID | Reader / actual signal | Current control | Coverage / implementation |
| --- | --- | --- | --- |
| C01 | Charged-button energy fill increase | Trainer Charge-Move Controls | Shared outer box. LivePlayerChargeMoveEnergyFillWitness / PlayerChargeMoveEnergyFillSampler; left/right centers and sampling radii are fixed inside it. |
| C02 | Charged-button rim, separator height and experimental fill | Trainer Charge-Move Controls | Shared outer box, fixed left/right/center inner search areas. ChargeButtonFillHeightReader / PlayerChargeControlLayout. |
| C03 | Cadence of button fill increases | C01 measurements | No new pixels. PersistedPlayerChargeMoveEnergyFillCadenceWitness. |
| C04 | Left charged-move name | Player Charged Move Name - Left | Direct. LivePlayerControlTextWitness slot 1. |
| C05 | Right charged-move name | Player Charged Move Name - Right | Direct. Same witness, slot 2. |
| C06 | Left charged-button matchup effectiveness | Charged Button Effectiveness - Left | Direct. Same witness, effectiveness mode. This is not text from an executed Fast Move. |
| C07 | Right charged-button matchup effectiveness | Charged Button Effectiveness - Right | Direct. Same witness, effectiveness mode. |
| C08 | Lone centered charged-move name | Derived from left/right name boxes | Relative / fixed detail; no independent center-name box. PlayerChargeControlLayout.centeredRegion. |
| C09 | Lone centered charged-button effectiveness | Derived from left/right effectiveness boxes | Relative / fixed detail; no independent center-effectiveness box. |
| C10 | Pointer Location X/Y text and mapped charged-button touch | Touch Coordinates plus Trainer Charge-Move Controls | Direct source strip; fixed button hit targets within the outer controls. LivePlayerControlTextWitness slot 0 / PointerCoordinateParser. This is visual OCR, not raw touch interception. |
| C11 | Upper inactive Pokémon HP | Inactive Cards: Upper / Lower | Registered older reader, not validated recognition. Shared outer box, fixed split and fixed inner HP window. PersistedPlayerInactiveHpBarWitness.upper; confirmed false positive on non-card pixels in Pixel match 421fd004. |
| C12 | Lower inactive Pokémon HP | Inactive Cards: Upper / Lower | Same limitation and confirmed false-positive case. PersistedPlayerInactiveHpBarWitness.lower. |
| C13 | Upper inactive sprite fingerprint | Inactive Cards: Upper / Lower | Shared outer box, fixed split and sprite window. PersistedPlayerInactiveSpeciesSpriteWitness.upper; fingerprint is not a species identity. |
| C14 | Lower inactive sprite fingerprint | Inactive Cards: Upper / Lower | Same limitation. PersistedPlayerInactiveSpeciesSpriteWitness.lower. |
| C15 | Initial inactive-card shading clearance | Inactive Cards: Upper / Lower | Shared outer box; fixed upper/lower halves. PersistedTrainerInactiveTimerOverlayClearanceWitness produces supporting start evidence, not timer seconds. |

The inactive-card capture divides the outer box exactly 50/50. In each half the
HP reader uses X 17–144 of 160 and Y 138–150 of 190; the sprite reader uses
X 18–144 of 160 and Y 28–132 of 190. These inner coordinates cannot be corrected
independently in the current editor.

Sources: [charge fill sampling](../../app/src/main/java/com/example/overdex/battle/observation/LivePlayerChargeMoveEnergyWitness.kt),
[button rim search](../../app/src/main/java/com/example/overdex/battle/observation/ChargeButtonFillHeightReader.kt),
[centered layout](../../app/src/main/java/com/example/overdex/battle/observation/PlayerChargeControlLayout.kt),
[control text](../../app/src/main/java/com/example/overdex/battle/observation/LivePlayerControlTextWitness.kt),
[inactive HP](../../app/src/main/java/com/example/overdex/battle/observation/PersistedPlayerInactiveHpBarWitness.kt),
[inactive sprite windows](../../app/src/main/java/com/example/overdex/battle/observation/PersistedPlayerInactiveSpeciesSpriteWitness.kt),
[opening shading clearance](../../app/src/main/java/com/example/overdex/battle/observation/PersistedTrainerInactiveTimerOverlayClearanceWitness.kt).

## Audio and motion readers

| ID | Reader | Input / output | Screen calibration |
| --- | --- | --- | --- |
| A01 | AudioCaptureWitness | Cue-centered recorded PCM/WAV, source/availability | N/A; raw audio capture, not species recognition. |
| A02 | PersistedBattleCryCandidateWitness | Retained audio versus reference cries; ranked species candidates | N/A. |
| A03 | DecisiveBattleCrySpeciesWitness | Cry candidates plus roster/opening context into species/side | Derived, no new pixels. |
| A04 | LiveFastMoveSoundWitness | Shared PCM and reference sound matching | N/A. |
| A05 | StatChangeSoundWitness | Attack up/down and Defense up/down sounds | N/A. |
| A06 | StatChangeAttributionWitness | Sound plus charged-move evidence into affected side/stat | Derived, no new pixels. |
| A07 | ResultMusicWitness | Win/loss music and distractor references | N/A. |
| A08 | DeviceMotionPulseWitness / DeviceMotionPulseDetector | Linear-acceleration pulses | N/A; this is not a direct vibration API reader. |
| A09 | Guided-registration cry recognition / RegistrationCryCapture | Bounded five-second cry recording, ranked local reference candidates, then user species confirmation | N/A; an explicit registration audio action outside battle. |

No user audio/sensor calibration screen was found in current wiring. Their
references, windows and acceptance thresholds are code-defined. Appropriate
future controls would be audio/sensor tuning, not screenshot rectangles.
ChargeMoveQteVibrationInference groups A08 using charge-phase evidence and does
not introduce another visual aperture. There is no separate production
charged-move-type audio classifier; retained charged-effect clips do not imply
that such classification is running.

Sources: [audio capture](../../app/src/main/java/com/example/overdex/battle/audio/AudioCaptureWitness.kt),
[cry candidates](../../app/src/main/java/com/example/overdex/battle/audio/PersistedBattleCryCandidateWitness.kt),
[Fast Move sound](../../app/src/main/java/com/example/overdex/battle/audio/LiveFastMoveSoundWitness.kt),
[stat sounds](../../app/src/main/java/com/example/overdex/battle/audio/StatChangeSoundWitness.kt),
[result music](../../app/src/main/java/com/example/overdex/battle/audio/ResultMusicWitness.kt),
[device motion](../../app/src/main/java/com/example/overdex/battle/observation/DeviceMotionPulseWitness.kt),
[registration cry matching](../../app/src/main/java/com/example/overdex/data/registration/RegistrationCry.kt).

## Raw capture territories, not additional recognizers

| Capture / experiment | Current box | Actual status |
| --- | --- | --- |
| QTE type icons | QTE Type Icons | Bounded raw crops. No moving-glyph/type classifier is currently enabled. |
| Opponent arm / Poké Ball | Opponent Hand / Poké Ball | Manual bounded raw crops. No automatic hand-open, entrance, recall or faint classifier. |
| Charge execution scene | Charge-Move Execution | Registered raw retention; no dedicated consumer recognizing execution from this crop found. |
| Initial inactive timer | Inactive Cards: Upper / Lower | InactiveMatchStartTimerCrop raw capture. Distinct from the working shading-clearance reader. |
| Switch lockout timer | Inactive Cards: Upper / Lower | SwitchLockoutTimerCrop raw capture. No active dial/remaining-seconds recognizer consuming it found. |
| Charged controls | Trainer Charge-Move Controls | Sparse raw retention alongside C01/C02 live readers. |
| Charged-impact HP | Charged Impact HP | Phase-bounded raw retention alongside H04. |
| Normal player/opponent HP | Trainer HP / Opponent HP | Sparse raw retention alongside live HP readers. |
| User-authored custom crops | Each custom Draggy Box | CustomCropCaptureWitness stores pixels. A chosen OCR/IMAGE/COLOR/MOTION method label does not itself attach a corresponding recognizer. |
| Exit icon | Exit Icon | Box and crop declaration exist; no registered exit-icon capture/concession classifier found. |

Team-badge aggregate crop declarations also exist, but current badge presence is
checked directly in the service; declaration alone is not another running
capture. Most other named CropCaptureWitness instances are the source-preserving
halves of the visual readers already listed, not extra recognizers.

Sources: [capture contracts](../../app/src/main/java/com/example/overdex/battle/observation/BattleObservationContracts.kt),
[custom capture implementation](../../app/src/main/java/com/example/overdex/battle/observation/CustomCropCaptureWitness.kt),
[QTE/hand sampler](../../app/src/main/java/com/example/overdex/battle/observation/QteTypeIconSampling.kt).

## Existing geometry helpers and derived observers

SpeciesBadgeTextLocator, TeamBadgeCropLocator and TeamBadgePresenceDetector use
the corresponding saved name, badge, resource or type-related geometry; they
are not extra independent semantic readers. Species row recovery searches
vertically near the assigned strip, keeping its X and size. Retained badge pixels
and Overdex's HUD must remain excluded from fresh game testimony.

These consumers do not inspect new screenshot pixels and do not need their own
screen boxes:

- PokemonFaintWitness / TeamFaintTracker, for both sides: Poké Ball count changes,
  identity and cry corroboration.
- CountdownMatchStartInference; PlayerActiveIdentityInference;
  PlayerBattleInformationInference; FastMoveUseInference;
  FastMoveCadenceInference; FastMoveEnergyInference;
  ChargedMoveAnnouncementInference; ChargeMoveQteVibrationInference;
  BattleInterpreter; OpponentEnergyForecast.
- SpeciesCheckCoordinator; FirstLiveCombatRouter; TeamResourceSnapshotGate;
  InactiveBenchSnapshotGate; MatchOutcomeCaptureGate; ActiveTypeSamplingGate;
  DecisionTimerCaptureGate; SwitchTimerCaptureGate; BattlePhaseTextSampling;
  ChargeTextOverlayProtection. These coordinate attention, availability or
  presentation from already observed evidence; their input readers retain the
  relevant boxes.
- Diagnostics, archive selectors, timeline projections and display widgets are
  not additional game readers.

The active replay exception is ArchivedSpeciesCropRecognizer: it re-OCRs bounded
samples of the original archived player/opponent species crops. Their historical
coordinates are already preserved. Today's profile must not move yesterday's
source pixels. This is a shared original crop, not another current box to add.

Sources: [Match derivations](../../app/src/main/java/com/example/overdex/battle/observation/Match.kt),
[faint witness](../../app/src/main/java/com/example/overdex/battle/observation/OpponentPokemonFaintWitness.kt),
[capture gates](../../app/src/main/java/com/example/overdex/battle/observation/BattleCaptureGates.kt),
[replay OCR](../../app/src/main/java/com/example/overdex/battle/replay/ArchivedSpeciesCropRecognizer.kt).

## Registration readers outside battle

RegistrationFields and RegistrationCalibrationScreen expose these 16 separate
boxes. Guided live requested reads use the selected field's crop. The explicit
Read Screenshot for Registration action additionally runs whole-page semantic
OCR through RegistrationScreenshotReader / DetailPageReader, then supplements it
with the text-field crops. That optional full-page search has no independent
page-territory control and is not a background battle reader.

| Field / box ID | Actual reading |
| --- | --- |
| CP / CombatPower | OCR |
| Shiny / ShinyIndicator | Manual choice; automatic image matching not implemented in this field path |
| Favorite / FavoriteStar | Manual choice |
| Name / Nickname / SpeciesName | OCR |
| Gender | Manual choice |
| HP Row / HpRow | OCR |
| Size Label / SizeLabel | OCR |
| Type Labels / TypeIcons | OCR text labels |
| Candy Family / CandyPanel | OCR |
| Evolution / EvolutionRow | OCR |
| Shadow / Purified / ShadowStatus | OCR plus manual choice |
| Gyms & Raids / Trainer Battles / MoveHeading | OCR |
| Fast Move Row / FastMoveRow | OCR |
| Charged Move 1 / ChargedMoveRowA | OCR |
| Charged Move 2 / ChargedMoveRowB | OCR |
| New Attack / Moves End / MovesEnd | OCR |

Sources: [registration fields and parsing](../../app/src/main/java/com/example/overdex/data/registration/RegistrationFields.kt),
[registration crop editor](../../app/src/main/java/com/example/overdex/ui/screens/RegistrationCalibrationScreen.kt),
[screenshot/field reader](../../app/src/main/java/com/example/overdex/data/registration/RegistrationScreenshotReader.kt),
[semantic page reader](../../app/src/main/java/com/example/overdex/data/registration/DetailPageReader.kt).

## Dormant, legacy, debug and not-yet-implemented paths

These were not found among current production registrations and must not be
added to the active missing-box count merely because their source files exist:

- PersistedSpeciesWitness: older player/opponent badge OCR implementation;
  current production uses LiveOverlaySpeciesPipeline.
- PersistedActiveHpBarWitness, PersistedActiveHpBarBorderCadenceWitness and
  PersistedActiveHpBarBorderPulseWitness: older implementations. Their shared
  geometry/helper code can remain in use by the live readers.
- SpeciesObserver and CountdownObserver: alternate older direct observer paths;
  no production instantiation found in this audit.
- battle/witness SpeciesWitness, PlayerSpeciesWitness, CountdownWitness,
  AnnouncementWitness, AttackIncomingWitness, YouWinWitness, GoodEffortWitness:
  older generic observation route, not the current dispatcher construction.
- PersistedFastMoveSoundWitness in FastMoveSoundMeasurement.kt: alternate audio
  path; current production uses LiveFastMoveSoundWitness.
- ObservationRecognizer / SimpleAnchorDetector and their legacy generic CP,
  candy, move and shadow recognizers are not independent currently registered
  battle readers. The older ObservationExtractor/RegistrationEngine route is
  separate from the guided field registration described above.
- DebugObserver / ObservationPipelineDemo and manual/synthetic observation
  sources are diagnostic inputs. data/BattleObserver is an empty class.
- QrCodeAnalyzer is a whole-camera reader with no production call site found;
  it is outside Pokémon GO screenshot geometry.
- Stat-change text, shadow-aura identity, opponent-arm recall/send-out, exit-icon
  concession, black/white-screen concession and tree/bush battlefield recognition
  have no dedicated active classifier found. Generic OCR may retain some visible
  text. Adding a rectangle alone would not implement these classifiers.

The six hidden legacy battle fields are enemyNameRegion, hpBarRegion,
teamIconsRegion, moveBannerRegion, youWinRegion and goodEffortRegion. Their old
names must not be confused with the current side-specific name/HP/resource
controls, Announcement, or Match Outcome. The two hidden active post-match
fields are the separately identified gaps above.

## Audit boundary and next implementation order

This was a source and caller audit, not a phone accuracy test. No application
code, device settings, calibration values or saved match records were changed.
The full 40-choice inventory and source-image collection plan remain in
[calibration-capture-checklist.md](calibration-capture-checklist.md).

Implement first: expose the two post-match controls and correct VS watcher
assignment. Then make HP effectiveness and recipient visual territories
independently adjustable while keeping them attached to their detected objects.
Next address inactive-card children and centered charged-control text. Preserve
current defaults and all existing user profiles when adding those controls.
Keep every independent raw witness beneath downstream inference.
