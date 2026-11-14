# Conservative HP background mask

## Scope and implementation

The whole-bar reader now separates eligible background uncertainty from unresolved fill color. It still locates both rails and both caps before evaluating color. The original pixels, geometry, live-color column count, damage-color column count, and ordinary HP measurement are unchanged.

`CompleteHpBarProfile` excludes a column's uncertain color from the orange-only test only when:

- a coherent orange prefix has already been measured;
- no recognized live-color pixel occurs anywhere in the measured interior;
- the uncertain column is strictly after the last orange-fill column;
- its uncertain pixels are dim and outside the possible live-HP hues;
- the column contains no protected bright saturated color or bright uncertainty.

Uncertainty before or inside the remaining fill, bright effects, and dim possible red/yellow/green HP remain vetoes. No erosion, minimum live-sliver size, color replacement, contrast reduction, or new geometry threshold is introduced. The mask is a derived color-test exclusion, not an opaque paint operation and not observed empty HP. Existing color thresholds remain in force; this is not proof that every possible dim or compressed HP color is recognized.

Two bounded per-column arrays record uncertainty and protection. The published complete-bar measurement and its archive payload include `backgroundMaskedColumns` and `unmaskedUncertainColumns`. Old archives deserialize with zero masked columns and unknown unresolved-column count. Source clocks, evidence references, existing HP amounts and archive entries are retained. No new faint or finishing-move inference is enabled.

## Recorded comparison

Outputs and reproducibility scripts are on the 1 TB drive:

`/mnt/overdex-backup/archives/overdex-device-backups/2026-11-14-hp-background-mask/`

The first three batches reuse the identical immutable 1,284 frames from the [expanded-box experiment](2026-11-13-expanded-hp-reader-comparison.md): six lethal views of four KOs, seven nonlethal views/windows, and one voluntary switch. Original and expanded territories, fresh acquisition, tracking, source timestamps, and reader components are the same. Phone views and multiple analysis paths are correlated tests, not independent matches.

Across all 4,018 primary measurement rows, `compare.py` verifies identical geometry, source timestamps, live/damage column counts, and HP measurements before and after the mask. Every sample with recognized live HP still vetoes terminal color and performs no background masking.

| Primary batch | Measurement rows | Rows with background exclusions | Sustained terminal-color result |
|---|---:|---:|---|
| Pixel charged-camera windows | 1,680 | 7 | No new candidates |
| Pixel ordinary-camera windows | 1,118 | 48 | No new candidates |
| Moondrop charged-camera windows | 1,220 | 12 | No new candidates |

The mask removes a demonstrated source of uncertainty, but **does not recover a sustained lethal-color sequence in this controlled paired match**. Nonlethal and ordinary-switch windows also produce no sustained terminal-color candidates. This is a negative result for reliable lethal/nonlethal discrimination, not a completed KO solution. The preexisting seven visible-empty samples in the Camerupt fast-KO window remain separate evidence.

The 740-row recipient-only rerun agrees: no new sustained candidates, and the final Pixel Shadow Ball still changes from zero complete recipient bars with the old crop to eight with the expanded crop. The Pixel Seed Bomb recipient interval still has no measurable complete bar; this is unknown, not a successful nonlethal classification.

The extra Sealeo controls include 160 frames (480 measurement rows). Near-lethal charged-move acquisition finds nine complete bars; tracking finds thirteen. The ordinary fast-hit window finds 71 complete bars. All complete samples remain `LIVE_COLOR`, with no masked columns, orange-only candidate, or empty-HP measurement. The two original near-lethal Sealeo fixtures and the earlier Cramorant orange-remainder fixture also remain passing persistent regressions.

### Remaining ambiguity

At Pixel video 78.402311 s, the Turtonator KO bar has eight orange columns and zero recognized live columns. The mask excludes 143 dim blue tail columns, leaving two unresolved columns near the start of the fill. One includes RGB (165,131,119), hue about 15.7 degrees: it could be edge blending, but it is also within the protected possible-live-color range. The reader retains the veto. A small, provenance-recorded crop from this resampled frame now tests these exact counts and the refusal to invent zero HP.

The Camerupt charged-KO tail includes dim reddish/brown pixels that remain protected. The final Gourgeist bar also has interference within its fill, where this mask cannot remove uncertainty. Widening the crop still recovers the full bar; masking the tail alone does not resolve these fill-color ambiguities.

## Reproduction and acceptance boundary

Run each external-data batch separately; always force test rerun when changing the fixture directory:

```sh
OVERDEX_DUAL_HP_STUDY=/mnt/overdex-backup/archives/overdex-device-backups/2026-11-14-hp-background-mask/pixel-normal \
JAVA_HOME="$PWD/.jbr" ./gradlew :app:testDebugUnitTest --rerun --tests '*RecordedDualViewHpReaderStudyTest'
```

Other batch directories are `pixel-charge`, `moondrop-charge`, `recipient-only`, and `sealeo-controls`. With no environment variable, the large external study skips; small bundled regressions run normally. The recipient-only pass reuses 185 frames from the primary set. The additional Sealeo controls use actual frames from the older recording, retaining presentation timestamps and resampling to the same analysis dimensions; they are not a claim about that older match's installed calibration.

These tests exercise the production reader components on video-derived pixels with manually assigned recipient windows. They do not prove capture-scheduler coverage, live-device accuracy, faint attribution, or faithful finishing-move Replay. Source videos and archives remain unchanged. Any further discrimination between edge blending and tiny surviving HP needs separate temporal/source-pixel evidence; this result does not justify simply relaxing the remaining color veto.

## Verification

- Initial focused run: 102 tests passed. The added recorded Turtonator regression and masked-color-to-tiny-survivor transition test subsequently passed with the recipient-only batch and in the full suite.
- All five external recording batches completed successfully, producing 5,238 measurement rows. Before/after comparison across the 4,758 primary plus recipient-only rows verifies unchanged status, geometry, source timestamps, live/damage counts, and HP amounts. The 480 additional Sealeo rows contain no orange-only or empty-HP samples.
- Full unit suite: 2,987 tests, **2,982 passed**, five optional diagnostics skipped, zero failures/errors. The externally supplied HP recording diagnostic was run separately above; it skips in an ordinary full-suite run.
- `:app:assembleDebug` passed. APK SHA-256: `14633f3625cadb72f05594a0db137b8e2f70cdad919d7eb20981f6f372fdc514`.
- `git diff --check` passed. No commit was made. Preexisting unrelated work remains intact.

Logs, per-frame tables, comparison script/results, source hashes, fixture provenance, test reports, and the built APK are retained in the external artifact directory. The persistent tests cover one-pixel red/yellow/green survivors, dim possible HP, bright interference, uncertain fill pixels, missing caps, incoherent orange, source/archive round-trip compatibility, and cancellation of a pending color candidate when live HP reappears.

## Device installation

Installed the verified APK on Pixel 4 (`99261FFAZ0017J`) and Moondrop (`MDPH00124112500414`) under the standing feature-install routine. Both had no active MediaProjection session or Overdex service before installation. Previous APKs were backed up to the 1 TB drive; replacement preserved app data. Both installed APK hashes match the build above, and both launch commands returned `Status: ok` with a running app process. `install-results.json` and per-device logs retain the checks. No new live battle was performed; launch success is not recognition acceptance.
