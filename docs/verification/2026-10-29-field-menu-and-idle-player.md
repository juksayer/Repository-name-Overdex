# Field menu positioning and idle-player recording — October 29, 2026

## Changes

- The prebattle control menu uses the supplied Pixel 4 screenshot coordinates:
  X=75..1005, Y=1001..1485 on a 1080×2280 display. Its 930×484px card
  is centered, opaque, and uses the sampled party-card interior color `#FBFEF5`.
  Coordinates scale against the entire display, including system bars.
- Droidball remains above the card. The complete native window is 930×625px,
  at X=75, Y=860 on the Pixel. Opening it does not save a new collapsed-body
  dock or battle HUD calibration. Team & Moves shares its width/color but
  remains a taller scrolling input form. Detailed menu design is deferred.
- Explicit native dimensions prevent the former wrapped window from narrowing
  the new card. The attached window origin is captured before alignment changes
  its requested position, avoiding feedback from stale native frame coordinates.
- An early Start Battle HUD request immediately displays **BATTLE HUD READY**.
  The underlying request still arms observation; actual badge evidence gates
  the battle layout. A late accepted Team Select scan preserves this request and
  an already armed countdown. Rescan/reset deliberately clear it.
- The shell session button shows only START or STOP, retaining its disabled
  transition during teardown and contextual module overrides.
- Single centered charged controls use two consistent lone-circle measurements,
  with neither side circle present. Existing name/effectiveness, touch and fill
  readers then use one logical slot. Layout changes reset fill baselines rather
  than creating an energy increase. Runtime center crops retain their distinct
  provenance and do not change saved calibration. Existing battle-surface support
  allows late capture without requiring an observed GO glyph.

## Zero-player-attack battle

The user's ground truth is no player attacks of either kind. Match
`c299159a-8190-4ac0-9c43-4ea65b20cade` is the latest actual completed Pixel
battle, with inferred start 13:56:42.929 CDT and loss 13:58:26.159. The later
14:15:57 archive contains status only and was excluded.

| Ledger evidence | Player | Opponent |
| --- | ---: | ---: |
| Individually inferred Fast Move uses | 0 | 31 |
| Fast Move identifications | 0 | 2 |
| Charged-move energy spends | 0 | 2 |

Both charged announcements visibly say “Sneasel used Ice Punch!” Opponent HP
has no damage ticks or PRESENT border pulses; 179 readings remain approximately
98.41–99.47% full, with adjacent downward jitter no larger than 0.535 percentage
points. The archive preserves motion, particle and one fill-change reading,
but none establishes a player attack. Current live/replay inference requires
HP-impact evidence before those raw signals can establish a Fast Move.

This is an archive audit and replay source inspection, not an executed replay
visual check or proof of general attack-detection accuracy. Raw replay/viewer
labels such as `FAST MOVE MOTION: PLAYER` and `attacker=PLAYER` still overstate
what HP-bar movement alone proves; neutral diagnostic wording remains follow-up
work. This task did not alter the original match or hide its raw measurements.

## Verification

- Full unit suite: **1,433 tests / 227 suites**, zero failures/errors/skips.
  Debug app and Android test APK builds pass.
- Pixel physical UI tests: **OK (9 tests)**. They cover full 48dp menu touch
  targets inside the supplied card bounds, distinct Start/Stop controls, Ready
  feedback, team confirmation and shell Start/Stop behavior.
- Native Pixel check over the actual Choose Your Party screen confirms window
  `[75,860][1005,1485]`, hence menu Y=1001..1485. Start changes to Ready;
  closing Team & Moves restores the same geometry and Ready state; Hide restores
  the compact body at `[917,350][1058,491]`. Normal Stop ends the service.
- The manual prebattle check records `battle_overlay_opened(USER_REQUEST)` and
  no `match_started`. All six Pixel calibration/profile, current-team and HUD
  position files retain their pre-install hashes.
- The copied idle battle matches its phone SHA-256; all **443 declared artifacts**
  verify. Original archives, source announcement crops, selected Articles,
  screenshots, native window dumps, logs and settings backups are retained in
  `/home/sean/overdex-device-backups/2026-10-29-menu-and-idle-review/`.
- Centered-control logic has synthetic regressions, but still needs a real
  single-unlocked-move battle to validate its rim and derived text territory.
  It does not add a new move-type icon classifier.
- Pixel installation is verified. Moondrop USB is unavailable; its later wireless
  advertisement did not answer read-only device queries. Moondrop installation
  remains pending.

Final debug APK SHA-256:
`c6c948d36679de785ffb65cbee94553d77e186f0aba3b7afcd81253b7f7384c3`.
