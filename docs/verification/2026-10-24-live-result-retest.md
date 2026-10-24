# Live result and retained-badge retest, October 24

## Recording and custody

Reviewed the Moondrop recording beginning at **11:45:50 CDT**, after the 11:37:11
install containing `1b67ee5` and `7cab769`. Match ID:
`d8b9fd1b-47d1-46b6-83f2-61a1cc2efb88`.

The desktop copy and review material are under
`/home/sean/overdex-device-backups/2026-10-24-result-retest/`:

- Archive: **7,200,941 bytes**, **1,973 Articles**, **225 referenced artifacts**.
- Phone and desktop archive SHA-256 agree:
  `a7c14d2ec0af6d0c15d47bd51ae3cfb2acb4d904832ff03ea46aa9492851a55b`.
- Every referenced artifact's size and SHA-256 verify.
- **59 separately retained full frames** were copied and verified against their
  content hashes. Their filesystem times help browse the images but are not
  event timestamps.
- `review-summary.json`, `frames-index.json`, badge contact sheets, selected full
  frames, and extracted result/resource crops preserve the review findings.

Only Turtonator was witnessed active for the player. The opponents were
Combusken, Magcargo, and Flareon. The recorded result is **WIN**.

## Retained Poké Balls: live acceptance

Times below are seconds after recording began, using Article wall timestamps.

| First reading of count | Source time | Published time |
| --- | --- | --- |
| 3 opponent Poké Balls | 36.326 | 39.448 |
| 2 opponent Poké Balls | 73.569 | 75.232 |
| 1 opponent Poké Ball | 100.647 | 102.504 |

No later count increased. The independently saved full frames also show the
correct retained images during charged-move sequences: Magcargo with two bright
balls and one dark ball, then Flareon with one bright ball and two dark balls.
The HUD marks Combusken and Magcargo fainted. The previous failure—an old
three-ball screenshot returning above already-fainted cards—does not recur in
these saved frames.

There are **two explicit opponent faint Articles**, not three. No zero-ball
measurement was captured for Flareon; the independent win text establishes the
outcome. The saved full frames do not extend beyond result publication far
enough to verify the final overlay clearing visually.

## Result recognition: live acceptance

The preserved result crop visibly contains upright **YOU WIN!**. Native OCR
returned `YOWIN!`; the existing phrase resolver accepted that one-character
omission. This match used one OCR pass, so it validates prompt scheduling and
normal phrase resolution, not the white-letter retry path covered by the
previous recorded-frame device tests.

| Stage | Source time | Published time |
| --- | --- | --- |
| Native result text | 127.264 | 129.035 |
| YOU_WIN_WITNESS | 127.264 | 129.039 |
| MatchEnded(WIN) | 127.264 | 129.046 |

Exactly one MatchEnded was recorded, **1.782 seconds after its source frame**.
The native read, accepted result, and MatchEnded retain that frame's original
source timestamps. This is capture-to-publication latency; it is not a claim
about the first potentially unsampled instant the result appeared on screen.
The preceding faulty recording's correct result read took **62.337 seconds**.

The new diagnostic for crop `f12663c1-0858-4fc1-87cb-9d3eb5ebfb02` reports:

```text
queueMs=600 captureToAdmissionMs=1160 nativeReadMs=435
processingMs=609 totalMs=1769 passes=1 pending=7 nonblank=true
```

Earlier nonblank result-region reads were `XX` on the intervening reward screen,
not missed win phrases. A remaining seven-item backlog did not strand this win
behind it. This is one successful live retest, not an all-device latency bound.

## Fast Move observations and remaining work

- The archive contains **14 player and 19 opponent FastMoveUseObserved Articles**.
  These are recorded counts, not independently verified attack totals.
- Turtonator's Incinerate identity comes from its configured move and witnessed
  active species. Combusken's Ember identity explicitly carries a higher-energy
  tie-break basis and therefore does not earn a confirmation checkmark.
- `≈ Ember` also appears for Magcargo and Flareon. This initially looked like a
  stale label, but the current bundled move catalogue permits Ember for both.
  With Ember at 9 energy / 2 turns, the forecast deliberately prefers it over
  Magcargo's Incinerate at 20 / 5 and Rock Throw at 5 / 2. Forecast identities and
  candidate pools are keyed by species. The repeated label alone is not evidence
  of a handoff defect; it is an unresolved, explicitly marked assumption.
- Audio was present: capture status reports **48 kHz, two channels, signal
  present**. Thirteen Fast Move sound analyses were preserved.
- Around recording second **75.326**, during Magcargo's appearance, the audio
  matcher accepted Rock Throw as its leading reference (similarity **0.9704**,
  next Incinerate **0.9431**). Both `attackingSide` and `cueAttackingSide` remained
  null. That similarity is not a calibrated probability or a confirmed opponent
  move. The archive contains no corresponding Magcargo FastMoveIdentified.

The next investigation should connect these preserved audio candidates to the
correct active side and its visual impact timing, while keeping the player's
Incinerate sound as a competing explanation. Repeated, corroborated evidence
can narrow the forecast; a single unassigned sound should not become a
checkmarked identity or an invented attack.

## Scope of this follow-up

This was a read-only phone/archive review and a documentation checkpoint. No
production code, calibration, app data, or recorded Articles changed. No APK
rebuild or reinstall was needed. Prior unit/device results remain documented in
[the result-reader fix](2026-10-24-outcome-latency.md) and
[the retained-badge fix](2026-10-24-retained-badge-faints.md).
