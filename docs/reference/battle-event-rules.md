# Battle event rules

Current-source audit: 2026-11-10, updated 2026-11-11 for partial result labels, source-time faint attribution, finishing-observation windows, and the one-way-rule audit. This describes the registered observation and inference paths, not everything a reader name or a planned feature might suggest. A capture assignment is permission to look; it is not a battle event.

The [battle rulebook](battle-rulebook.md) preserves the trainer's November 11
if/then notes, normalized team-count facts, outcome exceptions, and proposed
acceptance cases. Its ongoing collection also records implemented one-way rules,
their limits, and supporting source/tests. Proposed rules do not change the
implementation audited here.

The ledger keeps four things separate: pixels/audio captured, measurements made from them, events inferred from those measurements, and the HUD/replay presentation. Use the original `monotonicTimeNanos` and `perceivedAt` when associating evidence. A result published late must not become an event that happened late merely because `recordedAt` is later. The owning [Match](../../app/src/main/java/com/example/overdex/battle/observation/Match.kt) retains the source Articles and the derived Articles.

## Capability status: inactive HP, switch lockout, and replacement prompt

| Signal | Current implementation | Readiness boundary |
| --- | --- | --- |
| Upper/lower inactive Pokémon HP | Older registered [HP readers](../../app/src/main/java/com/example/overdex/battle/observation/PersistedPlayerInactiveHpBarWitness.kt) measure bright saturated pixels in fixed bands and publish `PlayerInactiveHpBarMeasured`. | **Not a reliable validated capability.** In Pixel match `421fd004`, the readers published 23.75% and 100% from referenced crops containing no visible inactive-card HP bars. Those are false positives. Existing code and published Articles do not establish valid HP recognition. |
| Switch lockout shade/dial | `SwitchLockoutTimerCrop` and its capture gate preserve images after a player appearance change. | **Capture only.** No registered consumer measures lockout duration, remaining seconds, or ready/locked state from this crop. The opening shading-clearance reader and central decision-timer reader are different signals. |
| “Switch in a new Pokémon?” replacement prompt | A dedicated adjustable crop and bounded OCR reader publish `ForcedReplacementPromptWitnessed`, preserving the original text/crop and waking player resources, inactive cards, and the decision timer. | **Implemented; fresh live acceptance pending.** Unit tests and two real-screenshot emulator OCR checks cover the reader. Installed on Moondrop. The prompt does not identify a fainted Pokémon or decrement the team count. |

The resource/bench wake-up repair changes acquisition timing. It does not repair inactive-HP pixel validation or implement switch-lockout recognition. The replacement reader is a separate implemented slice; see the [finishing-observation verification](../verification/2026-11-11-finishing-observation-windows.md). Capture scheduling and the existence of a reader do not establish recognition accuracy.

## What a cry can establish

**A cry alone does not currently establish a faint. A cry plus a bar moving, shrinking, or disappearing does not currently establish a faint either.** The current faint rule requires a measured decrease in that side's surviving Poké Ball count. Cry recognition separately supports species identity when its acoustic and side-attribution requirements pass.

| If | Then | Limit |
|---|---|---|
| A captured audio window has ranked cry-reference matches. | Retain `BattleCryCandidatesMeasured`, including alternatives and source audio. | These are species-reference similarities, not classifications of “entry cry” versus “faint cry.” |
| The best species match is at least **0.88**, exceeds a different-species runner-up by at least **0.035**, and a compatible cue and side attribution are available. | The decisive cry witness can publish `ActivePokemonSpeciesWitnessed` for that side. | A distinct-species runner-up is required. A countdown cue beginning more than three seconds after Match Start is rejected. Similarity is not a probability that a faint occurred. |
| Source-time team history resolves a loss to a Pokémon, and a cry-candidate Article includes that species ID within the preceding **five seconds** of the decrease. | The faint tracker can cite that cry as nearby corroboration; its assigned confidence is **0.99** instead of **0.96**. | This helper excludes `FAST_MOVE_IMPACT` and requires a matching species ID, but not a minimum similarity, independently recognized side, or faint-cry classification. No cry is required for a resolved count-based faint. These assigned confidence values are not calibrated faint probabilities. |
| A cry is recognized but no ball decrease is measured. | Species evidence may still be useful; no `ActivePokemonFaintedWitnessed` is produced by this path. | Absence of a faint Article does not establish that the Pokémon survived. |

