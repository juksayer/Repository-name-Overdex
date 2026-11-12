# Match Replay framing and icon perspective — November 9, 2026

Implemented the visual scope of the [replay design notes](../plans/2026-11-09-replay-and-result-confirmation.md).
Unit and native rendering checks passed, and the update is installed on the
Pixel 4 and Moondrop. Both phones successfully opened the ODX-Fi home screen.
Trainer questions, result disagreement notes and result-confirmation workflows
remain deferred.

## Observable behavior

- Charge focus resizes both combatants around each sprite's visible highest
  alpha pixel. Transparent margins and centered Fit letterboxing are accounted
  for. Existing attack rises/returns remain, and faint/defeat animation keeps its
  center pivot.
- Existing focus amounts and timing remain: player grows 20 percent and moves
  20 physical pixels right; opponent shrinks 15 percent and moves 10 pixels
  left. Source-time playback controls the transition, including pause and rewind.
- Player attack icons land on the opponent's facing side, and opponent attack
  icons originate there. Fast and charged paths use measured visible bounds
  with a 10 percent inset into the visible width; Turtonator's flip is included.
  Their shared vertical endpoint is the visible top plus 45 percent of visible
  height, scaled about the top pivot, keeping it on short, wide artwork as well.
  Draw order is opponent → attack glyphs → player, preserving incoming attacks'
  occlusion behind the player's opaque artwork.
- Slot layout, HP/resource placement, existing attack timing and unknown-type
  neutral rings are preserved. Unattributed charged actions stay centered.
  These presentation changes do not edit source sprites, archived Articles,
  result inference or the replay clock.

`ReplaySpriteAnchor.kt` reads actual alpha once per decoded sprite and maps all
four pixel edges into the square Fit node. Empty, recycled or unreadable art
falls back to the full node. `MatchReplayScreen.kt` consumes the visible top
for the pivot and the opponent's facing edge and upper body for projectile
placement.

## Verification

| Check | Result |
| --- | --- |
| Focused unit checks | Initial 25 passed; before the final rendered adjustment |
| Full debug unit suite | Final 2,305 passed; zero failures, errors or skips |
| Final debug app and Android test APK builds | Successful |
| Native replay checks | Final clean run: `OK (17 tests)` on API 34, 1080 × 2280, density 440 |
| Rendered-frame inspection | Reviewed charge focus, Stunfisk launch/arrival, incoming-player occlusion and KO pose screenshots |
| Physical device installation and launch | Verified on Pixel 4 and Moondrop; installed APK hashes match the built APK |
| Calibration preservation | All 4 Pixel and 15 Moondrop calibration files unchanged |

The new native alpha-geometry tests cover padded square art, opaque black art,
alpha-1 edges, tall/wide Fit letterboxing, transparent/recycled fallback and
actual rendered head/front-edge positions during scaling and mirroring. Native
scene checks establish both resize anchors, forward opponent launch/landing
visibility, player-side occlusion, mirrored art and rewind. The final run includes
6 charge-focus/foreground tests, 5 alpha-geometry tests, 3 charged-type tests
(including two recorded archives), 2 stat-change tests and 1 KO pose render.
The saved KO frames were also visually inspected for unchanged faint/defeat
presentation.

The initial native run exposed a real vertical-placement issue for short, wide
Stunfisk art: a fixed shoulder height could place the opponent's launch glyph
above its body. Production now shares the measured upper-body height between
fast and charged paths. The Canvas geometry fixture also inherited Android's
default bilinear filtering, spreading faint alpha beyond source pixel edges;
that fixture now explicitly disables filtering and antialiasing so its unchanged
exact-pixel assertions measure source-alpha geometry. The scene helpers were
corrected to isolate each sprite's bounds and exclude gray ankle/neighbor pixels
from the warm-art size measurement. The player-occlusion mask excludes one
source pixel around filtering edges and permits at most one 8-bit RGB step from
GPU compositing rounding; it still requires zero larger changes over protected
body pixels. Foreground glyph-over-opaque-art checks were retained. An intermediate
run passed 16 of 17 checks before that final rounding correction; the final clean
17-test run and full unit rerun are recorded above.

## Installed build and evidence

- Production APK SHA-256: `b41645d07cc47fae7d76b680d4e72f87b1798f37b665593ebffc70ce85d1b178`.
- Android test APK SHA-256: `824ae639c9983f443f82064546b0be78b81e856810e49773927e268f8658137e`.
- Private evidence root: `/home/sean/overdex-device-backups/2026-11-09-replay-framing/`.
  It contains build/source hashes, parsed unit/native results, build logs,
  `native-replay-accepted.log`, rendered PNGs and pre/post-install backups.
- Both capture services were idle immediately before installation. Both phones'
  installed APKs were independently hashed, and their visible home screens were
  checked after launch. No instrumentation ran on either personal phone.
- All 24 Pixel and 39 Moondrop backed-up settings/database/calibration files
  remain present. Calibration hashes match exactly; the only changed backed-up
  file on each phone was its Firestore cache after ordinary startup/sync.

Detailed interaction with saved replays on the personal phones remains for
physical visual acceptance. The rendered geometry and regression checks above
ran on the emulator. Original annotated reference PNGs remain unchanged.

No build or emulator result establishes new live battle-recognition accuracy;
this scope changes replay presentation only. No commit or push is authorized by
this implementation request.
