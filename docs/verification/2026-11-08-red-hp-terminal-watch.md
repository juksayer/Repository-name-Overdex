# Red HP terminal watch — 2026-11-08

The short visible empty HP outline needs its own small measurement path. The
ordinary live reader already attempts every delivered frame, but locating both
bars and recovering a missing bar can delay its next receipt. The general
locator also requires colored fill and cannot measure a fully empty bar.

## Implemented boundary

- A fresh, side-owned ordinary HP measurement supplies geometry, not an empty
  HP conclusion. The independent live subscriber inspects at most two small
  rectangles around that geometry. It does not enter the general locator's
  monitor or retain source images.
- Literal red fill at no more than one quarter HP arms that side's watch. This
  includes the Pixel's pale salmon fill. A previously accepted green/yellow
  anchor may admit the red check even when the ordinary locator rejects the
  pale red frame. Orange delayed-damage fill is excluded from remaining HP.
- One visible empty outline can produce one zero measurement. Both end caps,
  matching upper/lower borders, at least 97% dark interior and no saturated
  interior pixels are required. Unresolved dim red also vetoes zero, even for
  one interior column. Disappearance, occlusion, camera movement,
  flashes and detached particles remain unavailable evidence.
- Each lock expires after 500 ms without verified geometry; each red burst is
  bounded to three seconds before ordinary geometry must seed another watch.
  Species/appearance, faint, Ignore, capture dimensions, Match End and Stop
  invalidate the appropriate state. A green/yellow reading disarms red tracking.
- Source dimensions and original capture wall/monotonic clocks travel through
  the existing HP testimony, bitmap-free hubs, Timeline and archive. A shared
  per-side publication gate prevents duplicates and late ordinary results from
  rewinding newer terminal evidence. Terminal seed freshness does not discard
  an otherwise valid ordinary observation.
- Only explicitly multicast inputs opt into a second collector. One-shot and
  channel inputs retain one collector, with the same terminal measurement
  before ordinary acquisition. Calibration values and capture retention do not
  change.

The independent reader's cumulative `ACTIVE_HP_TERMINAL_WATCH_DIAGNOSTICS`
summaries preserve delivered sequence advances/skips, source gaps, bounded
sampler outcomes, elapsed/thread CPU durations and actual publications. Ignore
transitions reset receipt baselines. Ordinary receipt/location diagnostics
still describe ordinary acquisition; shared sampling, submission and Timeline
recording counters include both paths.

## Validation

The new Android pipeline tests exercise actual Bitmaps through the live witness,
Custody and Timeline, retaining a single zero through JSON and compact archive
export. A separate test blocks the ordinary locator and requires the one-frame
empty observation to reach the Timeline before releasing that locator. Missing,
stale, wrong-side and older frames must not produce an extra zero or fabricated
faint, move, energy or Match Start.

The supplied Pixel examples are independent stills, not a reconstructed video
sequence. Native 1080×2280 and capture-size 672×1419 checks use the same Android
decoder and scaler as the device fixture:

| Source | Expected reading |
| --- | --- |
| `152702-low-player-hp` | Literal red, orange remainder excluded |
| `152312-combusken-fainted` | Visible empty outline, zero |
| `152517-magcargo-empty-hp` | Visible empty outline, zero |
| `153343-flareon-fainting` | Bar missing, unavailable |

Initial recorded-pixel checks exposed neutral olive edge antialiasing falsely
classified as yellow, a short dark prefix before the red fill, and a continuous
orange remainder. The sampler was corrected without relaxing the empty rule;
the supplied-image anchors and expected results were preserved.

Final verification:

- Full `:app:testDebugUnitTest`: **2,161 tests, zero failures/errors/skips**,
  including 24 terminal-policy tests and 20 focused pixel-measurer tests.
- `:app:assembleDebug` and `:app:assembleDebugAndroidTest`: passed.
- Disposable API 34 `Overdex_API34_Check` (`emulator-5582`): **OK (12 tests)**:
  four terminal pipeline tests, four supplied-image tests (both sizes each),
  and four existing HP diagnostic integration tests. The existing Ignore
  regression caught and corrected exclusion of the first resumed frame.
- Native/capture red fractions: `0.18360655` / `0.18421052`. Both visible empty
  outlines measured `0` at both sizes. Disappeared Flareon remained unavailable.
- A regression reproduced a dim `(150,100,90)` sliver becoming a false zero;
  unresolved red now prevents that conclusion. Original image expectations
  remain unchanged.
- Tested debug APK SHA-256:
  `ede136390e57c0efc096e66cf8b3ca8dff19ed63bf88b6e6c8b154156a8eb3be`.
- Replaced the stopped Pixel 4 and Moondrop installations with that exact APK.
  Verified private-file inventories were unchanged before relaunch: **91 Pixel
  files and 411 Moondrop files**, with all backed-up hashes identical. As in the
  existing update workflow, `files/artifacts` was excluded from the backup/hash
  inventory; app replacement did not clear app data or settings.
- Both physical launches resumed `MainActivity` in a stable new process with no
  fatal startup log entries. Capture remained stopped. No instrumentation was
  run on either user phone; this is installation/startup proof, not a live KO.

Final logs are `confirmed-build-and-unit-tests.txt`,
`confirmed-instrumentation.txt`, the four `terminal-hp-*.json` source reports,
and `pre-install/*/{verification,update-provenance,startup-provenance}.json` in
the evidence directory. The disposable emulator was stopped after verification.

## Remaining acceptance

This path checks delivered capture frames; Android and the capture stream can
still discard frames before this reader sees them. Physical live-battle timing
and load acceptance remain pending. The red trigger cannot cover a charged KO
that jumps from green/yellow directly to zero; that needs a separate freshly
located charge-impact watch. A raw zero HP measurement does not itself assert a
faint, identify an attack, add energy, or repair every final-KO/result chain.

Local evidence directory:
`/home/sean/overdex-device-backups/2026-11-08-terminal-hp-watch/`.
