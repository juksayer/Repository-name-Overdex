# Finishing-move video and Replay review

The supplied `Screen_recording_20261111_112107.mp4` was compared with Moondrop
archive `87612e9f-35ed-4d22-b6cb-062fb264e223`. The video is live Pokemon GO,
not a recording of Replay. GO first appears at video PTS 24.863511 seconds;
video-to-archive alignment remains approximate within capture latency.

## Evidence boundaries

The copied archive contains 1,918 Articles and 190 verified artifacts. Its
SHA-256 is `936fdd4e6421a0987839ca741f8ea2dc22401512f34354a58d4b349c0750a77e`.
The source archive and its Articles were not rewritten.

- Charizard visibly faints following an Incinerate during ordinary play. Its
  last archived HP measurement is approximately 9.5% at GO +45.841 seconds;
  the finishing loss and empty outline were not recorded.
- Greninja faints directly after Brutal Swing in the video. The video does not
  establish an additional Incinerate between that charged move and the faint.
- Leavanny faints directly after Dragon Pulse. The final named Dragon Pulse is
  recorded at GO +88.981 seconds, but no final empty HP sample is recorded.
  An observed win does not supply a missing Fast Move or zero-HP measurement.

## A reproduced Replay animation overlap

One already-recorded player Incinerate against Greninja starts at recording
+152.466865 seconds and impacts at +154.966865 seconds. Its short sprite motion
occupies the first 270 milliseconds. The previous player charged move begins
its decorative strike at +151.144019 seconds and previously remained visible
through +153.064019 seconds, including its impact hold.

The renderer gives the charged pose priority. Consequently that entire
Incinerate sprite motion was hidden, although its projectile was rendered.
This is distinct from Charizard's finishing hit, whose impact evidence is absent
from the archive.

Replay now fits a completed charged move's decorative strike and hold before
that actor's next already-supported Fast Move. This charge lasts 1.322846769
seconds, retaining its windup and roughly 291 milliseconds of projectile flight,
and ends exactly when the recorded Incinerate begins. A separate source charge
schedule still controls Fast Move exclusions and QTE association. The fit does
not move a source event or create an attack.

Before/after full-archive projections preserve all 15 Fast Move cycles, all 15
displayed Fast actions, both complete HP tracks, all 33 HP changes, and both
combatant identity tracks exactly. Two charged visuals shorten (one on each
side). The final Dragon Pulse has no following Fast Move and remains unchanged.

## Final Dragon Pulse rendering check

The final Dragon Pulse reconstructs as one completed player charged action,
with no overlapping open phase or faint pose suppressing Turtonator. A compact
test-only fixture retains 492 exact original Articles, with a provenance file;
the full-reference diagnostic also reconstructs the same charged action.

The native emulator test passed and its saved images show the actual
Turtonator and Leavanny artwork. Turtonator rises approximately 47 physical
pixels, slams down while the projectile travels, and returns to its resting
height on impact. Pause and reverse seeking preserve the corresponding frame.
This establishes the tested renderer behavior, not what appeared in the user's
unrecorded Replay playback. The reported final charged-motion omission has not
been reproduced.

The full Replay route was also audited: opening this archive normally supplies
no clip window or initial seek, and the recorded win is about 15.54 seconds after
the final charge release. Full-archive projections retain that same action. The
remaining rendering boundary is continuous playback on the Moondrop at the
user's actual speed/route; sampled native frames do not measure frame pacing.

## Charizard HP coverage remains open

Lossless frames decoded from the supplied video provide a concrete pixel case.
These frames are 816 by 1808, whereas archived live measurements use a 672 by
1493 surface. They are not interchangeable original capture frames.

The compiled production pixel-reader probe finds several interacting limits:

- The low bar moves vertically farther than the narrow follower's search margin.
- Orange Charizard artwork visible through the bar's background fails the
  conservative dark-tail check, even while the red fill and outline are visible.
- The orange damage remainder outlasts the freshness interval before a clean
  empty outline appears. Orange pixels are correctly not labeled empty.

At video PTS 72.598656 seconds a correctly positioned direct probe can verify
the empty outline. The old moving anchor cannot acquire it. Merely increasing
one threshold would not prove a safe end-to-end finishing-hit repair: the
damage detector also requires a fresh, supported low baseline and matching
geometry. A future bounded outline-continuity measurement must remain separate
from an HP fraction or a zero claim, and must be tested through the damage/use
handoff. No HP thresholds were relaxed for this investigation.

A denser follow-up decoded 58 lossless video crops from PTS 70.708967 through
72.898422. The current strict terminal reader accepts no RED sample in that
sequence, even with diagnostically supplied Y positions. The ordinary reader
can still measure positive low HP through approximately 71.590 seconds. Strict
EMPTY readings succeed at 72.598656, 72.641678, 72.715822, 72.754356, and 72.824967.

An experimental outline-only follower using the existing paired-line/cap rules
loses continuity with a 46-pixel vertical range. A larger 63-pixel diagnostic
range follows 56 of 58 frames and reaches the empty outline. This is geometry
evidence only: it supplies neither a fresh HP fraction nor permission to count
an attack. The last actual archived numeric reading is roughly 1.9 seconds
before the first empty, exceeding the damage detector's 1.25-second ordinary
gap bound. The 500-millisecond single-empty shortcut also remains ineligible;
horizontal endpoint choices differ.

A future repair therefore needs actual bounded positive-HP remeasurement from
the intervening frames, followed by a validated handoff through the damage
detector. Two genuinely measured empty frames at least 90 milliseconds apart
could use its existing ordinary settling rule if that numeric baseline is
actually refreshed. Geometry-only updates must not refresh numeric timestamps,
copy old HP forward, or waive the single-empty guards. The experimental wider
follower is not part of this build.

## Verified presentation build

- Full JVM suite: 2,417 tests across 319 suites, zero failures/errors/skips.
- Debug app and Android test APK builds succeeded.
- Both new handoff regressions fail against the preserved pre-fix production
  classes and pass with the repair.
- Native API 34 renderer: `OK (2 tests)` for the recorded Incinerate handoff and
  final Dragon Pulse. The tests check actual loaded sprite positions, projectile
  presence, pause/reverse seeking, HP timing, and unchanged source Articles.
- Independent code review found no blocker. `git diff --check` passed.

The frozen presentation APK is
`/home/sean/overdex-device-backups/2026-11-11-final-hit-review/overdex-replay-attack-handoff-debug.apk`,
SHA-256 `7b7f909d39af71eaed9adefdd533ccad28d81f1b2de05a8e7f042c54364e5490`.
These checks do not establish new live HP recognition accuracy or recover an
unrecorded attack in an existing archive.

The reviewed APK was installed on Moondrop `MDPH00124112500414` after verifying
that Droidball and MediaProjection were inactive. A hash-verified backup retained
443 durable data files and the previous installed APK. All 443 files were
unchanged after installation; the installed APK hash matches the tested build.
The app launched successfully with no new entry in the crash buffer. No battle
or physical-phone Replay playback was used as a recognition acceptance test.

Private evidence, frame manifests, source hashes, compiled-reader probes, and
native render images are retained under
`/home/sean/overdex-device-backups/2026-11-11-final-hit-review/`.
