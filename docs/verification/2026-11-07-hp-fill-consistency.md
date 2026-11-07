# Reject obscured and color-inconsistent HP fragments

November 7, 2026. The supporting video for the
[profiled Moondrop match](2026-11-07-profiled-match-and-hp-acquisition.md) shows
countdown digit 2 masking the middle of Plusle's full green HP bar. The user
correctly pointed out that green fill contradicts the reported low-HP endpoint.
This fix abstains from incompatible measurements; it does not estimate hidden HP.

## Source-backed failure

At video28.28 seconds, the 672-pixel-wide resampled source has an apparent
left-fragment endpoint of 20.97%. All confidently filled columns in that fragment
are green, and another 95-column green segment remains visible to its right.
The countdown digit, not damage, separates them. At video31.03 the same bar is
full/green; at video48.25 it is genuinely low (22.58%) with no strongly green
filled columns. The pixel audit uses approximate video-derived bar bounds and
does not claim a complete production locator replay.

The original archive and supporting video remain unchanged. Native frames,
derivatives, audited bounds and column counts are retained privately in
`/home/sean/overdex-device-backups/2026-11-07-profiled-match-review/video/fill-consistency-evidence.json`.

## Measurement behavior

The existing substantial post-gap fill rejection now applies to **white and
orange as well as blue outlines**. If another sufficiently large saturated-fill
segment continues beyond the permitted gap, the left segment cannot establish
a valid HP endpoint. The original small-gap tolerance remains.

The pixel classifier separately retains the existing strong-green predicate.
An endpoint at or below **35%** is rejected when at least **90%** of confidently
filled interior columns are strongly green. This is a deliberately conservative
contradiction gate below the color transition, not a color-to-HP conversion or
an exact transition threshold. Mixed colors and marginal values remain available.
Yellow/red low HP and orange damage chunks are still measured independently.

Rejected samples produce no invented HP value, loss, move, faint or energy.
No calibration, source timestamp, side assignment, Timeline custody or archive
schema is changed. Raw historical testimony is preserved.

## Validation

Five new detector tests cover interrupted green fill under all outline colors,
substantial post-gap yellow/red segments, isolated low green endpoints, mixed
and marginal colors, and tolerated small compression gaps. Two older synthetic
fixtures painted impossible green fill at 25% and 7%; their fill colors now use
yellow/red while preserving the geometry and original regression purpose.

All **1,922 Android unit tests pass** with zero failures, errors or skips, and
the debug app and Android-test APKs build successfully. The read-only species
location analyzer separately passes **17 host tests**, including preventing its
output from overwriting an input archive.

API 34 emulator instrumentation reports **OK (4 tests)**. Real bitmaps pass
through the live witness, Custody, Match, Timeline and typed compact archive.
Centered acquisition records both measured bars without a wide scan; impossible
low-green fragments produce no geometry/attacks/energy, while subsequent red
low-HP bars retain their actual source times. Existing disabled/missing-frame,
append-delay, normal-result and terminal-summary checks also pass.

The tested app was installed with `adb install -r` on the stopped Moondrop and
launched successfully. The installed APK hash matches the tested build:
`90ecb1227a12835779e54effd79d46ab9b0042ac64ef1a2e4c18e4b11f3f73b8`.
The Android-test APK hash is
`f16f144c1b5898793d8e0e3f353576a751df0543bf11f508b7988402b4789cab`.
No instrumentation was run on the user's phone. The disposable emulator was
stopped afterward to release its memory.

Readable pre/post-install backups each contain 404 files / 319,913,984 tar bytes,
covering databases, preferences and app files except the existing loose artifact
store (`files/artifacts`, left untouched). All 402 other files are byte-identical;
only the Firestore cache and installation profile marker changed. No backed-up
files were added or removed. Teams, owned cards, calibration, preferences and
match archives are unchanged. Detailed test logs, hashes and inventory comparisons
remain in the private match-review folder.

Fresh real-match validation remains necessary for observation rate and live
Fast Move accuracy. The preceding recording identifies this failure and the HP
CPU bottleneck; it cannot measure the speed of code installed afterward.
