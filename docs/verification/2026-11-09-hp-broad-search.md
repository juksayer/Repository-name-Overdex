# Broad HP search: skip absent outline colors

November 9, 2026. The user asked to continue reducing broad HP search cost after
the latest Moondrop recordings still showed geometry dominating locator CPU.
The retained change skips impossible row scans while preserving detector output.
It does not narrow calibration or change recovery attention.

## Retained implementation

`ActiveHpBarMeasurer.HpPixels` already classifies each source pixel for neutral,
orange and blue outlines, saturated fill, and green fill. It now also accumulates
the bitwise union of each row's classifications. Before either ordinary or
contrast run detection for an outline color, the detector skips a row that has
none of that color. A contrast run still requires the current pixel to carry
the color, so the same absence rule applies to both searches.

Classification uses a row-outer loop with one row-mask store after each inner
pixel loop. It adds no division or row-boundary check per pixel. Metadata belongs
to this measurement's pixel buffer and is never reused across source frames.
Oversized backing arrays, including a partial final row, retain their existing
supported behavior; only the requested image dimensions enter detection.

All color thresholds, allowed gaps, outline enumeration/order, paired-edge
checks, cap recovery, interrupted-fill rejection, fill fractions, confidence,
deduplication and sorting remain unchanged. `LiveBattlefieldHpTracker`, its
midpoint/local windows, 250 ms recovery rule, side ownership, source clocks,
terminal/charged HP watches and Fast Move inference were not changed.

## Real Android differential comparison

The Moondrop MD_PH_001, API 34, ran both APKs through independent classloaders
inside one isolated Android shell process. Overdex capture was inactive. No app
installation, restart or private-data change was needed for this check. Only the
harness was on the process classpath; the app variants were loaded separately.

The harness alternated variant order, warmed each 16 times, then collected 32
paired CPU and elapsed samples for each of **71 cases**. Bitmap decode and pixel
extraction occurred outside timing. Each pair required an exact match of the
entire ordered candidate list: count, coordinates, fill and confidence. Every
comparison passed. There were 35 candidate-positive and 36 candidate-negative
cases; these labels describe detector output, not independent accuracy labels.

The cases retain the previous 21 recorded/generated workloads plus 25 full frames
from the newly reviewed Moondrop matches and 25 broad-band derivatives. The new
sources include low HP, camera movement, forced choice, and black game surfaces.
Same-match archived crop testimony establishes the 672 × 653 broad band at
`[0,367,672,1020]` in 672 × 1493 sources; expected bar width is 192. Original
images and hashes, source paths, crop bounds and cited geometry Articles remain
in the private workload manifest. Loose-frame modification times are not treated
as exact source clocks. Full frames and their derivatives are related cases.

| Recorded workload | Baseline CPU median | Retained CPU median | Reduction |
| --- | ---: | ---: | ---: |
| Sierra broad search band | 27.727 ms | 21.345 ms | 23.02% |
| Sierra player tracking window | 3.300 ms | 2.456 ms | 25.56% |
| Sierra opponent tracking window | 3.362 ms | 2.616 ms | 22.19% |

Among the **10 new candidate-positive broad bands**, the median per-case CPU
reduction was **25.74%**, ranging from **13.83% to 37.29%**, with no regressions.
The 15 negative broad bands improved by a median 24.25%, ranging from 9.49% to
77.37%. All 25 new broad bands and all 25 full frames improved; their median
per-case reductions were 24.25% and 24.03%, respectively. Positive cases are
reported separately so black/menu scenes do not stand in for battle geometry.
Tiny generated examples are not used for a general speed claim.

These are targeted pixel-detector CPU measurements. They exclude capture,
pixel acquisition, tracker assignment, other live readers, and source-to-Timeline
latency. They do not establish a sustained frame-rate gain or recognition
accuracy. A fresh live battle on the updated build remains the acceptance test
for delivered-reader gaps, locator CPU and preserved accepted HP testimony.

## Rejected first experiment

A preliminary lazy fill gate postponed the top-only saturated-fill scan until a
compatible lower outline existed. All 71 ordered outputs also matched, but
Sierra broad-band CPU rose from 26.713 to 29.364 ms, and newer cases had small,
mixed gains. That gate was reverted before the retained row-color comparison.
Its source, APK and raw results remain available privately. Do not combine its
separate-run baseline timings with the retained experiment's measurements.

## Validation and reproduction

All **2,236 unit tests pass**, with zero failures, errors or skips, and the debug
APK builds successfully. The added regression compares complete ordered outputs
from a recorded crop's packed pixel buffer against buffers with an unused full
row or a full row plus a partial colored tail. Existing HP geometry, obstruction,
color/fill, tracking, acquisition and diagnostics tests remain passing.

Private reproduction root:
`/home/sean/overdex-device-backups/2026-11-09-hp-broad-search/`.
It retains the Java/D8 harness, all workload hashes and provenance, both original
and experimental source snapshots/APKs, raw paired samples, summaries and build
logs. The isolated device files are under
`/data/local/tmp/odx-hp-broad-20261109/`.

- Baseline APK SHA-256:
  `173598abe7922cf7509318228a2a00a4261d40cba533be8603bb80fbbef82a25`.
- Retained row-color APK SHA-256:
  `5265a9705b166d16363a9bb09732aed266182f338079bc6a21a6f10e7a18135b`.
- Rejected lazy-fill APK SHA-256:
  `aa776fe6b951c43fc5ff6231673c920d178e672b612a3a52e863a8a45757e4f8`.

The retained APK has **not been installed** as part of this task. The benchmark
executes its detector without replacing the user's app. No source archives,
calibration profiles, battle records or unrelated pending work were modified.
