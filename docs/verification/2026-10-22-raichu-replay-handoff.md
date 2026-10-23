# Raichu replacement and retained badge review

Reviewed three Moondrop archives copied read-only to `/home/sean/overdex-device-backups/2026-10-22-replay-review/`.

- `2026-10-22_14-38-10_62ee3f19-5101-467c-a1be-9133236e8a41.odxmatch.zip`: 2523 articles, outcome ['WIN']; SHA-256 `0445752a843bb083ab611342663cbf14b3e855ae04978858f39ce38d5a151755`.
- `2026-10-22_14-44-06_91ce989c-f6e8-44bf-9d6e-403eba4c1d5b.odxmatch.zip`: 2248 articles, outcome ['WIN']; SHA-256 `9dfc44bcb398188c0ea00811785633f055becda9405377b5f9ebae35abbb05de`.
- `2026-10-22_14-48-30_3fad5ba0-2b84-40a8-8e05-1811efb758e2.odxmatch.zip`: 2252 articles, outcome ['WIN']; SHA-256 `1e84275b1c211ae83ee7d927627e58b71a34e07ec072142f6d811840bbb91a56`.

## Findings and changes

- The 14:38 recording has a second player Raichu entry at session +111.71 seconds, followed by the player faint at +113.18 seconds. The shared bare name let the delayed ball decrease faint the incoming appearance. The outgoing Alolan slot now receives that nearby delayed faint, and the remaining roster slot resolves the incoming ordinary Raichu. Raw articles are unchanged. Replay reserves 650 ms before replacement for the outgoing KO pose.
- The replay sprite provider used only dex 26 for both forms. Replay now selects the supplied `pokemon_icon_026_61.png` for Raichu (Alolan), bundled as `sprites/pokemon/forms/26-alolan.png`. The opponent uses this front portrait. Player Replay uses the dedicated rear sprite from the local sprites-master collection, `sprites/pokemon/back/10100.png`, bundled as `sprites/pokemon/forms/back/26-alolan.png`. No shiny status is inferred.
- Replay includes a small bounded entry announcement or active named charged-move badge.
- The completed-session flag now prevents late battle evidence from reopening battle-surface confirmation. Capture also explicitly excludes completed sessions from retained badge presentation. This prevents replaying a cached badge after the result; it does not fabricate resource counts.

## Validation

Focused identity and replacement tests passed, including a reduced original-article fixture from the 14:38 match. The fixture retains source IDs and times. Full JVM suite passed: 1,043 tests, zero failures, errors, or skips. Normal debug APK build passed. Device installation and visual acceptance remain pending: the user disconnected the Moondrop during the local checks.
