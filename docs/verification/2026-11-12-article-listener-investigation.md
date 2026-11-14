# Article listener investigation — November 12, 2026

Status: investigation active; production routing unchanged. Listener diagnostics installed on the idle Moondrop after full verification; live trial pending.

## Verified mechanism

Match publishes raw and derived articles through a SharedFlow with replay 4096,
extra capacity 4096 and DROP_OLDEST. The source tree contains 68 `match.articles`
references across 48 files; these include temporary `first` subscriptions and
multiple instances, so this is not a measured simultaneous subscriber count.

Examples of downstream filtering: PersistedActivePokemonTypeWitness first casts
to CropCaptured and checks the crop name; PersistedAnnouncementPhraseWitness
first checks source ID and RawTestimony. Every unrelated item reaches these
collectors before rejection. Turning off expensive reading does not unsubscribe
these listeners from the common stream. ActiveTypeSamplingGate still needs its
transition cues independently of whether a type crop requires recognition.

The locally resolved coroutines 1.11.0 source SharedFlow.kt confirms tryEmit
locks the shared flow, appends, scans slots for waiting continuations, then resumes
them outside the lock. Collectors update shared indices under that same lock.
No Dispatchers.Unconfined use was found in battle audio/observation. UNDISPATCHED
startup does not mean every subsequent callback runs inline on the publisher.
Do not equate collector body duration with publisher tryEmit duration.

## Controlled experiment

ArticleBroadcastFanoutProfileTest is opt-in via OVERDEX_BROADCAST_PROFILE.
It uses the production buffer settings, 1000 ordered events, four reader threads,
0/1/16/64 trivial collectors, burst and 1 ms paced publication, one warm-up and
three measured repetitions. Every expected delivery and order is asserted.
CPU and elapsed are measured separately; timer overhead is included and these
small desktop measurements are not Pixel/Moondrop performance predictions.

Paced median total emit elapsed over 1000 events: 0 listeners 4.884 ms; 1 listener
14.508 ms; 16 listeners 15.074 ms; 64 listeners 20.996 ms. Burst medians are
2.093/3.020/2.616/2.398 ms respectively. This supports a scheduling/fan-out cost
when readers repeatedly sleep/wake. It does NOT reproduce the phones' multi-second
aggregate cost or establish which live reader causes contention. Do not claim the
root cause fully proved from this experiment.

In the latest compact Moondrop archive, only 35 of 2705 archived articles pass
the charged-used phrase reader's source/type filter (98.7% irrelevant); 61 pass
Get Ready's filter (97.7% irrelevant). Compact archive selection omits some live
crop deliveries; these are archive-scope ratios, not exact live callback counts.

## Next evidence needed

Measure live subscriber counts and bounded sampled callback CPU/elapsed by listener,
without publishing diagnostics through custody. Correlate with direct broadcast
spans and backlog. A candidate remedy is routing by payload/source/crop before
waking subscribers, preserving original publication order, replay eligibility,
source clocks, and the independent transition listeners that rearm readers.
A plain `filter` after SharedFlow subscription does not remove the wake-up cost.

Experiment CSV and source-site inventory are on the 1 TB drive under
`2026-11-12-broadcast-investigation/`. Focused opt-in profile test passed. This initial experiment preceded the production
instrumentation described below; its desktop timings are not device acceptance.

## Listener-level diagnostics prepared

When existing processing diagnostics are enabled, Match now wraps the public
SharedFlow without introducing another queue or coroutine. It retains upstream
replay references, ordering, overflow behavior, exceptions and cancellation.
Counters are keyed by compiler-generated collector class (including inline filter
call sites), capped at 128 names plus overflow; identical collector classes are
aggregated. Current/peak subscription counts and deliveries are exact for the
wrapped article stream. Dedicated HP/species lanes are not included.

Every 64th callback per subscription is timed. These are downstream elapsed times,
including suspension and cancellation, not callback CPU or time the publisher
waits. CPU attribution across suspend/resume would be misleading without deeper
tracing, so this report deliberately omits it. Sparse periodic samples can miss
rare work; do not infer an exact total from a sample mean. Data is exported through
the existing out-of-band JSON summary, never as testimony.

Focused tests cover replay object identity, order, post-delivery filtering,
suspended-reader/nonblocking-publication behavior, cancellation, exceptions,
bounded name storage and export. The repeat fan-out experiment includes diagnostics:
for 64 listeners, paced median emission time is 21.65 ms without versus 21.25 ms
with diagnostics per 1000 publications. Burst median is 3.42 versus 10.52 ms.
Thus instrumentation is not free; the burst increase must be considered in device
comparisons. All synthetic deliveries remain complete and ordered. Three measured
repetitions follow warm-up; CSV is `instrumented-fanout.csv` on the backup drive.

Production routing is still unchanged. Listener-level device data is required to
identify which callbacks contribute meaningful work and how many dormant phase
readers remain attached. No claim of a completely diagnosed or fixed bottleneck.

### Pre-battle subscription audit

Current production registration creates seven party-surface crop subscriptions,
one league subscription, three roster-name subscriptions and three roster-sprite
subscriptions: 14 team-selection consumers before counting VS. Their enabled or
accepted guards reject articles downstream; they do not detach on assignment
completion. The roster-name readers also currently consume phase-closing signals
inside the reader. Moving those guards requires preserving the captured roster
window, not merely cancelling on the first party acceptance. VS adds another
filtered article subscription. These are statically verified registrations, not
live sampled subscription counts.

