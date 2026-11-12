# HUD latency and move-label readability investigation

Research and design preview only. No production code, installed APK, or device service changed during the trainer's current test.

## Vaporeon evidence

Read the three already-exported Pixel timelines; no device access was needed. Extracted article IDs and measurements are saved at `/mnt/overdex-backup/archives/overdex-device-backups/2026-11-12-hud-latency-review/vaporeon-latency.json`.

| Recording | Selected badge capture to OCR admission | Native OCR | Whole selected-crop recognition | Badge confirmation to delivery callback |
| --- | ---: | ---: | ---: | ---: |
| 11:17 | 7.894 s | 0.569 s | 8.596 s | 14.115 s |
| 11:29 | 0.435 s | 0.744 s | 1.360 s | 0.320 s |
| 11:38 | 1.078 s | 0.928 s | 2.235 s | 0.822 s |

These are three individual samples, not benchmark distributions. Recognition time contains admission time; do not add those columns together. Confirmation and delivery use the diagnostic emission time (`perceivedAt`), not when those diagnostics eventually reached the ledger. A delivery callback measures the presentation update path, not the first rendered screen frame. An independent entry-announcement identity can precede badge delivery, as in 11:38. The archive does not prove that the badge callback was Vaporeon's first visible HUD frame.

In 11:17, entry announcement source time was 93.428 s, the selected badge frame was 104.629 s, confirmation was emitted at 113.151 s, and delivery at 127.266 s, relative to record start. The identity's article creation time was 126.305 s. This supports a major pre-OCR and publication/presentation backlog; it does not isolate all CPU time to a particular inference function. Exact recollection of which match prompted the trainer's report remains unconfirmed.

## Current path and constraints

- `LiveOverlaySpeciesPipeline` already has latest-only one-item live/preserved crop queues, OCR warm-up, an urgent OCR turn, stale-window checks, and immediate catalogue-supported acceptance. Simply adding those again would not solve the problem.
- The preservation worker saves the PNG, submits `CropCaptured`, and waits for the corresponding `match.articles` publication before admitting pixels to recognition. The crop therefore waits behind shared testimony processing.
- Accepted species is submitted to the same custody stream. `Match` consumes that stream serially, creates/appends the raw article, publishes it, updates presentation, then runs several inline identity/charge/fast-use/energy/cadence/end interpretations before taking the next record.
- `DroidballOverlayPresentation` supplies StateFlows directly to Compose. There is no obvious intentional species debounce or animation wait in this path. Reference move-pool lookup precedes `recordOpponentSpecies`; measure/cache that work rather than assume it is free.
- `SpeciesCheckCoordinator.acceptImmediate` already supports one strong badge read. Its 1.5-second target is diagnostic, not a minimum waiting period. Lowering it is not a latency fix.
- Preserve source capture clocks, appearance ownership, raw crop lineage and archival completeness. A faster live path must not allow old identities to overwrite newer appearances, revive a fainted combatant, or reopen a finished HUD.

## Approaches, in recommended order

1. **Lightweight ledger publication, ordered inference worker.** Keep custody and immutable article creation authoritative; publish raw articles promptly and let a separate ordered consumer perform expensive derivations. This removes both crop-publication and identity-publication waits caused by previous inference. Keep explicit raw-publication and inference-completion barriers so final archives still drain all required work. Instrument each phase before refactoring; avoid parallel mutation of shared interpreters. This is the best first experiment, not yet an implemented improvement.
2. **A bounded live presentation path for accepted identity.** Once evidence is preserved and an identity is accepted, update a source-time/appearance-aware live model without waiting for unrelated inference. Preserve the exact same testimony for Replay. Potentially the shortest visible latency, but highest consistency risk: duplicates, later corrections, regional forms, faint resolution and match end must use the same rules. Do not show merely guessed identities as confirmed.
3. **Reduce and prioritize work before recognition.** Coalesce unchanged captures before PNG writes, prioritize unresolved new appearances over rechecks, cache reference lookups, and measure preservation/frame-copy/native-recognition costs separately. Existing urgent scheduling and latest-only OCR must remain. Additional recognizers or threads are an experiment requiring phone measurements, not an assumed speedup on Pixel 4. This cannot alone remove the measured post-acceptance delay.

Next small implementation slice: add capture/preserve/custody/ledger/identity/presentation timestamps and processing-duration diagnostics, then separate one measured blocking stage. Validate delayed/reordered identities, repeated species returning, faint corrections, old entry cues, result closure, and archive drain. Compare new-appearance median and upper-tail time plus late/dropped work and fast-move coverage; do not trade those for a quick-looking HUD.

## Readability

Current `BattleHudMoveText` uses 8 sp monospace, 10 sp line height, a .65 dp white stroke and a type-colored offset shadow. Move lists sit outside the white roster badge backing. `OpponentHudStatus` separately uses a white shadow. At this scale outlines, varying battlefield backgrounds and long labels compete with letter interiors.

Three enlarged, illustrative previews were prepared: white text on dark caption strips; dark text in a pale, double-bordered game-style window; and clean purple text on a solid pale badge. These are browser concepts, not measured Android screenshots or exact phone-scale typography. They retain confirmed-versus-possible wording and textual estimated readiness instead of color alone. No proprietary font was added. Start with solid backing and no shadow; test physical size before choosing a pixel-style font or expanding every row.

OCR coupling matters: `OcrSourceFilter` uses capture-time overlap, word size and purple/teal/red ink under its default policy. White caption text or enlarged labels can defeat that combination. Before shipping any preview, test actual rendered crops against the source filter and ensure overlay geometry/exclusion keeps our words out of game testimony. Preserve the existing charged-text yielding and calibrated badge seam. A style change is not complete on the basis of browser readability alone.
