# Pixel charged move then Incinerate review — November 12, 2026

## Sources and build boundary

Video: `/home/sean/Desktop/Screen_recording_20261112_111756.mp4` (120.629 seconds, 816×1712; no audio stream). Pixel serial 99261FFAZ0017J. Archive ed5f1bc2-4d13-462f-82c2-10255fd59c04, 9,667,615 bytes; 2,096 articles and all 185 declared artifacts verified. SHA-256 `79786b739b7af08c414a488338a223ffd649df49291ec0e89eecfb81783cc6c5`.

Read-only copies, timeline and contact sheets are in `/home/sean/overdex-device-backups/2026-11-12-pixel-incinerate-review`. Device archive unchanged.

Installed APK SHA-256 `e2cbf450c9ea2c36e142e13c339d3488e87d9f6296380acdb5f5e2f22872a67e`, package update time 11:05:11. DEX contains boundedWeightedDistance, but neither hpTerritory nor sharedTerritory. Thus the installed package contains the species-search optimization and predates the shared-HP-territory change. This is not live validation of that handoff. Exact full source provenance of this independently installed APK was not established. No installation in this review.

## Observed sequence

Video around 41 seconds: Camerupt used Rock Tomb against Sealeo. Sealeo retains a small red HP sliver through the pause. Around 47 seconds the finishing Incinerate lands; at 48 seconds a complete empty outline and disappearance are visible. Vaporeon enters around 49 seconds.

The video’s final announcement at 99 seconds is “Gourgeist used Seed Bomb!” (the trainer initially recalled Grass Knot). Vaporeon disappears by approximately 101 seconds and rewards follow. Gourgeist also used a Ghost-type charged move earlier. Video time and archive record time differ by roughly 44 seconds; do not treat this approximate alignment as exact frame synchronization.

## What the archive contains

Times below are seconds from MatchRecordStarted, not video time or GO.

- Camerupt Rock Tomb at 84.336.
- Opponent red HP approximately 3.2% after Rock Tomb, through 90.268.
- Full empty outline at 91.259, published 5.211 seconds after capture.
- Sealeo faint at 91.259, basis VERIFIED_RED_TO_EMPTY_HP.
- Independent opponent ball decrease supports Sealeo’s loss at 96.77. These are two supporting conclusions about one loss, not two Pokémon fainting.
- No PLAYER FastMoveUseObserved near 91.259. Neighboring player uses are 76.249 and 95.194. The archive catches the KO but lacks its finishing player attack event.
- Gourgeist active identity is first captured at 123.062, later than its video entrance; stored player attacks at 129.869 and 132.397 still name Camerupt. This is an archived attribution issue; rendered Replay correction was not tested in this review.
- No final Seed Bomb, Vaporeon faint or MatchEnded.

HP publication delays reach 59.580 seconds; late result recovery telemetry reaches about 54 seconds. The earlier single large post-announcement gap was not repeated as a >5-second gap between adjacent sequenced non-availability publications in this archive, but substantial accumulating delay remains. Different battle workloads prevent attributing this comparison solely to the optimization.

## Next diagnostic focus

Keep the finishing Incinerate omission distinct from the correctly detected faint. Investigate why a verified terminal HP transition supplies faint evidence without a corresponding Fast Move use after charge recovery. Independently trace delayed species/event publication, since it carries Camerupt into Gourgeist’s actions and prevents the final sequence from reaching the archive. Do not infer a missing move solely from a faint or fabricate original archive events.

This task is evidence review only: no production changes, tests, phone instrumentation, commits or pushes.
