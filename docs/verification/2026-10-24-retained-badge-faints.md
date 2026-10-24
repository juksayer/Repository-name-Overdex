# Retained opponent badge after faints — October 24

## Recording and diagnosis

Reviewed Moondrop match `f843fe0c-23a5-4fc2-8981-6acc8292e827`, recorded at
10:49:23. The desktop copy is in
`/home/sean/overdex-device-backups/2026-10-24-retained-balls/`. Phone and desktop
archive SHA256 agree:

`b6bb804fe0a5c26fc645cc33904b3874c46f9a68cf9193803594e62d7a7d0e32`.

All 217 referenced artifacts verify; the compact archive contains 1,766 Articles.
Twenty-four separately retained full frames were also copied and verified against
their content hashes. Their filesystem times are not event timestamps.

The opponent-ball witness correctly measured three balls, then two at archive
98.750 seconds, then one at 112.028 seconds. The faint witness attributed those
decreases to Mankey and Hitmonlee. Native badge crops visibly agree. Nevertheless,
later full frames show the retained Mankey badge with three bright balls above
HUD cards already marked fainted.

The retained badge was a screenshot with no resource/identity invalidation. It
could not refresh after the HUD appeared because the HUD overlapped its lower
edge, making the entire calibrated rectangle ineligible. This left the original
Mankey screenshot available for later badge disappearances.

## Change

- Identity changes, newly marked faints, ball/shield count changes, and resets
  invalidate the retained image through presentation state. Repeated unchanged
  readings and duplicate faints do not keep discarding it.
- Invalidation also reaches the overlay without waiting for another capture.
  Captures older than the change cannot repopulate its cache.
- A native badge with a small lower-edge HUD overlap can refresh from its exposed
  upper rectangle. No HUD pixels are copied, and the original top-corner geometry
  is preserved. At this HUD position, the exposed ball caps remain visible; the
  covered lower part is not reconstructed or copied from an older image.
- Overlap leaving less than 60% of the badge height is rejected. Retained pixels
  remain excluded from native witnesses, including the existing clearance period.

This does not decrement counts a second time on a faint, change observation
sampling rates, introduce an extra recognition pass, shift calibration or HUD
position, or rewrite recorded Articles.

## Validation and installation

- Full unit suite: **1,094 tests**, zero failures, errors, or skips.
- Debug application and instrumentation APKs build successfully.
- On Moondrop `MDPH00124112500414`: **OK (2 tests)** for the existing retained
  overlay test and the new recorded-frame faint/refresh regression. The latter
  exercises the production locator, cache, actual overlay drawing and screenshot
  pixel counts, including 3 → 2 → 1, immediate invalidation, rejection of an older
  queued frame, reset, and exclusion of our own image from observations.
- Test fixtures contain cropped original badge bands and source hashes under
  `app/src/androidTest/assets/badge-retention-20261024/`; full originals remain
  in the desktop backup. The HUD footprint in the test is a manual annotation
  informed by archived crop provenance, not fabricated capture telemetry.
- Installed in place on the idle Moondrop at **11:08:51**. MainActivity resumed.
  Before/after hashes match for battle calibration, Team Select calibration,
  current team, HUD layout, and selected calibration profile. No data was cleared.

Installed APK SHA256:
`62586e83e48140fdccec7608bf547ab7bb0087a60d722c48ed89deaa1eb4f914`.

Screenshots and preference hashes are in the backup directory's `install/`
subdirectory. The Pixel was not connected and was not updated. At installation,
a new live battle remained the acceptance boundary: after each faint, a
disappearing native badge must not bring back an older name or extra bright
Poké Ball.

The subsequent 11:45:50 Moondrop recording passes that check in its preserved
full frames, retaining Magcargo with two bright balls and Flareon with one.
See [the live retest](2026-10-24-live-result-retest.md) for evidence and limits.

## Separate result delay in the original recording

The recording contains a win, but `YOU WIN!` from a crop at archive 143.980
seconds was only published around 206.35 seconds, followed by `MatchEnded` at
206.41 seconds. Other result crops produced partial text, including `iNIAA NOA`.
The long delay needs a separate result/OCR queue investigation. This patch fixes
the stale retained image; it does not claim timely live match-end recognition or
an observed final zero-ball measurement.

The subsequent [result-reader change](2026-10-24-outcome-latency.md) and live
retest address that separate delay; no final zero-ball observation is claimed.
