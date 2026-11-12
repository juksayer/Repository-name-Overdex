# First missing Turtonator hit — verified trace, November 12, 2026

Match `41ed0b78-fc1c-413f-b05f-651270378eb9`; source archive retained unchanged on
the 1 TB drive under `2026-11-12-pixel-1447/`. Scope: locate where this opening HP
loss fails to become a move event, not repair every gap or alter recognition.

This section records the pre-fix diagnosis. The implemented handoff and validation are
recorded in [the checkpoint report](2026-11-12-hp-baseline-and-checkpoint.md).

## Evidence chain

1. The first archived OPPONENT HP observation is article
   `c1f303ae-d08f-4a3b-a87e-8e93dcfa5ad0`, fraction .98941797.
2. The next is `e2bf34f8-c2b1-49bb-933c-77ecf2b859db`, fraction .87234044,
   followed by .8677249 plateau samples. No opponent-side damage tick for that
   opening loss is recorded. No Turtonator Fast Move use is recorded in the match.
3. `LiveActiveHpBarWitness.start()` calls `chargedImpactWatch.resetAll()`.
   `reset(side)` inserts each side into `resetHubOnReturn`.
4. `publishMeasurement()` preserves HP testimony first, then publishes a hub
   sample carrying `fastMoveEvidenceAllowed`. The only production call that
   consumes the side's reset marker is this gate call. Therefore the first
   published opponent HP sample following startup is reset-only/ineligible;
   an appearance reset before it has the same effect. No preceding opponent
   HP testimony exists in this archive; ordinary non-artifact HP articles are
   retained by compact archive selection.
5. `fastMoveEvidenceAllowed()` removes the reset marker and returns false for
   that first otherwise-normal sample, even without a charged-camera window.
6. `LiveActiveHpBarDamageTickWitness` handles false by `detector.reset()` followed
   by `return@collect`. It does not seed the 99% sample as a baseline.
7. The following 87% sample becomes the baseline. Subsequent approximately 87%
   readings cannot establish the missing 99%→87% loss. The later 75% reading
   is followed by a large geometry move/gap in these first eight samples.

This explains how truthful HP evidence enters the archive while its damage
signal is unavailable to move inference. The drop is not caused by Replay
failing to animate an already-recorded Turtonator move: that move is absent.

## Verified controls

At diagnosis, `RecordedPixel1447HpDeliveryTest` had three passing cases:

- Original measurements directly into the unchanged detector: opening decrease
  settles at the original second-sample source timestamp.
- Real `ChargedImpactHpWatchPolicy.resetAll()` plus the production damage-witness
  eligibility branch: first sample false, subsequent samples true, no opening tick
  (indeed no tick among these eight measurements).
- Counterfactual control: supply the tick obtained from the preserved baseline to
  unchanged `FastMoveUseInference`, with explicitly test-only combatant identity
  articles. It yields one PLAYER/Turtonator use at that tick's source time.
  This is not a newly observed event and is not written into the archive.

The charged-impact policy regression suite also passes. No production code,
threshold, installed APK or archive is changed by this trace.

## Limits and next change

The archive does not retain exact hub collector execution order, all reset receipts,
or per-sample eligibility flags. Thus it cannot exclude additional scheduling,
late-identity or hub-delivery losses. The startup gate's first-sample suppression
is established by production control flow and reproduced using the saved sample
values; no claim is made that it explains every later staring interval.

The next narrow fix should distinguish normal boundary samples that may seed a
fresh baseline from charged-camera samples that must remain excluded. A reset-only
normal sample must not itself produce a hit, but discarding it loses the next
observable damage step. Do not simply enable every currently blocked sample:
that would risk interpreting charged damage as a Fast Move. Preserve appearance,
source-time, camera and late-delivery protections, and test switches/charge return
separately. Implementation is a subsequent step, not part of this diagnosis.
