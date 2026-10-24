# Pixel badge-location regression, October 24, 2026

These unaltered pixel crops come from retained 672 x 1419 Pixel frames copied to
`/home/sean/overdex-device-backups/2026-10-24-pixel-review/retained-frames`.
The manifest records each full-frame hash and exact crop rectangle.

The assigned resource row at y=165 contains our HUD cards; the native badge row
at y=123 contains the actual Poké Balls. Without the HUD footprint, the former
passes resource-shape validation and prevents relocation to the native row.

Tests use a manually annotated HUD exclusion covering the assigned crop only.
It is a regression annotation, not capture-time metadata or a new calibration.
The three native crops visibly contain one, two, and three remaining balls.
Filesystem modification times are not used as attack timestamps.

The Android test assets contain each source's `(0,90)-(672,230)` badge band.
The test restores those pixels to the original 672 x 1419 canvas and uses the
saved Pixel name/ball assignments to exercise the complete recovery path. It
uses a separate, manually labelled HUD footprint; it does not read or change
the running device's calibration preferences.
