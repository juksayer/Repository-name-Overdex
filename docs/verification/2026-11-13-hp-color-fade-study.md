# Complete-bar color and fade experiment — November 13, 2026

Offline recorded-pixel study. No production recognition, archive, installed build, or source recording was changed. This is a candidate-signal experiment, not an implemented faint detector or a demonstrated repair of finishing-move attribution.

## Result

Across five continuous recordings plus retained native frames of the earlier Cramorant KO:

| Independently reviewed outcome | Windows | Sustained complete-bar orange-only candidate | Unknown complete geometry |
| --- | ---: | ---: | ---: |
| KO sequence | 13 | 12 | 1 |
| Nonlethal comparison | 12 | 0 | 0 |

All nine explored hue/brightness combinations retained these totals. These are selected event windows, not independent matches or a population accuracy estimate. A window can contain several Fast Moves. Color, fade, and departure were reviewed together, but only complete geometry and the sustained color candidate were automatically scored. An automatic fade/departure classifier was NOT evaluated.

The nonlethal Sealeo Rock Tomb is the critical near miss: almost the entire prior fill becomes orange, but a tiny surviving red sliver remains. One-frame color classification briefly missed that sliver (longest candidate run 25.789 ms). Requiring repeated source frames prevented this false terminal candidate. Early exploratory palettes also briefly misclassified low-HP comparison frames; the final contiguous-prefix check rejected those fragments.

The unmatched KO is the Pixel Vaporeon charged finish at 97–101 video seconds: this locator found no confidently complete target outline. That is unknown, not an empty bar or a negative KO. A flying Golbat comparison initially fell above the search band; expanding its upper boundary from 28% to 18% of frame height recovered 80 complete target measurements, with no terminal candidate. This geometry adjustment is recorded explicitly.

## Sources and labels

Sources are listed with current SHA-256 hashes in [source-manifest.json](/mnt/overdex-backup/archives/overdex-device-backups/2026-11-13-hp-color-fade-study/source-manifest.json). Five videos are read at native resolution and original presentation timestamps. Cramorant uses 39 preserved lossless native frames; its original MP4 is absent at the old Desktop location. Only 13 of those frames satisfy the strict normal complete-outline locator. The older provenance file remains beside those frames.

Labels were assigned from reviewed continued play, replacement/departure/result context and trainer testimony, not from the color test. Side and camera phase were selected manually for this experiment. Player-recipient windows exclude the attacker's camera view. No archive capture-clock alignment or audio-based killer assignment was performed. Report times are seconds within each source video, not Replay time.

Sources: Pixel recordings 20261112_111756, 112931 and 113834; Moondrop recordings 2026-11-12_15-53-26-vCy2v5 and 16-05-21-wzGrCr; retained November 11 Cramorant frames. The Pixel recordings have no audio track. The two Moondrop recordings contain audio, but this experiment measures visual evidence only.

## Complete-bar measurement and temporal test

1. Decode native RGB frames. Find a white rectangular outline with plausible bar geometry. Require both horizontal rails and both end caps, each with at least 90% outline coverage. Partial or occluded geometry is unmeasurable.
2. Measure the full interior in that same frame. For each column, take median RGB over interior rows, then HSV. Keep live-colored fill, orange damage-colored fill and uncolored interior separate; do not call orange fill zero HP.
3. An exploratory color candidate requires no live-colored columns and at least three orange columns. Orange must begin within three interior columns of the left edge and cover at least 90% of its first-to-last-column span. This rejects isolated orange background pixels in the nominally empty portion.
4. Require at least three consecutive qualified frames spanning at least 100 ms, with no interframe gap over 150 ms. Missing/invalid geometry or a contradictory frame breaks the run.
5. Treat the result as pending terminal evidence. Track subsequent fade/departure separately. Do not synthesize a zero reading, a damage amount, a faint timestamp, or a killing move from color alone.

