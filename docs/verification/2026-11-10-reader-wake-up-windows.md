# Reader wake-up windows — 2026-11-10

Implemented, independently reviewed, and locally tested. Not installed or validated in a new physical-phone battle.

## Change

`BoundedCaptureAssignments` now starts its three-second acquisition budget when an applicable request is received. Cue source time still controls ordering and deduplication; it is never rewritten in the Timeline. Both `TeamResourceSnapshotGate` and `InactiveBenchSnapshotGate` use this behavior. This covers Poké Balls, shields, and inactive-card HP/sprite crops.

The Pixel and Moondrop reviews found cues arriving after the previous source-time deadline, including delays of 5.301–10 seconds and near-expiry arrivals leaving only 74–571 milliseconds. Fresh acquisition now admits only frames captured at or after receipt. A queued older measurement cannot close the new assignment. A successful measurement newer than the cue prevents redundant acquisition, and repeated copies of the same cue cannot extend or reopen an expired window.

No arbitrary maximum cue age is added to these measurement-only requests. Same-attempt source ordering, newer requests and measurements, and lifecycle boundaries determine applicability. Older stop/result/restart messages cannot cancel or rewind a newer observation attempt. A later same-name badge confirmation alone does not invalidate an otherwise relevant delayed check; a newer appearance request supersedes older side-specific hints.

## Validation

| Check | Result |
| --- | --- |
| Resource scheduling tests | 22 passed |
| Inactive-bench scheduling tests | 12 passed |
| Full unit suite | 2,353 tests, 309 suites; zero failures, errors, or skips |
| Debug APK | Built successfully |
| Independent source review | No actionable issues found |
| Diff whitespace | Passed |
| Rule-list local source links | All 40 resolve |
| Installation / live battle | Not performed |

Private logs, focused XML results, exact source hashes, and the preserved APK are under `/home/sean/overdex-device-backups/2026-11-10-reader-wakeup-fix/`. The preserved APK is `overdex-reader-wakeup-debug.apk`, SHA-256 `15987a11a5e1c125694e50c842f41d4c6d4e28c015a5d2bd7ee234e2f59963c1`.

This build includes the existing working-tree changes present before this task. They were preserved, not reset or committed.

## Acceptance boundary

This fixes expired acquisition opportunities; it does not establish complete faint recognition. The count readers still finish an assignment on a usable confirmed count, including an unchanged count. If a departure cue is followed by an unchanged count and the ball darkens later, another cue may still be needed. Validate that sequence separately before changing confirmation semantics.

Neither cry-only nor cry-plus-HP-geometry faint inference was enabled. The [battle event rule list](../reference/battle-event-rules.md) distinguishes current behavior from a proposed independent matching-cry, verified-zero-HP, and departure path. No archived match records were rewritten.

Next physical acceptance: compare a complete battle recording with the resulting archive; verify fresh resource capture after delayed identity cues, retained count decreases and faint attribution, reader sleep after completion, and cancellation at match end. Include a returning damaged Pokémon and a zero/departure sequence where ball darkening follows the first unchanged reading.
