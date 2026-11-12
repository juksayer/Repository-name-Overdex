# Moondrop team counts and missing results — November 11, 2026

Read-only investigation of the trainer's new-match report and retained-badge
feedback hypothesis. This review adds documentation; it changes no application
code, phone calibration, installed package, or recorded Match.

## Evidence and build boundary

Three new archives were copied from the connected Moondrop
`MDPH00124112500414`. Each local copy matched the phone archive hash before and
after transfer. All 1,035 declared artifacts were hash-verified.

The installed APK SHA-256 was
`e90dfcbde779100486dd45287da4afcfddd5a6966a75180e3ce608af94777e3e`;
its recorded update was November 10 at 12:39:36. This predates the
[receipt-based wake-up repair](2026-11-10-reader-wake-up-windows.md), which has
not been installed on this phone. These recordings cannot validate that repair.

Private originals, exact extracted crops, hashes, analysis, and `review.md`:

```text
/home/sean/overdex-device-backups/2026-11-11-moondrop-team-count-review/
```

| Archive prefix and filename time (America/Chicago) | Articles | Artifacts | Faint Articles | MatchEnded Articles |
| --- | ---: | ---: | ---: | ---: |
| `f5c0daa4`, November 11 06:47:44 | 3,740 | 403 | 3 | 0 |
| `1bdccf6b`, November 11 06:52:11 | 3,315 | 341 | 3 | 0 |
| `acb0eda4`, November 11 06:56:06 | 2,347 | 291 | 1 | 0 |

Filename times identify recordings, not their observed GO times. Elapsed times
below use each archive's Match-start monotonic time.

## Captured counts and missed faint reconciliation

All three recordings have opponent readings of **3 followed by 1**, with no
intermediate two-ball crop. Critical original images show real bright/faded
icons and agree with their recorded numerical values. The observed failure is
missing intermediate acquisition and incomplete aggregate reconciliation, not
a demonstrated pixel count of three over a visibly two-ball native badge.

In the latest recording, opponent crops at +0.474 and +1.907 seconds show three
bright balls. The next crops, +26.967 and +27.446 seconds, show two faded balls
and one bright ball. Only one opponent faint, attributed to Deino, was recorded.
A literal EMPTY opponent HP sample was recorded at +25.816 seconds, before that
one-ball reading. This does not recover the missing prior faint's exact time.

The first replacement-species cue arrived 5.780, 5.201, and 3.806 seconds after
its source frame in the three recordings respectively. Those delays exceed the
installed source-based three-second assignment deadline. The timing and absent
two-ball crops match the failure addressed by the pending wake-up repair.
They do not prove that all other scheduling/visibility problems are resolved.

The [current faint tracker](../../app/src/main/java/com/example/overdex/battle/observation/OpponentPokemonFaintWitness.kt)
emits at most one specific faint per accepted decrease. A skipped count of
3 → 1 needs separate aggregate accounting: two KOs occurred in the interval,
even if both identities and times cannot yet be established. See TEAM-06 and
TEAM-07 in the [rulebook](../reference/battle-rulebook.md). No synthetic faint
Articles were added to the originals.

## Result images existed but recognition failed

All three result crops used source dimensions 672 × 1493 and bounds
`[100,641 .. 564,858)`, a 464 × 217 image.

| Recording | Result crop source time | Visible image / archived text | Diagnosis |
| --- | ---: | --- | --- |
| `f5c0daa4` | +120.861 s | GOOD EFFORT with both ends clipped; OCR `OOD EFFOR` | The crop excludes required letters. |
| `1bdccf6b` | +144.391 s | GOOD EFFORT with both ends clipped; OCR `OOD EFFOR` | Same horizontal coverage failure. |
| `acb0eda4` | +66.130 s | Complete but translucent YOU WIN! lettering over the avatar; OCR `oU WINy` | Text is inside the crop. This is recognition/contrast failure, not clipping of the Y. |

All three had two OCR passes according to archived latency testimony. The win
has only one nonblank candidate preserved. The archive does not distinguish a
blank fallback from a failed fallback; it does establish that a second pass was
attempted. Widening the crop alone is not demonstrated to fix this win case.
Later latest-match crops show Rocket reward and catch flow despite the absent
MatchEnded Article.

The [result text witness](../../app/src/main/java/com/example/overdex/battle/observation/PersistedOutcomeTextWitness.kt)
and [interpreter](../../app/src/main/java/com/example/overdex/battle/interpretation/BattleInterpreter.kt)
require an accepted result phrase. Poké Ball counts affect acquisition urgency;
they do not overwrite or veto an otherwise accepted result. Do not teach the
resolver that arbitrary incomplete text always means WIN/LOSS to conceal a
capture or OCR problem. Keep these original crops as regression evidence.

## Retained badge hypothesis

The saved badge is an ephemeral bitmap, not a producer of count testimony.
The faint tracker rejects older source times and increasing counts, and
[Replay resource state](../../app/src/main/java/com/example/overdex/battle/replay/ReplayTeamResources.kt)
takes the minimum count. No path was found where drawing the retained badge
directly overwrites a recorded count or restores a faded combatant.

There is a real source-level risk: while the retained copy is displayed,
[its entire rectangle is excluded from capture](../../app/src/main/java/com/example/overdex/battle/observation/RetainedBadgeEvidence.kt).
[Visibility](../../app/src/main/java/com/example/overdex/battle/observation/RetainedBadgeVisibility.kt)
waits for native player-badge return, without a display-duration limit. Other
content/lifecycle resets can clear it, but if the return cue is missed a new
opponent-count window can expire while the region remains unavailable.

The archives contain no explicit retained-visibility/exclusion-reason event.
Generic unavailable readings cannot distinguish masking from a closed capture
assignment. Consequently, this remains a possible failure path, not a proven
cause of these three gaps. Do not remove overlay-exclusion protection: that
would permit the app to observe its own saved image as fresh game evidence.

Additional source-level follow-ups, not proven causes in these recordings:

- Resource candidate state persists across assignments without a maximum sample
  gap. One fresh unchanged count can agree with an old candidate and finish a
  terminal check before the native badge finishes fading.
- MatchOutcomeCaptureGate accepts dedicated raw count updates without the
  faint tracker's per-side source ordering/decreasing-count constraint. An
  erroneous or delayed higher count can reduce result-reading urgency, although
  sparse result checks continue and valid result text remains independent.
- Live numeric resource presentation can accept a newer count increase, unlike
  Replay. The audit found no live UI consumer of that numerical flow; its
  retained-image invalidation role does not clear a confirmed faint.

## Next verification slices

1. Correct result-crop coverage against full original Moondrop result screens,
   and test OCR on the complete translucent win crop separately.
2. Validate the already-reviewed receipt-based wake-up repair on a fresh battle
   with native count progression visible; these older-build recordings are not
   that validation.
3. Add aggregate KO reconciliation for skipped counts, preserving unresolved
   identities/times rather than manufacturing event precision.
4. Add observable, bounded native-badge reacquisition when retention masks a
   needed region, while preserving self-observation protection.

The [battle rulebook](../reference/battle-rulebook.md) preserves all 15 original
trainer if/then statements and the clarified count/outcome requirements.
Documentation links and whitespace checks passed; no production change, build,
installation, or live-recognition acceptance is claimed by this review.
