# Pixel 4 battle-event screenshot inventory

Reviewed November 8, 2026 against runtime source at `5a9b8f46`.

## Source and limits

The supplied folder is
`/home/sean/Desktop/pixel4calibrationscreenshots`. It contains **34 PNGs, all
1080 × 2280**. The user attached 33; `Screenshot_20261108_153001.png` is the
additional file present in the folder. Inputs were inspected without modifying
them. These are Pixel 4 references, not Moondrop calibration coordinates.
The device/aspect checks in calibration must continue to apply when these
screenshots are offered elsewhere.

These stills provide target appearance and coverage examples. They do not
establish transition duration, exact attack/KO time, ordering of adjacent video
frames, audio, or haptic timing. File timestamps describe taking the screenshot;
several images show an earlier recording clock. Do not use differences between
the filenames as elapsed battle time.

The developer pointer-location strip is present across the set and overlaps
the upper battle UI. Two files additionally show the gallery/video-player
interface. A reference may therefore be useful for one clear crop while being
unsuitable as a clean example of the entire screen. No screenshot grants
permission to treat gallery controls, the pointer strip, or Overdex's own UI as
Pokémon GO testimony.

For the calibration-library integration in this change, **32 supplied frames**
are admitted as native-dimension JPEG derivatives. The supplied gallery-overlay
Excellent frame `152222` and the discovered gallery-overlay Flamethrower frame
`153001` are excluded from that selectable set. Their untouched source PNGs
remain in the source folder, and the asset catalog records hashes and the
exclusion reason. The Excellent image is still useful as a hand-reviewed glyph
reference; exclusion does not invalidate its visible word.

## Per-file visual labels

Filenames below share the prefix `Screenshot_20261108_` and suffix `.png`.
Labels describe what is visible, rather than an inferred exact event time.

| Suffix | Visible target or state | Useful crop / restriction |
| --- | --- | --- |
| `151552` | Great League Choose Your Party; CP limit 1500; Swampert, Quagsire and Obstagoon | Team Select, league/limit, three roster slots |
| `151620` | Candela versus Cenospecie; central VS token | VS; portrait/context reference |
| `151641` | White game surface between VS and battlefield, with Android/pointer overlays | Candidate pre-battle transition appearance; no GO or Match Start proof |
| `151920` | Opening countdown 3; `Go, swampert!`; field camera before player appears | Countdown and entry announcement |
| `151936` | Countdown 2 with close-up of Candela preparing to throw | Countdown; different camera class from normal battle |
| `151956` | Countdown 1; `Go, Combusken!`; entry flash | Countdown and entry announcement |
| `152009` | Large GO; Swampert versus Combusken; both team badges, HP and two charged controls | Primary GO appearance; normal UI coverage, some content obscured by GO |
| `152027` | Normal combat; both bars; effectiveness over player's bar; Sludge and Hydro Cannon controls | HP, effectiveness, energy fill/control layout, normal trainer pose |
| `152126` | `Get Ready!`; player Swampert facing camera | Early player charge-phase text; charge-button/bench UI absent |
| `152147` | White `NICE!` with poison QTE icons and swipe trail | QTE progression and type-icon appearance |
| `152206` | Yellow `GREAT!` with poison QTE icons/swipe trail | QTE progression and type-icon appearance |
| `152222` | Orange `EXCELLENT!` in the poison QTE | **Gallery overlay** including header, playback controls and bottom actions; clear progression text, not a clean full-screen reference |
| `152242` | `swampert used Sludge!`; zoomed Combusken receiving impact; reduced HP | Named charged announcement/impact; charge-attack camera class |
| `152312` | Combusken has disappeared into a bright orb; empty outlined HP bar; opponent first ball dark; effectiveness text | Non-final KO/resource/drop/removal reference; source still cannot timestamp the finishing hit |
| `152408` | `Go, Magcargo!`; Candela's arm extended; normal controls visible | Entry and trainer-arm candidate; camera differs from resting trainer example |
| `152452` | `swampert used Hydro Cannon!`; Magcargo receiving water impact | Named charged announcement; impact/HP reference |
| `152517` | Magcargo HP empty during water impact; opponent second ball dark | Charged finishing damage and resource-drop example; no single still establishes exact depletion time |
| `152542` | Flareon entry; `Go, Flareon!` fading; full opponent HP | New active species, entry, updated badge |
| `152702` | Swampert HP red/low; Flareon active with green HP and fire effects | Pre-terminal HP and recipient effects; no inferred move identity from color alone |
| `152823` | `Attack incoming! Use a Protect Shield?`, x2 shields, NOT NOW, decision dial 4 | Incoming charge/shield-choice text, dial and controls |
| `152847` | Same shield-choice panel with decision dial 0 | Dial zero; not by itself proof of phase clearance |
| `153001` | `Flareon used Flamethrower!`; player receives fire impact with low HP | **Extra folder file; gallery overlay** at 1:13 / 2:00; clear named announcement, not a clean full-screen reference |
| `153046` | Player Swampert absent; `Switch in a new Pokémon?`; Quagsire/Obstagoon options; dial 12 | Forced replacement UI, player one dark ball, opponent still Flareon |
| `153125` | Same forced-replacement panel; dial 10 | Repeated panel/dial; no additional faint merely because another still exists |
| `153159` | `Go, Obstagoon!`; Great Ball in player foreground; forced-choice panel receding | Replacement entry, side-specific active badge, outgoing panel overlap |
| `153325` | Obstagoon faces Flareon; opponent HP has a tiny orange sliver | Pre-final-KO reference; **HP is not yet visibly zero** |
| `153343` | Flareon recoiling/bowed with white particles; battle badges, HP and controls absent; Candela arm pose changed | Final removal-animation candidate; no visible final ball count or HP reading |
| `153417` | Flareon absent, bright orb/particles in its place; Obstagoon remains; Candela standing | Final disappearance stage; missing battle UI is unavailable resource evidence, not a measured 0 balls |
| `153429` | Black game surface with Android/pointer overlays | Candidate end-of-field transition appearance; not a result/outcome by itself |
| `153449` | Empty portrait-oriented result scenery; no trainer or result words yet | Candidate result-scene setup; not a landscape rotation or an outcome |
| `153514` | Player avatar against same result scenery, before result words | Result avatar arrival; useful negative for text/medal reader |
| `153526` | `YOU WIN!` over celebrating avatar | Decisive win-text target |
| `153541` | Glove medal with green star, **84/100**, and a filled progress bar over dimmed avatar | Optional post-result medal/progress target; exact medal identity needs asset mapping |
| `153555` | `YOU WIN!`, avatar, blue-green confirmation check | Persistent result text and dismissal control |

