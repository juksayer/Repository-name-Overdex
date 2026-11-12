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

| Rule | If a valid native three-slot badge shows… | Then… |
| --- | --- | --- |
| TEAM-01 | 3 unfaded and 0 faded Poké Balls | 3 Pokémon remain; 0 have been knocked out. |
| TEAM-02 | 2 unfaded and 1 faded Poké Balls | 2 Pokémon remain; 1 has been knocked out. |
| TEAM-03 | 1 unfaded and 2 faded Poké Balls | 1 Pokémon remains; 2 have been knocked out. |
| TEAM-04 | 0 unfaded and 3 faded Poké Balls | 0 Pokémon remain; all 3 have been knocked out. |

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
| END-03 | The opponent's last Pokémon faints. | Expect 0 opponent survivors and 3 faded icons once the badge updates, if it remains visible. Resolve the player's simultaneous terminal state before choosing END-01 or END-04; do not require a badge frame that the transition never presents. |
| END-04 | Both final Pokémon faint in the same terminal resolution, leaving 0 on both sides. | Treat this as a possible simultaneous-KO/tie case. Do not award a win solely because the opponent reached zero. Preserve the game's displayed result separately from tie/reward interpretation. |
| END-05 | A battle ends with 1, 2, or 3 opponent survivors, while the player's state or ending reason is unknown. | Survivors alone do not establish a loss. The opponent could have conceded, or time could have expired. |
| END-06 | A battle ends with 1, 2, or 3 player survivors, including an active Pokémon with nonzero HP, while the opponent's state or ending reason is unknown. | Survivors alone do not establish a win. The player could have conceded, or time could have expired. |
| END-07 | A concession is independently established and attributed. | The conceding side loses even if it still has conscious Pokémon. Distinguish directly observed exit/confirmation, inference, and trainer testimony. |
| END-08 | A timeout is independently established. | Use both teams' terminal survivor counts and, where required, sufficient HP evidence under the verified timeout rule. A single active bar or stale badge is insufficient. Await the game result if evidence is incomplete. |
| END-09 | A black transition or capture gap interrupts play. | Open an appropriate bounded result check when supported by battle context. The transition alone establishes neither concession nor outcome. |
| END-10 | Result or ending reason remains ambiguous. | Keep unknown fields explicit. A future trainer response should be separately attributed evidence or a record note, preserving original observations. This response/note workflow is not implemented by this document. |

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

1. Native counts 3 → 2 → 1 → 0 produce correct totals and supported faint ownership.
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
These original statements remain source material, not unconditional executable rules.

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
| TEAM-14 | A count interval resolves historical faint owners after a replacement has entered. | Keep each owner's loss and source clock; preserve the living replacement's supported attacks. An aggregate loss cannot supply a fresh entry vacancy or borrow a nearby cry to time every loss. | Confirming an earlier loss does not establish that the current battlefield position just became vacant. |

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
| VIS-09 — recorded appearance counterexample | A previously supported foreground color contracts or disappears while bright attack pixels replace it, even with a stable background. | Treat the original appearance as obscured until independent visibility returns. Preserve the source frames and the competing explanation. | Color loss is not body shrinkage or departure. A foreground territory is not automatic player ownership during charge-camera movement. The living Quagsire Brutal Swing sequence demonstrates both limits. |

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
| MOVE-01 | Configuration, references or accepted evidence identifies a Pokémon's Fast Move. | Retain its identity and basis to interpret separately supported uses. | Learning the move is not another attack or an energy gain. A legal move list is not proof of use. |
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

HP-09 records the trainer’s November 12 request to let the normal and charged-impact HP crops share their image when a bar crosses the boundary. The live charged reader shares capture-space territory, not historical screenshots. Complete-outline requirements remain in force. See [handoff verification](../verification/2026-11-12-shared-hp-territory.md); live device acceptance remains pending.