Sources: [audio capture](../../app/src/main/java/com/example/overdex/battle/audio/AudioCaptureWitness.kt), [ranked cry measurements](../../app/src/main/java/com/example/overdex/battle/audio/PersistedBattleCryCandidateWitness.kt), [decisive species rule](../../app/src/main/java/com/example/overdex/battle/observation/DecisiveBattleCrySpeciesWitness.kt#L29), [faint tracker](../../app/src/main/java/com/example/overdex/battle/observation/OpponentPokemonFaintWitness.kt#L91).

The audio observers are registered when microphone permission is available in [PokedexViewModel](../../app/src/main/java/com/example/overdex/ui/PokedexViewModel.kt). Entry announcements and opening countdown evidence request cue-centered audio through [Match](../../app/src/main/java/com/example/overdex/battle/observation/Match.kt) and the [countdown presence cue witness](../../app/src/main/java/com/example/overdex/battle/audio/PersistedCountdownCryCueWitness.kt). `POKEMON_FAINTED` and `OPPONENT_DEPARTED_NON_FAINT` are accepted names in [BattleCryListeningCue](../../app/src/main/java/com/example/overdex/battle/audio/BattleCryListeningCue.kt), but this audit found no active producer requesting those two cue kinds. Their declarations do not establish a live faint-cry detector.

## Reading HP alongside a cry

| Observation | What it supports | What it cannot establish |
|---|---|---|
| A fresh, unobscured, correctly attributed, same-appearance bar contains nonzero HP at the same source moment. | That Pokémon was alive at that moment; this is evidence against a faint at that moment. | The meaning of a nearby cry, or survival after a later hit. |
| A half-full bar is visible before the final hit. | The Pokémon had that HP before the hit. | That the next hit could not be lethal. |
| A Pokémon enters with partial HP. | A previously damaged Pokémon may have switched back in. | That entry did not occur. Entry does not require full HP. |
| A bar changes size or position. | Its geometry changed. | Entry, faint, or damage cause: charged camera changes and ordinary motion can move bars too. |
| A bar disappears or cannot be measured. | The reader has no usable bar measurement there. | Zero HP. Occlusion, shadow aura, camera movement, and capture loss remain alternatives. |
| A complete outlined bar has an independently verified dark, unfilled interior and belongs to the current side/appearance. | A much stronger zero-HP measurement, with its own geometry and source time. | The current faint witness still needs its independent ball-count transition. |

The distinction is between **positive contemporaneous HP evidence** and **an unavailable or older measurement**. “Cry plus geometry” needs these qualifications before it can become a safe event rule. See the [terminal pixel verifier](../../app/src/main/java/com/example/overdex/battle/observation/TrackedTerminalHpBarMeasurer.kt) and [live HP witness](../../app/src/main/java/com/example/overdex/battle/observation/LiveActiveHpBarWitnesses.kt).

## Match start and end

| If | Then | Guard or unresolved case |
|---|---|---|
| The registered countdown witness publishes typed `CountdownGlyphWitnessed("GO")`. | Derive one `MatchStarted` using that witness's source time. | A name, full HP bar, or countdown-like button shading is not GO. |
| A registered `3`, `2`, or `1` predicts a start, and real combat evidence follows within 0–30 seconds of that prediction. | The fallback can derive `MatchStarted` at the predicted time, citing both countdown and combat evidence, confidence **0.75**. | The fallback does not invent a witnessed GO. Eligible confirmation is an HP damage tick, a PRESENT HP-border pulse, Attack Incoming, Get Ready, or a USED cue. |
| The accepted result witness resolves `YOU WIN` or `GOOD EFFORT` to its expected outcome. | Derive one `MatchEnded(WIN/LOSS)`. | Repeated raw result reads remain evidence; they do not create repeated endings. |
| Result music agrees with, or conflicts with, nearby result-screen testimony. | Record corroboration/conflict against the independent result evidence. | Music does not create or amend `MatchEnded`. Its current listening gate is not restricted to a witnessed last-Pokémon recall. |
| A black screen, departure animation, empty field, observation stop, or missing result occurs. | Keep those observations and leave unsupported outcome/cause unknown. | There is no dedicated current concession or tie classifier. A win screen alone does not distinguish an opponent concession from a final KO. |

Sources: [BattleInterpreter](../../app/src/main/java/com/example/overdex/battle/interpretation/BattleInterpreter.kt), [countdown fallback](../../app/src/main/java/com/example/overdex/battle/inference/CountdownMatchStartInference.kt), [Match boundary handling](../../app/src/main/java/com/example/overdex/battle/observation/Match.kt), [music corroboration](../../app/src/main/java/com/example/overdex/battle/inference/ResultMusicCorroboration.kt), [music listening gate](../../app/src/main/java/com/example/overdex/battle/audio/ResultMusicListeningGate.kt).

### Partial result-label recognition — November 11

The strict string-only resolver remains unchanged: it accepts the expected YOU
WIN / GOOD EFFORT labels with at most one text edit. The added
[partial resolver](../../app/src/main/java/com/example/overdex/battle/observation/PartialOutcomeTextResolver.kt)
uses preserved word geometry and crop provenance for a separate interpretation.
It does not recognize a font family or measure individual characters' positions.

| Measurement / comparison | Current requirement |
| --- | --- |
| Whole reading | Normalize case, accents and the 0/O, 1/I, 7/T confusions; compare all retained text with the two expected labels. Do not select an attractive substring from unrelated or mixed text. |
| Strong partial | At least **4 matching letters for YOU WIN**, or **5 for GOOD EFFORT**, allowing missing characters and at most **one wrong/extra glyph**. Exactly one label must satisfy the comparison. Recorded `OOD EFFOR` and `oU WINy` satisfy it. |
| Lettering geometry | Every retained word has valid measured bounds and a height of **48–160 pixels normalized to a 672-pixel source width**. The aggregate source-frame center lies within X **0.15–0.85**, Y **0.40–0.65**. Scaling uses the captured source width, not the enlarged OCR input or crop width. Small real OCR crop-edge overruns are tolerated. |
| Layout | One-line and two-line GOOD / EFFORT layouts are allowed. These are bounds of measured OCR words plus fixed layout tolerances, not inferred per-character boxes. |
| Weak WIN signatures | `W` needs center X **0.48–0.66**, Y **0.43–0.57**, width/height **0.4–2**. `WIN` needs X **0.54–0.81**, the same Y range, and width/height **1–4**. |
| Weak LOSS signatures | `FF` or `EFF` needs width/height **0.5–3**, plus either the one-line slot X **0.54–0.72**, Y **0.43–0.57**, or two-line slot X **0.31–0.49**, Y **0.50–0.60**. |
| Weak confirmation | Require the same outcome in **two distinct source crops and source times**, separated by **100 ms–3 s**. Two treatments of one crop do not confirm it; unrelated later text breaks the sequence. A single fragment cannot stop result OCR or establish MatchEnded. |

The result witness preserves original raw text, OCR word boxes and source times.
Accepted partials append `MatchOutcomeLabelWitnessed`, with the result, raw text,
OCR evidence, explicit interpretation basis, supporting-frame count and cited raw
Articles. The interpreter consumes this typed acceptance; it does not force the
damaged text through the old strict string check again. No original OCR is
silently repaired or replaced. Strict text acceptance remains a separate path.

These thresholds are measured heuristics, not calibrated probability scores.
The [verification record](../verification/2026-11-11-partial-result-labels.md)
covers the recorded positive/negative corpus and implementation checks. The
current slice is **installed on the Moondrop; live-battle validation is pending**. Fixed position
tolerances need new-device and new-layout evidence before claiming generality.

The trainer's proposed **victory jump** and **loss head-drop** remain independent
supporting-witness research. Neither is implemented. Each needs a calibrated
trainer region, a temporal sequence rather than a still pose, labeled real
positive/negative examples, and bounded attention after battle-ending context.
Generic bobbing, recall, camera movement, and interrupted captures must remain
negative/unknown alternatives; motion alone does not identify an ending reason.

## Species, entry, and switching

| If | Then | Guard or unresolved case |
|---|---|---|
| A dedicated player/opponent badge crop resolves a species. | Publish that side's active identity, with crop provenance and retained evidence. | Side follows the specific reader/crop, not whichever result finishes first. |
| `Go, <species>!` resolves against the catalogue and independent evidence attributes a side. | Publish the attributed entry identity using the announcement's original source time. | Saved roster membership is not proof a Pokémon was actually brought. Excluding a player species needs the complete witnessed team and no contradiction. Mirrors need independent side evidence. |
| An entry species resolves but its side cannot be established. | Retain unsided identification; attribution may resolve later from source-time context. | Do not choose a side from first/second arrival order. |
| The accepted active species changes on one side. | Establish the new active identity/appearance and reset appearance-owned tracking. | Identity change alone does not say voluntary switch versus replacement after a faint. |
| A fresh, stable ball count remains unchanged. | Refresh the source-time count baseline used to bound a later decrease's candidate interval. | An equal count alone does not establish a voluntary switch, name a fainted Pokémon, or clear all unresolved earlier losses. Partial HP is compatible with a returning combatant. |

Sources: [badge pipeline](../../app/src/main/java/com/example/overdex/battle/observation/LiveOverlaySpeciesPipeline.kt), [entry species witness](../../app/src/main/java/com/example/overdex/battle/observation/PersistedPlayerEntrySpeciesWitness.kt), [side attribution](../../app/src/main/java/com/example/overdex/battle/observation/Match.kt), [TeamFaintTracker](../../app/src/main/java/com/example/overdex/battle/observation/OpponentPokemonFaintWitness.kt#L80).

## Faints and resource measurements

| If | Then | Guard or unresolved case |
|---|---|---|
| Two agreeing usable Poké Ball crops establish a count. | Publish the side-specific count measurement. Opponent counts use `OpponentBattleResourceCountMeasured(resource = POKE_BALLS)`; player counts use `PlayerPokeBallCountMeasured`. | Missing/covered badge pixels are unavailable, not zero. An unchanged fresh count can finish a new reader assignment. |
| A side's later accepted count is lower than its previous accepted count. | Preserve the team loss and reconcile candidate Pokémon against capture-time appearance history. Publish a named `ActivePokemonFaintedWitnessed` only when ownership resolves uniquely. | A usable earlier baseline is required. Noisy increases and repeated readings cannot manufacture losses. Missing or ambiguous identity remains an unresolved team loss. |
| A count decrease or identity observation is delivered late or out of order. | Recompute the source-time history shared by live presentation and Replay. | Neither the currently displayed nor immediately previous Pokémon is automatically the owner. Preserve original Articles and their capture timestamps. |
| Independently attributed positive HP is observed strictly after a candidate's possible loss. | Eliminate that candidate from the earlier loss and revise the current attribution. | An identity label alone, same-timestamp HP, or missing HP is insufficient. Positive HP after a zero-survivor count leaves a contradiction unresolved. |
| Zero HP, a bounded departure hint, or another permitted battle transition wakes a resource reader. | Request a short independent resource check. | A wakeup is scheduling, not a faint, recall, send-out, or concession conclusion. |
| Two agreeing usable shield crops establish a count; a later count decreases. | Preserve shield resource evidence. | Spending a shield does not identify a move or establish a faint. |
| The player bench needs its initial snapshot, or a player switch/faint opens another assignment. | Capture the inactive cards until a registered reader accepts a measurement or the window expires. | Capturing an image alone does not finish recognition. The older HP reader can accept false positives; its accepted measurement is not proof of a valid bar. |

Resource and bench assignments now get a full bounded **three-second acquisition window from receipt of the request**, even when recognition of the triggering frame arrived late. The original cue time still orders requests and rejects stale cues; only current frames/readings belonging to the new assignment can complete it. This repairs a wakeup expiring before it could capture anything, without changing what counts as a faint.

Sources: [stable resource readers and pixel counter](../../app/src/main/java/com/example/overdex/battle/observation/PersistedOpponentBattleResourceWitness.kt), [faint witness](../../app/src/main/java/com/example/overdex/battle/observation/OpponentPokemonFaintWitness.kt), [source-time loss resolver](../../app/src/main/java/com/example/overdex/battle/inference/TeamLossResolver.kt), [live history](../../app/src/main/java/com/example/overdex/battle/inference/LiveTeamLossHistory.kt), [Replay projection](../../app/src/main/java/com/example/overdex/battle/replay/ReplayTeamLossProjection.kt), [resource attention gates](../../app/src/main/java/com/example/overdex/battle/observation/BattleCaptureGates.kt), [departure-check policy](../../app/src/main/java/com/example/overdex/battle/observation/HpBarDepartureCheckPolicy.kt).

## HP measurement and charged camera handling

| If | Then | Guard or unresolved case |
|---|---|---|
| A valid outlined HP bar is found in the assigned territory. | Publish `ActiveHpBarMeasured`: side, bounds, fill fraction, source size, confidence, and literal fill color when available. | A scalar fill fraction is a measurement, not damage cause or faint. |
| A fresh ordinary bar is actually RED and at most **0.25** filled. | Arm the short terminal follow-up so a brief verified EMPTY bar can be captured. | Low fraction alone is insufficient to arm it; GREEN/YELLOW disarm. Freshness, dimensions, and appearance boundaries invalidate old anchors. |
| Independent Get Ready/Attack Incoming/USED phase evidence selects the receiving side and a valid charged-impact window. | Search/track the centered recipient bar with that phase's ownership. | Get Ready prepares the opponent recipient; Attack Incoming prepares the player recipient. A bar moving by itself is not a charged move. |
| A stable bar loses fill with the detector's persistence and geometry requirements. | Publish an HP damage tick without naming the attack. | A large sample gap or relocation reacquires a baseline instead of treating all unseen loss as one hit. |
| An independently verified EMPTY sample follows a fresh settled baseline of at most **0.20**, with matching horizontal caps, sufficient support, and the detector's confidence/time guards. | The restricted terminal handoff can publish the damage tick without demanding a second EMPTY frame. | This **0.20** damage-handoff condition differs from the **0.25** watch-arming threshold. It still does not publish a faint. |
| A recent ordinary bar is missing, the other ordinary bar remains visible, and phase/visibility guards pass. | Wake a bounded independent resource check. | The departure policy publishes no departure/faint Article. Charged phases and missing normal-camera return limit when this hint can be used. |
| Announced charge context and matching camera geometry identify a charged interval. | Exclude that interval from Fast Move impact accounting. | Geometry alone does not announce a charge; the guard does not infer species or damage cause. |
| Charge context is followed by two qualifying normal-camera frames containing both ordinary HP bars. | Open a fixed three-second recipient-pixel watch, retaining actual new pixels even if a later HP outline is unavailable. | An old bar location can locate new pixels; it cannot supply a new HP value. One return frame is insufficient. Appearance, dimensions, and lifecycle changes invalidate the watch. |
| A bounded terminal-visual assignment preserves actual sprite-territory frames. | Retain those frames and a neutral `TerminalVisualSequenceCaptured` summary. | Capture does not classify shrink, disappearance, recall, send-out, or faint. Shadow aura, failed capture, and obscuration do not prove an absent sprite. |

Sources: [live HP and damage-tick detector](../../app/src/main/java/com/example/overdex/battle/observation/LiveActiveHpBarWitnesses.kt), [terminal watch](../../app/src/main/java/com/example/overdex/battle/observation/TerminalHpWatchPolicy.kt), [EMPTY pixel verifier](../../app/src/main/java/com/example/overdex/battle/observation/TrackedTerminalHpBarMeasurer.kt), [charged-impact watch](../../app/src/main/java/com/example/overdex/battle/observation/ChargedImpactHpWatchPolicy.kt), [departure check](../../app/src/main/java/com/example/overdex/battle/observation/HpBarDepartureCheckPolicy.kt), [charged camera guard](../../app/src/main/java/com/example/overdex/battle/inference/ChargedHpCameraGuard.kt).

The later capture paths are documented by [PostChargeRecipientWatchPolicy](../../app/src/main/java/com/example/overdex/battle/observation/PostChargeRecipientWatchPolicy.kt), [TerminalVisualSequenceWitness](../../app/src/main/java/com/example/overdex/battle/observation/TerminalVisualSequenceWitness.kt), and the [finishing-observation verification](../verification/2026-11-11-finishing-observation-windows.md). These are implemented acquisition paths, with live effectiveness still to be established.

## Charged moves, timers, and auxiliary crops

| If | Then | Guard or unresolved case |
|---|---|---|
| Accepted text establishes Get Ready, Attack Incoming/protect prompt, or a generic USED phrase. | Publish the corresponding charged-phase cue and wake its assigned readers. | A generic USED cue establishes phase, not the named move or its damage. Duplicate visible episodes are suppressed. |
| A named `<species> used <move>` resolves to a legal charged move and the performer can be attributed. | Derive the charged-move identity/use with cited announcement/context and reference cost. | Mirror species and nickname ambiguity require independent side evidence; legal move membership alone does not prove use. |
| An allowed faint/charge cue arms decision-timer reading and the crop resolves a valid timer/dial. | Preserve timer evidence and pause appropriate Fast Move accounting. | A timer's absence or a scalar guessed from unrelated pixels does not establish a faint. |
| A witnessed player appearance change arms switch-timer sampling. | Capture the bench-card shading region for the bounded assignment. | The current switch-timer sampler does not itself recognize cooldown seconds or certify that the switch was voluntary. |
| QTE icon or opponent-hand crops are sampled. | Preserve their raw crop evidence for review/calibration. | These raw samplers are not automatic type-glyph, hand-open, recall, send-out, or faint classifiers. |

Sources: [announcement phase witness](../../app/src/main/java/com/example/overdex/battle/observation/PersistedAnnouncementPhraseWitness.kt), [incoming-attack witness](../../app/src/main/java/com/example/overdex/battle/observation/PersistedAttackIncomingWitness.kt), [named charge inference](../../app/src/main/java/com/example/overdex/battle/inference/ChargedMoveAnnouncementInference.kt), [decision-timer gate](../../app/src/main/java/com/example/overdex/battle/observation/DecisionTimerCaptureGate.kt), [switch-timer gate](../../app/src/main/java/com/example/overdex/battle/observation/SwitchTimerCaptureGate.kt), [QTE sampling](../../app/src/main/java/com/example/overdex/battle/observation/QteTypeIconSampling.kt), [reader calibration inventory](reader-calibration-inventory.md).

## Fast Moves

| If | Then | Guard or unresolved case |
|---|---|---|
| Real impact evidence anchors a candidate use and the ownership, timing, and phase guards pass. | Derive an observed Fast Move use; identity can remain unknown. An HP tick or PRESENT border pulse maps the damaged side to the opposite attacker. | Rhythm, button fill, a configured move, or species identity alone cannot manufacture an impact. Complementary streams may describe the same use and must not be double counted. |
| An independent recipient visual effect and side-resolved Fast Move audio satisfy the matching, source-time, appearance, and phase guards. | Support one observed use even when no HP tick or border pulse was captured; merge complementary evidence describing that same use. | Audio alone is insufficient. Audio beginning after a switch cannot attach to an earlier visual effect; matching cannot cross either combatant's appearance boundary. See AUDIO-01–03 and MOVE-03 in the [rulebook](battle-rulebook.md). |
| A configured player move is compatible with the active species, a unique legal reference applies, or sufficient independent sound/cadence/effectiveness evidence resolves a move. | Record move identity with its stated basis and confidence. | Configuration/reference inference differs from independent recognition. A higher-energy tie-break is an assumption, not exact measured energy. Identity alone creates no extra use. |
| A charged camera interval, decision pause, appearance boundary, or unsupported gap overlaps evidence. | Apply the relevant exclusion/reacquisition rule and preserve the underlying measurements. | Do not fill a missing terminal attack merely to make replay or the KO sequence look complete. |

Sources: [FastMoveUseInference](../../app/src/main/java/com/example/overdex/battle/inference/FastMoveUseInference.kt), [FastMoveCadenceInference](../../app/src/main/java/com/example/overdex/battle/inference/FastMoveCadenceInference.kt), [charged-camera exclusions](../../app/src/main/java/com/example/overdex/battle/inference/ChargedHpCameraGuard.kt).

## Proposed cry/terminal fusion — not enabled

A proposed independent faint path is: **if a species-compatible cry, independently verified zero HP, and a genuine departure sequence agree on the same side, appearance, and short source-time window, then derive a faint citing all three witnesses**. It would need phase/occlusion checks and must not treat bar disappearance as verified zero. Departure needs an actual recognizer; the current scheduling hint and raw hand crops do not supply that testimony. This alternative to the ball-count path is **not implemented or enabled**.

A separate proposal is to let a cry wake an independent ball-count check. Generic cry-plus-geometry remains insufficient, and contemporaneous nonzero HP must remain contrary evidence at that moment. Neither proposed rule may infer the next hit's outcome from an older positive bar.

Concession/tie confirmation questions and user notes are also proposals, separate from the immutable observed ledger. The November 10 audit accompanied the bounded reader-wakeup repair. The November 11 one-way-rule audit records current behavior and remaining questions; it changes no runtime behavior and enables no new cry-to-faint inference.
