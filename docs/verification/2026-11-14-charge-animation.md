# Charged Replay animation pacing

November 14, 2026. Follow-up to the Rock Tomb/faint association: make the
charged sequence easier to follow without moving its evidence or landing time.

## Change

- Sprite focus grows/shrinks over 750 ms at each preparation edge (previously
  250 ms). Both QTE and incoming shield-choice use the same cursor-driven curve.
  Visible-head anchoring and the existing small horizontal shifts are preserved.
- The strike reaches its apex at 65% of its existing duration. The descent uses
  smooth easing over the remaining 35%, replacing the abrupt cubic drop over
  the last 22%. A normal 1.8-second strike therefore gets 630 ms of descent.
- The same move icon leaves at the apex as descent begins. Its flight originates
  at the apex rather than following the sprite downward after release.
- Existing impact, HP, faint, next Fast Move and replacement boundaries remain
  unchanged. Compressed charge windows still finish before the next real attack.
  Source archives, inference rules, attack counts and energy are untouched.

## Verification

Focused suite: 103 passed, zero failures or skips. New checks cover both attack
sides, no launch before the apex, smooth descent, independent flight, endpoint
alignment, slower focus growth/shrink and deterministic reverse seeking.

Full suite: **3,025 passed, six optional diagnostics skipped, zero failures or
errors**. Debug and Android test builds passed. The final test-only rebuild
updated the older Dragon Pulse display test to the new apex timing.

Physical Pixel 4 renderer run: **OK (4 tests)**. Verified the recorded Camerupt
apex and descending throw, supported Rock Tomb landing/faint/replacement,
final Dragon Pulse actor rise/throw/landing, the following Incinerate sprite
motion, and finishing-charge visibility after results. Seeking backward restores
the earlier pose; no archived evidence is changed.

Installed and launched on **Pixel 4 and Moondrop**. Both pulled APKs match:
`6277e57e85784a1db30a86bb1f364823a14a41ba184c9a0996dd4429e48dc813`.
Both devices' database integrity, table counts and protected team/calibration
preferences match their before-update baselines. Pixel renderer tests used the
readable full private-data backup from the preceding Rock Tomb verification;
Moondrop received an in-place install without instrumentation.

Also copied and verified three November 14 Moondrop archives (10:44:46, 11:17:39,
11:20:14) onto the backup drive. Device and copied hashes match and ZIP checks
pass. Originals remain on Moondrop. This is preservation, not a claim that these
new matches have completed event-by-event review.

The earlier Pixel ownership gap remains separate: its preserved mirror archive
cannot establish which Camerupt performed Rock Tomb. This presentation change
neither assigns that attacker nor supplies its missing final Gourgeist faint.

Artifacts are on the backup drive:
`/mnt/overdex-backup/archives/overdex-device-backups/2026-11-14-charge-animation/`.
