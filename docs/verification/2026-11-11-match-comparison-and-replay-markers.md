# Match comparison and Replay event markers — 2026-11-11

## Behavior

Returning to Overdex presents the completed/stopped recordings accumulated since the previous return as one comparison. Starting another recording does not discard earlier pending matches. Each card shows the recorded result (or an explicit unknown), observed battle duration, player/opponent totals and roster, and per-player-species Fast Move/charged-move use bars on a shared scale. Zero means no use was recorded, not proof that none occurred. Unattributed uses remain separate; no damage/DPS or new knockout credit is calculated.

Pending/delivered Match IDs and reference-only archive snapshots are retained in app-private `match_summary_inbox`. Persistence runs on the archive I/O scope, without loading crop/audio bytes. Snapshots precede membership publication. Acknowledgment removes only the displayed IDs, and late evidence refreshes the same record without offering it again. Match IDs own comparison and detail navigation; neither a newer live match nor directory modification order can replace an older selection. This does not repair two battles already merged into one recording because their boundary was missed.

The CRT comparison remains read-only behind GlassShield. LCD controls select the previous/next match, open that exact match, or return; instrument up/down scroll and left/right plus A select actions. Late article refreshes preserve scrolling. B returns from details to the comparison and from the comparison to the previous screen.

Replay retains purple GO and adds colored markers for completed charged moves, recorded faints, buffs/debuffs, active Pokémon changes, and observed shield-count decreases. Player/opponent markers occupy opposite sides of the scrubber. Labeled previous/next event controls pause and seek, including separate events at the same instant, and are reachable with instrument buttons. Initial identity reads and initial shield counts are not fabricated switches/uses. Identity transitions without a recorded faint are labeled “Active change”; known replacements are labeled “Send in”. A two-shield count drop has one aggregate marker because its separate use instants are unknown.

Markers reuse completed-Replay projections and retain their original source times/article IDs when animation timing is fitted. They do not add or rewrite Reality Timeline evidence.

## Validation

- Focused persistence/handoff, marker, summary and stat-change unit checks passed during implementation.
- Production and Android test source compiled; initial debug and test APK builds passed.
- Final full unit suite: **2,405 tests in 315 suites**, zero failures/errors/skips. Debug and Android test APK builds passed.
- Disposable `Overdex_API34_Check` emulator: **OK (18 tests)** across comparison controls/scrolling, Replay event controls, exact-match detail/history navigation, and resumed-activity return behavior. The final run includes selected-action accessibility semantics. Screenshots of the comparison component were inspected for chart/text layout; these are component harness captures, not physical-phone acceptance.
- Independent reviews covered marker evidence qualification, simultaneous-event navigation, batch identity, delayed writes, and snapshot-before-membership ordering. `git diff --check` passed.

Verification logs, component screenshots, and a frozen APK are in `/home/sean/overdex-device-backups/2026-11-11-match-comparison/`.

Frozen APK: `overdex-match-comparison-debug.apk`; SHA-256 `b27eb2baae96afa92b23b076dbcc2da384cc2bbee9b87b4e568850718ade3b5c`.

## Device boundary

At the completion of emulator validation, this feature build had not been installed on either phone; the Moondrop still had the preceding result-reader/wake-up-window build. Emulator navigation/layout checks do not establish live battle recognition accuracy.

Subsequent installation: these features are included in the combined HP-shape-guard APK installed on the Moondrop on November 11 at 11:14:54. See `2026-11-11-replay-premature-hp-drain.md` for artifact provenance, backup verification, and the successful home-screen launch. Physical match comparison and marker interaction remain unverified.
