# Coherent Fast Move audio comparisons

November 4, 2026.

## Change

`FastMoveReferenceMatcher` now measures the original spectrum and the changing
spectrum over the same query/reference passage. Previously, each component could
choose a different maximum, while the published offsets described only the
changing component. Two individually good scores therefore did not necessarily
describe the same sound.

The new `spectral-sequence-v3` comparison chooses one passage by the mean of its
two similarities and publishes both scores and offsets from that passage. The
existing independent acceptance thresholds, legal active-species candidate
pools, reference assets, short-reference coverage, and archive fields remain
unchanged. Historical v1/v2 measurements remain readable; they are not relabeled
as coherent v3 measurements. This change does not create attacks or energy from
audio alone.

## Verification

- The full JVM suite passed: **1,675 tests across 247 suites**, with no failures,
  errors, or skipped tests. The debug APK built successfully.
- New regressions reject combining perfect spectral matches from different
  passages, check the selected passage's offsets, and cover v1/v2/v3 compatibility.
- An inference regression uses the recorded Incinerate/Ember ambiguity, a single
  2.620-second outgoing HP interval, and blank announcement/control text. The
  player's identity and preference correctly remain unresolved.
- An offline production-matcher check covered 55 source windows from the latest
  match, seven clean references, 11 October 15 recorded fixtures, 16 November 1/2
  held-out fixtures, and 25 cry/stat/music negatives. All fixture expectations
  passed. These categories are different checks, not 114 independently labeled
  battles or a live accuracy percentage.
- A private checker replayed all 55 windows through production cadence inference
  in original publication order, first with the archived measurements, then with
  new v3 measurements of the original PCM. The supported move identities remained
  the same. No additional Ember identity was published. The extra accepted raw
  Hex comparison is not independently verified opponent ground truth.

The source archive was not edited. Private rematches retain their own IDs,
original source references, asset hashes, and implementation hashes outside the
repository.

## Remaining recognition gap

The user confirmed that Turtonator, Camerupt, and Gourgeist all had Incinerate.
This repairs score integrity; it does **not** establish earlier Turtonator
identification or earlier Camerupt confirmation in this recording.

The 33.927-second audio window overlaps a visible **Attack incoming!** announcement
in the retained 34.170-second crop, although the reader supplied no typed
`AttackIncoming` testimony. Times here are relative to `BattleOverlayOpened`;
this archive has no witnessed GO/`MatchStarted` boundary. All 44 charge-button
labels are blank, and all 134 button-height measurements are unavailable
(`UNKNOWN_RIM` or `UNKNOWN_SEPARATOR`). They cannot prove ordinary play was
visible. A missing phase observation is not evidence that no charged phase
occurred.

The next bounded recognition slice should repair the missed charged-phase cue
and establish positive ordinary-play evidence. A later joint audio/HP identity
path needs that evidence, stable raw HP geometry, distinct supporting impacts,
and preserved provenance. Lowering audio thresholds or treating a single legal
move as an observed attack would bypass the unresolved problem.

## Device boundary

The debug update was installed and launched on the Moondrop
`MDPH00124112500414` after confirming Droidball was stopped. The installed APK
SHA-256 is
`57e2bd83d846f4ccdfa5971204951240ac90815e89d227b4d933c06dd7ecd693`.
Existing recordings, calibration, and collection data were retained. A fresh
battle on this build is still needed to assess live identification speed and
phone performance.