Exploratory HSV ranges use OpenCV hue 0–179: orange lower bound 8 (also swept 10/12), upper bound 24 exclusive; saturation at least 100; brightness at least 160 (also swept 140/180). Live colors are hue below the orange lower bound, 24–94, or at least 174, with the same saturation/brightness minimum. Thresholds were explored on this selected footage, not validated on a blind device-independent holdout. The file name `holdout_vaporeon_fast_ko` is a retained research label, not a claim of blinded validation.

## The crop-fragment counterexample

At Sealeo video time approximately 41.299 s, the complete 235 × 24 bar contains four live-colored columns and 137 orange columns. Three deliberate fragments of this SAME frame appear to contain: surviving HP, orange-only damage, and no colored fill. All three fail the end-cap requirement; the complete bar passes. Assertions in `illustrate.py` check this result.

[Clipped-bar comparison](/mnt/overdex-backup/archives/overdex-device-backups/2026-11-13-hp-color-fade-study/clipped-bar-comparison.png) · [Measured coordinates and end-cap checks](/mnt/overdex-backup/archives/overdex-device-backups/2026-11-13-hp-color-fade-study/clipping-checks.json)

The production implication is to locate one complete bar in shared, same-frame capture territory. Never reconcile separately computed crop percentages, stitch different moments, or let either partial crop vote for empty HP. Missing geometry means unknown and may justify a bounded wider search.

## Fade evidence and timing limits

Cramorant is a clear positive sequence: green fill at 178.299 s becomes entirely orange; twelve complete-outline frames from 178.375 to 179.380 s contain no live-colored remainder. At 179.550 s the same fill extent is still present while the bar fades. Measured fill brightness drops from 247 to 113 and rail brightness from 203 to 107, approximately halving together. This is a fade, not an observed final damage tick or empty outline. Relaxed outline brightness was used only to inspect the already identified fading bar; it was not admitted as a normal HP or zero measurement.

[Side-by-side nonlethal and KO sequence](/mnt/overdex-backup/archives/overdex-device-backups/2026-11-13-hp-color-fade-study/color-sequence-comparison.png) · [Fade measurements](/mnt/overdex-backup/archives/overdex-device-backups/2026-11-13-hp-color-fade-study/cramorant-fade.json)

The Moondrop Camerupt final Rock Tomb is ONE event spanning 217–237 s. Orange-only fill starts at 220.706 s and persists through 233.841 s; departure follows around 234–236 s. Early inspection mistakenly split the earlier impact into a nonlethal window and the later departure into a different KO. Reviewing continuous context corrected this: both partial windows are excluded from totals, with the original research names retained. The long hold's cause is not diagnosed by this study.

Shield/charged sequences also show terminal-looking color before the queued sequence finishes. Therefore this cue must not immediately disable the actor, suppress supported attacks, or assign a faint to the most recently displayed charged move. A later independently identified live remainder contradicts the terminal candidate. Move ownership and pending damage resolution remain separate questions.

Ordinary switch context was reviewed, but the combined-side switch window is excluded from quantitative results because this evaluator requires a selected recipient. Concessions, all weather/effects, shadow occlusion, general device calibration, and autonomous actor tracking are not established by these results.

## Per-window measurements

`Complete frames` counts accepted target geometry, not all decoded frames. `Longest` is the longest orange-only run even when it falls below the persistence threshold.

