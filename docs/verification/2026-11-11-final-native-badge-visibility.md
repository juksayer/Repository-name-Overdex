# Final Cramorant: native badge visibility review

Source video: `/home/sean/Desktop/Screen_recording_20261111_211335.mp4`.
All times below are **MP4 presentation timestamps**, not archive source clocks.
Decoded with PyAV at native **816×1808**, time base **1/90000**. The recording is
variable cadence (approximately 9.68 frames/sec overall); no interpolation or
fixed-frame-rate timing was used.

## Conclusion

**No decoded frame shows a readable native opponent count of zero.** The last
remaining red Poké Ball stays visible while both native Team Info Badges fade.
The native badge is gone before the clearly recognizable Cramorant faint
animation finishes. A later crisp opponent badge is consistent with Overdex's
cached bitmap presentation and is excluded from independent count evidence.

Bounded count reacquisition can still collect the last positive count or fail
honestly as unavailable, but this recording does not demonstrate any zero-count
frame that it could recover. Do not treat a fully faded/absent badge, a cached
badge, or absence of red pixels during alpha fade as a measured zero.

## Decoded sequence

| Video seconds | PTS | Observation |
| ---: | ---: | --- |
| 178.299278 | 16046935 | Green opponent HP; native opponent badge has two faded balls and one red ball. |
| 178.553633 | 16069827 | Orange-only charged-damage trail; native badge still has one red ball. |
| 179.379700 | 16144173 | Last fully opaque native badge/HP frame in this fade sequence; one red ball. |
| 179.549744 | 16159477 | Both native badges, HP, and battle controls are fading together. Remaining opponent ball is still red. Camera pulls back. |
| 179.708056 | 16173725 | Native badge is translucent but still shows one red ball. Opponent avatar visibly holds a red Poké Ball, arm beginning to extend. |
| 179.858456 | 16187261 | Native badge/HP very faint. Last ball remains red-tinted; this is not three independently faded ball slots. Avatar's arm is extended with ball. |
| 180.023400 | 16202106 | Both native badges and HP bars absent; Cramorant remains on battlefield. Overdex sprite row persists, excluded from evidence. |
| 180.108822 | 16209794 | Crisp opponent-only badge reappears above Overdex sprite row, while native player badge remains absent; excluded as retained presentation. |
| 180.447456 | 16240271 | Cramorant still visible, with no native HP or badge available. |
| 180.551367 | 16249623 | First decoded frame in this inspected transition with conspicuous white faint particles around Cramorant. |
| 180.723156 | 16265084 | Cramorant collapses/shrinks amid particles. |
| 181.091478 | 16298233 | Last small blue/yellow sprite fragments visible below bright collapse flash. |
| 181.147356 | 16303262 | Sprite no longer identifiable; only flash/bubble/sparkles remain. |

The final sampled native-badge transition spans **164.944 ms** between
179.858456 and 180.023400. An unsampled sub-frame zero cannot be excluded; there
is no observed zero duration to report. Apparent shrinking during 179.55–180.02
includes a camera retreat and must not alone be labeled the faint collapse.
The native badge/HP disappear roughly **0.528 seconds before** the first
conspicuous faint particles, and **1.124 seconds before** the sprite is fully
unidentifiable. An arm/ball cue at 179.708 starts before native UI disappears,
but its visible badge still has count one.

## Presentation provenance

`RetainedOpponentBadge` caches source badge pixels including the resource balls,
then renders them in a TYPE_APPLICATION_OVERLAY. `RetainedBadgeVisibility`
waits 100 ms after both native badges disappear before showing the cached opponent
badge; return of the native player badge hides it. This mechanism explains the
opponent-only reappearance. No render invocation timestamp is retained here, so
it is excluded as potentially retained presentation rather than asserted as a
new native observation.

Existing safeguards exclude that region from crop recognition while shown and
for 250 ms after removal. Source references:

- `app/src/main/java/com/example/overdex/battle/observation/RetainedOpponentBadge.kt`
- `app/src/main/java/com/example/overdex/battle/observation/RetainedBadgeImage.kt`
- `app/src/main/java/com/example/overdex/battle/observation/RetainedBadgeVisibility.kt`
- `app/src/main/java/com/example/overdex/battle/observation/CropCaptureWitness.kt`
- `app/src/main/java/com/example/overdex/battle/observation/TeamBadgeCropLocator.kt`

## Preserved analysis frames and limits

39 full native-resolution frames from 178.299278 through 182.106856 are saved in:
`/home/sean/overdex-device-backups/2026-11-11-moondrop-2113-review/native-final-badge-frames/`.
`durable-provenance.json` maps the original extraction paths to verified identical
PNGs in this backup; the original `frames.json` is preserved byte-exact.
`frames.json` contains original PTS/timebase, exact timestamps, PNG hashes and
pixel probes. PNGs are decoded full frames with no resizing, cropping, overlays,
or annotations added by this review. Probes at the native badge coordinates
are descriptive pixels only; they are not validated count decisions and include
cached-overlay pixels after native UI disappearance.

The hand/ball and disappearance sequence is a manual review finding, not an
implemented automatic faint classifier. See the [original archive review](2026-11-11-moondrop-2113-review.md).

This review does not create or revise a faint, HP, result, or count Article.
It changes no production code or phone state.