This supports the user's proposed division: phase control owns activation and
capture-time boundaries; content readers receive only matching evidence while
assigned. Result readers similarly require their own crop lane and a separate
bounded wake-up controller. Stopping expensive OCR alone leaves delivery work.

## Build and device preparation

Full `:app:testDebugUnitTest :app:assembleDebug` passed: 2908 tests, zero
failures/errors, two skipped (2906 passed). The opt-in fan-out profile was run
separately and passed. `git diff --check` passed.

Inspected direct SharedFlow operator-fusion behavior in the resolved coroutine
source: a wrapper can change direct rendezvous-buffer/flowOn fusion. No direct
`articles.buffer`, `articles.conflate`, `articles.flowOn`, or `articles.cancellable`
call sites were found in current production Kotlin sources, including multiline
forms. Do not assume this remains true if new call sites are added.

Moondrop MDPH00124112500414 reported no active app services before replacement.
Installation succeeded and MainActivity launched; processing diagnostics flag
remained enabled. APK SHA-256:
`340c78647260d594273d8a1802a5438b5738700c87c8ab5cd97852687f0656ae`.
Pixel was not replaced during this listener-diagnostics slice. No new commit.
A completed Moondrop match with the new listener report is still required to
rank actual deliveries and sampled callback durations alongside broadcast spans.

## Follow-up: post-match and inference listeners

Read-only inspection after installation found:

- `PersistedOutOfBattleMenuWitness` keeps collecting after `accepted=true`;
  the first callback statement then returns. This is a concrete completed-task
  subscription candidate, independent of whether its upstream capture has stopped.
- `PersistedOutcomeTextWitness` has three article subscriptions: result-stop,
  normal outcome crops, and probe crops. It cancels its whole witness scope on
  `MatchEnded`. It therefore differs from the menu reader: do not describe all
  post-match readers as remaining active after results. Sparse result probing
  during a battle supports concessions; removing it requires a reliable wake cue.
- `ResultMusicWitness` combines phase-gate updates, audio artifact correlation,
  and result corroboration in one article collector. It also has a separate PCM
  subscription. Routing its article inputs alone will not remove PCM delivery.
- Match's opponent-energy forecast collector runs on `matchScope`, the same
  serial worker used for testimony processing. It invokes two HP histories and
  two terminal histories before its own semantic-event filter. Those histories
  have their own relevance/early-return guards; this is not proof of a history
  rebuild for every article. Its work can delay subsequent testimony even though
  it should not be equated with time spent inside `tryEmit`.
- `StatChangeInference.accept` returns immediately for payloads other than
  stat-change sound and charged energy spend. Its history join is bounded to 64
  sounds and 64 moves. The count-based faint listener likewise returns when its
  history reports no changed reconciliation. Neither supports the hypothesis
  that every unrelated delivery necessarily triggers a full history scan.

The connected Moondrop still contained only the pre-install 16:00 and 16:08
processing reports at this check. No listener-level live data is available yet.
These findings prioritize measurement; they do not establish the main cause of
multi-second broadcasting overhead or authorize dropping accepted evidence.

## First live listener report: Moondrop 16:37

Archive `fe875243-f072-42c1-a1f0-7ac4b4a05e07` and diagnostics copied to the
1 TB drive under `2026-11-12-moondrop-1637/`. Archive verification passed all
246 artifacts and 2049 manifest/Timeline articles. The processing report appeared
shortly after the archive; initial absence was export completion timing, not a
missing diagnostics flag. Accepted/completed testimony: 2484/2484. Result WIN.

Raw broadcast total: 28.664 s elapsed / 6.568 s thread CPU (2484 calls).
Timeline append: 0.324 s / 0.121 s CPU. Other major stages: cadence 12.252 s,
Fast Move 9.899 s, energy 7.563 s, charge announcement 5.344 s, faint reconciliation
4.849 s. These are cumulative spans, not a single uninterrupted stall. HP
tracking reached 35 pending observations and 3.969 s oldest tracked age.

The wrapped stream recorded 584015 deliveries across 298 lifetime subscriptions,
with peak simultaneous subscriptions 93. Of those, 210 temporary `first` collectors
accounted for 331546 deliveries (56.77%, average 1578.8 per subscription).
This is an exact count of measured deliveries; callback timing is sampled.
The collector class aggregates call sites, so this report cannot assign all 210
waits individually to a particular reader.

Current source contains five article-first call sites across four readers:
announcement raw and vocabulary resolution, species crop preservation, player
control crop preservation, and forced replacement raw testimony. Each waits for
a custody sequence on the article stream, whose replay capacity is 4096. A new
subscription traverses retained history until it finds the requested article.
This establishes substantial repeated replay delivery, not merely idle readers
receiving new events. It does not establish what fraction of broadcast wall time
those scans cause: they run outside the publisher, can contend with it, and the
22.096 s elapsed-minus-CPU gap can include scheduling, locks and runtime pauses.
Do not label that difference as OCR time or lock time without tracing.

Next smallest candidate: a sequence-keyed raw-publication acknowledgement for the
five wait sites, so callers obtain the exact raw Article without subscribing to
all prior Articles. It must handle publication-before-wait registration,
cancellation, release, original identity/references, and bounded pending state.
It must acknowledge raw publication without waiting for inline inference; the
existing full `awaitAcceptedEvidencePublished` drain barrier must remain separate.
Use a controlled before/after run to establish actual latency improvement.
Do not shrink the article replay buffer as a shortcut; other subscribers rely on
historical delivery. Pre/post phase routing remains useful, but the temporary
waits are the largest measured delivery category in this trial.
