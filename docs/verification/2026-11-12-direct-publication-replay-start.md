# Direct publication notifications and Replay default position

## Changes

Normal Match Replay now opens at the archived Match Start and returns there on
RESET or replaying after the end. Full recording bounds are unchanged: the user
can scrub backward into setup. Explicit event positions and selected clips keep
their requested starting position; missing Match Start falls back to the recording
beginning. No archive data, timestamps, or evidence are removed.

Five temporary `match.articles.first` call sites across announcement reading,
species crop preservation, player control text, and forced replacement reading
now call `awaitRawArticlePublished(sequence)`. Raw publication notifies only
waiters for that custody sequence, after Timeline append and article broadcast,
before inline derivations. The existing full accepted-evidence drain barrier is
unchanged and still includes derivations and availability.

The Timeline indexes references by custody sequence, retaining the same immutable
Article objects. The notifier keeps no completed article cache; publication that
precedes registration is recovered from the ledger after checking the publication
watermark. Match's raw testimony lane publishes in custody order. Availability
must not use this raw-testimony receipt API. Pending registrations are limited to
256; further callers suspend for capacity. Cancellation removes registration and
Match release cancels pending waits, including capacity-waiting callers as slots
become available. Completion resumes consumers outside the registry lock.

Existing article-stream replay capacity and routing are unchanged. A separate
`raw_publication.receipt_notification` timing stage exposes notification cost
rather than attributing it to broadcast. Earlier listener diagnostics remain
opt-in through the existing processing flag.

## Validation

Focused run: 21 tests passed, zero failures or skips. Includes direct notification
race/cancellation/capacity tests, retrieval after 5000 publications, concurrent
publication/registration, 200 real Match receipts preserving payload/references/
capture time with no temporary article-stream collectors, and blocked interpreter
verification that raw receipt completion does not release the full drain barrier.
Replay position tests cover normal start, missing start, clipping and bounds.

Full `:app:testDebugUnitTest :app:assembleDebug` succeeded. XML totals: 2917 tests, 0 failures, 0 errors, 2 skipped. `git diff --check` passed.

This fixes the measured repeated-history-delivery mechanism. No numerical phone
latency improvement is claimed without another device trial. No device installed
or commit made during this implementation slice.

## Authorized device trial installation

Following the user's request to include installation in the feature routine,
installed on Moondrop MDPH00124112500414 after confirming no active app services.
ADB installation succeeded, MainActivity launched, and the existing processing
profile flag remains present. APK SHA-256:
`c229218d920b57d9ca37b4a7f10b275bca20f82f2b50154c9ac80e786db879be`.
Live match and Replay navigation acceptance are pending the user's trial.
