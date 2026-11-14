# Pre-battle article routing

## Change

Move the 14 team-selection subscriptions (seven surface apertures, league, three
roster names, three roster sprites) and the VS reader from `match.articles` to
`match.preBattleArticles`. The new stream is fed directly by the publisher; it
does not add another general-stream subscriber or processing coroutine.

Its allowlist contains the ten team-selection crop names, VS crop, party
confirmation, VS confirmation, countdown glyphs, Match Start, Match End, and
explicit scan/restart/stop/ignore controls. All raw and derived article publication
sites use the same broadcast helper, including the derived Match Start.

All Articles still reach the canonical Timeline and original general stream.
Routing retains the same objects, evidence references and source timestamps.
The pre-battle stream has bounded replay/buffer settings matching the general
stream (4096 each, DROP_OLDEST); these are notification buffers, not archive
retention. Relevant replay is no longer displaced by unrelated battle traffic.
Existing reader filters, conflation, enabled checks, and source-time roster-window
rules remain unchanged. No new completion-time phase cutoff discards delayed
pre-battle evidence. A crop captured after the roster window remains subject to
its existing rejection rule even when its crop name is otherwise routable.

Opt-in reports now separate `articleListeners` from `preBattleArticleListeners`.
Count both when discussing total subscriptions, and compare the general-stream
count separately when measuring reduced broadcast fan-out. Publication still
performs a small type/crop-name route check for each article. Do not promise a
latency improvement before the next phone trial.

## Verification

56 focused tests passed. New tests verify all crop/control routes, identical
objects and ordering for late subscribers, zero reader deliveries for 2000
unrelated battle/result items, late capture timestamps and roster eligibility,
and an integrated Match with 15 routed consumers, preserved raw ledger entries,
no added general subscriptions, and delivery of the actual GO-derived Match Start.
Existing team-selection, delayed roster, Match lead, publication drain and export
checks passed alongside them.

Full `:app:testDebugUnitTest :app:assembleDebug` succeeded: 2921 tests,
2919 passed, two skipped, zero failures/errors. `git diff --check` passed.

Installed on Moondrop MDPH00124112500414 after confirming no active app services.
ADB installation succeeded and MainActivity launched. The existing processing
profile enable flag is present. Installed APK SHA-256 matches the tested build:
`e0a7e539a421de726b4ec225ec31394fbfcbe82770b5d911f9cf9f528e30233c`.
Phone latency and live recognition acceptance await the next match. No commit
or Pixel installation performed during this slice.
