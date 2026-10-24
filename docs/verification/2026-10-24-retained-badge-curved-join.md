# Retained badge follows the HUD's curved join

## Report and recorded cause

The user reported background appearing where the retained opponent badge and
HUD overlap. The October 24 saved frames show a straight horizontal cut through
the lower Poké Balls/shields, followed by visible battlefield inside the HUD's
concave upper opening. Native frames have complete resources in that opening.

`retainedBadgeSource` previously truncated the cached image at the rectangular
HUD window's top. That rectangle includes transparent space above the HUD's
curved contour. Excluding the entire rectangle discarded legitimate native
badge pixels and exposed the scene beneath the missing badge section.

## Change

- Retain the full eligible badge region and mask the actual rendered HUD shape.
  `retainedBadgeImage` uses `BattleHudBadgeShape`, the same contour drawn by the
  live HUD, instead of maintaining another approximation of the join.
- Keep the native badge's rounded outer corners transparent, and make all
  HUD-body pixels transparent in the cached bitmap. One source-pixel guard
  around the HUD contour accounts for scaling and antialiasing at its boundary.
- Preserve native ball/shield pixels below the rectangular window top when
  those pixels occupy the curved opening. Do not stretch a partial badge into
  that space or paint a strip of captured battlefield over the HUD.
- Invalidate a cached mask when the HUD moves/resizes. Existing content revision,
  faint/switch invalidation, match scope, return detection and queued-frame
  exclusion remain in place.
- The retained window's full footprint remains excluded from observation. The
  presentation image cannot become new species/resource testimony.

No calibration, saved HUD position, live HUD contour/material, audio processing,
Timeline Articles, or historical archives were changed. This is still a native
screen snapshot: background already composited inside GO's translucent badge
cannot be separated from that badge. This change addresses the missing/corrupt
join, not reconstruction of an independently transparent native game layer.

## Validation

The recorded regression uses the existing October 24 three/two/one-ball badge
fixtures, preserving their original pixels. A new pixel test checks complete
ball rims below the old straight cut and transparency below the curved join.
A synthetic image with bright green scenery and magenta HUD artwork verifies
that neither outside-corner scenery nor HUD artwork survives in the copy.

The window regression also verifies 3 -> 2 -> 1 retained balls, immediate
invalidation after a faint, rejection of queued pre-faint frames, release when
the native player badge returns, and exclusion from observation.

The first emulator window checks were obstructed by an Android System UI
not-responding dialog. The saved screenshots establish the obstruction. After
dismissing it, the same checks passed; no production change was made to conceal
that failure. The final validation and installation results are recorded below.

Evidence, logs, and device-data backups:
`/home/sean/overdex-device-backups/2026-10-24-curved-badge/`.

Final checks: **1,101 unit tests pass**, zero failures/errors/skips; debug app
and instrumentation APK builds succeed. API 34 emulator reports **OK (4 tests)**
after the final one-pixel guard change. Inspected the retained bitmap and window
screenshots: complete ball rims, no HUD edge sliver, and correct two-ball state.
The disposable emulator was shut down afterward.

Installed in place on Moondrop MD_PH_001 (`MDPH00124112500414`) at 13:14:44 on
October 24. APK SHA-256:
`1cd2e23b73e56ad9965c3b95b123718eb56591b42f38c5d6df9699c141820a77`.
Droidball was stopped before replacement and remained stopped. The Directory
Tree opened normally; no Overdex fatal exception appeared in the saved startup
log. A readable 268-file settings/data backup preceded installation. Afterward,
all compared preferences, calibration profiles, owned-card/team database files,
battle records and replay data were unchanged; only the Firestore cache changed.
The archive list was unchanged. No phone instrumentation, uninstall or data clear
was used.

## Live acceptance

In a Moondrop battle, watch the badge during a charged-move/QTE sequence when
GO hides it. Expected: complete current balls/shields and a curved join to the
HUD, without a straight cutoff or a scenery strip across the join. The native
badge should replace the retained copy when it returns. No recalibration is
required. A new live recording remains the final acceptance boundary.
