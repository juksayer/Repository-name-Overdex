# Exit Icon calibration — October 28, 2026

Added a dedicated Exit Icon region to Match Calibration and a live guide.
Screenshot selection remains unchanged while the user decides its new workflow.

- CRT: cycle to **Exit Icon**, immediately after **QTE Type Icons**, then use the
  existing position/size controls and calibration profile saving.
- Live HUD: short tap opens a temporary menu; **EXIT ICON BOX** closes that menu
  and opens the guide. Long-press dragging retains its existing behavior.
- The prebattle session controls also offer **EXIT ICON BOX**.
- The live guide supports view-only touch passthrough, adjustment, position/size
  pixel nudges, Cancel, and Save & Close. It has no QTE sampling controls.
- Live saving reloads calibration and updates only `exitIconRegion`. Closing
  without saving leaves the stored region unchanged.
- Legacy preferences and named profiles both store the new region. Loading an
  older profile supplies only the missing Exit Icon default in memory, without
  rewriting the file or migrating other coordinates.
- Coordinates remain normalized against the full source frame. The initial
  upper-left box is an adjustable starting point, not measured device calibration.

This is calibration infrastructure only. No automatic Exit Icon visibility
reader, new capture workload, or battle-transition inference is enabled here.

## Verification

Persistence regressions cover restart, legacy/named profile saving, unrelated
box preservation, and old-profile fallback without file changes. Independent
review found no missing persistence or enum path. Focused device-test source
covers temporary-menu access, long-press dragging and the labeled guide; physical
live touch behavior remains for the user's test.

- Full unit suite: **1,273 tests / 210 suites**, zero failures/errors/skips.
- Debug app and Android device-test APKs built successfully. The two new device
  cases compiled but were not run; no instrumentation touched user data.
- `git diff --check` passed.
- Moondrop update installed after verifying no Overdex service was active.
  All 16 saved calibration/profile, HUD-layout and current-team file hashes
  match before/after installation. The Pixel was not updated.
- APK SHA-256: `5462cb61a6d3a7a80727a3181c2a8fbf819ab22f49264a5ee505b0fe502267c6`.
- Build/install logs and hash comparisons:
  `/home/sean/overdex-device-backups/2026-10-28-exit-icon-calibration/`.
