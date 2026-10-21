# October 15 09:00 battle: missing countdown and species recovery

## Source

Reviewed the Moondrop MD_PH_001's
`2026-10-15_09-00-43_76487bfd-d8b2-40ad-abc7-3436e7e64fcd.odxmatch.zip`.
The 4,194,958-byte archive has 2,184 articles and 644 declared artifacts; archive
structure and every declared artifact hash pass. Original ZIP SHA-256:
`c7b2a6c5481d7be327ed13b84646e5402c1d06db4e95b9fffb286ac24a7bfe03`.
The installed APK at collection matched the preceding HP-tracker update:
`2853af031d049ecf7693b3fcdbe60ed6b722589ba9a9152b360e3251cfdf5399`.

The record lasts about 96.5 seconds. Source-relative times below are approximate;
publication delays compare the article's recorded and perceived wall times.

## Findings

- There are **258 HP measurements** (131 player, 127 opponent), up from 104 in
  the previous recording. There are 29 damage ticks, 12 PRESENT border pulses,
  30 player charge-fill increases, and nine motion-cadence measurements. The new
  blue-border recognition is producing actual pulse evidence.
- Despite that evidence, there are **zero canonical FastMoveUseObserved and
  FastMoveEnergyDerived articles**. No countdown glyph or MatchStarted article
  was captured, and FastMoveUseInference required MatchStarted before counting.
  Timer-clearance support alone does not locate GO. A compact archive's retained
  countdown PNG count cannot establish whether the capture worker stopped.
- Abomasnow and Camerupt are assigned to PLAYER; Sneasel and Sealeo to OPPONENT.
  Sneasel is first accepted around +49 s, with another 2.1 s of publication delay.
  Sealeo's **Water Gun** is identified from side-located effectiveness text near
  +65 s, but published about five seconds later. Sneasel's move remains unresolved.
- `Go, Vaporeon!` is read cleanly around +76.6 s, alongside Sealeo's Poké Ball-based
  faint. It remains unsided because no independent Vaporeon badge reading arrives
  and an unverified configured roster cannot prove opponent membership.
- Species recovery sometimes crops the phone status icons at y=19 instead of the
  assigned name strip at y=96 on the 672×1493 capture. This is temporary scanner
  geometry: pixel-based row location was followed by an additional blind offset.
  Some raw reads arrive 23–26 seconds late; the archive alone does not isolate how
  much of that delay belongs to OCR, scheduling, matching, or publication.
- Two Weather Ball (Ice) announcements, Rock Tomb, and Earth Power resolve.
  Sneasel and Sealeo have faint articles. WIN is present near +94.9 s, published
  1.56 s later. There is no separate final Vaporeon faint article.
- The two distinct retained WAVs contain zero-valued PCM. Audio cannot corroborate
  this record. Nine retained QTE type crops are raw evidence, not classified icons.

## Changes

1. Fast Move accounting can begin with a valid persistent HP loss or an explicit
   charged-combat cue when the countdown is missing. Names, motion, charge-button
   countdown shading, and isolated border flashes cannot open it. This does not
   create MatchStarted or assert an exact GO time. Ignore/restart, phase, and end
   boundaries remain enforced.
2. Charged phases retain bounded numerical evidence while waiting for a delayed
   charge announcement. Its source time separates charged impact from resumed
   Fast Moves. Measurements after that boundary retain their original timestamps
   and active-species context. A missing announcement cannot suppress the rest of
   the match; the provisional phase expires after 12 seconds. No missed attacks
   are synthesized.
3. The species pipeline no longer adds blind offsets after locating the name row.
   The locator rejects clipped text fragments at its search-band edge. Calibrated
   X, width, height, and saved profiles are unchanged.
4. A clean, otherwise unsided entry can use a recent single-side faint to infer the
   replacing side. Both the announcement and faint are cited, and the entry's
   original timestamp is retained. Double KOs, stale faints, same-name resurrection,
   and stronger conflicting side evidence are not resolved with this fallback.

## Validation

- All **782 unit tests** pass; debug app and device-test APKs build successfully.
  The 50 focused tests also pass, including missing GO, countdown shading,
  phase boundaries, ignore/restart, late delivery, and ambiguous-side cases.
- Reprocessing the selected original articles in their original delivery order
  yields **30 canonical uses: 14 PLAYER, 16 OPPONENT**, versus zero in the original
  archive. The regression rejects in-phase charged damage, preserves the first
  measured combat timestamp, and verifies that the input archive is unchanged.
- Installed both APKs on the Moondrop using replacement installs after checking
  that MediaProjection was stopped. Installed app SHA-256 matches the tested APK:
  `807ef2557cc4731558d6d5ce0d46285d7c39cf4418f355910bfa9bfd3df1e9df`.
- **Four physical-device tests pass.** The actual October 15 menu frame does not
  yield a species; the correctly aligned Sneasel and Vaporeon frames do, and their
  assigned crops remain in place. Warm recorded-frame OCR plus catalogue matching
  took 124 ms for the empty menu, 211 ms for Sneasel, and 175 ms for Vaporeon.
  These are isolated recorded-frame measurements, not live capture-to-HUD latency.
- The pending-entry integration test delivers `Go, Vaporeon!` before Sealeo's faint
  reaches custody, then verifies opponent attribution, original entry time, both
  evidence links, and a populated Vaporeon HUD with fast and charged move lists.
- Existing recorded-row tests still recover both offset badge strips while keeping
  calibrated columns and dimensions. The two-sided live-pipeline test still sends
  Raichu and Sneasel through custody and populates Sneasel's HUD move list.
- Normal app launch succeeds. All **11 calibration/profile/HUD-layout files** are
  byte-identical to the pre-install backup. The phone's reviewed archive hash is
  unchanged. `git diff --check` is clean.

Logs and install verification are under `/tmp/overdex-review-20261015-0900/`.
The numerical fixture and its provenance are in
`app/src/test/resources/battle/recorded-no-go-20261015.*`.

The source phone archive remains the historical record. Reprocessing in a test
is not an archive rewrite and is not ground truth for attacks missed by every
witness. A fresh live recording is needed to measure overall attack coverage and
species latency under Pokemon GO's workload.
