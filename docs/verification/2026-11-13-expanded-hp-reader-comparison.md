# Expanded HP territory: lethal and nonlethal recorded sequences

## Result

The expanded Pixel charged-impact box repairs a demonstrated geometry failure, but the current production color reader does **not** reliably distinguish the lethal and nonlethal sequences in this paired match. No selected lethal window produces `SUSTAINED_DAMAGE_COLOR`. This is a negative acceptance result, not a reason to weaken whole-bar geometry or label missing pixels as empty.

In the manually reviewed recipient-camera interval of the final Pixel Shadow Ball, fresh acquisition finds **0 complete bars with the old boundary and 8 with the expanded boundary**. Starting tracking inside that same recipient interval also recovers 8 with the expanded box. Those complete bars remain nonterminal under the current color rule. The other phone's view produces one orange-only frame but no sustained candidate.

## Inputs and reproducibility

Artifacts, extracted frames, test logs, source hashes, per-frame outputs, and scripts:

`/mnt/overdex-backup/archives/overdex-device-backups/2026-11-13-dual-reader-comparison/`

Source recordings and archive provenance are documented in [the paired-match review](2026-11-13-dual-view-controlled-match.md). Sources remain immutable. No new build was installed and no production reader, threshold, or calibration was changed during this test. The previous turn's authorized Pixel calibration adjustment remains in place.

`extract_sequences.py` selects actual decoded frames at up to 20 Hz, retaining video presentation timestamps and never interpolating frames. The input contains 1,284 frames across 14 windows: six lethal views of four distinct KOs, seven nonlethal views/windows, and one voluntary-switch control. Paired phone views are correlated evidence, not independent battles.

Frames are resized with bilinear resampling to the archived capture dimensions, Pixel 672×1419 and Moondrop 672×1493. The recordings have separate encoding and slightly different aspect ratios from capture; these are playback experiments, not bit-identical reproductions of Android MediaProjection frames. The exact archived clipped crop independently establishes the original boundary defect. No frame contrast or color filtering was applied.

`RecordedDualViewHpReaderStudyTest` invokes the production Kotlin implementations of `ChargedImpactHpBarMeasurer`, `TrackedTerminalHpBarMeasurer`, `CompleteHpBarTrack`, and, for ordinary-camera windows, `BattlefieldHpTracker`. It does not substitute the earlier research-script classifier. `source-manifest.json` fingerprints the dirty-tree production sources used.

The harness supplies the manually identified recipient, the archived normal/impact territories, and a 192×18 reference-bar size consistent with the archive. It tests the reader components, not the full capture scheduler, delayed identity pipeline, faint inference, or Replay. It does not manufacture a faint or a finishing-move article.

## Comparisons

1. Original and expanded Pixel territories. Only the impact box's bottom changes, y=887 to y=951. Moondrop uses its existing territory in both branches as an unchanged control.
2. Fresh acquisition on each frame versus acquiring once and following the resulting anchor. Whole-outline and cap requirements remain in both paths.
3. Ordinary-camera acquisition followed by complete-bar tracking, including fast-hit and switch controls.
4. A second charged-camera pass restricted to manually annotated recipient-camera intervals. This prevents early acquisition of the attacker's centered bar from masquerading as a color-classifier failure. It is an analysis control, not an implemented phase selector.

The additional recipient pass uses 185 frames already present in the main set. It adds no independent observations. Its per-frame data and source-time bounds are in `recipient-only/`.

## Recipient-camera results

Counts below use fresh acquisition. All sustained-candidate counts are zero. Tracking after acquisition reaches the same qualitative conclusion, including the final Pixel 0-to-8 boundary improvement.

| Receiving Pokémon and event | Outcome | Phone view | Complete bars, old → expanded | Orange-only frames, expanded |
|---|---|---|---:|---:|
| Turtonator, Dragon Claw | Survives | Pixel | 7 → 7 | 0 |
| Turtonator, Dragon Claw | Survives | Moondrop | 17 → 17 | 0 |
| Pixel Camerupt, Rock Tomb | KO | Pixel | 6 → 6 | 0 |
| Pixel Camerupt, Rock Tomb | KO | Moondrop | 15 → 15 | 0 |
| Moondrop Camerupt, Seed Bomb | Survives | Pixel | 0 → 0 | 0 |
| Moondrop Camerupt, Seed Bomb | Survives | Moondrop | 11 → 11 | 0 |
| Moondrop Gourgeist, Shadow Ball | KO | Pixel | 0 → 8 | 0 |
| Moondrop Gourgeist, Shadow Ball | KO | Moondrop | 22 → 22 | 1 |