HP-06/07 use the same source-time resolver for live publication and completed
Replay. HP-08 guards consumers that cannot retract permanent roster/audio state.
See the original [Replay verification](../verification/2026-11-11-verified-hp-replay-faints.md)
and the [live integration record](../verification/2026-11-12-live-verified-hp-faints.md).
The retained Tinkaton pixel fixture supplies a positive case; the living Carbink
recall supplies a negative case. Reprocessing video pixels in a test does not
add missing HP measurements to an existing archive.
| MOVE-04 | A supported Fast Move impact lacks a resolved reference duration. | Replay may show a neutral decorative flight ending at its source impact, clipped to the current appearance and ordinary-play window; an impact exactly at entry can be an impact-only marker. | The decoration cannot identify the move, assert its cadence, generate energy/extra uses, shift source HP time, or borrow flight time from a previous appearance or charge sequence. |

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
| CHARGE-01 | A dedicated accepted stream preserves the shield-choice prompt. | Replay can show bounded opponent charge preparation from that captured time. | The prompt does not prove shield expenditure, name a move, debit that move's energy or invent a completed strike. Generic shield text from an unrelated crop is insufficient. |
| CHARGE-02 | A dedicated announcement/QTE reader accepts a qualifying phase cue from an already-preserved crop for the current Match, with valid source clocks and provenance. | It may prepare the bounded receiving-side HP watch immediately, before raw/accepted/typed publication completes, carrying the original crop reference and capture time. Continue the unchanged canonical evidence chain. | Scheduling is not an attack, impact, damage or faint. Recognition/receipt time cannot replace the source time, unknown text cannot supply a cue, and a stopped reader or ended Match cannot be reopened. |
| CHARGE-03 | Early HP preparation lacks fresh pre-cue side-owned geometry, and that eligible geometry arrives before canonical delivery of the same cue. | Permit the canonical cue to retry preparation within its original source-time deadline and current appearance. A successfully opened window cannot renew on duplicate delivery or later QTE grades. | Later geometry from a replacement or after the cue cannot supply ownership. Missing/stale geometry, a newer phase, stop, resize or expiry does not authorize a longer search or an invented zero. |
| CHARGE-04 | The existing result reader preserves a complete `No Protect Shields remaining!` reading whose retained word geometry fits its official crop and excludes the capture-time overlay. | Reuse that reading for bounded player charge-camera HP preparation. Independently derive incoming-phase testimony from the unchanged raw reading and cited crop, retaining their Match and capture clocks. | It is not a result, shield expenditure, named move, impact, HP loss or faint. Other result-crop text cannot supply Get Ready or QTE grades. Newer blank/invalid outcome readings cannot consume an older valid prompt's episode. |
| CHARGE-05 | A charge completion was delivered before its earlier compatible incoming/Get Ready cue. | Keep the completed interval's witnessed closing time and tail when its beginning is resolved. Retire preceding incoming-prompt visibility at completion by source time, while preserving late historical cues and duplicate suppression. | Late delivery cannot reopen a full-length pause or hide supported post-charge Fast Moves. Do not match across an appearance boundary, conflicting/intervening phase, restart, Match end or the existing phase bound. A later new warning must remain eligible. |
| CHARGE-06 | A native announcement clearly names the independently known attacker followed by `used` and a partially readable move label; its typed Used cue has the same capture time, and the latest trusted incoming/Get Ready phase within 20 seconds agrees on direction. | Use this as attributed charged-use evidence in VIS-08 even when the exact move remains unknown. Cite the raw announcement, Used cue and phase with their original clocks; retain all the terminal sequence, last-survivor, identity, result and contradiction guards. | The correlated text and Used cue are not two independent attacks. A partial label does not identify a move, debit energy, supply measured damage, or by itself prove a KO. Missing/stale/wrong-direction phase, a conflicting performer, player intent or result alone is insufficient. |
| QTE-01 | The dedicated accepted QTE stream reads NICE, GREAT or EXCELLENT. | Replay may show that observed grade at its source time within the relevant QTE. | Do not invent unobserved grades, physical vibration, a named charged move or its finish. A lingering grade after completion must not reopen the QTE. |
| QTE-02 | The dedicated accepted QTE grade stream reads a complete NICE, GREAT or EXCELLENT, and normal opponent HP geometry was measured within five seconds before that image. | Prepare a bounded opponent charge-camera HP watch if Get Ready has not done so. Later grades support the existing phase without replacing its anchor/token or extending its deadline; an older delayed Get Ready cannot reset it. | The grade does not establish QTE onset, move name, impact, damage or faint. Stale/missing geometry or appearance, stop and resize boundaries cannot establish ownership. |
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
| START-01 | The registered countdown witness supplies a typed GO observation. | Derive one Match Start at that image's source time. | Opening the HUD, seeing VS/species/full bars or clearing button shading is not GO and does not itself start the Match clock. |
| START-02 | A trusted countdown number predicts a start and independent combat follows within the allowed 0–30-second interval. | The fallback may estimate Match Start at the predicted time, citing both observations. | This does not create a witnessed GO or claim the estimate was directly observed. A countdown number alone is insufficient. |
| START-03 | The current capture session accepts Match Start and reaches active battle while Droidball was left closed. | Open Droidball once for that Match, including the hidden-shell transit handoff when the source start is newer than that journey. | This does not start capture, create a Match, or repeatedly override a later manual collapse. Old sessions, stopped capture, Replay and newer return navigation cannot trigger it. |
| END-14 | Eligible result music and independent result testimony occur in the same Match within the source-time window. | Record agreement or disagreement. Before resolution, music may wake bounded result-text reading. | Music alone does not create or amend Match End. A conflict must not silently replace the observed result, and a result does not identify concession versus elimination. |

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