## Runtime coverage at audit time

The composition root is
[`PokedexViewModel.kt`](../../app/src/main/java/com/example/overdex/ui/PokedexViewModel.kt).
The distinction below is between code actually registered there and visual
ideas that are only represented by these supplied examples.

| Cue | Implemented path | Boundary |
| --- | --- | --- |
| Team Select / league and roster | Dedicated Team Select crop contracts and `PersistedTeamSelectWitnesses` | Saved roster or party recognition is not battle start |
| VS | `PersistedVsScreenWitness` reads the preserved central VS crop | Exact central VS token, not the whole trainer portrait; no white-flash witness |
| Opening 3 / 2 / 1 / GO | `PersistedCountdownGlyphWitness` and associated countdown routing | GO is the primary start boundary; a white screen is not a replacement |
| Entry announcement and species | `PersistedAnnouncementWitness`, `PersistedAnnouncementSpeciesWitness`, `PersistedPlayerEntrySpeciesWitness`, side-specific badge/species paths | Announcements and side/roster context remain separate evidence |
| Get Ready / named charged move | `PersistedAnnouncementPhraseWitness` and named announcement inference | Retains source crop time and raw text |
| Nice / Great / Excellent | Dedicated `qteProgressText` crop read by `PersistedAnnouncementWitness` under `QTE_PROGRESS_TEXT_WITNESS`; replay reads accepted grades | Existing OCR path, not a new feature implied by inventory; animation needs vertical margin |
| QTE type icons | `QteTypeIconSampling` opens a bounded raw-image collection after Get Ready, with manual sampling support | **Sampling, not a type recognizer or bubble-pop counter**; 125 ms interval, at most 80 images / 10 seconds |
| Incoming attack / choose shield | `PersistedAttackIncomingWitness`, announcement phrase checks and corroborated shield-control OCR | A grade or generic mention of shields is not an incoming-charge start |
| Decision dial | `PersistedDecisionTimerWitness` checks dial appearance plus repeated legal numerals; emits absence only after repeated disappearance | Dial can be used during shield choice and forced replacement; a numeral alone does not resolve which workflow |
| HP damage / empty bars | `LiveActiveHpBarWitness`, damage-tick, border-pulse and cadence witnesses | A removed or occluded bar is unavailable; it must not be fabricated as zero |
| Non-final faint / resources | Side-specific `PokemonFaintWitness` derives from a measured Poké Ball decrease and may cite a recent cry candidate | Not a generic fall-over-animation classifier; a missing third-ball frame needs independent terminal evidence |
| Win / loss | `PersistedOutcomeTextWitness`, outcome probe/capture gates and `PersistedOutcomePhraseWitness` | YOU WIN / GOOD EFFORT supply outcome; result music can corroborate via a separate registered audio path |
| Exit icon / bench / charge controls disappearing | Exit crop is calibratable; bench and control crops have existing separate consumers | No dedicated all-controls-disappeared visual transition reader found in the current composition |
| White transition / black transition | No dedicated registered recognizer found | Candidate low-cost screen measurement only; cannot independently start/end a match |
| Empty result background / avatar arrival | No dedicated registered recognizer found | Candidate result-OCR wake cue after established battle context |
| Glove medal / progress | No dedicated registered recognizer or progress parser found | Optional post-result evidence; absence must not reject a real result |
| Opponent trainer's arm motion / recall beam | No dedicated registered recognizer found | Candidate entry/exit motion support; cannot alone distinguish faint from voluntary switch |

