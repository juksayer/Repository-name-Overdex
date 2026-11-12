# Reusing a captured no-shields prompt — November 11, 2026

## Recorded finding

The second match in `Screen_recording_20261111_190345.mp4` contains a usable
preparation cue that the live announcement readers did not receive. The existing
result reader captured and correctly read **No Protect Shields remaining!**.
This resolves the narrower question left open in the earlier
[preparation report](2026-11-11-early-charge-hp-preparation.md); it does not recover
the missing final zero or faint from that archive.

| Observation | Source evidence |
| --- | --- |
| Match | `e6fa7745-8827-4fd0-a4ec-659d1bbf7ad7` |
| Prompt image | `87936339-bf17-4117-9448-d2d1eb858337`, Match +141.205292 s |
| Correct raw OCR | `a5ec92e7-b8c7-4463-945e-cfa70e34e28a` |
| Prompt location | Source x168–504, y696–725 in a 672 × 1493 frame |
| Dedicated crop coverage | Announcement y326–375; QTE progress y425–508. Neither covers this message. |
| Recognition completion | Approximately +144.571292 s, reconstructed from the recorded 3,366 ms duration |
| Eligible player reference | Camerupt's ordinary bar at +140.847664 s, 357.629 ms before the prompt; a second measurement is co-timed with the prompt |
| Final Mud Bomb announcement capture | +142.727025 s |
| Last archived player HP | Nonzero at +143.986838 s |

Recognition completed about 1.844 seconds after the move-announcement image.
This proves an available routing opportunity, not that the new reader would
necessarily catch the terminal bar before it disappears. The source archive
does not contain the final player zero or faint. Stunfisk's thin red remainder
and subsequent attack remain evidence that it was still conscious.

The [recorded fixture](../../app/src/test/resources/battle/recorded-evidence/2026-11-11-no-shields-phase/README.md)
contains 36 literal original Article objects with complete reference closure,
the unchanged 464 × 217 PNG, and hashes/provenance. Original archives, raw OCR
treatments, IDs, times and source assets remain unchanged.

## Narrow implementation

- The result reader reuses its existing OCR candidate before publishing latency
  and raw testimony. There is no extra image capture, OCR request, queue priority
  change, or wait for publication.
- `OutcomeShieldPromptEvidence` accepts only the complete no-shields phrase from
  an official result crop. Retained words must reconstruct the phrase; their
  finite, positive boxes must fit inside the original crop and avoid the
  capture-time overlay. Missing geometry, fragments, other shield text, grades,
  Get Ready and unrelated words cannot use this path. The optional OCR source
  width may be absent, as it is in the real recording. Shadow aura color behind
  native white letters is not an automatic rejection.
- The Match-owned early handoff permits only incoming-charge preparation from
  that validated reading. It retains the crop reference and original capture
  clock. Its 12-second age bound and the existing HP policy's ownership,
  pre-cue geometry, lifecycle and source-time deadlines still apply.
- The existing incoming-attack witness independently consumes the preserved raw
  OCR. It checks the cited crop, Match and both capture clocks, then publishes
  typed `AttackIncoming` citing the raw reading and crop. This canonical path
  adds no dependency to result-reader publication. The raw reading is not
  rewritten, promoted into a result, or used as evidence of damage or faint.
- Invalid and blank outcome readings are not forwarded to phase deduplication.
  The result lanes can finish out of source order; a newer blank must not hide an
  older valid prompt. Positive readings share incoming-episode suppression with
  the existing announcement/QTE lanes. Repeated treatments of one capture cannot
  create another phase. A witnessed charge completion retires preceding prompt
  visibility by source time in both the early and canonical paths. A delayed
  historical prompt may still be preserved without suppressing the next warning;
  duplicate frames of that historical prompt remain deduplicated. Without a
  witnessed completion, the existing six-second visibility expiry remains the
  fallback. Unordered result-crop blanks are not completion evidence.
  The completion watermark is not a full reconstruction of multiple historical
  phases all delivered after their newest completion; arbitrary historical
  episode reordering is not claimed solved.
- A late incoming/Get Ready cue can join the next compatible already-completed
  charge interval in Fast Move accounting. It extends only that interval's
  beginning and preserves the witnessed closing time and 750 ms tail. It cannot
  borrow completion across an appearance boundary, conflicting/intervening
  phase, restart, Match end or the existing 12-second phase bound. Supported
  Fast Moves after the completed tail therefore remain countable. A genuinely
  newer charge still opens its own pause.

## Validation and remaining boundary

Focused tests cover the original crop/OCR and eligible Camerupt geometry, source
deadlines, duplicate and reordered observations, invalid provenance, overlay
exclusion, cancellation, match end and subsequent result recognition. The early
scheduling test verifies delivery before custody submission; canonical tests
verify the later source chain rather than treating a scheduling hint as testimony.
Direct inference and actual Match tests deliberately deliver charge completion
before its earlier prompt, retain a synthetic supported post-charge Fast Move,
and verify that a subsequent warning does not wait for old visibility to expire.

The focused run passed **113 tests across 9 suites**, and the complete unit
suite passed **2,710 tests across 359 suites**, with zero failures, errors or
skips. The debug APK built successfully, SHA-256
`a48c0ea8653a71bd15f8d4062f21abd4c88ecc452ff3b5d4942888e2ff14305e`.
Logs and machine-readable summaries are retained in the original review backup
under `outcome-shield-focused-*` and `outcome-shield-{full-tests-build.log,test-summary.json}`.

Installed on Moondrop `MDPH00124112500414` at **21:03:57 −06:00** on November 11.
The installed APK hash matches the tested build. Capture was inactive before
replacement, and Android subsequently reported Overdex's MainActivity as the
resumed activity. This is launch evidence, not live recognition acceptance.

A readable 453-file records/settings backup was created before installation.
Immediate pre/post-install hashes show 452 unchanged files, no added files, and
only the Firestore cache database changed. Match archives, records, calibration,
preferences and Replay source files were unchanged. The exact file comparison
and install result are saved in `outcome-shield-update-verification.json` beside
the backup; this is not a claim that every database byte stayed identical.

Fresh phone acceptance remains pending. The next match should establish
whether preparation was received, a scan occurred, and a complete bar was
actually measured before disappearance. OCR queue delay remains a separate
constraint; no terminal zero or finishing attack is synthesized by this change.
