# Active type-icon geometry and complete readings

## Problem and change

The November 4 Moondrop battle retained 72 player type-icon crops containing
Poison/Flying, Ghost/Poison, and Poison/Dark badges. The old matcher accepted
Water or Electric instead. These were matcher errors inside correctly positioned
saved crops; this repair does not change calibration profiles or source images.

The matcher now measures each colored disk from its circular RGB boundary and
refines its center and radius in half-pixel steps using only that boundary. Crop
height, surrounding margins, and the width of a particular white glyph no longer
choose the badge scale. It compares the retained official reference glyph at the
observed scale, including antialiased stroke coverage. Every candidate uses the
same shape/color score for ranking and acceptance. Neutral Dark and bright
Electric colors are valid measurements; unavailable color is not a black sample.

Acceptance retains the combined-score threshold of 0.40 and additionally requires
shape support of at least 0.45 and a winner margin of at least 0.04. These are
matching scores, not calibrated probabilities. Ambiguous, unreadable, or detected
clipped disks remain unknown. A mostly clipped sibling must agree with the
complete badge's horizontal row and scale; background arcs elsewhere cannot
create a missing badge. Proposal queues and the geometry search are bounded.

The witness publishes only complete one- or two-badge readings. An unknown
member of a pair, duplicate type, or third badge prevents publication instead of
silently turning a partial pair into a single-type conclusion. The original crop
and its provenance remain evidence. Previously archived Articles are unchanged.

## Audio observation

The user's latest observation is that Fast Move stutter repeats audio while the
visual animation remains unaffected. This qualifies the current report; it does
not rewrite earlier historical reports of visual stutter. A read-only audit
confirmed that audio alone cannot create canonical Fast Move uses or standalone
energy credits: independent HP-impact evidence remains necessary. Audio can
still affect move identity and the resolution of independently observed pending
hits. No audio inference or energy rules change in this repair.

## Verification

- Focused JVM checks: 11 matcher tests and four complete-reading tests pass.
  They cover all 18 official glyphs at 24/30/42 pixels, asymmetric crop margins,
  color availability, ties, unreadable disks, excess badges, clipping, and
  unrelated background arcs outside the badge row.
- Android API 34 emulator: `ActiveTypeIconReplayAndroidTest` reports
  **OK (9 tests)** with no skips. Public cases include the retained August battle
  screenshot, all 18 types at three scales and in pairs, text/sky/solid negatives,
  unknown second glyphs, and shallow/deep clipping. The old opponent test rectangle
  pointed at sky; its corrected rectangle is test-only. Saved profiles are unchanged.
- The Android run loads a private manifest of 86 SHA-256-verified source images:
  72 unchanged November 4 archive crops, six clean video crops, four pointer-strip
  obstructions, and four deliberately deep-clipped crops. **86/86 decisions match
  the independently reviewed labels**: all 78 unobscured crops identify their
  expected complete type list; all eight negatives remain unavailable. No raw
  phone archive, video, fixture PNG, or data backup is added to the repository.
- Independent review also compiles the production pixel core outside Android and
  passes all 86 private cases and 11 JVM matcher tests. No remaining review blocker.
- Final full JVM suite: **1,665 tests across 247 suites**, zero failures, errors,
  or skips. Debug APK build passes.
- A separate quiet-emulator run of the 86-case method reports **OK (1 test)**,
  with the same correct decisions. Median matching time is 99.4 ms, p90 158.5 ms,
  mean 112.6 ms, maximum 517.0 ms. The earlier run overlapped the full JVM suite
  and had multi-second scheduling outliers. These emulator measurements do not
  establish physical-phone latency or live frame delivery.
- Installed and launched the tested debug APK on idle Moondrop
  `MDPH00124112500414`; installed SHA-256 matches the build:
  `2735c3599326110e719847f3a71aa79a5bace42db7f0f9fe96b221f0b058f1d6`.
  Fresh before/after backups contain 336 files each. All 314 compared user-data,
  current-team, and calibration files are unchanged. All four Room tables have
  identical logical contents, including 29 owned Pokemon and 1,026 species. The
  reviewed match archive is unchanged. No instrumentation runs on the phone.

Private source manifests, native results, independent diagnostics, build logs,
install proof, and data backups are under
`/home/sean/overdex-device-backups/2026-11-04-type-icon-repair/`.

## Remaining boundary

This establishes the repair against the retained examples and Android image
handling. A fresh recorded battle is still required to check live type readings,
phone latency, downstream audio acceptance, and Fast Move counts. It does not
claim to fix the game's audio stutter, missing charged-move observations, duplicate
Fast Move timing evidence, or result-reading coverage. Those remain separate
items. A completely absent badge outside the crop cannot be reconstructed by
this pixel matcher; detected incomplete or uncertain rows are withheld.
