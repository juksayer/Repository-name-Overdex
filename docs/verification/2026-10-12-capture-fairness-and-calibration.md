# October 12: missed start, capture starvation, and calibration reference

Registration work remains paused. This follow-up reviews the match recorded
after the HP/resource/replay changes described in
`2026-10-12-battle-hp-resources-energy.md`.

## New archive

Moondrop archive `2026-10-12_12-07-43.odxmatch.zip`, match
`42fff18b-0d3f-4cb5-bc96-c2541b84e473`, contains 2,219 articles over 162.843
seconds. Its 623 declared artifacts passed SHA verification. Size: 2,737,846 bytes.

- HP measurements now exist for both sides: 128 player and 75 opponent.
- Player species: Turtonator at 25.642 seconds, Camerupt at 74.817, Gourgeist at
  121.221. Opponent species: Sneasel at 25.642, Sealeo at 50.697, Vaporeon at 95.959.
  These are observation times, not independently verified entry times.
- Countdown `3` was witnessed at 21.709 seconds. GO and `MatchStarted` were absent.
- Nine border pulses were PRESENT: eight on the player bar, one on the opponent
  bar. The archive has nine HP-damage ticks but no canonical `FastMoveUseObserved`
  events or Fast Move energy accounting because the start gate never opened.
- No Poké Ball counts, shield counts, faints, charged-move announcements, or
  `MatchEnded` were recorded. Vaporeon's Water Gun identification came from its
  unique legal move pool; this is not proof of successful cadence identification.
- Countdown has only two preserved crops. Announcement crops stop after 20.8
  seconds and resume at 141.091. Resource crop availability does not become true
  until about 141.4 seconds. All 623 preserved images are small crops; there is no
  full battlefield frame or sparse HP PNG in this archive.

This coverage cannot support faithful per-attack counting or reliable energy
advice. Missing evidence is not reconstructed as fact in the old archive.

## Capture scheduling

The live input collected a hot SharedFlow with synchronous crop/PNG work. When
the next frame was already available, collection did not suspend, so one crop
could occupy a bounded dispatcher's worker indefinitely. Other crops on that
dispatcher did not even reach their enable checks.

The regression test `LiveSampleDeliveryTest` reproduces this with a single-worker
dispatcher and a continuously ready stream. The neighboring resource task ran
only after all 100 frames before the fix. An explicit coroutine yield after each
frame allows it to run before that burst completes. The test failed before the
change and passed after it. This demonstrates the starvation mechanism; actual
phone capture rates still require another recorded battle.

## Missing GO fallback

A trusted countdown numeral predicts when GO should occur. A later HP-damage
tick, PRESENT border pulse, or explicit charged-attack cue can confirm that a
battle followed within 30 seconds. The derived start cites the numeral and the
confirmation and retains an inference source. It does not create a GO glyph.

The new match's `3` plus its first player damage tick at 26.836 seconds imply a
24.709-second start. Regression tests cover these timestamps, all three numerals,
out-of-order delivery, rejected pre-start evidence, manual ignore/restart, and a
later real GO without a duplicate start. Initial species, full bars, and charge
button countdown fill do not confirm the boundary. Fast Move use inference also
rejects late-delivered evidence captured before the accepted start.

## The 84-pixel calibration discrepancy

Android reports the Moondrop's status bar as `[0,0][1080,84]`, agreeing with the
user's 84-pixel measurement. The navigation bar is another 72 pixels. Physical
bounds are 1080 × 2400; app-content metrics are 1080 × 2244.

The selected profile, `Moondrop - October 10 capture`, is unchanged from its
October 10 copy. Legacy species, shield, and Poké Ball coordinates are 84 physical
pixels lower than this selected profile; team-info coordinates differ by about
89 pixels. The user saw the disagreement in Match Calibration over a bundled
reference screenshot. No user screenshot directory was selected. Preserved
species crops from the new live match contain clean Turtonator and Sneasel text
using the selected profile.

Both old reference screenshots and actual capture frames can contain a status
bar. Matching the 84-pixel displacement identifies a strong inset/layout clue;
it does not prove that MediaProjection discarded those rows. Do not subtract 84
from every rectangle or replace the user's saved profile.

Match Calibration now prefers one retained, unaltered frame from the actual
capture stream when there is no explicit screenshot folder. It labels the source
as DEVICE CAPTURE, YOUR SCREENSHOT, or BUNDLED REFERENCE. A sparse full-frame HP
capture can refresh that reference after start when both bars were recently
visible. The copy survives archive cleanup and does not change calibration
coordinates. CRT effects are bypassed for this precision preview; GlassShield
remains enabled.

## Verification

- Full unit suite: 538 tests across 105 suites, no failures, errors, or skips.
- Debug APK assembled successfully.
- `git diff --check` passed.
- APK SHA-256: `9dd98f51a2b0762c5b444ba8d94dbfca80d4a8d4d20733163931e9c0d1bee8a0`.
- Installed in place on Moondrop `MDPH00124112500414`; no uninstall or data clear.
  Eleven calibration/profile/settings files were identical before installation,
  after installation, and after navigating the updated preview.
- Seeded the new reference slot with an existing, unmodified October 10 capture
  already in the phone's artifact store:
  `42d2d26bf5d4be41b1cdc5e2ab60bf490acd2953d915635dc768a1b8573a72fa.png`.
  This is an older real capture, not a frame from the newly reviewed match.
- Navigated through INTUEOR → Signal Observatory → Match Calibration using the
  instrument buttons. The LCD identified DEVICE CAPTURE, 672 × 1493. Both saved
  species boxes enclosed the correct text (Obstagoon left, Vaporeon right), with
  no coordinate adjustments. The preview had no CRT curvature/scanline effects.
- No AndroidRuntime errors for the installed app's process during this check.

The next suitable battle should refresh the reference automatically. That live
refresh, improved crop delivery, canonical attack counting, and resource/faint
coverage remain physical-device acceptance checks for the next recorded match.

Local temporary evidence and build logs are in
`/tmp/overdex-match-review-20261012/`.