| Window | Label | Complete frames | Sustained candidate | Longest (s) |
| --- | --- | ---: | --- | ---: |
| challenge_opponent_lowhp_fast | nonlethal | 134 | no | 0 |
| challenge_player_lowhp_fast | nonlethal | 176 | no | 0 |
| challenge_vaporeon_lowhp_fast | nonlethal | 94 | no | 0 |
| cramorant_retained_ko | KO | 13 | yes | 1.005022 |
| expanded_golbat_nonlethal_fast | nonlethal | 80 | no | 0 |
| holdout_vaporeon_fast_ko | KO | 87 | yes | 0.787611 |
| m_abomasnow_ko | KO | 110 | yes | 0.917567 |
| m_camerupt_charge_ko_full | KO | 345 | yes | 2.4012 |
| m_camerupt_nonlethal_charge1 | nonlethal | 92 | no | 0 |
| m_golbat_after_shield_ko | KO | 58 | yes | 1.747344 |
| m_gourgeist_fast_ko | KO | 92 | yes | 0.876067 |
| m_gourgeist_nonlethal_charge | nonlethal | 95 | no | 0 |
| m_player_turtonator_charge_ko | KO | 74 | yes | 0.812689 |
| m_player_turtonator_ko2 | KO | 68 | yes | 0.5436 |
| m_shield_nonlethal | nonlethal | 59 | no | 0 |
| m_swampert_final_ko | KO | 49 | yes | 1.182756 |
| m_turtonator_charge_ko | KO | 84 | yes | 0.921833 |
| m_turtonator_nonlethal_charge | nonlethal | 40 | no | 0 |
| p_gourgeist_nonlethal_charge | nonlethal | 48 | no | 0 |
| p_sealeo_fast_ko | KO | 84 | yes | 0.448811 |
| p_sealeo_nonlethal_charge | nonlethal | 88 | no | 0.025789 |
| p_sealeo_nonlethal_fast | nonlethal | 136 | no | 0 |
| p_vaporeon_charge_ko | KO | 0 | unknown | 0 |
| p_vaporeon_nonlethal_charge | nonlethal | 112 | no | 0 |
| shield_sneasel_ko | KO | 126 | yes | 0.762 |

## Reproduction and artifacts

All media and generated measurements reside on the 1 TB drive at `/mnt/overdex-backup/archives/overdex-device-backups/2026-11-13-hp-color-fade-study`. Source media are immutable. `probe.py` defines event windows and the native outline/color locator; `expanded_band_check.py` records the flying-bar adjustment; `summarize.py` defines manual target-side selection; `evaluate.py` computes candidate runs and the nine-combination sweep; `illustrate.py` checks clipping and prepares the native-bar figures. `overview.py` and `contexts.py` create visual context for manual outcome/phase review. Measurement JSON comes from native decoded pixels, not JPEG context sheets.

The existing research runtime can reproduce evaluation with:

```sh
PYTHONPATH=/tmp/overdex-video-review-python:/tmp/overdex-terminal-motion-python python3 /mnt/overdex-backup/archives/overdex-device-backups/2026-11-13-hp-color-fade-study/evaluate.py
PYTHONPATH=/tmp/overdex-video-review-python:/tmp/overdex-terminal-motion-python python3 /mnt/overdex-backup/archives/overdex-device-backups/2026-11-13-hp-color-fade-study/illustrate.py
```

The temporary runtime paths may need reconstruction later; exact versions and script hashes are in the manifest. Re-running `probe.py` rebuilds video measurements; `probe_retained.py` reproduces retained Cramorant measurements and verifies their original hashes. Re-run `expanded_band_check.py` after the ordinary probe. Existing native measurements permit evaluation without decoding again.

Machine-readable outputs: [event-results.json](/mnt/overdex-backup/archives/overdex-device-backups/2026-11-13-hp-color-fade-study/event-results.json), [sensitivity.json](/mnt/overdex-backup/archives/overdex-device-backups/2026-11-13-hp-color-fade-study/sensitivity.json), and [evaluation.log](/mnt/overdex-backup/archives/overdex-device-backups/2026-11-13-hp-color-fade-study/evaluation.log). Final count assertions and source hashing are in `finalize.py`.

## Recommended next small slice

Preserve a complete-bar observation with geometry, side/appearance, source time, live-fill extent, damage-colored extent and confidence. Add the sustained terminal-color candidate separately from the existing empty-outline path. Retain it through a charge/shield sequence and test independent fade/departure confirmation, contradictions and ordinary switches before enabling a new faint inference. Do not manufacture a finishing hit: its sight/sound evidence still must be associated with the same recipient and source-time interval.

This experiment supports investing in that narrow implementation. It does not yet support shipping color alone as a reliable KO decision. No app unit suite or debug build was rerun because this slice changed research artifacts and documentation only.
