# Battle rulebook

Started November 11, 2026 from the trainer's if/then notes. This is the home for
battle facts, proposed inference rules, exceptions, and acceptance examples.
Keep adding notes here; no separate repository is needed. This document is a
specification, not executable configuration and not proof of implementation.

The [current reader and event audit](battle-event-rules.md) describes what the
app actually does. The [reader inventory](reader-calibration-inventory.md)
records calibration and recognition gaps. A rule becomes an implemented feature
only when its code, checks, and recorded-battle evidence are linked here.

## Terms and scope

- **Remaining/conscious** means not knocked out, whether on the field or on the
  bench. **Active** means the single Pokémon currently on the battlefield.
- These count rules concern a standard three-Pokémon team within one Match.
  A six-Pokémon tournament roster is not the three selected for this battle.
- **Bright/unfaded** Poké Balls count remaining Pokémon. Faded icons still occupy
  their badge positions; counting every circular icon would always give three.
- A **fresh valid reading** belongs to the correct side and Match, has its
  original frame time, and comes from a visible native game badge. A hidden,
  covered, missing, unreadable, or retained Overdex badge is not a zero reading.
- **Result** (win/loss/tie/unknown), **ending reason** (all knocked out,
  concession, timeout, unknown), and **capture stopped** are separate facts.
  Stopping observation does not establish that the battle ended.

Pokémon GO's [official battle guide](https://pokemongo.com/news/pokemon-go-world-championship-2023-pvp-basics)
describes three-Pokémon teams, bright Poké Balls as remaining Pokémon, and
timeout decisions using remaining Pokémon followed by HP. Retrieved November
11, 2026; the guide is dated 2023. This rulebook does not hard-code its old timer
durations or assume that one active HP bar supplies a complete timeout comparison.

## Team-count facts

These are the intended meanings of validated badge observations, not a claim
that the current pixel reader reliably produces every value.

| Rule | If the native three-slot badge reader reports… | Then… |
| --- | --- | --- |
| TEAM-01 | 3 unfaded and 0 faded Poké Balls | 3 Pokémon remain; 0 have been knocked out. |
| TEAM-02 | 2 unfaded and 1 faded Poké Balls | 2 Pokémon remain; 1 has been knocked out. |
| TEAM-03 | 1 unfaded and 2 faded Poké Balls | 1 Pokémon remains; 2 have been knocked out. |
| TEAM-04 | 0 unfaded Poké Balls | Reject the reading. Retain the source for inspection and the last valid count with its capture time. The game keeps the last ball visible. An independently confirmed final faint establishes zero survivors (END-03). |

These apply symmetrically to the player and opponent. Their inverse describes
the expected native badge: if one Pokémon remains, two icons should be faded.
An expected state is not a new pixel observation. If independently observed
facts disagree, preserve both and request a fresh check.

| Rule | Condition | Required behavior / boundary |
| --- | --- | --- |
| TEAM-05 | A newer valid count decreases within the same Match, even if recognition arrives out of order. | Record the team loss and reconcile all candidates against original capture-time appearance history. Cite the source counts. Name a faint only when ownership is uniquely supported; neither the current nor immediately previous displayed Pokémon is an automatic answer. |
| TEAM-06 | The first usable reading is already 2 or 1. | The total number knocked out is known from the badge. Earlier faint identities and exact times can remain unknown; do not fabricate earlier events. |
| TEAM-07 | A reading skips from 3 to 1. | Two team members have been knocked out in the intervening interval. Do not invent two exact faint times or assign both to the current Pokémon. |
| TEAM-08 | A later reading appears to increase the surviving count in the same ordinary Match. | Preserve the conflicting measurement, reject an automatic revival, and reacquire independent evidence. Match/attempt boundaries must prevent cross-battle mixing. |
| TEAM-09 | A count is unavailable or an old retained badge is still displayed. | Preserve the last reading with its age/availability; do not treat it as a current count, a zero, or independent corroboration. |
| TEAM-10 | An independent faint is recognized. | Reconcile the expected remaining count and request a native badge check. Never feed the expected count or faded HUD back into the raw count witness. |
| TEAM-15 | A valid independent last-Pokémon count belongs to the same ordinary Match and no contradictory later count or observation reset intervenes. | Its original source time may continue supporting a later independently qualified terminal sequence while the event-driven ball reader sleeps. Elapsed time alone does not restore knocked-out Pokémon or require a replacement count. This historical constraint is neither a new current reading nor proof that the last Pokémon remains alive; the charge, owned visual sequence and result still need their own evidence. |
| TEAM-16 | A side’s active-name crop was captured before Match Start, or a possible cry wakes a name check. | Preserve the crop, sound, and capture times. Before Match Start, take the player’s ordered team from trainer configuration or the designated Choose Your Party readers. A pre-start badge guess or possible cry cannot fill or replace a team slot or own an attack. After Match Start, a repaired badge name needs a visible native badge or independent support for that Pokémon on that side. If the countdown was missed, the first independently observed combat event can establish that the battle is underway. Black out the visible Android status bar on the copy sent to OCR. |

The November 22 paired Turtonator match provides a regression case. The Moondrop
read `VoLTE 92)` as Polteageist 20.21 seconds before Match Start. The Pixel had
Turtonator in its first configured slot. The opponent HP pulse and damage tick
at 0:02.46 remain evidence of a hit even when its original owner is rejected.

If an opponent Poké Ball check expires unanswered, a fresh reading of the native opponent species badge or its shield count can reopen the ball crop once for three seconds. The ball reader still needs two independently captured, readable strips. Repeated sightings after confirmation leave the reader asleep.

TEAM-15 follows the trainer's event-driven reader assignment and the
[player terminal coverage audit](../verification/2026-11-12-player-terminal-coverage.md).
The shared final-charge resolver and its delayed-count regression implement this
scope; it is not a general permission to treat retained presentation as evidence.

## Outcome rules and exceptions

These are proposed domain/inference requirements. The current registered faint
path uses count decreases; this document does not enable count-only results or
add missing tie/concession recognition.

| Rule | If… | Then… |
| --- | --- | --- |
| END-01 | At the resolved terminal state the opponent has 0 remaining and the player has at least 1. | This supports a player win by elimination. Check/retain decisive result evidence and keep the inferred basis distinct from observed result text. |
| END-02 | At the resolved terminal state the player has 0 remaining and the opponent has at least 1. | This supports a player loss by elimination. Apply the same evidence requirements as END-01. |
| END-03 | The last active Pokémon on a side faints. | Infer zero survivors on that side. The native team badge retains its last ball through the transition to the result. A zero-ball reading is a reader error. Preserve one as the last measured count and the faint as a separate conclusion. Resolve both sides before choosing an elimination or simultaneous-KO outcome. |
| END-03a | A native entry announcement appears after a previously confirmed count. | Recheck the ball strip on the source frame when available. Confirm a decrease on independent captures, using each capture’s own time. The incoming name and later evidence of survival can identify the outgoing Pokémon. An unchanged count also occurs on a living switch. |
| END-04 | Both final Pokémon faint in the same terminal resolution, leaving 0 on both sides. | Treat this as a possible simultaneous-KO/tie case. Do not award a win solely because the opponent reached zero. Preserve the game's displayed result separately from tie/reward interpretation. |
| END-05 | A battle ends with 1, 2, or 3 opponent survivors, while the player's state or ending reason is unknown. | Survivors alone do not establish a loss. The opponent could have conceded, or time could have expired. |
| END-06 | A battle ends with 1, 2, or 3 player survivors, including an active Pokémon with nonzero HP, while the opponent's state or ending reason is unknown. | Survivors alone do not establish a win. The player could have conceded, or time could have expired. |
| END-07 | A concession is independently established and attributed. | The conceding side loses even if it still has conscious Pokémon. Distinguish directly observed exit/confirmation, inference, and trainer testimony. |
| END-08 | A timeout is independently established. | Use both teams' terminal survivor counts and, where required, sufficient HP evidence under the verified timeout rule. A single active bar or stale badge is insufficient. Await the game result if evidence is incomplete. |
| END-09 | A black transition or capture gap interrupts play. | Open an appropriate bounded result check when supported by battle context. The transition alone establishes neither concession nor outcome. |
| END-10 | Result or ending reason remains ambiguous. | Keep unknown fields explicit. A trainer can mark a win or loss by concession in Replay. Keep that entry attributed to the trainer and preserve the original result and battle observations. |
| END-MEDAL-01 — proposed | After this battle, a medal or progress award independently verified to require winning this battle appears while its result is unresolved. | Use its first verified capture as fallback evidence for a win and a single Match End. A later YOU WIN can corroborate that result. Before enabling, compare the specific award against loss, concession and unrelated progress screens. Its time establishes when the result was visible; attack and KO credit still require battle evidence. |
| END-ROCKET-01 | After battle entry and a gap in ordinary battlefield HP, the Rocket reward card shows both **SUMMARY** and **Grunt Defeated** in one capture. | Record an observed win at the capture time. Preserve the summary crop and raw OCR. Grunt dialogue, a generic reward card, and a black transition remain insufficient. This rule establishes the result only. |
| RECORD-01 | The same Match contains positive mode evidence at entry or a verified Rocket result label. | Count its observed result under that battle mode. Ranked matchmaking status and a rank badge must agree on one versus frame. A Great League party screen can be shared with training and direct challenges. Missing or conflicting mode evidence leaves the record unclassified and outside ranked totals. |

**Completed Replay, player concession.** A recent player surrender prompt, a verified live player HP reading at the prompt, two consecutive dark exit frames, and GOOD EFFORT confirming a recorded loss within ten seconds support an inferred loss by concession. A canceled prompt, resumed combat, or an observed connection interruption breaks the sequence. Place the reason at the recorded loss. Preserve earlier attacks and their original damage ticks. The concession supplies no faint or finishing-hit credit.

The result reader may pass over “Surrender and exit?” when assessing how promptly it reads the outcome. A concession with a known side still needs independent support for who exited. A prompt that was read retains its original timestamp.

### Opponent concession review — November 20

An independently confirmed concession and a confirmed final Incinerate KO both
went through a black transition before **YOU WIN**. The frames just before the
transition differ: Galvantula remained visibly alive in the concession, while
Vaporeon contracted and disappeared in the KO. The last archived ball count can
remain one in either case. An obscured or stale HP bar, an interruption, or a
missing final attack does not settle the ending reason.

Proposed witness scope under END-07 and END-09: preserve a bounded, source-timed
view of the last battlefield and the transition, including the opponent's
position, whole HP bar and team badge when visible. Let the ending resolver
use this with the existing result witness and independent KO evidence. A
visible live opponent through the exit supports a live withdrawal; an attributed
concession requires stronger cause evidence or separately attributed trainer
confirmation. Keep timeout, connection loss and uncertain departures distinct.
This is a capture and evaluation proposal, pending held-out validation.

### Result-label observations and supporting motion — November 11

The trainer noted that `W` in YOU WIN and `FF` in GOOD EFFORT can distinguish
the two expected labels even when much of the lettering is missing. This is a
result-screen interpretation rule, not permission to recognize those letters
inside arbitrary battle text. The source's position, lettering size, complete
remaining text, and independent frames matter.

| Rule | If… | Then… |
| --- | --- | --- |
| END-11 | A preserved result crop contains a unique strong partial of YOU WIN or GOOD EFFORT, with large central lettering and sufficient matching characters. | Interpret the expected label with its measured geometry and explicit partial-label basis; retain and cite the original OCR. The implemented examples include `OOD EFFOR` and `oU WINy`. |
| END-12 | Only a weak `W`/`WIN` or `FF`/`EFF` signature remains, at result scale in its expected title position. | Require two distinct source frames separated by 100 ms through 3 s, supporting the same outcome. RAW and contrast treatments of one frame are one observation. Ordinary WATER PULSE or SUPER EFFECTIVE text does not become a result because it contains W or FF. |
| END-13 — proposed | A trainer's victory jump or loss head-drop is recognized as a temporal sequence after a battle. | Treat it as a proposed independent supporting result witness. It needs a calibrated trainer region, labeled real positive and negative sequences, and bounded after-battle attention. A single still pose, generic vertical motion, or an animation name is insufficient. No such motion classifier is implemented in this slice. |

