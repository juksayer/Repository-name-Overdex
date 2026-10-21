# October 15 review of the late October 14 battle

## Source and attribution

Reviewed `2026-10-14_23-35-55_fa89d58b-3d65-4cfa-979b-2ffe17656808.odxmatch.zip`
from the Moondrop MD_PH_001 (`MDPH00124112500414`). It is the only new internal
archive since the previous review. The 10,340,600-byte archive contains 2,820
articles and 909 declared artifacts; structure and all artifact hashes pass.
SHA-256: `83d9961f623e750c9b1b291e06f0fad1eab3a9abe9ac6352a5c0f661c5e1f5eb`.
At collection time, the installed APK hash matched the October 14 attribution update:
`8aac8ecc0f7f60116a4a855355404af67227bdcc457c103d39ae358fddad6351`.

The initial stale Samurott / Ceruledge / Machamp configuration remains raw history,
but Team Select subsequently witnesses Abomasnow / Camerupt / Gourgeist and the
user configures their moves before GO. Every active-species article has the correct
side: Abomasnow PLAYER; Sneasel, Sealeo, and Vaporeon OPPONENT. The saved roster
does not invent an active Samurott or put a player species in the opponent HUD.

Player charges resolve as Weather Ball (Ice), Energy Ball, Weather Ball (Ice).
Sneasel's Ice Punch resolves as OPPONENT. Abomasnow's configured Powder Snow
remains identified through the charged-move effectiveness text. Vaporeon's
Water Gun is identified from its unique reference move. Sneasel and Sealeo have
no resolved fast-move article in this recording.

## Remaining measurement gaps

- Only eight canonical fast-move uses appear: six PLAYER, two OPPONENT. Six Powder
  Snows generate 48 energy; the three player charges spend 115. At 8 energy per
  Powder Snow, at least 15 uses were required from zero starting energy. This is
  an energy lower bound, not an exact count or invented attack timestamps.
- HP witnesses were enabled during battle, but only 104 measurements appear
  (39 PLAYER, 65 OPPONENT). Available full frames show visible bars that the pixel
  locator rejects. Compact PNG counts alone were not used to diagnose availability.
- All 17 audio capture articles refer to two retained WAV files; both files contain
  zero-valued samples. Audio provides no corroboration in this recording.
- Sneasel and Sealeo have Poké Ball-based faint articles. WIN is recorded, but no
  separate final Vaporeon faint article appears. Result publication trails its
  source observation by about 9 seconds.
- Species publication still sometimes exceeds the 1.5-second target. Correct
  trainer assignment does not establish prompt HUD delivery.
- There are 42 retained QTE type-icon crops. They are raw evidence, not classified
  moving type icons.

The record lasts 171.9 seconds. GO occurs at +82.72 s, WIN evidence at +162.41 s.
The 33.8-second capture gap is before GO, during pre-battle setup.

## Reproducible HP failures

Seven retained battlefield frames show both bars with remaining HP. Their file
modification times were used only to locate them; those times are not exact frame
capture timestamps. Test fixtures retain the original pixels in the calibrated
vertical band (source x=0..672, y=367..1080), without scaling or filtering.

Before correction, the production pixel scanner found the player bar in **0/7**
frames and the opponent bar in **3/7**. Two causes are directly visible:

1. Abomasnow's white body merges into its white outline, extending the horizontal
   white run outside the expected bar width.
2. The opponent's border flashes blue in these frames. The locator accepted only
   white or orange outlines, and the pulse detector only counted orange flashes.

The correction adds contrast-based white-outline recovery and blue-border
recognition, retaining paired outlines, end-cap checks, expected width, and
left-aligned HP fill. Blue and orange border transitions remain recipient-side
evidence; border color alone does not identify a move type. Existing orange-specific
archive fields retain their actual values (zero orange for a blue flash), while
the color-change measurement reports the flash. No raw archive is rewritten.

## Validation

- Recorded-pixel regression: both bars now found in **7/7** frames. Expected
  player/opponent ordering, complete widths, and green/yellow/red HP are retained.
- The real white and blue border samples exercise the pulse detector: one blue
  flash produces one onset; repeated blue samples do not count again; a return
  to white permits the next onset. Synthetic test times are not claimed as the
  original attack cadence.
- All **774 unit tests** passed, along with debug app and device-test APK builds.
  `git diff --check` is clean.
- Installed the debug app and device-test APK on the reconnected Moondrop with
  replacement installs, after verifying that MediaProjection was stopped. The
  installed app matches the tested build's SHA-256:
  `2853af031d049ecf7693b3fcdbe60ed6b722589ba9a9152b360e3251cfdf5399`.
- The recorded-frame device test passed: both bars and the white/blue border
  appearance remain correct across 70 alternating frames. After ten warm-up
  frames, the 60 measured calls to `LiveBattlefieldHpTracker.measureFrame` took
  **18.03 ms median, 21.56 ms p90, 32.42 ms maximum**. All were within a 33.33 ms
  frame budget. This measures tracking on two retained frames, excluding decoding
  and subsequent border-appearance processing; it is not a simultaneous Pokemon
  GO / MediaProjection workload benchmark or proof of every attack being counted.
- Normal app launch succeeded. All eleven calibration/profile/HUD-layout files
  match their immediately preceding backup byte-for-byte after install and launch.
  The reviewed archive's hash is unchanged. Results are saved in
  `device-hp-test.log` and `installation-verification.json` in the review folder.

Build and inspection logs are under `/tmp/overdex-review-20261015/`. A fresh live
battle is still needed to measure the change in attack coverage. Sparse retained
PNGs cannot establish every missed hit's timing, and this fix does not fabricate
those missing uses in the old archive.