The Pixel Seed Bomb recipient view remains **unmeasurable**, not a correctly classified negative. Green attacker frames earlier in the broader window must not hide this gap. Likewise, a missing color candidate on a KO is a miss, not evidence that the move was nonlethal.

The fast-KO windows produce complete geometry followed by unresolved colors. In the Camerupt fast-KO window, the existing empty-outline path also finds seven `VISIBLE_EMPTY` frames. That separate positive evidence should not be conflated with success of the new sustained-color path, and does not establish that the live scheduler captured those same frames.

## Located color failure

`CompleteHpBarProfile.measure()` checks `uncertainColor` across the full interior, including the unfilled tail. A single uncertain pixel anywhere vetoes `orangeOnly`. This is distinct from the requirement that the entire rectangle be measured.

Examples from complete, production-accepted outlines:

- Pixel Turtonator fast KO, video 78.402311 s: zero live-color columns, eight damage-color columns, but 718 uncertain pixels. Many lie in the dark blue unfilled portion; representative RGB values are (28,47,81) and (29,48,82).
- Pixel Camerupt charged KO, 101.804122 s: zero live-color columns and 106 damage-color columns, but 87 uncertain pixels, with brown values such as (114,68,53) in the unfilled part.
- Pixel Gourgeist final KO, 152.329167 s: zero live-color columns and 127 damage-color columns, but 135 uncertain pixels. This frame also contains colored pointer trails crossing the bar. Uncertainty is not confined to the unfilled tail in every case.

`color-veto-explanations.json` records coordinates and representative colors. Enlarged diagnostic crops retain the full outline. `explain_color_veto.py` explains the existing predicate from the measured pixels; it is not a replacement classifier and does not change the Kotlin results.

This establishes a concrete mismatch between “remaining HP changes color” and “all other pixels anywhere in the bar must also be unambiguous.” It does not justify ignoring every dark or isolated colored pixel: a tiny surviving live remainder must continue to contradict a terminal candidate.

## Regression coverage and acceptance boundary

The opt-in study verifies timestamp ordering, complete sample bounds inside the available territory, and that orange-only evidence does not become an empty-HP measurement. Existing synthetic and recorded controls cover intact rails/caps, clipped bars, tiny surviving HP, and complete-bar temporal state.

A small, provenance-recorded fixture from Pixel video 152.329167 s adds two persistent boundary regressions: the old bottom cannot accept the clipped outline; an expanded territory can recover the whole outline without inventing empty HP. The fixture is a cropped, resampled video frame, explicitly identified as such.

Validation completed: **50 focused tests passed**, zero failures/errors/skips,
including the new boundary regressions and the Pixel charged-window study.
The Moondrop, normal-camera, and recipient-only diagnostic batches also completed
successfully. The final main dataset contains 4,018 measurement rows; all 3,835
complete rows retained from the interrupted first run match the completed reruns
exactly. Production source hashes are unchanged. No full app build or device
installation was needed because this slice changes tests, a small fixture, and
documentation only. Passing test assertions establishes the measurement and
geometry invariants, not successful KO classification.

The initial combined diagnostic process ended before all rows were written; its partial output is not the completed dataset. Completed smaller batches and the recipient-camera control provide the final results. Reproduction uses the external fixture directory, for example:

```sh
OVERDEX_DUAL_HP_STUDY=/mnt/overdex-backup/archives/overdex-device-backups/2026-11-13-dual-reader-comparison/recipient-only \
JAVA_HOME="$PWD/.jbr" ./gradlew :app:testDebugUnitTest --rerun --tests '*RecordedDualViewHpReaderStudyTest'
```

Use `--rerun` when changing the external directory: the opt-in environment variable is not a declared Gradle test input. With no variable, this large external-data study skips; the small bundled boundary regressions still run normally.

## Recommended next slice

Keep both rails and both caps mandatory. Preserve the distinction between live fill, damage-colored fill, unfilled background, and uncertain pixels, including where uncertainty lies. Test that a complete bar's unfilled background cannot by itself erase an otherwise measured fill-color transition, while tiny live remainders and uncertainty inside the remaining fill retain their veto. Keep pointer trails, shadow effects, camera changes, and the unmeasurable Seed Bomb view as explicit controls.

Do not enable a new faint rule from these results. The present color predicate fails this match's lethal acceptance cases; geometry expansion alone is insufficient. A subsequent fix must pass these cases and the earlier near-lethal Sealeo controls before revisiting downstream KO attribution.