The motion proposals supplement the two expected text labels; they do not
establish elimination, concession, or timeout by themselves. See the exact
implemented text thresholds and boundaries in
[battle-event-rules.md](battle-event-rules.md#partial-result-label-recognition--november-11).

## Evidence and presentation must agree without feeding each other

The native game badge is an input. A count measurement is testimony about that
input. Faint reconciliation and match-result reasoning consume testimony. The
HUD, retained badge, and Replay display the resulting understanding.

An old badge image cannot reset a newer accepted count, clear a confirmed faint,
or serve as a new count reading. Conversely, the need to keep the overlay out of
capture must not make native badge acquisition permanently unavailable. Track
that as a capture-availability problem, not a newly observed survivor count.

## Implementation and acceptance status

| Area | Status on November 11, 2026 |
| --- | --- |
| Raw player/opponent Poké Ball readers | Present; live reliability and missing sampling remain under review. |
| Decrease-based faint inference | Source-time history is rebuilt after delayed observations. Count increases do not create losses; ambiguous owners remain unresolved, and later independent survival evidence can revise attribution. Named faints require a uniquely supported candidate. See [Step 1 verification](../verification/2026-11-11-faint-attribution.md). This does not prove every skipped multi-loss count is fully resolved. |
| Receipt-based resource wake-up windows | Implemented and unit-tested November 10; see [verification](../verification/2026-11-10-reader-wake-up-windows.md). The three November 11 Moondrop recordings used the preceding installed build. |
| Retained badge self-observation protection | Present. A displayed retained region is excluded from raw capture. A prolonged exclusion is a possible starvation path, not proof of numeric count overwriting. |
| Partial result-label interpretation | Implemented with raw OCR/source times retained and separately cited typed acceptance. Weak signatures require independent-frame confirmation. Installed on the Moondrop November 11 with saved records/calibration verified unchanged; live-battle validation is pending. See [verification](../verification/2026-11-11-partial-result-labels.md). |
| Victory-jump / loss-head-drop result support | Proposal only; no calibrated motion recognizer or live acceptance evidence. |
| Inactive-card HP | Older reader has demonstrated false positives; not a validated capability. |
| Switch-lockout recognition | Missing; raw shading crops or the central decision timer do not substitute for a lockout recognizer. |
| Forced-replacement text witness | Implemented with an adjustable crop, bounded attention, preserved OCR and player-side follow-up checks. Real-screenshot emulator checks passed; installed on Moondrop, with fresh live acceptance pending. See [Step 2 verification](../verification/2026-11-11-finishing-observation-windows.md). The prompt itself does not assign a faint. |
| Aggregate-count, elimination, concession and timeout requirements above | The count-loss attribution portion has the Step 1 implementation described above. Automatic elimination/concession/tie/timeout conclusions remain separate specifications where no linked implementation establishes them. This document itself changes no app behavior or archived records. |

The [November 11 Moondrop review](../verification/2026-11-11-moondrop-team-count-review.md)
documents the three new recordings, the missing intermediate two-ball crops,
incomplete 3 → 1 faint accounting, and result-crop/OCR failures. It does not
confirm the retained-badge feedback hypothesis.

Acceptance examples for future changes:

1. Native counts 3 → 2 → 1 establish two team losses. A subsequent zero reading adds no loss. An independently confirmed final faint establishes the third loss exactly once while the last measured badge count stays at one.
2. A saved three-ball overlay never resurrects a Pokémon after a valid count of 2.
3. A badge hidden during charge resolution can be reacquired afterward without
   requiring the missing faint to have already been confirmed.
4. An initial count of 1 establishes two prior KOs without invented identities or times.
5. A 3 → 1 gap preserves two KOs with unresolved timing/ownership where needed.
6. A blank/obscured badge is unavailable, while three visible faded icons can
   support a literal zero.
7. Either side can concede while all three Pokémon remain; survivor-only rules
   do not override the actual result.
8. Timeout and simultaneous final KOs cannot be forced into an elimination win
   from one side's stale or incomplete evidence.
9. A fresh battle gets its own baseline; prior results/counts cannot leak into it.

## Original trainer notes — November 11, 2026

Preserved verbatim below. The normalized rules above clarify “active” versus
“remaining,” unfaded versus all icons, and exceptions to the outcome proposals.
Items 4 and 5 describe an early expectation about the final ball. TEAM-04 and END-03 give the corrected rule for the native badge.

1. “if 3 pokeballs are in a Team Info badge, then all three pokemon on that team are still conscious.”
2. “if 2 pokeballs are in a Team Info badge, then one pokemon on that team has been KO'd.”
3. “if 1 pokeball is in a Team Info badge, then two pokemon on that team has been KO'd.”
4. “if 0 pokeballs are in a Team Info badge, then all three pokemon on that team have been KO'd.”
5. “if 1 pokemon remains on the opponent team, and it faints, then all of the pokeballs must be faded and the user wins.”
6. “if 1 active pokemon remains on the opponent team, then two of the pokeballs must be faded.”
7. “if 2 active pokemon remain on the opponent team, then one of  the pokeballs must be faded.”
8. “if 3 active pokemon remain on the opponent team, then none of the pokeballs can be faded.”
9. “if 3 active pokemon remain on the opponent team at match end, then the result is a loss.”
10. “if 2 active pokemon remain on the opponent team at match end, then the result is a loss.”
11. “if 1 active pokemon remains on the opponent team at match end and player has no active pokemon left, then the result is a loss.”
12. “if 3 opponent pokemon have been KO'd, then the result of the match is a win.”
13. “if 3 player pokemon are still active at match end, then the result is a win.”
14. “if 2 player pokemon are still active at match end, then the result is a win.”
15. “if 1 player pokemon is still active at match end AND has a non-zero amount of HP, then the result is a win.”

The trainer also reported new Moondrop matches with no observed Overdex result
and only one remembered faded Pokémon sprite, and proposed that a retained Team
Info Badge might overwrite counts or create a feedback loop. Keep this report
distinct from the archive/code findings; an investigation must test the hypothesis.

Additional trainer observations, summarized November 11: result labels can remain
distinguishable through their W/FF signatures; a victory jump and loss head-drop
may provide independent motion support. These additions do not alter the 15
original statements above. Their implemented/proposed boundaries are recorded in
END-11 through END-13.

## Audio and visual timing — November 11, 2026

Original trainer rule, preserved verbatim:

> audio that starts after a switch must not attach to an earlier visual effect

Apply this to Fast Move sound/recipient-effect pairing in both live inference
and Replay. Compare the effect's original captured time, the switch's source
time, and the sound's reconstructed onset. The onset is reconstructed from the
audio capture time and the accepted reference's matching/reference offsets;
recognition completion and delivery order do not say when the sound began.

| Rule | If… | Then… |
| --- | --- | --- |
| AUDIO-01 | A recipient visual effect occurs before a source-supported switch on either side, and the matched sound begins after that switch. | Reject that audio/visual pairing. Neither the attacker's nor the recipient's appearance boundary may be crossed to attach new audio to an earlier effect. Preserve the original evidence and times without creating a Fast Move from the rejected pair. |
| AUDIO-02 | Audio recognition finishes or is delivered after a switch, but its reconstructed onset and the earlier effect belong to the same pre-switch exchange. | Delayed recognition alone does not reject the evidence. Within the bounded pairing allowance, check the captured/source times, both combatants' appearances, charged-move boundaries and independent side attribution. Receipt time cannot move the sound into a different exchange. |
| AUDIO-03 | A correctly matched sound began in the same exchange as a supported impact, and only its trailing sound continues after a later switch or faint. | The ringing tail alone must not erase the supported earlier hit. Keep the original onset and impact time; the tail neither counts as a new attack nor makes a genuinely post-switch onset eligible for AUDIO-01's rejected pairing. |
| AUDIO-04 | Fast Move sound and animation rapidly repeat during a reported stutter in the same exchange. | Treat repeated effects as potentially correlated presentation evidence, not one additional use per sound or flame. Compare source-time damage, energy and cadence before admitting extra uses; retain the original observations. Independent real damage can still support uses during stutter. Proposed evidence rule from the November 13 Pixel Camerupt/Sealeo report; exact stutter onset is unverified. See the [archive review](../verification/2026-11-13-pixel-cry-stutter-review.md). |
| AUDIO-05 | One side has exactly one configured/eligible Fast Move, uniquely owned across the two scopes, and a complete global audio comparison passes the existing score and margin checks. | That move may supply strict audio support; another same-side alternative is not required. A separate eligible impact is still required for a use. | Configuration alone, missing/duplicate references, weak scores, shared ownership, or stale species context do not prove an attack. Reproduced with original Pixel Sealeo evidence; see [finishing-hit verification](../verification/2026-11-13-sealeo-finishing-hit.md). |
| AUDIO-06 — completed Replay | Preserved audio onset and recipient visual effect align within the existing source-time allowance, but publication times differ by more than the live delivery budget. | Completed Replay may recover the pair using captured clocks and unchanged side, appearance, charge and deduplication guards. | This does not extend live retention, align unrelated source times, admit audio alone, or cross a switch. Publication delay cannot create another use. |
| AUDIO-07 — completed Replay candidate | Two nonoverlapping segments of a sound reference align to one source-time occurrence while another move is audible. | Keep the segments and competing sound as possible shared-time occurrences; investigate ownership and any separately supported impact. | Multiple segments or overlapping captures are not multiple moves, and a sound is not a faint. The first low-threshold experiment matched Hidden Power incorrectly; the candidate-only matcher rejects that example, but broader calibration is still required. See [mixed-audio review](../verification/2026-11-15-incinerate-mixed-audio-review.md) and [Replay implementation](../verification/2026-11-15-mixed-audio-replay.md). |
| AUDIO-08 — completed Replay candidate | Two nonoverlapping portions of a cry reference align to the same estimated onset in preserved audio and exceed the competing-reference margin. | Show a possible cry with its original clip references and estimated onset. | The clip may start after the estimated onset. Species, side and purpose are not independently confirmed by this candidate, and it cannot by itself establish a faint. |

The source-time guard also rejects pairing across an intervening confirmed
faint or charged-move boundary. A sound beginning shortly after the visual
effect remains eligible only if the bounded alignment and all other evidence
requirements pass within the same exchange. Sound alone remains insufficient.

Implementation checked November 11: shared onset/alignment policy in
[`FastMoveUseEvidence.recipientAudioSupport`](../../app/src/main/java/com/example/overdex/battle/inference/FastMoveUseEvidence.kt),
live appearance/phase guards in
[`FastMoveUseInference.reconcileRecipientAudio`](../../app/src/main/java/com/example/overdex/battle/inference/FastMoveUseInference.kt),
and Replay guards in
[`MatchReplayModel.recoverRecipientAudioUses`](../../app/src/main/java/com/example/overdex/battle/replay/MatchReplayModel.kt).
The [live pairing tests](../../app/src/test/java/com/example/overdex/battle/inference/RecipientAudioFastMoveUseTest.kt)
cover delayed/reordered delivery, either combatant's intervening switch or
faint, charged-move boundaries and a legitimate post-impact sound tail. The
[Replay pairing tests](../../app/src/test/java/com/example/overdex/battle/replay/ReplayRecipientAudioPairTest.kt)
cover the same onset boundary in normal/reversed archive order and preserve
supported attacks before a confirmed faint. These checks are included in the
[Step 2 verification](../verification/2026-11-11-finishing-observation-windows.md#validation);
they do not establish live-device recognition accuracy.

## One-way event rules — audit November 11, 2026

These rules record what an observation permits us to conclude, and what it does
**not** establish. Unless stated otherwise, evidence must belong to the same
Match, side, combatant appearance and relevant source-time interval. An IF/THEN
does not imply its reverse. Missing evidence is not evidence of the opposite.
One observation may legitimately support several independent consumers.

Status: the code and test bodies cited below were inspected in this audit. The
latest preceding build passed 2,563 unit tests, but that is not a live accuracy
score. This documentation audit did not rerun Android tests or change app code.

### Team-loss ownership and survival

| Rule | If… | Then… | Does not establish… |
| --- | --- | --- | --- |
| TEAM-11 | An unambiguous identity interval and independent positive HP establish that a loss candidate is alive strictly after a count decrease. | Exclude that candidate from the earlier loss and recompute the current attribution. | An identity label alone or same-timestamp HP is not later survival proof. Missing HP is not death. A zero-survivor count followed by positive HP remains a contradiction, not permission to assign a loss backward. |
| TEAM-12 | Team history already resolves a loss, and a non-Fast-Move cry candidate with the same species ID occurs in the preceding five-second window. | The faint tracker may cite the cry as corroboration. | The cry does not create a count loss or classify entry, recall or faint; a cry is not required for a resolved count-based faint. |
| TEAM-13 | Two confirmed source-ordered counts in the same team history decrease by more than one, such as 3 → 1. | Retain the aggregate number of team losses over that interval; resolve owners only as independent appearance/survival evidence permits. | The count gap does not supply intermediate count observations, individual KO order, or exact earlier faint times. Do not silently reduce it to one loss or arbitrarily name the current Pokémon. |
| TEAM-14 | A count interval resolves historical faint owners after a replacement has entered. | Keep each owner's loss and source clock; preserve the living replacement's supported attacks. An aggregate loss cannot supply a fresh entry vacancy or borrow a nearby cry to time every loss. | Confirming an earlier loss does not establish that the current battlefield position just became vacant. A Replay ball-decrease check can stay closed when that exact drop resolves to the outgoing Pokémon, and the cited entry announcement and badge already independently establish the incoming appearance before the drop. |

TEAM-05 and TEAM-11 are implemented in [TeamLossResolver](../../app/src/main/java/com/example/overdex/battle/inference/TeamLossResolver.kt)
and the live/replay projections. Checks include [TeamLossResolverTest](../../app/src/test/java/com/example/overdex/battle/inference/TeamLossResolverTest.kt),
[recorded-source cases](../../app/src/test/java/com/example/overdex/battle/inference/TeamLossResolverRecordedTest.kt)
and [shuffled live delivery](../../app/src/test/java/com/example/overdex/battle/observation/RecordedLiveTeamLossHistoryTest.kt).
TEAM-12 is the [faint tracker's](../../app/src/main/java/com/example/overdex/battle/observation/OpponentPokemonFaintWitness.kt)
corroboration path, covered by [OpponentFaintTrackerTest](../../app/src/test/java/com/example/overdex/battle/observation/OpponentFaintTrackerTest.kt).
Its species-ID match is not independent acoustic side/faint classification.

TEAM-13 is implemented by the aggregate owner-set resolver using the second
[19:03 recording match](../verification/2026-11-11-moondrop-1903-connection-review.md)
and [aggregate regressions](../../app/src/test/java/com/example/overdex/battle/inference/TeamLossResolverAggregateTest.kt).
TEAM-14 is covered by the [live consumer tests](../../app/src/test/java/com/example/overdex/battle/observation/AggregateLiveFaintConsumersTest.kt),
[entry-side tests](../../app/src/test/java/com/example/overdex/battle/observation/AggregateFaintEntrySideTest.kt),
and [Battle Record tests](../../app/src/test/java/com/example/overdex/battle/history/BattleRecordAggregateFaintTest.kt).
See the [implementation validation](../verification/2026-11-11-aggregate-team-loss.md)
for the verified scope and remaining final-hit capture gap.

### Reader assignments and actual observations

| Rule | If… | Then… | Does not establish… |
| --- | --- | --- | --- |
| OBS-01 | An ordinary HP bar becomes unavailable and the fresh, unobscured, noncharged departure guards pass while the other ordinary bar remains visible. | Wake one bounded ball-count check for the affected side. | Missing/covered/relocated pixels are not a measured zero, recall or faint. An immediate post-charge disappearance without an ordinary baseline cannot use this particular hint. |
| OBS-02 | A resource or inactive-card reader supplies a usable fresh reading for its current assignment. | Retire that crop's assignment independently; otherwise permit attempts until its fixed deadline. | A saved crop alone is not recognition. An old queued reading cannot complete a newer assignment. Expiry is not zero or absence. An unreliable underlying reader can still falsely claim a usable reading. |
| OBS-03 | The dedicated reader accepts a replacement prompt from its bounded assignment. | Preserve the prompt/OCR and wake player resources, inactive cards and decision-timer reading. | The prompt does not name a fainted Pokémon, decrement balls, or wake opponent resources. Fragments such as “switch” are insufficient. |
| OBS-04 | The current decision-timer assignment first sees its own numeric timer and then its disappearance. | Complete that timer assignment early. | A blank initial crop or a prior assignment's disappearance cannot complete it. The timer alone does not identify shield choice versus replacement or prove a faint. |
| OBS-05 | Match End closes stat-sound attention while a finite probe was already requested in the same observation generation. | Stop new work but allow that existing probe to finish. Explicit stop, ignore or restart invalidates queued probes. | “Match ended” does not mean erase every unfinished witness; the exception does not permit new post-result probes or evidence crossing into the next attempt. |
| OBS-06 | A resource assignment obtains its first usable candidate image, captured within that assignment. | Permit one three-second confirmation tail, capped at six seconds from opening; retire the assignment after a usable confirmed count. First GO or first side identity can retry an expired, still-missing baseline once. | Scheduling feedback is not a count, switch, recall or faint. Old/future images and repeated candidates cannot extend the deadline. This resource-specific tail does not extend every reader's assignment. |
| OBS-07 | Two source-ordered independent resource images agree within six seconds, with native badge visibility and pixel guards satisfied. | Publish the stable count and both image references; a changed count retains the first capture time. | A duplicate image observation at the same capture time, distant matching images, missing badge, or single candidate cannot establish a loss. Video-derived test evidence is not automatically original archive testimony. |
| OBS-08 | A delayed ENTRY cue is strictly older than an already accepted native species-badge image from the same side and observation attempt. | Reuse that image to cover the entry's species-reading assignment, cite cue and crop, and leave other pending assignments intact. | Recognition completion time, implicit clocks, configured rosters, uncertain reads and the other side's badge cannot supply coverage. Equal/newer entry cues and faint, cry, switch and charge assignments retain their checks. This is scheduling, not a new identity or faint. |
| OBS-09 — trainer proposal, unvalidated | Frame-processing backlog approaches a measured overload threshold. | Test temporarily reducing new optional recognition work to protect HP capture and other evidence needed for the active exchange; recover normal rates when pressure subsides. | Do not discard already accepted observations or disable essential identity, switch, charged-move, audio, terminal or result evidence indiscriminately. Queue pressure is not evidence of a battle event, and reducing unrelated work cannot guarantee throughput if the HP worker itself remains too slow. |

OBS-08 is implemented in the [species coordinator](../../app/src/main/java/com/example/overdex/battle/observation/SpeciesCheckCoordinator.kt)
and covered by [recorded delayed-entry regression](../../app/src/test/java/com/example/overdex/battle/observation/RecordedDelayedEntrySpeciesTest.kt).
It skips only requests delivered after badge acceptance; cancelling already-admitted
work in the opposite delivery order remains outside this change.

Sources/checks: [departure policy](../../app/src/main/java/com/example/overdex/battle/observation/HpBarDepartureCheckPolicy.kt)
and [its negatives](../../app/src/test/java/com/example/overdex/battle/observation/HpBarDepartureCheckPolicyTest.kt);
[resource/bench gates](../../app/src/main/java/com/example/overdex/battle/observation/BattleCaptureGates.kt),
[resource tests](../../app/src/test/java/com/example/overdex/battle/observation/OpponentResourceSnapshotGateTest.kt)
and [bench tests](../../app/src/test/java/com/example/overdex/battle/observation/InactiveBenchSnapshotGateTest.kt);
[replacement reader](../../app/src/main/java/com/example/overdex/battle/observation/PersistedForcedReplacementWitness.kt)
and [routing tests](../../app/src/test/java/com/example/overdex/battle/observation/ForcedReplacementAttentionRoutingTest.kt);
[timer gate](../../app/src/main/java/com/example/overdex/battle/observation/DecisionTimerCaptureGate.kt)
and [timer tests](../../app/src/test/java/com/example/overdex/battle/observation/DecisionTimerCaptureGateTest.kt);
[stat gate](../../app/src/main/java/com/example/overdex/battle/audio/StatSoundListeningGate.kt)
and [stat-gate tests](../../app/src/test/java/com/example/overdex/battle/audio/StatSoundListeningGateTest.kt).
OBS-06/OBS-07 were implemented and covered by 61 focused tests on November 11;
see [resource confirmation verification](../verification/2026-11-11-resource-count-confirmation.md),
[two-frame confirmer](../../app/src/main/java/com/example/overdex/battle/observation/BattleResourceCountConfirmation.kt)
and [recorded count-to-faint test](../../app/src/test/java/com/example/overdex/battle/observation/RecordedResourceCountFaintTest.kt).
Future live acquisition remains unverified by these offline tests.

Do not assign every reader the same deadline clock. Resource/bench acquisition
gets an initial three seconds from receipt so delayed recognition can still
request fresh pixels. Only a usable resource candidate can earn OBS-06's single
bounded confirmation tail; cue identity/order remains source-timed. Replacement-prompt and decision
timer assignments have source-time hard deadlines. None of these scheduling
choices changes when the underlying battle event happened.

### Trainer-proposed Replay faint-time estimate

When a faint is resolved but its frame is missing, the Replay KO marker shows the interval
from the last supported living HP for that appearance (or its preceding verified team count)
to the first evidence establishing the loss. Displayed bounds round outward. With only an
upper bound, show “KO by”. A measured zero-HP frame can supply the upper bound.
A confirmed faint cry and a trainer's timestamp each get their own label. The animation's seek point supplies neither bound nor KO credit.

The trainer proposes placing a clearly labeled estimated Replay faint near the
end of the last supported move or charge sequence, even when a faint itself is
only a plausible hypothesis. Keep that hypothesis separate from an independently
established faint whose exact time is missing. A guessed faint or time, an inferred faint and a
directly witnessed faint should be visually distinguishable, for example by an
indicator on the CRT. Preserve the original event clocks and evidence; an
estimated time must never become a witnessed timestamp, a confirmed loss, or
establish the move's KO credit by itself. Fast Move start versus impact, queued damage, QTE/shield
delays, switches, competing attacks and missing observations can move the true
faint away from a simple move-start-plus-duration formula. This is a proposal,
not an implemented or validated attribution or Replay rule.

### Unnamed opponent faints in completed Replay

A verified single-step native opponent ball-count drop can record an unnamed
loss in Replay when no independently named faint already accounts for that
interval. The count brackets the loss; it does not time the hit or select an
outgoing species. Do not split a skipped count into invented event times.
For the independently verified last opponent, nearby ordinary red and
verified empty readings of the same HP bar can also establish an unnamed
faint. A switch boundary, later positive HP, contradictory result or unresolved
bar must not be treated as verified depletion. A win alone proves no faint.
These markers do not change the archived Timeline, active species, or KO credit.

### Trainer convention: last witnessed attack credit

Once the final faint is independently established, Replay should credit the
last witnessed opposing attack against that active recipient when stronger
finishing evidence is absent. The attacker must already be identified.
Ignore decorative attacks, outright blocks, and attacks on an earlier
recipient. If verified living HP from the named recipient appears after a
candidate's reception and before its faint, exclude that occurrence as
nonlethal. HP seen before reception can still be removed by that attack.
If the opening width cannot verify the later bar, a native tick and co-timed
positive fill, corroborated by the same fill and compatible geometry on two
later frames of that appearance, can establish survival for this fallback.
Label the fallback as last-witnessed credit. It does not establish
a missing faint, the attack's finishing impact time, or measured zero HP.
A trainer can separately record a witnessed finish that the archive missed.

### Save the opposing Pokémon at a confirmed final cry

If a confirmed final departure cry establishes a faint and the source-time
active appearance identifies the opposing Pokémon, save that Pokémon's KO
credit against the existing faint before Replay opens. Use a supported move
when the finishing occurrence is established. If the move is unresolved,
store the owner and the cry reference with the move marked unknown. Replay
can display that owner without creating an attack animation or a damage time.
Later move evidence can revise the same credit without adding a second faint.
A result label or a concession alone does not establish a KO. For an
independently established player concession, show the active player Pokémon
at the surrender prompt when its identity is known.

### Missing final Fast Move tick in completed Replay

If the active last opponent has a distinct departure cry, compare its last surviving whole bar and the subsequent recipient response with the side and duration of the attacker’s supported Fast Moves. In Moondrop archive `9489a918`, Golbat's last linked Wing Attack tick at GO+83.339 s left Vaporeon alive. The next expected landing at +84.339 s overlaps a changed whole bar at +84.289 s. Vaporeon's separate cry occurs at +85.255 s. Replay can credit that next Wing Attack as inferred when the previous linked hits dealt enough damage to finish the remaining HP. One unresolved bar column remains, and the next beat is a prediction. Preserve those facts when displaying the inferred landing.

Use the retained VS opponent label to identify the battle mode when available. In Team Leader and Rocket encounters a shield choice can shorten the opponent's charged sequence. Across modes, a missing announcement or an interval shorter than 9.5 s cannot exclude an already-started charge. Compare actual preparation, shield, announcement and reception times. A living return, a later surviving column, competing damage, or a capture gap covering a plausible alternative can leave the finishing occurrence open. For a missing tick, exclude a Fast Move when the interval from the last supported attack to the faint exceeds twice the recorded move duration. Equality remains eligible. Inferred beats cannot reset that clock. A late ball-count reading alone gives an upper bound on the faint time. Preserve the changed whole-bar frame and the distinct cry as separate sources, and leave the measured reception time unknown.

### Pixels, moves and energy

| Rule | If… | Then… | Does not establish… |
| --- | --- | --- | --- |
| VIS-01 | Charged-phase context and two qualifying normal two-bar frames establish the camera's return. | Open one fixed three-second watch of new recipient pixels, even if a later HP outline is missing. | An old anchor supplies a sampling location, not a fresh HP value. One return frame is insufficient; repeated cues cannot extend the window, and appearance/size/lifecycle changes invalidate it. |
| VIS-02 | A bounded assignment preserves actual terminal-region frames. | Retain the images and a neutral sequence summary with their capture times. | This is not automatic shrink, disappearance, recall, send-out or faint recognition. Failed/obscured samples are not absent-sprite observations; living recalls, camera scaling and Shadow aura remain alternatives. |
| VIS-03 — final charged-KO path required | After a witnessed charged hit, the HP bar disappears as the recipient shrinks, with the opponent trainer extending a hand. | Preserve the ordered sequence as a departure candidate for a bounded resource/result check; combine independent terminal evidence when resolving a KO. The final charged-KO recognition path must work without an empty-HP-outline observation or a final native zero-ball frame. | An orange damage trail is not a measured surviving HP amount. Shrinkage, bar disappearance or the hand alone also occur around recalls/camera changes and cannot independently establish a faint or its owner. A subsequent win by itself does not distinguish elimination from concession. |
| VIS-04 | The dedicated player-QTE reader accepts a grade from a current, source-timed crop. | It may request a bounded watch of the opponent receiving territory before delayed move-used testimony arrives. | A grade is progress, not QTE completion, impact, damage or faint. Repeated grades cannot renew the same phase budget; later opponent moves and newer appearances cannot inherit its ownership. |
| VIS-05 — classifier acceptance constraint | A visual tracker loses feature correspondence or cannot separate subject motion from camera/background movement. | Report the visual sequence as unavailable or interrupted, preserving the frames and source clocks. | Lost tracking is not an observed disappearance, zero HP, recall or faint. The offline terminal-motion prototype fails on both real KOs and living recall/camera controls. |
| VIS-06 — recorded counterexample | A shrinking Pokémon still has independently readable positive HP in its own native bar. | Preserve that positive measurement and keep a living recall among the possible explanations. A later unreadable/fading bar remains unknown. | Shrinkage or a trainer holding a ball cannot retroactively convert positive HP to zero or supply a faint clock. Positive HP before a later hit also cannot prove survival through that later hit. |
| VIS-07 — candidate-reader constraint | An image contains a spatial cluster of bright particles with cool halos. | Preserve its source time and geometry as a possible visual component of a later, owned sequence assessment. | Particles alone do not identify a faint, receiving side, or depletion. QTE, shields, entry effects, pointer rings and ordinary attacks produce competing candidates; missed particles do not prove survival. Sparse retained images do not guarantee coverage of the brief signal. |
| VIS-08 — final charged collapse plus result | A known last recipient has a source-ordered particle/flash contraction-and-clear sequence, or the separately visibility-checked player appearance sequence, after an independently attributed opposing charged use, followed by the matching result, without intervening play or conflicting evidence. A fully named move or CHARGE-06's qualified partial announcement can establish the charge. | Infer that recipient's terminal KO at the sequence clear clock; use the same revisable conclusion in live state and Replay and allow an inferred depleted HP endpoint. An empty HP outline and native zero-ball frame are not prerequisites. | QTE-before-hit, result alone, missing pixels, an unresolved owner, a switch, positive receiving HP, another charge phase or later independent survival must not become a final KO. Do not invent measured zero HP, intermediate damage, ball counts, move names or energy costs. |
| HP-ROLLBACK-01 — trainer-described orange damage display | The orange segment shows the amount of incoming damage. During fast attacks that damage applies normally. If the Pokémon showing orange is executing a charged move, check whether its target survives the impact, including by shielding. | If the target survives, the incoming hit can finish resolving. If the charged move KOs the incoming attacker and live HP returns on the charged-move user, preserve both the orange observation and the restored HP. | The orange segment alone supplies neither the application time nor KO credit. The counter-charge case has a Replay regression. Treat the broader timing description as a trainer rule pending paired recordings. |
| TEAM-LAST-01 — three-member team | The game badge shows one conscious Pokémon, and the active identity is established. | The two other slots have fainted by that badge time. A later verified living appearance can help identify which teammate survived an earlier drop. | The count establishes team state. It does not supply individual hit times or moves. Keep unnamed slots anonymous. |
| VIS-09 — recorded appearance counterexample | A previously supported foreground color contracts or disappears while bright attack pixels replace it, even with a stable background. | Treat the original appearance as obscured until independent visibility returns. Preserve the source frames and the competing explanation. | Color loss is not body shrinkage or departure. A foreground territory is not automatic player ownership during charge-camera movement. The living Quagsire Brutal Swing sequence demonstrates both limits. |
| VIS-10 — proposed charged-hit departure | A correctly sided, identified recipient is independently observed receiving a charged attack, then a different Pokémon is confirmed on that same side within the same uninterrupted battle. | Keep faint and voluntary switch as competing explanations for the departure; use independent, source-timed count, replacement, HP, shield and later-appearance evidence to resolve them. | An HP bar visible during the attack is not proof the charged attack caused a faint. No witnessed Fast Move does not prove no Fast Move occurred or was pending. A shielded attack, delayed evidence, missed transitions, identity ambiguity or replay glitches prevent attributing the departure or a KO to that charge without corroboration. |
| VIS-11 — departure burst | A source-timed sequence at the departing Pokémon’s position shows particles, a localized flash, contraction and clearance. | Cite it as supporting departure evidence alongside the team count, identity and other witnesses. A surviving recall can produce the same cue. | A single white frame or an obscured crop leaves the cue unresolved. The burst supplies neither the identity of the attack nor a measured damage time. |

VIS-05 and VIS-06 have an [offline motion and native-HP study](../verification/2026-11-11-terminal-motion-feasibility.md).
Its tested tracker is not connected to runtime faint inference. The living-recall
pixel regressions preserve positive red HP and reject a fabricated empty bar.
VIS-07 is supported by an [offline five-recording study](../verification/2026-11-12-terminal-particle-feasibility.md).
Its [Kotlin measurement primitive](../verification/2026-11-12-terminal-particle-reader.md)
has recorded-image regressions and a standalone phone cost check. Its
[live observation path](../verification/2026-11-12-terminal-particle-live-observation.md)
now records bounded, source-timed measurements independently of sparse PNGs;
particle observations alone remain insufficient. The compound VIS-08 path is now
implemented with [recorded regressions](../verification/2026-11-12-terminal-charged-ko.md);
fresh live KO acceptance remains unfinished.

VIS-09 comes from the [player appearance experiment](../verification/2026-11-12-player-appearance-contraction.md).
The [bounded Android measurement](../verification/2026-11-12-player-appearance-reader.md)
now supplies a player-side candidate to the same compound VIS-08 resolver. The
living Gourgeist switch still passes the visual test, so appearance alone must
never be published as a faint. Brightness guards cover the reviewed Quagsire
counterexample; they are not general-purpose occlusion or sprite segmentation.

The [five-recording Fast Move appearance comparison](../verification/2026-11-12-fast-terminal-appearance.md)
adds recorded counterexamples for narrower hue/brightness supports: a shield,
QTE, ordinary attacks, entry and charge-camera effects can remove a selected
color while the Pokémon remains visible. If a color patch disappears, preserve
that measurement but do not equate it with disappearance of the whole body.
The experimental variant recovers one missed final Fast Move departure but is
not connected to production inference; its false candidates remain checks for
any replacement reader.

VIS-04 is conditionally implemented as raw-image scheduling in the working
tree, with focused/full tests and the debug build passing. The Pixel 4 build is
installed and launch-verified; actual live-match coverage remains pending.
The dedicated accepted player-QTE cue may open one source-timed 8.5-second
opponent-region budget, with attempts at least 250 ms apart and at most 34.
Recognition delay uses only the remaining budget. HP preparation failure does
not disable this independent image assignment. Repeated grades, delayed Used,
and return cues cannot replenish it; an incoming phase, newer appearance or
valid dimension/lifecycle boundary retires it. Ordinary capture remains capped
at 30 attempts. This implements neither a faint reader nor damage inference,
and actual sampling rate/storage/CPU cost remains unproven. See the
[implementation and acceptance record](../verification/2026-11-11-qte-terminal-coverage.md).

A November 11 audit of six archives found eleven usable
grade-to-used source pairs: the gap ranged from 1.677 to 5.146 seconds. Native
grade recognition preceded the used-move source in ten pairs, while typed
used-move publication missed the existing three-second capture deadline in ten.
These sparse archived readings are not universal animation timing. One player
NICE followed much later by opponent Carbink's Rock Slide was excluded because
an incoming-attack phase intervened. See the [timing report](/home/sean/overdex-device-backups/2026-11-11-moondrop-2113-review/qte-terminal-capture-timing-analysis.md)
and its companion JSON for exact source clocks and archive provenance. The
experiment retains original capture deadlines and measures actual sampling gaps;
it cannot claim missing frames were observed or use HP preparation failure to
discard independent raw-image attention.

VIS-03 is a proposed automatic sequence reader, supported by manual review of
the user's November 11 21:13 Moondrop recording: Camerupt's final Rock Tomb
against Cramorant. The recording shows green fill, then an orange-only remainder,
then fading/shrinkage/departure; it is video evidence, not an original live zero-HP
measurement. Frame-by-frame review finds the native badges/HP absent at video
180.023 s, before conspicuous faint particles at 180.551 s, with no observed
native zero-ball frame. The opponent-only badge reappearing afterward is
potentially Overdex's cached badge image and cannot supply independent count or
faint evidence. This supports a bounded departure investigation, not an
implemented hand/faint classifier or a promise that count reacquisition will
recover every final KO. Existing individual capture guards are unchanged. See
the [recording review](../verification/2026-11-11-moondrop-2113-review.md) and
[native badge visibility review](../verification/2026-11-11-final-native-badge-visibility.md).

**Trainer-animation scope, November 15.** Blanche's hand and Poké Ball appear
in the saved Moondrop hand region during the reviewed Vaporeon departure. An
eligible frame-local opponent missing-bar hint now requests at most four raw
hand crops within three seconds. The hint does not prove that a Pokémon left;
the crops are not a hand-gesture, switch, or faint classification. Trainer
reports that leaders, Rocket opponents and player poses may use different
animations; that remains to be checked against separate examples. Living
recalls, entrances, camera changes, and obscured regions are required controls
before using a gesture as supporting evidence.

Trainer clarification, November 12: “we dont get to see an empty hp bar for the
final ko if it is a charge move.” This is a recognition requirement: do not make
an empty-outline frame a prerequisite for that finish, or describe the whole
problem as missed empty-frame capture. The reviewed Cramorant recording directly
supports the no-empty case; it does not establish that every device or animation
version must hide the outline. HP-06 remains a separate sufficient-evidence path
for normal-field red-to-empty observations. A terminal sequence may support an
inferred depletion endpoint when qualified; it must not invent an observed empty
frame, an exact intermediate damage trace, or an unobserved Fast Move.

| Rule | If… | Then… | Does not establish… |
| --- | --- | --- | --- |
| MOVE-01 | Configuration, references or accepted evidence identifies a Pokémon's Fast Move. | Retain its identity and basis to interpret separately supported uses. Use the configured card for the initial player move. Once measured timing corroborates it, keep that name through the appearance. A switch or faint requires checking the next appearance. If multiple cards match the species, use the identified slot. | Learning the move is not another attack or an energy gain. A legal move list is not proof of use. |
| MOVE-02 | Independent impact evidence establishes a Fast Move use with appearance and phase guards satisfied. | Preserve the occurrence even if unnamed. An eligible identity learned later for the same historical appearance can support generated energy at the original use time. | The hit does not identify its move. A higher-energy tie-break is not measured fact; the replacement's move cannot label an earlier actor's use. Generated energy is not automatically exact remaining energy. |
| MOVE-03 | Complementary HP, border and independently paired audio/visual evidence describes the same occurrence in the same appearance/phase. | Count one use and retain the separate supporting Articles, in either delivery order. | Every recognition result is not a new attack. A second distinct measured loss or a hit against a replacement must not be swallowed into the first. |
| HP-01 | A partial fill loses its classified color, but the next pixels do not visibly form depleted interior or an orange damage trail. | Leave that frame's HP unreadable. | Bright/white attack effects are not missing HP. Do not bridge the obscured portion or estimate its amount from color or move damage. |
| HP-02 | An isolated nonzero HP reading is contradicted by three distinct, nearby, stable higher readings of the same geometry and identity, without any intervening or co-timed ownership/charge boundary. | Replay may exclude the contradicted fill and its derived damage while retaining the original Articles. | One higher reading, a distant sample, a new appearance, or a single terminal zero does not satisfy this rule. Independent attack/pulse evidence in that frame is not disproved by a wrong fill. |
| HP-03 | A fresh ordinary low-HP location is independently confirmed red. | Track fresh pixels within one fixed three-second burst, allowing the small vertical movement of a shrinking Pokémon's bar. A complete visible empty outline may produce one zero. | Missing/obscured frames never mean zero or renew the burst. Appearance, screen-size and lifecycle boundaries invalidate it; the entire sampled region must be unobscured by Overdex overlays. Large charge-camera relocation requires its separate acquisition reader. |
| HP-04 | Native-resolution evidence shows a remaining colored HP sliver, even when a thumbnail looks empty. | Preserve it as nonzero evidence and retain the distinction between low HP and verified depletion. | Downsampling, an expected finishing hit, or an apparently empty thumbnail cannot establish zero HP or a faint. |
| HP-05 | A confirmed identity boundary is older than already measured normal HP on the same side, or arrives while those newer pixels are being measured. | Retain eligible post-boundary geometry for charged-HP preparation, using its original capture time and unchanged freshness/deadline limits. | Geometry at/before the boundary, another side, centered camera pixels, stale geometry, or work crossing a hard reset/resize is ineligible. A retained reference is not a charged damage measurement, zero HP or a faint; ordinary HP publication keeps its independent ownership guard. |
| HP-06 — live and completed Replay | A trusted normal-field reader measures RED then a complete EMPTY outline within the fixed terminal window, with compatible geometry, frame dimensions, and unambiguous side-located identity. | Publish or reconstruct that actor's KO at the empty frame's capture time, citing the identity, red and empty observations. One empty frame is sufficient. | A numeric zero without the EMPTY classification, vanished pixels, expected damage, a cry, or a centered charge-camera bar is insufficient. This does not create a move, ball count or match result. Switches/entries and charge transitions cannot carry an old red anchor forward. |
| HP-07 — live and completed Replay | A fresh independent badge after an empty reading re-identifies the same Pokémon, with subsequent positive HP. | Retract the current HP faint, restore eligible live identity/move tracking and opponent HUD/energy, and exclude contradicted zero values and their co-timed zero damage ticks from Replay HP. Retain raw observations and correction evidence. | A repeated name without HP is not survival proof. Positive HP with only the old name may belong to an unidentified replacement. Another actor's HP or an ambiguous identity cannot revive the fainted actor. |
| HP-08 | A live faint was inferred from verified HP, or a later count corroborates that same actor's KO. | Keep the HP inference revisable; reconcile presentation by actor and source time. Keep independent audio available, and require separate support for roster-slot retirement or assigning an unsided entry. Corroboration must not pause a replacement or count a second KO. | A faint inference is not an independent Poké Ball measurement, permanent roster disqualification, entry-side proof, or permission to stop listening after tallying five inference events. |
| HP-09 | During a source-timed, recipient-owned charged-impact window, the recipient’s ordinary HP region touches or overlaps the charged-impact region. | Read their combined territory from the same captured frame and measure one complete outline and interior; retain the recipient, phase cues and source time. | An empty portion is not zero HP. Do not add crop percentages, stitch different capture times, include the other side’s territory, bridge disjoint boxes, extend the watch, or infer a faint from missing/clipped pixels. |
| HP-10 — completed Replay | Independent verified red-to-empty proof and a later resolved count loss identify the same side, species and appearance. | Present one fall at the earlier verified HP time with both evidence chains. Keep the count's existing presentation time separate. | Another visit by the same species, ambiguous proof, or another actor/side cannot backdate the fall. Earlier HP proof creates neither an extra count decrease nor a finishing move. Covered by original Sealeo and controlled ownership/resource tests. |

**HP-13 — proposed charged-phase search coverage.** If a charged-move or shield
sequence begins, retain the relevant ordinary HP territory as well as the
expected impact territory until a complete, source-timed observation supports
relocation. In the reviewed Moondrop Vaporeon Water Pulse recording, the player’s
bar remained on the left during the shielded sequence; a centered-only crop
would clip it. This is a search requirement, not evidence of HP, a shield,
identity, an impact, or a faint. Keep both full end caps and the intervening
interior from the same frame, preserve each appearance and its timestamps, and
fall back to a wider search when ownership or geometry is uncertain. The example
does not establish a universal path, timing, or coverage across devices. See the
trainer annotations and held-out example under
`/mnt/overdex-backup/archives/overdex-device-backups/2026-11-17-hp-corridor-eval/`.

**HP-11 — proposed terminal color sequence.** If, after a source-supported Fast
or Charged Move, the receiving Pokémon's remaining HP fill changes color and
the bar fades away, preserve that sequence as candidate terminal evidence even
without an empty outline or a measured final damage tick. Retain side,
appearance, phase, geometry and capture times. Color change or fading alone
does not confirm a KO, an observed zero, damage amount or killer assignment.
Compare ordinary HP thresholds, damage trails, attack flashes, transparency and
background blending, camera movement, switches and concession. Exact colors
and the discriminating sequence require positive and negative recording checks.

**HP-11 acquisition, November 15.** A source-located complete-bar reading with a
small visible live/damage-color span may request a fixed three-second terminal
visual and particle sampling window, including when other columns are unknown.
The bar and later frame measurements remain independent source-timed evidence.
This is attention only: the visible span is not necessarily all remaining HP,
and a nonlethal hit or living recall can trigger the same acquisition. A
frame-local missing-bar hint may likewise request a window without inventing an
Article for the hint; only actual preserved crops and measurements are cited.

**Terminal capture priority, November 15.** When a fresh, complete-bar color
measurement or a frame-local missing-bar hint requests terminal attention, a
fixed three-second priority window can pause new routine charged-button label
and effectiveness captures and sparse diagnostic HP image capture. The HP,
attack and cry audio, species, shield, ball-count, announcement, result and
terminal readers remain active. Already captured OCR work may finish. Repeated
hints cannot extend the window; timeout resumes routine capture even when a
Pokémon survives or a switch remains unresolved. This is scheduling, not a
hit, faint, zero HP or result; the accepted bar testimony retains its original
source clock. Direct frame-local attention can precede Timeline publication;
only real captured crop Articles are cited by a direct visual sequence.

**Trainer clarification, November 15 — proposed interpretation:** “if there is
any green, yellow, or red to the left of the orange, then the hit is not a ko.
if there is only orange in the hp bar, we typically have a faint”. For a
complete, clearly readable bar belonging to the same recipient, preserve a
visible live-color sliver to the left of the damage trail as evidence of
surviving HP in that frame, even if it is only one pixel. A later hit or
unfinished damage animation can change the outcome, so retain subsequent
source-timed evidence. A complete orange-only bar is a terminal candidate, not
an automatic faint or a measured empty bar; compare repeated frames, departure,
count and later positive HP for the same appearance. Missing or clipped caps,
obscured pixels, uncertain colors and background blending remain unknown rather
than being filled or excluded merely to reach either conclusion. See the
[Sealeo background counterexamples](../verification/2026-11-15-sealeo-background-ambiguity.md).

**Trainer-reported shield and pending Fast Move, November 15:** If a target's
remaining HP turns orange while its Charged Move interrupts a player's Fast Move,
keep the pending hit, charge and any shield decision as separate source-timed
evidence. In the reviewed Vaporeon match the player shielded Water Pulse and
Vaporeon then departed; the trainer identified Incinerate as the finishing hit.
Do not mark the target fainted merely because the orange trail appeared before
the shield choice, or discard a supported pending hit because a charge began.
The trainer also proposes that, if the Charged Move instead defeats the attacker,
the target can retain HP and the orange trail can revert. That counterfactual
was not exercised in this recording and requires a separately observed control.
See the [recorded exchange](../verification/2026-11-15-vaporeon-shield-incinerate.md).

**Complete-bar evidence continuity, November 15:** If a readable complete bar
retains live-colored pixels while an orange trail appears or disappears, record
the changed color evidence at that frame's source time; it is not an HP amount,
confirmed hit or faint. An unreadable sampled frame interrupts a pending
orange-only persistence run without creating an empty measurement or discarding
the last usable geometry. A dropped or never-delivered frame remains a separate
capture limitation. See the [reader verification](../verification/2026-11-15-hp-reader-update.md).

**User-stated timing interpretation, pending validation:** In the Sealeo
exchange, the remaining sliver's fade and disappearance represent the final
hit's damage resolving at the end of the turn. Compare this interpretation with
source-timed move, whole-bar and disappearance frames in independently labelled
KO, nonlethal and living-switch recordings. Preserve the visual fade interval
separately from any inferred damage-resolution clock; the first dim frame or
last visible pixel is not automatically an exact turn boundary. This timing
interpretation does not itself establish the move's identity, recipient, faint,
or a measured zero, and it does not override an independently visible survivor.

November 13 offline experiment: sustained orange-only fill inside a complete
outline appeared in 12 of 13 selected KO sequences and none of 12 nonlethal
comparison windows; the remaining KO had no confidently measurable complete
outline. A near-lethal Sealeo hit briefly fooled the single-frame color test,
but not the repeated-frame test. These selected-window results are exploratory,
not a universal KO classifier. See the [complete-bar color/fade study](../verification/2026-11-13-hp-color-fade-study.md).

If both rails and both end caps are not supported in the same captured frame,
the whole-bar color/empty assessment is unknown. A surviving fragment, an
orange-only fragment and an empty fragment can all come from one nonlethal
bar. Use HP-09's shared capture territory; do not combine partial percentages.

If complete-bar terminal color persists, it may arm a terminal watch, but must
not itself timestamp a faint or suppress supported pending attacks. One reviewed
Camerupt bar remained orange-only for about 13 seconds before departure.
Retain source-timed fade/departure evidence separately; a subsequently identified
live remainder contradicts the candidate. The offline study measured the color
candidate automatically and reviewed fade/departure manually; it did not add a
production faint rule or infer a missing killer move.

November 13 follow-up, offline only: [complete-bar tracking and relative fading](../verification/2026-11-13-hp-tracking-relative-fade.md)
preserved the tiny Sealeo survivor and produced no terminal candidates in 12
nonlethal windows, two ordinary-switch windows or two screen-transition windows.
Eight controlled variants checked isolated fade, global dimming, missing bar
ends and camera translation. Relative brightness fading was not a reliable
mandatory KO gate: only one correctly owned two-frame fade survived review of
13 KO windows. This is a selected-footage result, not live acceptance.

If an independently observed species/appearance boundary occurs, clear the old
bar's pending color/fade state at the boundary's source time even when geometry
looks continuous. The geometry-only experiment carried Sealeo's state into
Vaporeon's entry; an independently annotated badge change at video 48.220044 s
prevented that late confirmation. Geometry continuity alone is not actor identity.

If a bar and the surrounding scene dim together, do not treat that as independent
local-bar departure proof. If low-light geometry continues a previously complete
bar, retain its geometry/confidence separately; missing ends still mean unknown,
not empty. Colors visible through a fading bar may belong to the background, so
they cannot alone establish surviving HP or revive an actor. Relative fading can
support a terminal sequence, but its absence cannot veto other valid KO evidence.

Trainer clarification, November 13: “sometimes there isnt a final damage tick.
sometimes the bar just fades away. however, if a charge move or fast move ko's an
opponent, the color of the HP remaining in the hp bar changes”. HP-11 records
that observation as a testable sequence, not a validated universal color rule.
The reviewed Cramorant orange-only remainder described above is a relevant
existing example. Absence of a final damage-tick Article is not itself a failed
KO test: observed damage and inferred terminal outcome have separate acceptance
conditions.

Implementation work, November 13: complete-outline color measurements have a
separate payload from HP amounts. An orange-only interior may retain an owned
geometry anchor; it cannot publish zero HP or a faint. Repeated samples use
capture time, and appearance boundaries clear pending color and geometry even
for another visit by the same species. Fading remains optional supporting
evidence, not a required condition. See the [complete-bar implementation report](../verification/2026-11-13-complete-bar-implementation.md)
for the current verification and device-trial boundary.

November 13 controlled paired-match check: expanding the Pixel charged-impact
territory recovered eight complete recipient-bar frames for the final Shadow
Ball where the previous territory recovered none. The production color predicate
still produced no sustained candidate in the selected lethal windows. If uncertain
pixels occur in the unfilled portion of an otherwise complete bar, retain their
location separately from the remaining fill's color: blue scenery and underlying
artwork in that portion triggered the current global uncertainty veto. This is a
diagnosed classification limitation and a proposed next refinement, not permission
to ignore tiny live fill, pointer trails, uncertain fill pixels, or missing caps.
The [expanded-territory reader comparison](../verification/2026-11-13-expanded-hp-reader-comparison.md)
records lethal, nonlethal, switch, and recipient-camera controls. These results do
not validate a color-only faint rule.

**Candidate preprocessing checks, not validated recognition rules:** if a color
mask is applied, retain its original source frame and the complete-bar geometry;
masked pixels are not observed empty HP. Multiple filters of one frame are
correlated measurements, not independent witnesses. Compare local outline
contrast with surrounding scenery before calling a brightness change a local
fade. Motion relative to the background may support a departure hypothesis,
but camera cuts, attack effects, scene motion and ordinary switches must remain
negative controls. Do not require a particular filter or motion result for a KO.

November 14 implementation: after complete-outline acceptance, dim non-HP-hue
uncertainty strictly beyond a coherent orange remainder may be excluded from
the color test. A recognized live pixel anywhere prevents masking; dim possible
HP, bright effects, and uncertainty before/inside the fill remain protected.
Preserve the original pixels and publish masked/unresolved column counts.
The mask cannot create zero HP, repair a clipped bar, or confirm a faint.
The [background-mask comparison](../verification/2026-11-14-hp-background-mask.md)
found eligible background exclusions but no newly sustained lethal-color
sequence in the controlled paired match. Remaining edge colors cannot be
discarded merely because the labeled outcome is a KO; tiny surviving HP is
still a competing explanation.

HP-09 records the trainer’s November 12 request to let the normal and charged-impact HP crops share their image when a bar crosses the boundary. The live charged reader shares capture-space territory, not historical screenshots. Complete-outline requirements remain in force. See [handoff verification](../verification/2026-11-12-shared-hp-territory.md); live device acceptance remains pending.

HP-06/07 use the same source-time resolver for live publication and completed
Replay. HP-08 guards consumers that cannot retract permanent roster/audio state.
See the original [Replay verification](../verification/2026-11-11-verified-hp-replay-faints.md)
and the [live integration record](../verification/2026-11-12-live-verified-hp-faints.md).
The retained Tinkaton pixel fixture supplies a positive case; the living Carbink
recall supplies a negative case. Reprocessing video pixels in a test does not
add missing HP measurements to an existing archive.
| MOVE-04 | A supported Fast Move impact lacks a resolved reference duration. | Replay may show a neutral decorative flight ending at its source impact, clipped to the current appearance and ordinary-play window; an impact exactly at entry can be an impact-only marker. | The decoration cannot identify the move, assert its cadence, generate energy/extra uses, shift source HP time, or borrow flight time from a previous appearance or charge sequence. |
| MOVE — repeated charged banner in completed Replay | A fresh announcement crop names one active performer. Its OCR retains the move up to a merged final double `l` and exclamation mark. The same appearance has an earlier independently recorded use with an exact announcement and matching energy-spend record. | Recover the unique legal spelling on the later attack and retain the later crop’s time. The earlier use corroborates only the spelling. | A competing spelling, ambiguous side, different appearance, missing fresh announcement or uncorroborated prior use leaves this repair unavailable. Pixel archive `d2895133` contains two distinct Shadow Balls. |
| MOVE-04 — Fast Move timing in Replay | A known Fast Move has a same-time recipient damage reading near the start of an appearance, and only part of its windup fits on screen. | Keep the observed impact and shorten the displayed windup. Mark the adjusted animation timing as inferred. | Within a continuous exchange a later witness can align to a nearby beat. The evidence schedule retains the observed uses. MOVE-04a supplies any additional beats for playback. |

**MOVE-04a — Continuous Fast Moves in completed Replay (presentation only).**
Once a timed use identifies a Fast Move and its duration, Replay assumes the same
attacker starts another cycle at the earliest eligible turn. Each side runs on
its own clock. The animation, optional attack sound and small **Inferred** marker
describe the same cycle. Selecting the marker identifies the last confirmed use
and the duration in turns. A saved moveset alone supplies no timing anchor.

A supported hit occupies its own beat. An off-beat use resets the rhythm.
A capture gap can contain inferred beats while both appearances remain eligible.
A switch, faint, recorded connection interruption or match end cuts the rhythm.
A charge or shield sequence pauses ordinary cycles. A late QTE grade can bound
its opening when the previously visible charge buttons have disappeared.
Keep every supported attack in that interval. When the same pair returns,
visible battlefield controls or a supported ordinary cycle can restart playback.
Independently supported attacks already in flight keep their evidence. A
returning Pokémon needs a new timing anchor.

When reliable whole-bar readings bracket a beat and rule out the previously
measured damage to that recipient at the available pixel resolution, Replay
suppresses the inferred attacks and waits for another supported use on that
side. If two predicted attacks span one resolved loss, the measured live edge,
orange trail and settled positive HP can place one inferred landing at the
first frame showing that loss. Missing frames, an unreadable bar or an
unresolvable change leave the timing open. At the end of a match, the losing
side’s last Pokémon can show a live sliver turning orange while its precise HP
remains unknown. If the previously visible battlefield controls then disappear
and stay absent through the recorded result, the first corroborated loss of
controls can close the speculative rhythm. Later evidence of ordinary combat
invalidates that estimate. The uncertainty window supplies no faint or KO time.
The Replay HP border pulses when the displayed Fast Move lands. With two
qualified positive whole-bar readings around multiple supported attacks of
the same move and a single aggregate loss, Replay can display that loss in
equal steps at the landings. This estimate requires the same uninterrupted
pair and gives way to individually measured damage ticks. A rhythm-only
attack supplies no HP loss, energy or faint credit. The archive retains
its original clocks.

**MOVE-04b — Energy balance in completed Replay.** Use the matching archived
energy gain for each admitted Fast Move. When that derivation is missing, a
confirmed move identity or a matching saved team move can supply the reference
gain at the supported impact. If accepted hits lack an energy value, widen the
estimate. A recorded spend greater than the accounted balance establishes that
some gains are missing. Show the possible remainder up to the cap less the
recorded cost, or unknown when the estimate spans the entire bar. These are
presentation estimates from the available record. Rhythm-only animations and
a higher-energy identity tie-break supply no energy gain.

**MOVE-04c — Saved announcements in a Replay evidence gap.** If the last
established attack precedes the recorded result by at least 12 seconds,
compact saves keep a bounded sample of the last 52 seconds of announcement
crops with their source clocks. In completed Replay, legible phase and entry
messages from those crops can interrupt the Fast Move rhythm. A named charge
still needs a legal move and a resolved active performer. A move name in the
HUD, an empty crop, or a result supplies no attack, damage, or faint. This
review leaves the original testimony intact. Two consecutive original dark
strips within twelve seconds of the result can stop speculative attacks if
there is no later battle activity. The screen transition supplies an animation
boundary only.

**MOVE-05 — finishing association; bounded Replay implementation November 14.** If an
existing supported move is directed at the same opposing appearance that an
independent evidence chain establishes fainted, and source-time ordering and
pending-hit evidence leave that move as the unambiguous cause, associate the
existing move with the faint and retain both evidence chains. An absent empty
outline or final damage-tick record does not by itself forbid that association.
Do not create an extra attack, guessed damage or rewritten timestamp. Mere
adjacency is not enough when a pending Fast Move, switch, shield or mirror-side
ambiguity supplies a competing explanation. The trainer's Camerupt mirror case
is [recorded for review](../verification/2026-11-14-replay-actor-and-finishing-link-report.md).
The completed-archive path now links a named, independently sided charge to one
uniquely count-resolved outgoing appearance when its replacement follows within
eight seconds of the charge source time. Unchanged native shield readings must
bracket the exchange; a missing bracket, shield conflict, competing use (including
an unscheduled canonical Fast Move), surviving receiving HP after the presentation
impact, intervening phase/reset/result, or changed actor prevents association.
The eight-second bound is a conservative candidate limit tested on this recording,
not a universal game timing rule. The existing charge impact drives the faint
pose and inferred depleted endpoint; original move/faint clocks and separate
move, faint and shield references remain available. This does not manufacture a
measured damage tick or backdate the badge count. See the
[Rock Tomb verification](../verification/2026-11-14-rock-tomb-faint-association.md).
Pixel's unresolved mirror performer, aggregate losses and the unrecorded final
Gourgeist faint remain outside this path. Live inference is unchanged. MOVE-02's
appearance ownership also forbids assigning Camerupt's use to an earlier
Turtonator appearance.

**MOVE-05 — mixed-audio additions; completed Replay, November 15.** A uniquely
owned mixed-audio candidate, independently supported recipient effect, and
compatible complete-bar color change may recover a hit without deciding whether
it was lethal. Separately, a uniquely owned mixed-audio candidate with compatible
attacker motion, an independently localized repeat cry, and a uniquely resolved
team loss may support a finishing association when the appearances and phase
remain consistent and no competing attack intervenes. A landing displayed at
the cry is an estimate for presentation, not a measured damage time. Preserve
all original clocks and testimony; missing loss evidence, a living switch,
ambiguous ownership, or a competing attack must not create a KO. See the
[recorded verification](../verification/2026-11-15-automatic-finishing-recovery.md).

**MOVE-05 — two paths to finishing-hit credit in completed Replay.**
Either path can establish one KO link. In both paths, resolve the attacker,
the specific move use, and the recipient appearance on the source clock.

| Rule | If | Then |
| --- | --- | --- |
| MOVE-05a · Watched impact | A supported, sided Fast Move cites a qualified recipient tick to zero, or a uniquely owned, completed Charged Move has a qualified recipient impact tied to that charge. Separate evidence establishes that the same recipient appearance fainted, and the impact and faint belong to the same exchange. | Credit the supported move that caused the impact. Retain the observed impact and the evidence for the faint with their own references and clocks. |
| MOVE-05b · Departure cry | A localized cry independently qualifies as the established recipient's departure faint under CRY-06, and exactly one supported, correctly sided attack could have hit that appearance before the cry within the applicable move and phase window. | Credit that attack even when the final HP tick is missing. Use the cry onset for the faint, and label any landing estimated from it as inferred. Where the source impact is unavailable, leave measured reception empty. |

A terminal-faint decision may also support a completed charge when it explicitly
cites that charge and establishes the recipient and exchange. Count and audio
inference retain their own basis. Move power, type, energy, and effectiveness
can narrow the candidates. Treat facts derived from the same move record as
related context. Apply the shared conflict checks below to either path.

The November 27 3:28 Moondrop archive `a6db17e9` verifies MOVE-05b in
`RecordedGolbat1528ReplayTest`. Golbat's second Shadow Ball announcement at
1:26.232 precedes Vaporeon's final cry at 1:27.312. The receiving HP frame is
unresolved, and verified live HP after the first Shadow Ball rules that earlier
attack out. The second attack receives the sole KO credit.

For an independently established final faint, the last supported attack on
that same appearance is the default inferred finisher when it fits the move
and battle-phase timing. Stronger evidence such as a measured impact, a
confirmed block, or a later verified living HP reading can replace or rule
out that link. Keep credit open if competing pending attacks leave the
recipient or the order of impact unresolved. A result alone leaves the
faint open.

A valid credit from a repeat banner can attach to the completed charge at its
cited opening time. Require the typed opening, the
same attacker and recipient appearances, and an uninterrupted announcement
episode. Preserve the opening time for the attack and the repeat's own
source clock and reference. November 24, 6:15 p.m. archive `110aa81e` tests
this route with both orange impact readings withheld.

Use one decision per fainted appearance for the attack marker, projectile
landing and faint marker. A later identity change does not transfer credit to
the incoming Pokémon. Merge corroboration for one occurrence, including a
typed banner that cites its named announcement. Check pending and unscheduled
uses by source time across both move kinds. Withhold credit when distinct
attacks could explain the faint, ownership conflicts, the relevant charge was
shielded, the recipient switched while alive, or qualified surviving HP is
seen at or after the proposed finishing reception. A tiny live sliver before
reception records earlier HP. A supported attack without a resolved name
can display its move kind. A result alone leaves the cause unresolved. Keep
all original testimony and times.

**TEAM-01 and MOVE-05 — one faint signal and the last possible recipient.**
Each side has one live faint witness. Its signal goes to custody once for a
resolved loss. The HP frame, cry, badge decrease and other corroboration retain
their own clocks and evidence IDs. Restarting the witness does not renew its
allowance. At the capture instant a trusted badge with three, two, one or zero
balls permits zero, one, two or three cumulative losses respectively. A fresh
departure after that instant may establish another loss. The winning side can
announce at most two faints. The losing side can announce at most three. If two
have already been announced while the outcome is open, keep a third supported
decision pending. A result alone cannot supply a victim, attack, or cause.

When the opposing side has a single possible recipient, a uniquely owned attack
and an independently resolved faint close together can establish an inferred
finishing link. Require the same recipient appearance, a completed unshielded
charge or supported Fast Move, and no conflicting attack, intervening switch or
subsequent live HP. Replay marks a confirmed faint cry at its source onset. Without a confirmed cry,
show the window from the last supported living observation to the earliest evidence
of departure. Show an upper bound alone if the lower bound is unknown. Keep attack,
reception and original faint testimony on their own clocks.

**MOVE-05 — charged impact and a tracked departure.** When a named charge changes
the last recipient's complete live bar to damage color, require a localized flash
that contracts and clears on actual frames in the same charge phase. A matching
last-ball badge and result support one faint. Use the impact frame for the
projectile and the clear frame to bound the faint. A competing supported attack
leaves the faint visible with its cause unresolved. A shield, switch, surviving
HP, or missing departure evidence blocks this route.

**MOVE-05 — Fast Move finish on either side in completed Replay.** A complete
low-HP bar followed by damage color on the same side can locate a landing.
Resolve the recipient at the source frame. When an older appearance tag
conflicts with a newer, independently read badge from before the hit, use
the verified appearance and retain both original observations.

Evaluate the faint before assigning credit. A distinctive departure cry with
last-ball evidence can support the final faint. When the ball read is missing,
require a nearly resolved damage profile, a localized flash confirmed in a
saved crop, clear follow-up samples, and a consistent result for that side.
If the saved audio includes the start of the cry, use it for the marker.
Otherwise retain the supported time window.

Compare the hit sound against both active move pools. If the attacker's move
identity is open, require another strongly matched effect from the same
appearance. Use the damage-colored frame for inferred landing timing.
Preserve the original crop, audio, and measurement clocks. A competing hit
can leave a known faint without credit. A new active appearance, later
surviving HP or a result alone cannot supply this finishing link.

**MOVE-05 — shield bracket for a count-resolved charged finish, November 18.**
If an independently count-confirmed faint follows one named player charge, a
nearby pre-QTE shield reading can bracket that exchange with an unchanged
post-replacement reading. Require the same appearances, no intervening charge
or observation boundary, and a reliable full-outline sample during impact
that cites a crop from this QTE. The pre-QTE reading may precede preparation
by up to four seconds in this path. The empty-looking outline corroborates the
exchange while the independent team loss establishes the faint. Its uncertain
interior supplies no measured reception timestamp. A surviving sliver after
the sample, a living switch, or another plausible attack withholds credit.

**MOVE-05 — retained finishing announcement.** A full frame whose calibrated
HP crop exactly matches a unique source-timed crop can restore a missing
announcement for completed Replay. A named player charge, an opponent damage
trail and a native win can support credit when the last opponent's departure
has two independent cues among a qualified cry, a localized departure burst and
a native 1-to-0 ball count. Multiple clips of the same cry corroborate one cue. Use the
first supported impact frame for the projectile and the cleared frame for the
faint. Uncertain interior pixels leave the measured damage time and zero HP
unknown. A living return, surviving HP, shield, competing use or unresolved
ownership withholds credit. File modification time only narrows the image search.

**MOVE-05 — lethal-damage feasibility, proposed November 18.** If the
recipient has a reliable exact 1 HP immediately before a confirmed damaging
hit reaches that appearance, a hit that removes at least 1 HP is lethal.
Use a qualified damage bound to exclude a candidate that could not have
finished the target. A displayed HP fraction or one visible fill column
does not establish exact HP, and base move power does not establish damage
dealt. A Poké Ball count decrease resolves a loss through the appearance
history. Credit the previous opposing hit only when it reached that
appearance and no other hit can explain the loss. This proposal does not
supply missing hit, HP, or owner evidence.

**Charge-impact attention, November 17.** A witnessed contact inside a charged
control can prepare the opponent's impact reader ahead of the QTE. A centered
gold grade pattern with recent low-HP geometry can also request a bounded scan
ahead of OCR. These cues schedule observation. The dedicated OCR still witnesses
the grade and a separate announcement identifies the completed move. A small
orange-dominant fill with one or two red columns may request continued visual
sampling while retaining every surviving red column and its live-color status.
Paired rails and caps are still required, and a faint needs independent evidence.

**MOVE-05 review proposal — November 14, not implemented.** If native evidence
has established zero shields for a side in the same Match, consider carrying
that zero forward for finishing-hit association rather than requiring a fresh
reading during every charge. Reset and conflicting evidence must invalidate that
carry-forward; this does not authorize stale positive shield counts or bypass
actor, surviving-HP or competing-move checks. The new Moondrop Earth Power →
Rapidash case is blocked by this fresh-reading requirement despite repeated zero
readings. See the [new-match review](../verification/2026-11-14-new-moondrop-finishing-review.md).

**MOVE-03 clarification — tested diagnostic, November 14.** A border pulse and
an HP step can describe one hit even when the step's confirming sample arrives
more than 450 ms after its initial lower reading. The paired Pixel recording
reproduces this duplicate presentation. However, sharing that confirmation frame
alone does not prove sameness: a separate small or fast hit can occur then.
Widening the general merge window can swallow genuine rapid attacks and finishing
hits. Zero orange pixels likewise does not disprove a valid blue border flash.
Retain the independent evidence and require additional same-occurrence support.
See the [recorded overlap study](../verification/2026-11-14-hp-confirmation-duplication.md)
for the three experiments and counterexamples. No production rule changed in this study.

**MOVE-06 — implemented Replay reconstruction, November 14.** If an entry
announcement is bracketed by independent badges showing exactly one side changing
to the announced species while the other remains unchanged, Replay may assign the
announcement to the changing side even in a mirror. The first subsequent badge
must agree within the existing eight-second lookup window. Missing, conflicting,
simultaneously changing or reset-spanning evidence does not establish this repair.
Retain the announcement and badge references; leave archived attribution intact.

**Last remaining sprite in completed Replay.** If a side is down to its last Pokémon,
a health discrepancy can reopen HP verification. When the same appearance is
confirmed on both sides of that check with one ball remaining, keep its sprite
visible through the gap. HP, attack ownership and faint credit still require
their own evidence. A fresh departure or count-loss check is a separate event.

**Nicknames in completed Replay.** A native player badge can identify a nicknamed
attacker when its text matches the actor in the completed move announcement,
a player QTE grade establishes the side, and only one of three fully configured
team members has that charged move. Keep punctuation when comparing nicknames.
A competing prompt, an intervening handoff, or conflicting identity evidence
prevents the reconstruction. Date the identity to the badge frame.

**MOVE-07 — implemented Replay reconstruction, November 14.** A legally resolved
charged-move announcement shared by both active species may inherit its side from
an unambiguous Get Ready or Attack Incoming cue within the existing ten-second
preparation window, provided neither appearance changes and no conflicting cue,
active faint, intervening ordinary use or separate completed charge interrupts it.
A delayed faint for a different outgoing species does not terminate the current
preparation. This resolves move ownership/name only; it does not prove damage,
a faint, or the finishing-move association. Pixel's missing cue in the paired
recording remains an explicit unresolved case.

**MOVE-08 — implemented Replay HP recovery, November 14.** If successive
accepted HP samples relocate substantially, change bar size incompatibly, or
change source dimensions, establish a fresh HP baseline. Do not infer one Fast
Move from the difference across that discontinuity. Preserve independent pulses,
accepted uses, source HP values, and later corroborated losses at the new position.
This is not a claim that no damage occurred while the camera moved. Ordinary
bobbing, tiny remaining HP, and an equal confirmation frame do not by themselves
suppress an independently supported attack. The paired Pixel case retains three
attacks; its first retained pulse still has delayed source timing. See the
[repair verification](../verification/2026-11-14-hp-confirmation-repair.md).

**MOVE-06 derived-evidence clarification — implemented November 14.** If the
independent badge bracket explicitly corrects an entry to OPPONENT, a recorded
PLAYER identity derived from that entry cannot reintroduce the rejected side in
Replay. Preserve both original records. Missing or ambiguous bracketing does not
justify discarding a recorded player identity. Both paired archives, reversed
ordering, a genuine player entry, and missing-proof controls cover this repair.

VIS-01: [post-charge policy](../../app/src/main/java/com/example/overdex/battle/observation/PostChargeRecipientWatchPolicy.kt)
and [tests](../../app/src/test/java/com/example/overdex/battle/observation/PostChargeRecipientWatchPolicyTest.kt).
VIS-02: [sequence capture](../../app/src/main/java/com/example/overdex/battle/observation/TerminalVisualSequenceWitness.kt),
[gate tests](../../app/src/test/java/com/example/overdex/battle/observation/TerminalVisualCaptureGateTest.kt)
and [recorded counterexamples](../verification/2026-11-11-terminal-visual-capture.md).
MOVE-01/02: [use evidence](../../app/src/main/java/com/example/overdex/battle/inference/FastMoveUseEvidence.kt),
[energy inference](../../app/src/main/java/com/example/overdex/battle/inference/FastMoveEnergyInference.kt)
and [energy tests](../../app/src/test/java/com/example/overdex/battle/inference/FastMoveEnergyInferenceTest.kt).
MOVE-03: [live pairing tests](../../app/src/test/java/com/example/overdex/battle/inference/RecipientAudioFastMoveUseTest.kt)
and [Replay pairing tests](../../app/src/test/java/com/example/overdex/battle/replay/ReplayRecipientAudioPairTest.kt)
cover both delivery orders and recipient replacement. Live capture cost/accuracy
for the new visual windows remains pending.

HP-01 is implemented in [the HP pixel reader](../../app/src/main/java/com/example/overdex/battle/observation/PersistedActiveHpBarWitness.kt)
with [recorded bright-effect regressions](../../app/src/test/java/com/example/overdex/battle/observation/RecordedObscuredHpEndpointTest.kt).
HP-02 is a Replay-only reconciliation in [ReplayHpTrack](../../app/src/main/java/com/example/overdex/battle/replay/ReplayHpTrack.kt),
checked against 294 unchanged original Articles and boundary counterexamples in
[ReplayContradictedHpTest](../../app/src/test/java/com/example/overdex/battle/replay/ReplayContradictedHpTest.kt).
MOVE-04 is presentation only in [ReplayFastMoveSchedule](../../app/src/main/java/com/example/overdex/battle/replay/ReplayFastMoveSchedule.kt).
HP-03 is the bounded [terminal watch](../../app/src/main/java/com/example/overdex/battle/observation/TerminalHpWatchPolicy.kt),
with [actual Tinkaton sequence tests](../../app/src/test/java/com/example/overdex/battle/observation/RecordedTinkatonTerminalHpTest.kt).
These checks preserve raw evidence and do not establish future live capture performance.

HP-04 has a recorded counterexample in the
[evening Moondrop review](../verification/2026-11-11-moondrop-1903-connection-review.md):
Stunfisk retains a thin red sliver and subsequently uses Mud Bomb. This is a
video-review rule and fixture-labeling constraint, not a claim that every
near-zero live frame has been classified correctly.

HP-05 is implemented in the [charged HP policy](../../app/src/main/java/com/example/overdex/battle/observation/ChargedImpactHpWatchPolicy.kt)
and its live caller. [Recorded Cramorant ordering tests](../../app/src/test/java/com/example/overdex/battle/observation/RecordedCramorantHpReferenceTest.kt)
and [ownership regressions](../../app/src/test/java/com/example/overdex/battle/observation/ChargedHpReferenceOwnershipTest.kt)
cover delayed and in-flight delivery. See [verification and acceptance limits](../verification/2026-11-11-charged-hp-reference-ownership.md).

### Preparation, completion and effects

| Rule | If… | Then… | Does not establish… |
| --- | --- | --- | --- |
| CHARGE-01 | A dedicated accepted stream preserves the shield-choice prompt. | Start the opponent’s Replay wind-up at the first shield prompt. Show “Opponent charging…” and ease the rise until a matching announcement within ten seconds. In Team Leader and Rocket practice, a shield decision can close the wait early. Use the observed prompt-to-announcement interval even when it is well under 9.5 seconds. If the announcement is unavailable, the prompt-only cue expires after an estimated 9.5 seconds or an earlier interruption. | The prompt does not prove shield expenditure, name a move, debit that move's energy or invent a completed strike. Generic shield text from an unrelated crop is insufficient. |
| CHARGE-01a — completed Replay, November 22 paired match | A later badge confirms the same continuous appearance during a charged move. | Keep the earlier preparation within that appearance. A switch or unresolved handoff bounds the estimate. | A repeated name cannot date the start of the charge. |
| CHARGE-01b — completed Replay, November 22 Pixel | A retained native frame near a recorded block contains the shield-choice prompt while the attacker is unidentified. | Show opponent preparation at the prompt’s capture time. Independently check the attacker at release. | The prompt alone establishes neither the attacker’s name nor shield use. |
| CHARGE-07 | Replay has a supported player QTE or opponent shield-choice/incoming preparation, followed by a completed charge. | Ease into the larger player / smaller opponent framing, hold through the presentation impact, then ease back over 750 ms. Framing is separate from the attack action so its return does not add an attack. | An uncompleted preparation has only its existing bounded expiry, not an invented impact. New preparation, replacement, restart and Match boundaries stop stale framing. Decision timer and disappearing controls are supporting cues, not proof of a named move, damage or shield expenditure. |
| CHARGE-02 | A dedicated announcement/QTE reader accepts a qualifying phase cue from an already-preserved crop for the current Match, with valid source clocks and provenance. | It may prepare the bounded receiving-side HP watch immediately, before raw/accepted/typed publication completes, carrying the original crop reference and capture time. Continue the unchanged canonical evidence chain. | Scheduling is not an attack, impact, damage or faint. Recognition/receipt time cannot replace the source time, unknown text cannot supply a cue, and a stopped reader or ended Match cannot be reopened. |
| CHARGE-02a | The charged-impact capture has an eligible source frame. | Preserve the calibrated announcement strip from that same frame on the charged-impact capture cadence. Keep both crop records and their original source clocks even when OCR reads the announcement late. | A preserved crop supplies pixels for recognition. It does not establish a move, damage, or a faint on its own. |
| CHARGE-03 | Early HP preparation lacks fresh pre-cue side-owned geometry, and that eligible geometry arrives before canonical delivery of the same cue. | Permit the canonical cue to retry preparation within its original source-time deadline and current appearance. A successfully opened window cannot renew on duplicate delivery or later QTE grades. | Later geometry from a replacement or after the cue cannot supply ownership. Missing/stale geometry, a newer phase, stop, resize or expiry does not authorize a longer search or an invented zero. |
| CHARGE-04 | The existing result reader preserves a complete `No Protect Shields remaining!` reading whose retained word geometry fits its official crop and excludes the capture-time overlay. | Reuse that reading for bounded player charge-camera HP preparation. Independently derive incoming-phase testimony from the unchanged raw reading and cited crop, retaining their Match and capture clocks. | It is not a result, shield expenditure, named move, impact, HP loss or faint. Other result-crop text cannot supply Get Ready or QTE grades. Newer blank/invalid outcome readings cannot consume an older valid prompt's episode. |
| CHARGE-05 | A charge completion was delivered before its earlier compatible incoming/Get Ready cue. | Keep the completed interval's witnessed closing time and tail when its beginning is resolved. Retire preceding incoming-prompt visibility at completion by source time, while preserving late historical cues and duplicate suppression. | Late delivery cannot reopen a full-length pause or hide supported post-charge Fast Moves. Do not match across an appearance boundary, conflicting/intervening phase, restart, Match end or the existing phase bound. A later new warning must remain eligible. |
| CHARGE-06 | A native announcement clearly names the independently known attacker followed by `used` and a partially readable move label; its typed Used cue has the same capture time, and the latest trusted incoming/Get Ready phase within 20 seconds agrees on direction. | Use this as attributed charged-use evidence in VIS-08 even when the exact move remains unknown. Cite the raw announcement, Used cue and phase with their original clocks; retain all the terminal sequence, last-survivor, identity, result and contradiction guards. | The correlated text and Used cue are not two independent attacks. A partial label does not identify a move, debit energy, supply measured damage, or by itself prove a KO. Missing/stale/wrong-direction phase, a conflicting performer, player intent or result alone is insufficient. |
| CHARGE-06a · completed Replay | Two announcement reads at different capture times in one charged exchange repeat the same player announcement. Its move text has at least seven normalized characters and lacks only the last letter of a single legal move equipped by the named player Pokémon. | Replay can name the existing charge at the first read and carry that name to a separately supported finishing hit. Retain both reads. | A repeated banner represents one use. A lone clipped read, an ambiguous owner or a result alone cannot establish this repair. |
| QTE-01 | The dedicated accepted QTE stream reads NICE, GREAT or EXCELLENT. | Replay may show that observed grade at its source time within the relevant QTE. | Do not invent unobserved grades, physical vibration, a named charged move or its finish. A lingering grade after completion must not reopen the QTE. |
| QTE-02 | The dedicated accepted QTE grade stream reads a complete NICE, GREAT or EXCELLENT, and normal opponent HP geometry was measured within five seconds before that image. | Prepare a bounded opponent charge-camera HP watch if Get Ready has not done so. Later grades support the existing phase without replacing its anchor/token or extending its deadline; an older delayed Get Ready cannot reset it. | The grade does not establish QTE onset, move name, impact, damage or faint. Stale/missing geometry or appearance, stop and resize boundaries cannot establish ownership. |
| QTE-03 | The player’s source-timed Get Ready opens a ten-second glyph window. If it was missed, a fresh whole-line NICE or GREAT from the player grade crop can open a three-second window measured from capture. Repeated grades cannot extend that charge. A grade captured within three seconds after a known close cannot reopen it. Two distinct frames in that window show the same complete, unobscured type glyph on a supported bubble. | Record one observed **type** with the original glyph crop and capture times. The four calibrated regions restrict the search. Close on charge completion, incoming attack, stop, or expiry. | Region, fill color, an estimated count or grade alone cannot establish type. The reading does not name a move or establish that it completed, spent energy, landed, damaged, or fainted a Pokémon. Overlapping Fire and Poison bubbles need not be individually countable. |
| QTE-04 · recorded observation and proposed counter | Within one player QTE, captured pop audio shows distinct attacks progressing through the same calibrated five-step pitch cycle. | Count separately supported attacks. Treat a confirmed wrap as supporting a run even if only a few video frames show it. When the cycle and both attacks are independently established, equal-pitch endpoints can bound at least one cycle. | One compound sound can have repeated peaks. Same pitch or greater loudness alone cannot supply another pop or a full cycle. Simultaneous identical notes, missing audio and competing sounds may leave the exact count unknown. The offline waveform counter subtracts a fitted attack before checking for another. It supports 22 ordered attacks in both copies of the Excellent Grass QTE, with four wraps. The trainer inventory suggests 23, leaving one unaccounted for by the audio. Uncollected Normal and Grass yield zero candidates. Exact live counts and collected Normal remain unverified. |
| QTE-05 · recorded hypothesis | During a player bubble sequence, well-separated battlefield landmarks enlarge together relative to the same charge’s pre-grade view. | A step in scale is a candidate for an inferred grade. One collected Gallade Leaf Blade clip stepped up at Nice, Great and Excellent. An uncollected Leaf Blade control stayed near its starting scale. | A held-out test hid the lettering and distinguished all three grades on Dragon Pulse, but measured Great on one visibly Excellent Brutal Swing frame and abstained on the sampled Excellent Poison Fang frames. Four uncollected sequences produced zero false grades across 161 scored frames. Treat zoom as corroboration. Zoom alone currently supplies no recorded grade or damage. |
| SHIELD-01 | A valid side-specific shield baseline exists and a later source-ordered count decreases. | Record the expenditure; a 2-to-0 gap represents two usages with unknown individual instants. | An initial count of 1 does not timestamp a use. Repeated counts or a noisy increase followed by the old value do not spend again. |
| STAT-01 | An accepted stat-change sound has exactly one compatible, attributed named charge in the six-second source-time window. | Attribute the observed change and target side with both sources. Retract that side/name assignment if later evidence introduces another compatible move. | A move's possible or guaranteed reference effect alone is not an observed change. Timing proximity must not choose between competing candidates. Preserve the original cue and any revision. |

These implemented paths are scoped separately: CHARGE-01/QTE-01 describe Replay
interpretation, SHIELD-01 describes Replay markers from accepted counts, and
STAT-01 describes live inference consumed by Replay. Sources/checks:
[Replay announcement cues](../../app/src/main/java/com/example/overdex/battle/replay/ReplayAnnouncementCues.kt),
[charge-focus tests](../../app/src/test/java/com/example/overdex/battle/replay/ReplayChargeFocusBoundaryTest.kt),
[QTE tests](../../app/src/test/java/com/example/overdex/battle/replay/ReplayQteProgressTest.kt),
[shield marker code](../../app/src/main/java/com/example/overdex/battle/replay/ReplayEventMarkers.kt)
and [tests](../../app/src/test/java/com/example/overdex/battle/replay/ReplayEventMarkersTest.kt),
[stat inference](../../app/src/main/java/com/example/overdex/battle/inference/StatChangeInference.kt)
and [stat tests](../../app/src/test/java/com/example/overdex/battle/inference/StatChangeInferenceTest.kt).
QTE-02 is live measurement scheduling in [ChargedImpactHpWatchPolicy](../../app/src/main/java/com/example/overdex/battle/observation/ChargedImpactHpWatchPolicy.kt),
with [delayed-cue and deadline tests](../../app/src/test/java/com/example/overdex/battle/observation/ChargedImpactQtePreparationTest.kt).
CHARGE-02/03 are early live capture scheduling in
[AcceptedChargeCapturePreparation](../../app/src/main/java/com/example/overdex/battle/observation/AcceptedChargeCapturePreparation.kt)
and the same HP policy, with
[blocked-publication evidence-chain tests](../../app/src/test/java/com/example/overdex/battle/observation/PersistedAnnouncementEarlyChargePreparationTest.kt)
and [recorded-delay/geometry-retry tests](../../app/src/test/java/com/example/overdex/battle/observation/EarlyChargeHpPreparationTest.kt).
See [early preparation verification](../verification/2026-11-11-early-charge-hp-preparation.md)
for the original 2.729-second post-recognition delay, passing test/build checks,
verified Moondrop installation and pending live acceptance. This path does not
remove queued OCR delay. The subsequent
[result-crop prompt review](../verification/2026-11-11-outcome-shield-hp-preparation.md)
establishes the second evening match's missing preparation routing and the
separately scoped CHARGE-04 reuse. It does not establish a recovered final faint.

CHARGE-06 is implemented by the shared terminal resolver and Replay source
adapter. The first 19:03 match's original `Ninetales used Weather` reading now
supports the final player Gourgeist faint when combined with separately labeled
video appearance measurements. Original archives without those measurements
remain unresolved. See the [partial-charge finish verification](../verification/2026-11-12-partial-charge-finish.md).

### Match boundaries and result support

| Rule | If… | Then… | Does not establish… |
| --- | --- | --- | --- |
| START-01 | Any registered countdown glyph (3, 2, 1, GO) is accepted for this attempt. | Start the match once at the first qualifying observation’s capture time. | A numeral establishes countdown progress. Only a GO reading witnesses GO itself. |
| START-01 — Replay countdown | Two distinct, correctly ordered numerals in the same attempt support the same one-second cadence on the capture clock. | Reconstruct the missing beats and label GO estimated. Repeated reads of one glyph add no second anchor. | Each frame establishes that the glyph was visible then. Its exact onset may be earlier. |
| START-02 | A battle event is witnessed even though the countdown was missed. | Open the match at the observed event, keeping its source. The last opponent’s independently established second cry can resolve its faint when no GO was recorded. | The first observed battle event can be later than the true opening. Pre-battle setup, reader availability and unclassified crops or audio alone do not establish a battle event. |
| START-03 | The current capture session accepts Match Start and reaches active battle while Droidball was left closed. | Open Droidball once for that Match, including the hidden-shell transit handoff when the source start is newer than that journey. | This does not start capture, create a Match, or repeatedly override a later manual collapse. Old sessions, stopped capture, Replay and newer return navigation cannot trigger it. |
| END-14 | Eligible result music and independent result testimony occur in the same Match within the source-time window. | Record agreement or disagreement. Before resolution, music may wake bounded result-text reading. | Music alone does not create or amend Match End. A conflict must not silently replace the observed result, and a result does not identify concession versus elimination. |
| END-15 | A result phrase from the current attempt has been confirmed while older battle evidence is queued. | Publish one Match End at the result crop’s source time. Continue processing accepted evidence and its derivations until the queue drains for the archive. | A delayed switch keeps its own capture time and cannot move the battle boundary or create another result. |

Sources/checks: [BattleInterpreter](../../app/src/main/java/com/example/overdex/battle/interpretation/BattleInterpreter.kt),
[countdown fallback](../../app/src/main/java/com/example/overdex/battle/inference/CountdownMatchStartInference.kt),
[Match interpretation tests](../../app/src/test/java/com/example/overdex/battle/observation/MatchInterpretationTest.kt),
[fallback tests](../../app/src/test/java/com/example/overdex/battle/inference/CountdownMatchStartInferenceTest.kt),
[result-music corroboration](../../app/src/main/java/com/example/overdex/battle/inference/ResultMusicCorroboration.kt)
and [its tests](../../app/src/test/java/com/example/overdex/battle/inference/ResultMusicCorroborationTest.kt).
START-03 implements the trainer's November 11 instruction in
[DroidballSessionPresentation](../../app/src/main/java/com/example/overdex/battle/observation/DroidballSessionPresentation.kt)
with [custody-to-session tests](../../app/src/test/java/com/example/overdex/battle/observation/DroidballMatchStartPresentationTest.kt).

### Open items found during this audit

- The requested last-Pokémon-recall trigger for result-music listening is still
  a proposal. The current [listening gate](../../app/src/main/java/com/example/overdex/battle/audio/ResultMusicListeningGate.kt)
  permits an established enabled battle and a bounded result grace; END-14
  does not claim that listening is already narrowed to a recognized recall.
- Inactive-card HP still has recorded false positives, and switch-lockout
  recognition remains missing. OBS-02's scheduling completion is not validation
  of the underlying reader's pixels.
- General body/hand recognition remains unfinished. VIS-08 implements bounded
  compound terminal inference from qualified particle/appearance measurements;
  it does not cover every Fast Move ending or establish fresh live acceptance.

## Adding the next batch

User direction, November 11: note useful if/thens encountered during ordinary
work, including events that support one conclusion but not another. This is the
ongoing collection place; no separate repository or second list is needed.

Keep new trainer wording with its date. For each meaningful relationship, record
IF, allowed THEN, what does not follow, scope/counterexamples, source links and
status. Reuse an existing ID when refining a rule instead of duplicating it.
Distinguish implemented behavior, proposed requirements, unresolved questions
and live acceptance. Do not treat every code branch as a battle fact or expand
every small discovery into a full audit. New notes do not authorize changing app
behavior or archived observations. Continue using
[battle-event-rules.md](battle-event-rules.md) for the dated current-code audit.


## Versioned charged/Fast Move timing — checked November 12, 2026

Source: [official 2026 battle-system explanation](https://pokemongo.com/en/news/pvp-updates2026), with [June 23 rollout scope](https://pokemongo.com/news/pvp-updates-competitors-cup-2026). The updated PvP system resolves charged effects before Fast Attacks completing during the charged sequence. Team Leader/Rocket parity is not established by these sources.

| Rule | If… | Allowed conclusion | Does not establish / status |
| --- | --- | --- | --- |
| TIMING-01 | Independent evidence establishes a Fast Attack already in progress across a charged sequence in a verified applicable ruleset. | Preserve its initiation separately from its eventual impact; a visible charge boundary does not by itself disprove that attack. | Do not invent a Fast Move from a later faint or assume every mode/version shares the same ordering. External timing rule is sourced; exact observation-to-impact association remains separately testable. |
| TIMING-02 | A Pokémon disappears immediately after its own charged attack is shielded, with independently supported pending opposing Fast Move evidence. | Consider a pending Fast Move KO and perform bounded side-appropriate follow-up checks, retaining original clocks and competing explanations. | The shield, charge animation or disappearance alone does not prove zero HP, identify the finishing move, or establish the damage-resolution order. Proposed recognition scheduling; not implemented by this documentation. |
| TIMING-03 — official 2026 battle update | The updated Trainer Battle rules apply to the match and an attack completes on a turn. | Pokémon GO describes damage resolving at the end of that turn, alongside energy; switches resolve before damage, and charged damage/effects precede Fast Attacks finishing during the charged sequence. Retain those as versioned game-rule context for attribution. | This does not timestamp a rendered HP transition, establish the exact ruleset in an older match or other mode, prove a candidate move was performed, or decide ambiguous captured events. Source: https://pokemongo.com/news/pvp-updates2026?hl=en . |
| TIMING-04 | An incoming charge has a saved named announcement and the same two appearances remain on screen through a complete receiving bar with “BLOCKED!” above it. | Close the shield sequence at the captured block. Subsequent distinct ordinary HP drops can support separate Fast Moves. | The shared center crop alone cannot assign a side. A living sliver before the block cannot date a later faint. |
| TIMING-05 | After a verified block, ordinary damage resumes. A known player Fast Move is audible under the opponent’s independently localized departure cry, and a later team count confirms that appearance fainted. | Replay can land the Fast Move at the cry and credit its owner when competing moves and surviving HP have been ruled out. Retain the original clip, cry and count clocks. | The clip start and the reference excerpt are not measured impact times. A living return, an unassigned side, or just the sound of the cry is insufficient. |
| TIMING-06 — live finishing credit | A named faint and its attacker’s last supported hit belong to the same exchange. For a departure right after shielding, prefer a sided, localized cry with a distinguishable attacking move in the retained audio. | Publish one credit linked to the existing faint as soon as both sources arrive, in either order. Keep the cry onset for presentation and the later confirmation and publication clocks for review. | A shield or cry alone cannot identify the attacker. A living return, unresolved sound overlap, or another plausible finishing hit leaves the credit open. Late contradictory evidence can withdraw the link. |
| TIMING-06a — completed Replay | GO was missed, but the archive independently establishes combat. | Use the battle badge boundary to limit the participant and audio search. | GO remains unknown. |
| TIMING-06b — completed Replay | An exact incoming charge announcement, zero shields, a damaged recipient identified by its native badge, and distinct entrance and departure cries agree. The complete departure waveform begins within five seconds of the charge, the recipient impact phase was sampled, and the loss follows. | Credit the unique attacker at use and show the faint at cry onset. | Impact time is inferred. A living return, later surviving HP, competing attack, shield, or concession evidence withholds this credit. |
| TIMING-07 — move selection | A completed, unshielded charge has evidence tying its impact to the faint, and no subsequent supported opposing Fast Move can account for it. | Credit the charged move. When a later received Fast Move is supported, evaluate that occurrence for the finish instead. | Missing audio, a predicted Fast Move, the team’s moveset, and the match result cannot supply a missing attack. |

Trainer observation: the November 12 11:38 Pixel recording shows Sneasel disappearing immediately after Camerupt blocks Foul Play, described as falling over from fright. Preserve that visual report separately from the trainer’s proposed damage-before-charge explanation. See [recording review](../verification/2026-11-12-pixel-1138-review.md). Future entries should record source, checked date, applicable modes/version, observable cues, permitted inference, counterexamples and implementation/test status; do not merge old mechanics guides into current rules without checking scope.

### Diagnostic replay clocks (2026-11-12)

| ID | If | Then | Limits / status |
| --- | --- | --- | --- |
| REPLAY-DIAG-01 | A diagnostic harness replays accepted archive evidence through inference that uses publication/receipt grace. | Preserve archived capture clocks **and** `recordedAt`, raw IDs, evidence references, and relative custody order; compare derived content with newly assigned UUIDs normalized. | Publication time is not custody-arrival time. These archives do not preserve exact arrival cadence. New host-clock diagnostic submissions must not be mixed into the preserved input as duplicates. Confirmed by the processing-profile harness: replacing publication clocks changed Fast Move support/fusion between repetitions. This is a harness rule, not a new battle-event inference. |

Evidence and reconstruction limitations:
[processing bottleneck measurements](../verification/2026-11-12-processing-bottleneck-measurement.md).

### Exact faint-history reuse (2026-11-12)

| ID | If | Then | Limits / status |
| --- | --- | --- | --- |
| PROCESS-FAINT-01 | Within one Match, the same resolver receives the same side and complete ordered observation values, including source clocks, conflicts, geometry and confidence where used. | Reuse its previously computed result; each consumer retains its own evidence ownership, filtering and recovery state. | Matching IDs, row counts or hashes alone are insufficient. Changed inputs, a different resolver or side must not reuse that result. Eviction only causes recomputation. This is a processing equivalence rule, not new faint evidence or permission to weaken recognition. Covered by `FaintHistoryCacheTest` and cached/uncached archive comparisons. |

### HP baseline handoff (November 12, 2026)

| ID | If | Then | Limits / status |
| --- | --- | --- | --- |
| HP-HANDOFF-01 | A normal battlefield HP sample is the first after startup/reset and is discarded instead of seeding a fresh detector baseline. | The next lower sample may become the baseline, losing the first observable damage step even though both HP measurements are archived. | Verified with Pixel 14:47 opening samples and the current startup gate. This does not justify using charged-camera samples as Fast Move evidence or inventing moves in old archives. Implemented: the first normal sample seeds a fresh baseline without emitting a tick; charged-camera samples remain excluded. Startup, camera-return and appearance-boundary regressions cover this handoff. |

Evidence: [first Turtonator hit trace](../verification/2026-11-12-pixel-1447-first-hit-trace.md).


### Phase-scoped reader delivery (November 12, 2026)

| ID | If | Then | Limits / status |
| --- | --- | --- | --- |
| READER-PHASE-01 | A pre-battle or result reader's assignment is inactive. | Its content lane should not deliver unrelated battle articles merely for that reader to reject. A separate phase controller retains the transition cues needed to rearm it. | User requirement; routing/lifecycle remedy under investigation. Existing downstream filters avoid recognition work but do not avoid SharedFlow wake-ups. Legitimate queued pre-battle evidence retains its capture-time phase; it must not authorize reading fresh battle images as team selection. |
| READER-PHASE-02 | The Choose Your Party reader receives battlefield text such as SUPER EFFECTIVE. | Investigate capture gating, stale ownership, or wrong crop delivery; never accept it as team-selection evidence. | User-provided negative case. Not proof of which failure occurred without the source crop and capture clock. |

### REPLAY-WINDOW-01 — Playback time versus retained evidence

User preference (2026-11-12): Match Replay need not play idle time before Match
Start or after the Match. Preserve pre-match team/VS evidence and late-recognized
in-match evidence for reconstruction without extending the visible playback
window to their processing/archive timestamps. Retain the finishing battle
sequence within the selected boundary; trimming playback does not authorize
removing source Articles, changing capture timestamps, or dropping listener work.
Status: requested playback behavior; current implementation coverage not audited
in this listener investigation.

### Cry sequence and KO credit — proposed rules, recorded pilot November 12

Trainer proposals: “the fast or charge move performed immediately preceding a
battle cry for reason of fainting should be attributed to the KO”; two different
opponent cries in succession should put the preceding player move under
consideration. Subsequent refinement: “match time elapsed and number of cries
in succession would probably tell us why a pokemon cried”.

[Recorded experiment](../verification/2026-11-12-cry-sequence-experiment.md):
two preserved audio/video recordings, eight visually reviewed faint moments,
and ordinary player/opponent switches. This is an offline pilot, not a live
recognition accuracy claim or implemented inference change.

| Rule | If… | Permitted conclusion | Limit / counterexample |
| --- | --- | --- | --- |
| CRY-01 — proposed | A faint is independently established and a compatible attacker/target damage event precedes the matching victim cry. | Consider that damage-producing move for KO credit, retaining source-time and appearance evidence. | Last animation/banner alone is insufficient. Pending Fast Move damage can straddle shield/charge sequences; the cry time is not necessarily the damage time. Do not invent a missing attack. |
| CRY-02 — shortcut contradicted | Two different opponent species cries occur close together. | Open a bounded transition/identity check. | 1605 recording: Swampert entry at 97.740 s and Abomasnow entry at 101.217 s are an ordinary switch, with Swampert later returning alive. A 3.477 s gap overlaps confirmed KO-pair gaps of 2.240–4.970 s. This pair does not itself prove a faint. |
| CRY-03 — implemented candidate; device trial pending | Source-ordered cry history has an established active species, then its cry repeats before a different species enters. | Treat the repeated active cry as a departure/faint candidate for corroboration. Match phase can distinguish opening entries. | In the non-mirror pilot this marked four reviewed faints and did not mark the two ordinary switches. Roster-to-side mapping came from video; missed cries, mirror sides and other cry contexts remain unvalidated. A final KO need not have a replacement cry. Elapsed match time alone does not distinguish mid-battle switches from KOs. |
| CRY-06 — complete damage color and a localized departure cry | A side’s established active Pokémon has a whole bar with orange damage, zero live columns and zero unexplained columns, followed within two seconds by its localized cry. One native ball or two independently named earlier losses establish it is the last conscious teammate. | Resolve one faint at the cry onset, even before results. The final native count can remain one. Keep the orange frame as the measured reception and evaluate the attacker separately. | A mirror cry, an entrance, later surviving HP, a contradictory result or a living replacement prevents terminal credit. For a nonfinal departure this path additionally requires a matching recent live fill and a corroborated replacement within three seconds. A block or competing use can leave the faint recorded with its attack unresolved. Covered by `RecordedDepartureCryTest`. |
| CRY-06 — last opponent, distinct entrance and departure cries | The opponent’s native badge identifies the species during its localized entrance cry and confirms it again after entry. Its team shows one remaining ball. The player roster excludes a mirror. A later, separately localized cry matches the same species. | Record one opponent faint at the second cry’s source onset. A live sliver measured before the cry describes earlier health. The faint supplies neither a damage timestamp nor an attack owner. | An overlapping sound, a replacement, verified surviving HP after the cry, or a contradictory result leaves the terminal decision unresolved. Moondrop archive `75672efc` and `RecordedDepartureCryTest` cover the Vaporeon exchange. |
| CRY-06 — participant-only battle cry comparisons | Start with up to three selected player species and up to three independently identified opponents. A trusted side-specific sighting can fill an unknown player slot. | Compare cries only against eligible team members. A mirror species uses one species reference pool and still requires side evidence. | Unknown slots stay empty. A cry candidate cannot add itself to the roster. An empty pool skips comparison and the captured audio retains its own evidence record. |
| CRY-06 — completed Replay, last opponent and an HP identity check | The native one-ball panel, sided entry, active badge and distinct departure cry establish the same final opponent across an HP-only identity check. | Keep that opponent visible through the finishing exchange. A recipient border pulse followed by a complete, settled living bar on the same appearance describes an earlier nonlethal hit. | A separate departure or conflicting identity keeps the handoff unresolved. A later independently supported attack still competes for credit. In the 7:30 replay, Golbat’s second Shadow Ball lands at Vaporeon’s departure cry; its first stays nonlethal. |
| CRY-06 — last Fast Move, either side in completed Replay | The last active recipient cries after an acoustically identified opposing Fast Move within its duration. A reliable complete bar shows only damage color or empty HP, or two comparable earlier hits establish sufficient damage for a mixed-color sliver. | A localized departure burst, the correct outcome label, two consecutive dark transition crops, or the adjudicated outcome can confirm the ending. Start the audio search at the cry and look backward on the source clock. Cite the attack, recipient, cry and confirmation separately. | A live column still represents surviving HP at that frame. Unknown pixels cannot prove an empty bar. A later surviving bar, living return, competing attack, shield or contradictory result can defeat attribution. A capture of the trainer’s hand alone does not identify a retrieval gesture. |
| CRY-06 — live last-opponent repeat cry | The listener independently identifies two separate cries of the established last opponent. A sided entry announcement or native badge corroborates entrance, subsequent badges establish the active species, one native ball remains, and the player roster excludes a mirror. | Record one faint at the second cry onset. Use the listener’s waveform acceptance and competing-sound checks. | A later living bar or replacement blocks the inference. The separate orange-impact path retains its own 0.90 threshold. In the November 24 17:08 archive the 0.828 repeat qualifies and the later named attack evidence credits only Golbat’s finishing Shadow Ball. |
| CRY-06 — live final cry and archive handoff | A confirmed last-active faint cites its localized departure cry. Saved playback audio and the established participants’ move references can support a lookback. | Run the bounded lookback on the match’s source clock. Before saving, let this review finish or reach its five-second wait limit, then drain its submitted evidence and any resulting credit. Stopping capture leaves already-saved audio eligible. | An earlier repeat-cry comparison does not settle the confirmed-faint lookback. An ambiguous sound or an unfinished review can leave the cause open. The saved 3:12 recording tests both overlapping clips with a stop during review. |
| CRY-06 — team changes before GO | For the last opponent’s cry, identify the player’s team from the latest complete pre-GO selection. All three configured slots share the selection’s capture time. A later confirmed team scan takes precedence. | Cite the active selection when excluding a mirror and retain all superseded entries in the ledger. | Keep slots from different selections separate. A more recent partial reading that puts the same species on the player’s team leaves the cry’s side unresolved. The 9:58 Moondrop archive `4a7895cf` exercises two complete selections. |
| CRY-06 · named charge before the last departure cry, completed Replay | The last active recipient has an independently resolved departure cry. A uniquely owned, completed charge is followed by a centered whole bar with visible live HP and an orange damage trail, then the cry within 2.5 seconds of the announcement. | Use the cry as the estimated projectile landing and faint time. Retain the announcement, bar and cry at their own source times. | A later surviving bar, a block, an appearance change or a competing attack can withhold credit. The mixed-color bar supplies no measured zero-HP time. If the impact reader misses the exchange, a recent damaged ordinary bar and a verified match to the saved departure audio can support the same inferred landing. November 23, 8:37 Moondrop, Golbat versus Vaporeon. |
| CRY-06 — charge audio in the live HUD | A preserved charged-effect recording follows a one-ball opponent badge. A complete participating species' cry matches in that recording at correlation ≥ 0.90 and passes the competing-species check. A full fit from 0.85 to below 0.90 also qualifies if another team species was compared and every other species scores at least 0.20 lower in that window. | Supply the original audio reference and localized onset to the usual cry and faint checks. Repeated slices and continuous capture of the same occurrence count once. | The charge clip by itself establishes neither a faint nor its cause. |
| TIMING-06 — repeated charge announcement before a terminal cry | A completed player charge has a native banner near the independently confirmed cry. A second, clean native read within one second confirms the same nickname and equipped move. The nickname is established by the native player badge across the same preparation. | Credit that particular charge when the clean read arrives. Keep the original cue, both banner captures, and the cry onset. | A shield, competing hit, changed participant, or surviving recipient after the proposed impact blocks credit. The 10:08 Moondrop fixture `cf51a2cd` includes a nonlethal first use of the same move. |
| COUNT — final opponent handoff without a send-in banner | A native two-to-one count decrease and a fresh exact badge for a different opponent within three seconds agree with ordinary living HP on the new appearance. | Resolve the outgoing appearance once when supporting evidence arrives. HP observed during the handoff can belong to the incoming opponent. | A switch at unchanged count establishes no loss. A conflicting intervening identity keeps the attribution open. |
| CRY-06 — badge during the entrance cry | A final opponent’s native badge is visible during its entrance cry. A subsequent ordinary healthy bar confirms it settled into battle before a distinct later cry. | Use the later cry’s onset for the faint when the existing one-ball, side and appearance checks pass. | A living return or later surviving HP still challenges the decision. |
| TIMING-06 — evidence from the same charged move | An energy entry from the announcement interpreter directly cites the named attack and agrees on its side, owner, move and source instant. | Count these as one occurrence when checking for competing finishing attacks. | An independent energy expenditure remains a competing attack. |
| TIMING-06 — repeated damaged move name from a known performer | Two distinct native announcement frames in one performance, the second captured by the terminal cry, have the same performer and each identifies the same single move on that active Pokémon’s saved card. The performer is established by species, card nickname, or an earlier sided named announcement with the same nickname in an uninterrupted appearance. | Keep the opening frame as the move source. The separate last-opponent cry bounds the inferred landing and faint. A distinctive initial in the move field or the shape of a damaged word can distinguish equipped slots. | A repeated OCR pass on one frame supplies no independent confirmation. A shared initial or word cannot select between both slots. Shielding, conflicting owners or attacks, and subsequent survival still challenge credit. The 12:12 Moondrop match has a nonlethal first Shadow Ball and a separately supported finishing Shadow Ball. |
| TIMING-06 — clipped first banner before a terminal cry | The first native banner drops the last letter of a doubled ending, as in “Shadow Bal!”. A different frame before the cry spells the uniquely equipped move in full, and the repeats agree on the performer. | An older matching badge can still support ownership when the announcement interpreter cites that complete frame for the same player, move and energy expenditure. Count one charge and retain both frames and the cry. | A conflicting performer or an unconfirmed clipped prefix leaves this path unresolved. The November 27 11:41 match has two separate Shadow Balls and one terminal cry. |
| TIMING-06c — nickname during a charge | Two native announcements agree on the complete equipped move and differ by one decorative nickname character. The opening announcement and localized entrance cry identify the uniquely configured, uninterrupted lead. An exact badge within the owner window can corroborate it. | The repeat can identify that charge even when the player-name reader was asleep and Get Ready was missed. Add credit to the existing faint when the repeat arrives. | A competing owner, switch or faint, shield, or supported survival after the landing still blocks this link. The 9:09 November 26 match has a nonlethal first Shadow Ball. |
| CRY-06 · overlapping announcement and terminal cry in Replay | A uniquely owned completed charge was already in progress, the confirmed last recipient cries, and the first saved announcement may still be visible just after cry onset. | Preserve the banner capture time. In the absence of a measured reception, the cry bounds the estimated landing and ends combat playback. Show the result at its own observed time. Inferring the first visible frame of text requires coverage of the preceding frames. |
| CRY-06 — trainer proposal, November 15 | A correctly identified non-mirror cry belongs to the opponent's independently established last remaining active Pokémon, and its source-timed HP is below full. | Test treating the cry as sufficient faint evidence for that active appearance, without requiring a zero HP outline or a second cry. If it is an entrance with full HP, test classifying it as entry. | A previously damaged Pokémon can return to the field below full HP, including as the final survivor after a teammate faints. Require the active appearance and whether an entrance is in progress to be independently resolved; stale HP, missed entry cues, uncertain cry identification, HP animation, shields/charged moves, and incomplete resource counts can all make purpose ambiguous. Full HP alone does not prove entry. The trainer-confirmed Vaporeon ending is a positive case, not universal validation or a runtime rule change. |
| CRY-06 — final active appearance, November 18 recording | A trusted one-ball badge establishes the last teammate. The Pokémon is already fighting in that appearance, and its own distinct cry accompanies departure after a received hit. | Count the departure as a faint. A bench switch is impossible. Keep the attack identity and KO credit as separate decisions. | Verify side and appearance. Its entrance cry, a mirror-species cry of uncertain origin, or the latest cry in an interrupted recording can still be ambiguous. In Moondrop match `78813508`, Vaporeon was active before the hit, cried at GO +80.718 s, then disappeared on video. This is a confirmed review example. |
| CRY-06 — final team loss | The independently identified last active Pokémon gives its distinct departure cry. | The cry accounts for the team's third loss even though the last badge ball never visibly disappears. Keep the cry onset and record one faint for the appearance. | Preserve the last measured ball count. A finishing attack needs its own evidence. |
| Finishing banner — full name followed by a clipped repeat | An independent native announcement crop corroborates a full, uniquely owned move name from the same charged exchange, with the last doubled “l” read as one. | Use the full banner's occurrence and source time for credit if the recipient's confirmed faint, shield, survival and competing-attack checks agree. | A repeat from another performer or another charge cannot corroborate the opening. Moondrop 10:31 supplies the positive case and an earlier nonlethal use of the same move. |
| CRY-06 — announced charged finish, Moondrop match `650b264c` | Replay identifies a complete cry from the already active, last opponent after a uniquely owned named charge. The recipient's flash contracts and clears on consecutive source samples in that charge phase, followed by a win. | Use the cry onset for the faint marker. Associate the charged move after checking attacker, recipient, shielding, competing attacks and subsequent living HP. The landing time is inferred. | A result, isolated flash, entrance cry or missing frame alone leaves the finish unresolved. Electrode supplies a count-confirmed comparison in this same archive. |
| CRY-06 — corroborated full cry in completed Replay | The already-active last opponent has a damaged, complete HP bar before a named charged hit. A full cry and two nonoverlapping portions of the same cry in a different original recording agree on its onset. | The two recordings support one departure and one cry-timed faint. The charge may receive credit after checking ownership, shields, later survival, and competing hits. | The landing is inferred. A living switch, a later living bar or another plausible attack withholds credit. A pre-charge Fast Move with its own nonlethal damage tick can be ruled out when the same recipient is still alive after charge preparation. Moondrop archive `2329d744` provides the Cradily case. |
| CRY-06 — retained charged banner during a repeat cry | The last active recipient has an independently measured damaged bar. A complete centered damage-colored bar, an exact named charged-impact banner and a side-resolved repeat cry agree on the exchange. | Completed Replay may credit that occurrence and use the cry onset for faint timing even when the archive lacks Match End. The HP response may precede the readable banner. Preserve both clocks. | Check the original pair of distinct cry occurrences, one-ball count, both appearances, and alternatives. A shield, later living HP, or a competing attack withholds credit. The faint does not add a match result. Recorded Rocket case `e4ce20ad` is Hydreigon’s Brutal Swing against Froslass. |
| CRY-06 — distinct repeat cry in completed Replay, Moondrop match `408e9802` | A whole cry distinctly matches the already active last opponent after an exact, uniquely owned charge announcement. An earlier independent clip identifies the same species near entry, the opponent has a verified damaged whole HP bar before the charge, and their shield count is zero. | The named charge can receive credit with inferred landing at the cry if a win follows and ownership, competing attacks, later survival and interruption checks agree. | A duplicated detection of the terminal cry, a living replacement, a later positive bar or a result by itself supplies no finishing attribution. Lanturn in `593b8a2d` is a surviving-HP control. |
| CRY-06 — overlapping full-cry detections in completed Replay | Whole-waveform fits for the same species and reference overlap on the source clock, with onset estimates within 10 ms of the earliest fit. | Count one occurrence and retain every contributing source. Use the earliest matched onset for timing. | Duplicate captures supply no additional cries. A separate entrance, another species or waveform, or a later distinct cry stays separate. KO credit still requires an eligible attack and independent departure support. Recorded case `457f590b` has two Dragon Pulses against the same opponent. |
| Cry marker in completed Replay | Both active species are identified at the cry onset and only one matches the sound. | Display the cry on that Pokémon’s side. A corroborating fragment can support an already resolved departure. | A mirror or missing identity leaves the side open. A known side alone does not establish a faint or credit an attack. |
| CRY-06 — player badge unavailable at the finish | The selected lead enters under the announcement nickname and remains active. Its recognized entrance cry or a separate player QTE and named move corroborates that identity. The finishing banner matches that nickname and identifies one configured move owner. | Credit the finishing occurrence once when the opponent faint and impact checks agree. Keep the opening evidence and earlier move as ownership references. | A player switch, faint, conflicting identity, ambiguous move owner, or competing impact leaves the link unresolved. |
| CRY-06 — incoming charged finish against the player | A native badge identifies the active recipient. Separate source clips match its entrance and terminal cry. A recent red HP reading, zero shields, a uniquely owned named charge, a flash in the player impact phase, disappearance of the flash, and a loss agree. | Completed Replay may credit that charge once, with an inferred landing at the first captured portion of the final cry. Preserve the source times for the badge, charge, cries and departure images. | The flash alone also occurs during recalls. A competing attack, living switch, later surviving HP or surrender leaves credit unresolved. Pixel archive `d2895133` has a shielded first Aerial Ace and a finishing second one. |
| CRY-06 — partial cry during a recipient flash in completed Replay | A uniquely owned charge has a named announcement against the established last opponent. A distinctive partial cry from that opponent overlaps the recipient’s independently tracked flash as it contracts and clears in the charge phase. | Credit the charge once. Replay places the inferred landing at the first flash frame and bounds the faint by the clearing frame. Keep the cry fragment, flash, announcement and badge on their original clocks. | An extrapolated cry onset is not a captured damage tick. A tied sound, living switch, later confirmed surviving HP, shield, competing hit or missing collapse withholds this association. Recorded Moondrop case `af45f682` is Gourgeist’s Seed Bomb against Vaporeon. |
| Cry reference selection — completed Replay | A species is identified in battle from an appearance, an archived battlefield crop, a faint or an attack. | Include its cry as a competing sound, even after it leaves the field. | A place on a saved team alone does not show that the Pokémon participated. Reference selection has no effect on the archived audio. |

### A retained announcement and a separate last-opponent cry

For live HUD credit, a completed charge announcement captured within half a second of a confirmed last opponent's departure cry can name the finisher after the faint is recorded. The announcing nickname must match a recent native player badge and the move text must uniquely identify one equipped move on the selected team. Require an injured recipient on the same appearance. Conflicting charges, a competing hit, a shield, or evidence of survival leave credit unresolved. Attach credit to the existing faint and retain the banner capture, cry onset, and publication times separately.

If a banner contains a complete charged-move name followed by `!`, the announcement ends there. Keep the full OCR reading and source crop. For a single exact banner up to 3.5 seconds before the independently confirmed final cry, an earlier crop-backed use of a different equipped move can corroborate the nickname when each move uniquely identifies the same team member and a recent native badge confirms the nickname. One missing punctuation stroke in the nickname is tolerable. Credit belongs to the current banner. A conflicting badge, verified surviving HP after reception, a block or a competing attack leaves this association unresolved.


When a departure cry independently resolves a faint, search backward on the capture clock for the last opposing attack. A uniquely sided Fast Move sound needs a known duration and an onset within twice that duration. A named charged move needs a compatible unblocked exchange. A later supported attack, a living return, or surviving HP after reception can rule out the earlier candidate. Late recognition can add or withdraw credit on the existing faint. The cry bounds an inferred landing when the impact frame is missing.

A clipped double letter in a finishing banner such as “Shadow Bal!” can match one equipped move when an earlier, different, fully named move identifies the same performer, both player charge preparations are witnessed, and the appearance is continuous.

If the first announcement crop precedes the last opponent's departure cry, a second matching crop captured up to half a second after that cry can complete the same reading. Keep the first crop as the move source. A reliable living sliver between the announcement and reception describes earlier health. A living sliver after a complete terminal impact, or after the cry when reception is inferred, contradicts credit. A late banner can update the existing faint once. In the November 25 10:10 match this distinguishes Golbat's finishing Shadow Ball from its earlier Poison Fang. An earlier crop-backed, independently announced use may corroborate the performer's nickname even if its closing exclamation mark is missing. Its entire move name and the finishing move must each have the same unique owner on the selected team. The finishing announcement and all impact, cry, badge and conflict checks still apply. In the 10:10 arrival-order test, Poison Fang establishes ownership and only Shadow Ball receives credit. A clipped name or extra trailing letter on the earlier use still requires independent corroboration.

For damage plausibility, compare the recipient's supported pre-hit HP with the attack actually performed. In recording reviews, a count of distinct QTE pops from that charge can support its strength. Missing pops leave the total uncertain. A charge's bubble inventory, haptic pulses, and a grade are different observations. None establishes the recipient's departure by itself.

If a retained banner names the attacker and uniquely identifies its equipped move, two separate archived audio clips can date that charge. Both clips must match different portions of the move sound and agree on its onset. The image's file time guides the search only.

If the last opponent already had a localized entrance cry, a later clip that continuously matches at least one second and two thirds of the same cry can support departure. Replay checks the active appearances, the last-ball badge, a centered damage profile during the charge, and the subsequent result. A missing bottom rail or a surviving HP column prevents this route from assigning credit. Competing hits, shields, and living switches still block it. A cry clipped at the beginning supplies a time bound.

Trainer wording: “hearing vaporeon cry when its hp is not 100% should be enough
for a faint by itself. a cry and full hp bar would be an entrance.” Here “by
itself” is understood in the stated context: Vaporeon is uniquely identified,
opponent-owned, and the independently confirmed last surviving Pokémon. The
[November 15 match](../verification/2026-11-15-vaporeon-shield-incinerate.md)
visually shows Vaporeon's departure and a win after the shielded charge, while
the stored sound match was not accepted by the existing reader. Compare the
proposal with previously damaged re-entries, low-HP charged sequences that
continue, active same-species mirrors, HP/read delays, and sound reference
competitors before enabling automatic faint attribution.

**Successive-cry refinement:** The trainer identifies two successive opponent
Vaporeon cries in this match: its entrance and its later faint, with no other
opponent cry between them. When both are independently identified and the
same appearance remains active, this sequence adds context beyond a single
low-HP cry: a damaged re-entry can explain the first cry but does not by itself
explain a separate subsequent cry. Keep the two source intervals distinct and
check for missed cries, overlapping clips of the same sound, re-entry, and other
cry-producing events before treating the second as an automatic faint. This is
a trainer-labelled positive case; current archived recognition has not yet
accepted both cries and the sequence has not been validated as a universal rule.

The existing production RMS-envelope matcher was not validated by this pilot;
the experiment compared actual waveforms against a small roster-specific
reference pool. Cry species, cry purpose, side and finishing-move identity
remain distinct conclusions. Same-species entry/faint matches used the same
reference waveform in these recordings; purpose came from sequence/context.

### Context-dependent cry sample requirements — proposed November 12

**Partial-cry review — November 14; diagnostic, not a live acceptance rule.** If
a retained audio window begins partway through a cry, failure to match the entire
reference does not establish absence of that cry. In the reviewed ending,
Dragonair's retained 600 ms tail correlates at 0.973 while the complete-reference
comparison fails. Preserve partial coverage, reference offsets and competitors;
do not label it a complete observed cry or infer faint purpose from it alone.
Two separated windows matching different portions of one Seed Bomb likewise
support one effect, not two attacks. See the
[ending audio study](../verification/2026-11-14-ending-seed-bomb-audio.md).

**Retrospective implementation — November 14.** Replay now recovers distinct
portions of one charged effect and a separately bounded cry tail from verified
original audio. Both legal Fast and Charged references, configured/recorded extras
and cry competitors participate. A matching Fast effect remains available as a
competing cause even when no animation was scheduled. Missing references for
an active Pokémon prevent terminal attribution. Multiple windows of one effect
remain one attack.

For a missing final opponent faint, the implemented rule additionally requires
stable non-mirror identities, a unique legal performer, last-survivor count,
zero shields and native WIN. Pending/unknown competing attacks, later survival,
new charge/entry/session boundaries or missing relevant evidence leave the link
unresolved. The result is explicitly a retrospective audio reconstruction; it
neither rewrites original evidence nor creates a measured zero HP/count. The
cry's estimated onset and its original capture clock remain separate. Live cry
acceptance and universal numeric sample requirements are unchanged. See the
[implementation and regression scope](../verification/2026-11-14-ending-seed-bomb-audio.md#implemented-retrospective-recovery).

Trainer refinement: “mirror matches need to double the required samples”, then
“if user A has the same lead pokemon that user B uses as a safe swap, maybe only
3 or even 2 would be a prerequisite”. The refined proposal replaces a blanket
team-level mirror multiplier with an evidence requirement based on unresolved
side attribution.

| Rule | If… | Permitted conclusion | Limit / validation status |
| --- | --- | --- | --- |
| CRY-04 — proposed | Both sides could have produced a same-species cry in the relevant capture-time interval. | Require additional independent observations and evidence that distinguishes the sides before accepting side or KO attribution; retain the cry with unresolved attribution meanwhile. | Doubling samples is a candidate policy, not a validated threshold. Repeated or overlapping windows of the same cry are one occurrence, not independent samples. More samples alone do not identify the side or prove a faint. |
| CRY-05 — proposed | Established entry, departure or survival history rules out one side as the source, even though both teams contain that species. | Reduce the additional evidence requirement according to the remaining ambiguity. A lead on one team and later safe swap on the other need not incur the same requirement as simultaneous mirrors. | Two or three independent observations may suffice, but no numeric minimum is validated. Non-overlapping appearances must be established from source-time evidence, not assumed from roster membership or recognition completion order. Cry purpose and finishing-move attribution still require their own support. |

Acceptance examples to test: simultaneous same-species leads; a departed lead
followed by the opponent's same-species safe swap; delayed/reordered evidence;
and overlapping audio windows that must not inflate the sample count. Compare
two- and three-observation combinations with the doubled-sample hypothesis,
including ordinary switches as negative cases. This entry documents intent;
it does not change runtime thresholds.


### Repeat-cry implementation evidence (November 12)

The live shared-PCM candidate path is implemented; device validation is pending.
See [implementation and verification](../verification/2026-11-12-repeat-cry-departure-implementation.md).
The continuous 1605 recording yields four candidates and neither ordinary switch,
including reconstruction with archived species capture times and their delivery delays.
A first badge reading bounds when identity became known; it is not the exact entrance
instant. A preceding cry can belong to that appearance only within the uncertainty
interval after the last old-species sighting. Keep both original timestamps and their
references. An old-species sighting after the cry excludes that proposed appearance.
A cry-derived candidate requests independent counts; it still does not assert a faint.

Two distinct opening cries can overlap in duration. Their distinct localized onsets
must not be discarded simply because the sounds overlap. Competing same-onset species
matches remain unresolved. Correlation 0.5, margin 0.1 and 100 ms onset proximity are
pilot acoustic parameters, not universal game rules or faint confidence thresholds.

### Camera-transition pulse qualification — November 14

**FM-CAMERA-01 — implemented in completed Replay; recorded-case scope.** If a
source-timed border pulse coincides with a coherent diagonal excursion of a
complete HP bar, continuing movement and return, stable HP within measurement
jitter, and an independent nearby charged-move announcement within unchanged
appearances, its border comparison is not sufficient proof of a new Fast Move.
Retain the raw pulse and its archived derived use, and retain the qualification's
evidence references. Remove only that border basis from Replay's attack evidence.
Independent HP damage or matched recipient sight-and-sound support remains usable.

A missing bar, zero orange, unchanged HP, one moving frame, or a crop label alone
is insufficient. A changed HP plateau, incompatible/conflicting geometry, missing
return/charge corroboration, faint, switch or capture boundary prevents this
qualification. Do not suppress a whole time interval or round tiny surviving HP
to zero. One-column jitter tolerance does not disprove a subpixel hit. Validated
on the paired Nov 13 recording and synthetic controls; not a universal game rule
or a live-witness change. See
[the recorded comparison](../verification/2026-11-14-camera-transition-pulse.md).

**HP-12 — Replay HP certainty (November 14, 2026).** If a numeric HP value
has explicitly weak or invalid measurement confidence, preserve the Article
but do not use its amount as a fresh HP measurement. Missing confidence is
unknown for survival attribution. For terminal survival and the current Replay
bar, require classified live fill, complete in-frame geometry, confidence at
least 0.80, the current appearance, and no contradictory complete-bar color
profile. A tiny classified live sliver remains positive; there is no minimum
surviving percentage. The confidence score qualifies measurement support, not
the probability of an exact HP fraction.

Replay retains its historical amount internally, but shows unknown when its
latest source measurement is older than 500 ms, a later supported incoming hit
has no new HP measurement, or newer color evidence is damage-only/unresolved.
The 500 ms bound is a conservative presentation policy, not a KO window or a
validated universal game constant. Capture time governs freshness; delayed
publication cannot refresh it. A new appearance cannot inherit that certainty.

Legacy high-confidence numeric measurements without color metadata can still
corroborate team-loss ownership at their own capture time; they do not establish
current terminal survival. Low-confidence or missing-confidence measurements
cannot perform that corroboration. Independent move, pulse and faint testimony
remains available. Unknown HP neither creates zero HP nor proves a faint, move
use, or killer assignment. Original archives remain unchanged.

### Fast Move credit after a charged exchange

If a late QTE vibration cites the preparation for a charge that already completed, Replay keeps it in that charge. A new preparation can open the next charge.

If complete, aligned views of the same opponent HP bar change from a verified red sliver to orange damage, Replay can place a separately supported Fast Move at that change. Two disjoint portions of a move sound must agree on its source time. One clip can contain portions of two distinct attacks.

A matching departure cry and a count-confirmed replacement, or a last-opponent count followed by a win, can support the faint independently. A living return or even one verified surviving HP column after the proposed reception vetoes that finish. A competing attack leaves credit open. Earlier red HP supplies the lower time bound and the first matched cry fragment can bound the departure. An uncertain bar interior never establishes an observed zero.

If the opponent’s bar moved between readings, a retained full battlefield frame can show the later damage during the same appearance. The reader requires both rails and both caps, a coherent orange trail, and a localized flash below the bar. Possible live color in even one column leaves the finish unresolved. A previously verified one-ball count must still apply to that appearance, and distinct saved clips must agree on its departure cry. A strong waveform match can corroborate the original spectral read of the same clip when the estimated onsets align. The appearance and competing-attack checks still determine who gets credit.

Implemented in completed Replay and checked against the November 18 Pixel Sealeo and Vaporeon finishes and the living-switch controls.

### Live HUD during a charged move

Once the party screen departs or VS is recognized, hide setup and nickname prompts through the result. An accepted start can arm the display while the opponent badge is missing. The native opponent badge anchors the live HUD. Keep the calibrated HUD box stationary when move rows appear or disappear, clear of the announcement and QTE grade. A badge captured before the current opponent was identified cannot switch the HUD back to an earlier move list.

When a result is accepted, open the post-match menu once. Returning to the battle lobby retires result observation and keeps Next Match and Stop Session available. A delayed battle HUD or party reading cannot reopen combat. A trainer who hides the result menu can reopen it.

A charged-move cue can establish that combat is already underway if observation began late. That cue dates the charge. The actual GO can be earlier and remains unknown without opening evidence. The HUD keeps the move list hidden through this charge even if the inferred Match Start and the charge cue share a capture timestamp.

Once Custody accepts a saved announcement crop, the announcement reader can
select it at its original capture time while the Timeline publishes earlier
records. The published text keeps links to the crop and the full OCR output.
If the filtered OCR block contains one complete named “used …!” line, the
reader can pass that line to the move resolver. Wrapped or competing move
lines retain the existing interpretation path. Species, side, and legal move
checks still determine whether the named attack can be published.

When both complete HP bars pan right together from their ordinary positions in the same captured frame, the live HUD can hide the opponent’s move list before the announcement or grade is read. A trusted Get Ready or incoming-attack prompt can also start this window. A complete grade on its own line can keep the list hidden even if a second line contains HUD text. The camera cue and mixed grade guide only the display.

Keep the roster sprites in place. In the calibrated announcement strip, a line of game text outside the HUD’s own pixels briefly hides the move list. Each fresh visible sample renews that short interval. It does not identify a move.

Once Overdex recognizes Get Ready, an incoming attack or shield choice, or a completed charged move, keep the list hidden until either side’s next observed Fast Move or the match ends. Use capture order so a late report from before the charge cannot restore the list. A camera return, a faint, or a switch can occur while the banner is still visible. Camera and grade hints on their own retain their bounded display windows.

### Completed Replay · opponent entrance during an identity gap

When a later sided announcement or badge confirms a different opponent, a matching entrance cry close to the start of the gap can establish that appearance from the first measured cry fragment. Require a distinct preceding cry from the outgoing opponent and exclude species shared with the player. Two distinct matching Fast Move sounds can name the incoming opponent's independently supported uses when that move is exclusive to its side. Overlapping recordings of one sound count once. The handoff alone assigns neither a faint nor a damage tick. The November 22 Pixel Gallade → Jumpluff exchange is the recorded regression.

If the selected player's card has a confirmed nickname, an exact reading of that nickname in the proper party slot or native player badge identifies the card's species. Compare nicknames with whitespace and case ignored, retaining symbols. A complete charge announcement naming that nickname and one of its equipped moves can identify the player as its owner. Cite the selected card and announcement at capture time. A card supplies identity and moveset only. A faint and finishing credit still require their own evidence. Before GO, a trainer may confirm a new nickname from a party-slot crop and bind it to a specific owned card.

### Completed Replay · shared Fast Move at a handoff

If a team loses a ball across a handoff and the incoming Pokémon is later identified with surviving HP, use that survival to narrow the earlier loss. A complete ordinary bar and a matching numeric reading from the same capture can establish survival. A Water Gun sound compatible with either Pokémon cannot settle ownership during the handoff.

For the last opponent, a one-ball badge, verified surviving HP before a completed charge, a centered complete orange-only impact, and a distinct departure cry can link the faint to that occurrence. A shield, a subsequent surviving sliver, or a plausible competing hit withholds that credit. Preserve the move, impact, and cry at their own source times.

When a complete saved selection identifies a unique lead, the player-name reader can wait up to eight seconds from battle start for a localized entrance cry and an independently identified, different opponent. Together they put the name reader to sleep through ordinary attacks and charged moves. A confirmed native nickname tied to the selected card can also finish the assignment. A player handoff, faint, forced replacement, or contradictory appearance evidence wakes a fresh check. An unresolved opening or mirror keeps the badge available. This rule schedules a reader and supplies no new identity or KO testimony.

A known charge direction wakes the defending shield strip. An unsided impact can wake both shield strips. A count reader stops collecting once its independently timed pair confirms the assignment. A nearby opponent ball strip may be checked during a ball-count assignment or briefly after a charged impact. A confirmed one-ball reading from the incoming opponent’s own native badge can identify the last opponent while its entrance cry is still playing. The later departure cry remains a separate occurrence.

### Corroborating an unreadable send-in and a clipped finishing announcement

If an incomplete opponent send-in resembles the name in a subsequent native badge, a localized cry of that species near the **send-in frame** can confirm the entrance. Require a different previous opponent and a complete player team that excludes the incoming species. Keep the send-in, badge, and cry as distinct observations and use the send-in's capture time for the appearance. A ball-count decrease can then resolve the preceding loss. A living switch can produce the same entrance evidence.

If two separate announcement frames at the last opponent's departure cry show the same clipped move and performer, check the selected card, a recent native player badge, and a different, fully named charge by the same performer during that appearance. Only the current occurrence can receive credit. The original faint, confirmed shield, competing attack, and post-impact survival checks still apply.

In the November 26 11:19 arrival-order check, “Go, Vaeron!” resolves to Vaporeon. The first Shadow Ball leaves a surviving sliver. Withholding the independent second image of the finishing banner leaves both named faints intact and the finishing move uncredited. See `RecordedCorroboratedSendInTest` and `CorroboratedOpponentEntryTest`.
