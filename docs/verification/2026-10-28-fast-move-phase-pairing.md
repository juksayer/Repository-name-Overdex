# Fast Move loss/flash phase pairing — October 28, 2026

## Existing defect

The controlled October 27 Charm review retained fourteen persistent HP losses
and fourteen border onsets across two seven-episode visual groups. The Moondrop
recorded eight uses in each group. The first loss/flash separation was 803.854 ms
before trusted move identity; the second group's final separation was 1,138.599 ms
after identity. Both exceeded the applicable ordinary fusion allowance. Widening
a symmetric window could attach the first flash to the next, closer HP loss.

The original archives and raw Articles remain unchanged. The visual expectation
is fourteen completed visual episodes, not independent server-registered attack
truth. Reported animation stutter remains unlocalized and is not an invented
explanation for the discrepancy.

## Bounded correction

`FastMoveUseInference` now keeps complementary impact streams separate while
unpublished and resolves their association from the latest retained context:

- An earlier HP loss and later flash can form one use when at least three
  distinct full-frame HP measurements show the same post-loss plateau. The
  readings must cover both endpoints within 150 ms, have no internal gap over
  650 ms, retain source dimensions/end caps, have plausible bar geometry, and
  stay within 1.5 inner pixels of the post-loss HP level.
- This directional route accepts at most 1.25 seconds between loss and flash.
  It rejects an intervening impact, either side's appearance/faint boundary,
  charge phase, or decision interval. Each phase belongs to one use.
- The ordinary duration-aware close-time route is retained as fallback; the
  default window is not globally widened. Separate same-kind losses and pulse
  onsets remain separate measurements. Reference time creates no extra use.
- Associations are recomputed before publication. A late contradictory HP
  reading or receiver transition can invalidate an unpublished plateau join.
  Paired impacts each receive the existing bounded receipt grace.
- All paired source IDs and plateau-support IDs are cited. The original impact
  time remains the use time. Correlated plateau readings do not multiply
  confidence, establish a new attack, or independently award energy.
- Retained numeric/Article history is bounded. This adds no image capture,
  encoding, OCR, audio matching, archive schema, or calibration changes.

An impact already published before an unseen late phase arrives cannot be
retroactively changed by this repair. The explicit expiry regression preserves
that boundary. A measured plateau is association evidence, not a guarantee that
all subpixel damage or server execution was observable.

## Recorded regression and controls

The timing fixture retains 273 untouched source Articles in original serialized
order, including the initial incorrect Pikachu identity, later Gardevoir/Charm
evidence, raw HP geometry, flashes, and idle-side motion. Its selector and hashes
are in `app/src/test/resources/battle/2026-10-27-charm-pairing/provenance.json`.
No earlier correct identity was inserted to obtain the expected result.

Reprocessing that excerpt now produces **seven plus seven uses**, pairs each
original loss with its own flash, keeps the idle defender without attacks, and
adds no uses in the retained settled controls. Original Articles, serialized
fixture, source envelope fields, and impact times are unchanged.

Synthetic controls cover unknown/known duration, adjacent half-second attacks,
one-to-one evidence ownership, pulse-before-loss fallback, missing measurements,
invalid geometry, changed HP/end caps/source dimensions, wrong-side samples,
late delivery/contradictions, switches/faints, QTE and decision boundaries, and
the publication-grace limit.

## Verification and live boundary

- Full unit suite: **1,303 tests / 213 suites**, zero failures/errors/skips.
  This includes two recorded regressions and 24 synthetic controls added here.
- `:app:assembleDebug` succeeded; `git diff --check` passed.
- Independent review identified and closed eager-association order dependence,
  ordinary-pair fallback, and false-bar geometry issues before final verification.
- Installed and launched on the idle Moondrop (`MDPH00124112500414`). All **17**
  saved calibration/profile, HUD-layout, and current-team files matched their
  pre-install hashes. The Pixel was not updated.
- APK SHA-256:
  `f5d6f3253f898acdea5f369466f194ef645160ce047ae05ccebec85bf967c2f7`.

Build logs and protected-setting snapshots are outside Git at
`/home/sean/overdex-device-backups/2026-10-28-fast-move-phase-pairing/`.

New live recordings consume this corrected inference through the existing Match
publication, cadence, and energy paths. Old Replay, summary, and history retain
their archived canonical uses; this update does not silently rewrite or migrate
old recordings. A future read-only reconciliation view would need to retain both
old canonical IDs and raw evidence links across those consumers.

The offline excerpt does not recreate every original capture/admission watermark
or validate live phone latency. A post-install recorded battle is still needed
for physical acceptance.
