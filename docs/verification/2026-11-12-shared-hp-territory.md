# Shared HP territory during charged impact — November 12, 2026

The trainer reported the risk that a bar crossing the ordinary HP crop and charged-impact crop could leave an unfilled portion in one while colored HP remains in the other.

Both live readers already receive the complete captured frame. This change lets the charged-impact reader search the bounding rectangle of its calibrated region and the receiving side’s ordinary HP region when those regions overlap or share an edge. It uses the tracker’s actual calibration, the current frame, and the existing source-timed recipient-owned charge window. No screenshots from different times are stitched and no fractions are added. Calibration itself is unchanged.

Acquisition and subsequent tracking use the combined bounds. Complete paired outlines, both end caps, reference-sized centered geometry, unique candidate, clean interior and the existing overlay visibility check remain required. Invalid/missing charged calibration fails closed; disjoint or corner-only boxes do not expand the territory. The other team’s region is not used. Existing appearance, expiry, frame-size and monotonic publication guards are unchanged.

This adds search coverage during bounded charged windows; it does not assert that a clipped crop previously produced a verified zero. Ordinary terminal tracking already reads a complete local outline from the full frame. The added regression demonstrates the requested hazard is handled as unavailable until the full bar is measurable.

Tests cover horizontal splitting with all yellow fill in one box and the empty portion in the other; vertical splitting; continued tracking; a genuine complete empty outline; invalid/missing/disjoint calibration; existing absent, flash, cap, single-red-pixel and ambiguous-bar cases. Full unit suite: 2879 tests across 380 suites; zero failures, errors or skips. Debug APK build succeeded. Focused charged-impact and tracked-terminal checks also passed.

Real-device acceptance remains pending. No archive changes, commit or push in this task. Installation is recorded below. The expanded region can cost more pixel work during an eligible charge window and may conservatively reject a frame if the combined region overlaps an overlay. Live performance and actual camera-motion coverage need a recorded Pixel trial.

## Pixel installation

Installed with data-preserving replacement on Pixel 4 (`99261FFAZ0017J`) on November 12. The installed APK SHA-256 matches the verified build: `771d6f293c00b7cd845e6ca06710bbbd731feb1b17f7771268bdaf8446968f47`. All 126 files in the essential backup (match archives, battle records, calibration profiles/references, summary inbox, preferences and databases) matched byte-for-byte before first launch. MainActivity then launched successfully and was the resumed activity. Moondrop was not updated. A live battle remains the acceptance boundary.

The first full-backup attempt exhausted root disk space; it was stopped and its incomplete 2.1 GB tar removed. A verified 365 MB essential backup replaced it; raw observation artifacts remained on the phone. Installation receipts are under `2026-11-12-pixel-shared-hp-install` in the device-backup collection. The trainer requested that backups live on the mounted 1 TB OverdexBackup drive.