Relevant source contracts:
[`BattleObservationContracts.kt`](../../app/src/main/java/com/example/overdex/battle/observation/BattleObservationContracts.kt),
[`PersistedAnnouncementPhraseWitness.kt`](../../app/src/main/java/com/example/overdex/battle/observation/PersistedAnnouncementPhraseWitness.kt),
[`PersistedAttackIncomingWitness.kt`](../../app/src/main/java/com/example/overdex/battle/observation/PersistedAttackIncomingWitness.kt),
[`OpponentPokemonFaintWitness.kt`](../../app/src/main/java/com/example/overdex/battle/observation/OpponentPokemonFaintWitness.kt),
[`PersistedOutcomeTextWitness.kt`](../../app/src/main/java/com/example/overdex/battle/observation/PersistedOutcomeTextWitness.kt),
[`QteTypeIconSampling.kt`](../../app/src/main/java/com/example/overdex/battle/observation/QteTypeIconSampling.kt).

The existing [reference-asset inventory](pokemon-go-reference-assets.md) locates
glove-medal artwork under `Images/Badges/Achievements/Badge_14_{1,2,3}_01.png`.
That is an asset lead, not proof of the exact badge identity, tier, or runtime
recognition of this 84/100 example.

## Bounded next tests

1. **Progression-text coverage.** Use the three visible grades as crop fixtures,
   including the Excellent gallery-overlay warning. Widen vertical coverage for
   the animated text while retaining its independent lane. Verify full glyph
   coverage at both top and bottom of a continuous recorded QTE; these three
   stills alone cannot establish the full excursion. Retain raw crops, and do not
   silently overwrite a user's adjusted box when changing an unchanged default.
   Native-frame glyph bounds measured in this set are approximately: Nice
   X 396–684 / Y 628–717; Great X 334–740 / Y 637–731; Excellent X 149–930 /
   Y 620–730. The proposed broader default is X .12–.88 and Y .23–.36 of the
   full source frame, including a roughly ±64-pixel vertical-motion allowance
   at this resolution. That allowance is an explicit test envelope, not a claim
   that three stills measured the animation's maximum movement.

2. **Final-KO sequence.** Obtain contiguous video around Flareon's last HP sliver,
   impact, visible removal, black transition, result scene and YOU WIN. Annotate
   each observed cue independently. The `153325` / `153343` / `153417` stills
   establish appearances, not the finishing attack's identity or timestamp.
   Compare with a voluntary switch, a non-final faint, a concession with live HP,
   and a final charged KO. Missing third-ball UI must remain missing evidence.

3. **Transition probe, offline first.** Measure the game interior's near-white
   and near-black fractions, excluding Android status/navigation/pointer areas.
   Require a bounded sequence of preceding and following scene cues; flash
   attacks, loading screens, gallery playback, screen-off and capture failure are
   necessary negatives. The proposed output should say which appearance was
   measured, with source time/geometry/availability. It should only wake a
   bounded follow-up reader, not fabricate GO, KO or outcome.

4. **Medal/progress probe.** Compare the glove image against mapped source assets
   and parse `84/100` independently from the bar geometry. Require the result
   context, not the glove icon alone. Include other medals, tier variants, the
   same badge shown outside battle, and a completed-badge account with no medal
   overlay. Never require the overlay to recognize a win, and never infer the
   user's full badge history from one progress sample.

5. **Trainer-arm/recall probe.** For this Pixel/Candela set only, an initial
   hand-reviewed inspection envelope is approximately **X 790–1080, Y 860–1140**
   in the full 1080 × 2280 frame (normalized x ≈ .7315, y ≈ .3772, width ≈ .2685,
   height ≈ .1228). It includes upper torso, elbow and hand across the ordinary
   field poses; it is **not a saved calibration or a validated universal box**.
   Resting `152027`, entry `152408`, and removal `153343` / `153417` are initial
   comparison examples. They do not all have an identical camera framing.
   The close-up countdown `151936`, charge-impact camera, and result avatar must
   be rejected or handled in different camera classes. Prefer a trainer-body
   anchor and relative hand/forearm motion over hard-coded clothing colors.
   Keep the output as an entry/exit gesture candidate until independently
   corroborated by ball/beam direction, entry text, same-side ball count or HP.
   One gesture cannot distinguish recall after fainting from a voluntary switch.
   Test PvP avatars, all leader/Rocket poses, both normal and zoomed camera,
   mirrored side assumptions, clothing/skin variants and recipient particle
   overlap before any live integration.

No new runtime witnesses, phone profile changes, or battle-history edits are
introduced by this inventory.
