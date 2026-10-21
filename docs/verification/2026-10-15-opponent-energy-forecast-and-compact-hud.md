# Opponent energy forecast and compact fixed HUD — October 15

## Problem and resulting behavior

Accepted Fast Move uses remain incomplete in recent recordings. A gap in those
observations must not make the live assistant assume the opponent earned no
energy. `OpponentEnergyForecast` is a separate, disposable presentation model:

- Select the greatest energy per turn among surviving legal candidates until
  compatible identity evidence selects a move. A tie-break remains an assumption.
- Advance on complete move cycles during active combat. Do not earn energy
  before GO; a recording without GO can begin a clearly partial estimate at its
  first accepted impact-backed use.
- Stop for incoming attacks, Get Ready, charged announcements/spends, numeric
  decision timers and faints. A disappearing timer alone does not prove the
  charged sequence is over; a new accepted hit resumes the estimate.
- Count the larger of predicted cycles and accepted uses in each running
  segment, never their sum. Retain observed-use counts separately.
- Keep energy across switches, stop earning while benched, cap at 100, subtract
  witnessed charged-move costs and clear fainted opponents' energy.
- Recompute in capture-time order when delayed evidence arrives or move
  candidates change. Keep unfunded observed spends as explicit discrepancies.
- Display `≈` on speculative energy and the move assumed for that estimate.

The model does not emit testimony, add synthetic uses to the Timeline, modify
archived Articles, or change the observed-energy accounting used by replay.
It uses the existing side-attributed canonical hit evidence and candidate pool.
Timing gaps, missing charged announcements, and uncertain identity still limit
the estimate. Missing observations are not proof of an opponent's missed turn.

## HUD

The permanent announcement cutout and its layout spacer have been removed.
The timed hide/reveal path remains removed. Labels are right aligned, move rows
have no added vertical gap, and the position/QTE editing controls are absent.
Saved HUD coordinates and QTE calibration are not changed by this work.

A small pixel probe recognizes both calibrated team badges without OCR and
latches the HUD visible for that battle. Side-located name testimony and stable
resource counts also establish badge presence. The anchored HUD has no arrival
slide and cannot be dragged accidentally. Once shown, it stays visible through
the game's temporary badge disappearances.

Poké Balls and shields are not duplicated in the HUD. While both native badges
are visible, a single small opponent-badge image is cached in memory. If both
disappear for at least 100 ms, the image is drawn at its original calibrated
display coordinates. This is the last visible state, not a new measurement.
The untouched player badge signals the native badges' return; the retained
opponent image cannot signal its own return or refresh its own cache. The copy
is removed before original opponent readings resume, with a 250 ms clearance
after the actual window removal for queued captured frames.

The retained region travels with every frame as excluded presentation pixels.
Crop capture, resource counts, species OCR, HP tracking, effectiveness text,
player controls and custom crops do not read through that region. Other parts
of the frame continue normally. No black mask is painted into the source.
Results, next-match resets, opening Overdex and the team editor clear the copy.
The display assumes both native badges disappear/return together; independent
badge animations may briefly leave the copy hidden until both sources agree.

The upper joining contour includes a shallow 1.5-display-pixel center dip at
415 pixels width. The supplied unoccluded reference
`/home/sean/Desktop/ overdex related/dexnotes_screenshotsandcaptures/Screenshot_20260811_184154.png`
is 1080×2400; its visible lower transition is approximately row 347 near the
rounded ends and row 348 near the center. Background translucency and the gallery
overlay mean this is an approximate contour fit, not a recovered original asset.
The regular HUD remains below the badge. Only the temporary retained image
occupies the original species/resource region, and that region is excluded from
new evidence until the copy has cleared.

## Validation

Final production build and all 810 JVM unit tests passed (zero failures/errors).
The debug app and instrumentation APK both built successfully. The physical
Moondrop passed two instrumentation tests in 9.122 seconds:

- `RetainedOpponentBadgeDeviceTest`: original badge placement, no extra content
  below it, self-copy exclusion for name/balls/shields, unaffected player and
  battlefield regions, removal when the player badge returns, and resumed reads
  after the capture clearance.
- `HudAnnouncementReadDeviceTest`: badge-gated appearance, no duplicate resource
  row, no position/QTE controls or section labels, right-aligned contiguous move
  rows, unchanged sprite size, no accidental dragging and no timed flashing.

The first retention screenshot assertion compared two frames during Android's
activity-entry fade. Same-frame background comparisons corrected that test;
no production change was required. Final device results are in
`/tmp/overdex-forecast-check/retained-badge-device-final.log` and build output in
`/tmp/overdex-forecast-check/retained-badge-build.log`.

Installed app SHA-256 (verified against the phone's installed base APK):
`e80c51a73c7b2e885ae3592a5cd8b8c65235747676834cc28a69e0a917a1bb2e`.

Recorded badge-presence samples retain their original source filename, SHA-256,
dimensions, normalized sampling region and sampled colors. Both battlefield
badges are accepted; a result screen with a lingering old HUD is rejected.

Physical phone: Moondrop MD_PH_001, serial MDPH00124112500414. Before replacement,
MediaProjection was inactive and 13 calibration/layout/profile files were
backed up to `/tmp/overdex-forecast-check`; all matched the previous installation.
All 13 still match byte for byte after installation and device checks.

A new live battle is still needed to judge estimated versus actual attack
counts, phase-resume latency, the seam on the current battlefield, and whether
the reduced text footprint improves announcement OCR. A useful estimate is not
evidence that every Fast Move is now observed.
