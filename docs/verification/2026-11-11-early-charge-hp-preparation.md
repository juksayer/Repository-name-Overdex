# Early preparation of charged-move HP capture — November 11, 2026

## Scope and acceptance boundary

The dedicated announcement/QTE reader can now prepare the existing bounded
charge-camera HP reader as soon as it accepts a qualifying phase reading, before
waiting for that reading's raw, accepted and typed Articles to finish publication.
This is a scheduling handoff within one Match. It does not publish an attack,
impact, damage, zero HP or faint, and it does not change the original evidence
chain or any saved archive entries.

Implementation and test definitions were checked against the canonical checkout.
The focused 102 tests and full 2,682-test suite passed, the debug APK built
successfully, and the matching APK was installed on the Moondrop. Fresh
live-battle acceptance remains **pending**. No improvement in phone capture
timing or recovery of either missing final faint is claimed here.

## Recorded reason for the change

The two matches in `Screen_recording_20261111_190345.mp4` were preserved under
`/home/sean/overdex-device-backups/2026-11-11-moondrop-1903-review`.
The [original connection review](2026-11-11-moondrop-1903-connection-review.md)
remains a record of the preceding build's behavior.

The first match's final incoming cue illustrates an avoidable wait *after*
recognition. Times below are relative to that archive's own Match Start.
Recognition completion is reconstructed from the reader's recorded total
duration; publication times are Article `recordedAt` values, not exact collector
receipt times.

| Stage | Recorded timing |
| --- | --- |
| Incoming text image captured | +139.664 s; crop `d1148925-06e1-4c47-90d6-141d30b5b064` |
| Shared OCR queue wait | 5.158 s |
| Capture to OCR admission | 6.195 s |
| Native recognition / processing including native recognition | 0.463 s / 0.583 s |
| Recognition complete | Approximately +146.443 s, or 6.779 s after capture |
| Raw OCR Article recorded | 8.978 s after capture |
| Accepted announcement Article recorded | 9.448 s after capture |
| Typed Attack Incoming Article recorded | Approximately +149.172 s, or 9.508 s after capture |
| Final Weather Ball announcement image captured | +146.686 s |

The old path therefore spent approximately **2.729 seconds** between recognized
phase text and typed cue publication. The new handoff removes the requirement
to wait through that publication path before preparing HP capture. It does not
remove the earlier OCR admission delay. The recorded interval identifies an
opportunity; it is not a measured 2.729-second improvement on a phone.

The preceding build did eventually open a charge window. Its retained crop at
+148.776 s shows an empty outline already moving left and clipped by the crop
boundary; the following retained crop has no bar. Complete-outline validation
correctly rejects the clipped fragment. This change does not loosen that
validation or reconstruct an unseen zero.

The second match remains a separate unresolved capture gap. Its final Mud Bomb
announcement was captured at +142.727 s, with a 3.983-second OCR queue wait and
typed publication about 4.756 seconds after capture. Post-recognition typed
publication added only about 0.192 seconds. No fresh accepted incoming/shield
preparation cue for that final phase appears in the archive. The compact archive
does not establish whether the needed image was missing, never offered, expired
or unreadable. Early handoff cannot supply an unrecognized cue; this change does
not prove a remedy for that second final hit.

## Implemented path and retained evidence

1. A crop Article already exists with its Match, provenance and original source
   clocks before the dedicated reader recognizes it.
2. After native recognition returns and cancellation is checked,
   [PersistedAnnouncementWitness](../../app/src/main/java/com/example/overdex/battle/observation/PersistedAnnouncementWitness.kt)
   offers its accepted text to the Match-owned
   [AcceptedChargeCapturePreparation](../../app/src/main/java/com/example/overdex/battle/observation/AcceptedChargeCapturePreparation.kt).
   The dedicated QTE vocabulary resolver runs before this offer; the original
   native OCR reading is still preserved unchanged afterward.
3. The handoff accepts only the paired announcement or QTE reader/crop contract,
   a source crop belonging to this Match, and valid source/recognition clocks no
   more than 12 seconds apart. Get Ready prepares the opponent's HP reader;
   Attack Incoming prepares the player's. Complete NICE/GREAT/EXCELLENT text is
   eligible as player-QTE support only through the dedicated QTE lane. Existing
   phrase-episode suppression controls duplicate/rearmed phase text.
4. [LiveActiveHpBarWitnesses](../../app/src/main/java/com/example/overdex/battle/observation/LiveActiveHpBarWitnesses.kt)
   checks the active Match, reader generation, enabled state and end state before
   applying the cue to the existing charge HP policy. The handoff retains no
   queued hints, crops or Articles and cannot replay a hint to a later subscriber.
5. Normal custody publication continues: preserved crop → original raw OCR →
   accepted text → typed phase testimony where that phase has a typed witness.
   The same evidence references and capture clocks remain attached. Scheduling
   diagnostics are separate raw receipts, not substitutes for that chain.

The ordinary OCR priority, pending crop selection, queue limits and native
recognition rate are unchanged. This is not a global priority increase or an
always-on centered HP search.

## Geometry, deadlines and lifecycle

[ChargedImpactHpWatchPolicy](../../app/src/main/java/com/example/overdex/battle/observation/ChargedImpactHpWatchPolicy.kt)
still requires positive ordinary HP geometry for the receiving side, measured
at or before the cue and within five source seconds. Centered charge geometry
cannot seed that ordinary side-owned reference.

Preparation keeps the cue's original capture time: the existing two-second
preparation delay and 12-second preparation deadline are not rebased to OCR
completion or receipt. A successful early preparation cannot be renewed by
later canonical delivery of the same cue, repeated phase text or later QTE
grades. Grades may support the existing opponent window without replacing its
token or anchor. Existing impact handling remains bounded to the earliest
supported closing cue, at most three seconds after that cue and 15 seconds from
preparation; repeated later impact readings cannot slide that deadline forward.

A `NO_REFERENCE` or `STALE_REFERENCE` early result leaves same-cue canonical
retry available if eligible pre-cue geometry arrives later. That retry keeps the
original cue time and cannot cross a newer cue, appearance boundary, stop,
resize or expiry. A newer trusted phase invalidates the previous recipient's
window even when its own reference is not yet available. A successfully opened
window is never reopened by this fallback.

Initial acquisition remains at most 10 Hz. After finding an actual complete
outline, the small rectangle can be tracked on each captured frame while its
anchor is fresh. Missing, obscured or incomplete pixels remain unavailable.
An accepted empty outline consumes the window; camera measurements cannot be
reclassified as ordinary Fast Move damage evidence.

[Match](../../app/src/main/java/com/example/overdex/battle/observation/Match.kt)
closes the handoff when it establishes Match End, before publishing the derived
end Article, and again on release. The HP reader also closes its active watches
on Match End. Explicit stop invalidates its generation and detaches the
subscriber, so a late native callback or an old listener cannot reopen capture.

## Sparse diagnostics for the next archive

The `CHARGED_HP_CAPTURE_SCHEDULING` source records these stages without emitting
an Article for every scanned frame:

| Stage | What it establishes |
| --- | --- |
| `EARLY_PREPARATION` | An accepted cue reached the active HP reader. Includes cue kind, policy result, recognition and receipt clocks, recognition delay and current window/reference/deadline description. The source crop is cited. |
| `FIRST_SCAN` | The first frame admitted to a given window's scan, with window token, recipient and preparation/reference/deadline description. This is not proof that the pixels were observable or that a bar was found. |
| `FIRST_ACCEPTED_SAMPLE` | The first geometry-accepted sample for that window, with recipient and classified fill color. It is not itself a faint and need not be the eventual terminal sample. |

The two first-stage markers are limited to once per window token. Early cue
receipts follow accepted phase/grade offers, including explicit rejected or
supporting policy results. Querying the diagnostic description does not consume
a scan or advance cadence. Existing terminal aggregate diagnostics and retained
HP measurement evidence remain necessary to interpret unavailable scans and
later samples. These sparse receipts do not provide a complete per-frame trace.

## Verification status

| Check | Status / intended coverage |
| --- | --- |
| [AcceptedChargeCapturePreparationTest](../../app/src/test/java/com/example/overdex/battle/observation/AcceptedChargeCapturePreparationTest.kt) | 11 passed. Trusted lane/provenance, original clocks, vocabulary, duplicate episodes, age bounds, missing subscriber, replacement listener, terminal close and lock/lifecycle behavior. |
| [EarlyChargeHpPreparationTest](../../app/src/test/java/com/example/overdex/battle/observation/EarlyChargeHpPreparationTest.kt) | 7 passed. Recorded +139.664/+146.443/+149.172 delay regression; no renewed deadline; complete geometry; no Fast Move reinterpretation; missing/stale reference retry and lifecycle negatives. Geometry is synthetic and does not claim a recovered archived KO. |
| [PersistedAnnouncementEarlyChargePreparationTest](../../app/src/test/java/com/example/overdex/battle/observation/PersistedAnnouncementEarlyChargePreparationTest.kt) | 6 passed. Deliberately blocked raw publication proves early preparation can occur while canonical evidence waits; later crop/raw/accepted/typed contents, references and clocks are checked. Includes QTE vocabulary, filtered text, late callback after stop, and Match End closure before end publication. |
| Focused charge-policy/QTE/reader checks | 102 tests across 13 suites passed; zero failures, errors or skips. |
| Full unit suite | 2,682 tests across 357 suites passed; zero failures, errors or skips. |
| Debug APK build | `:app:assembleDebug` succeeded. SHA-256: `e0b709a87640632c9bc602f0f1bcda8f4259e083e8e642579ab125e8a3583526`. |
| Device installation | Moondrop `MDPH00124112500414`, November 11 at 20:08:08 −06:00. Install succeeded and installed APK hash matches the built APK. |
| Fresh Moondrop battle | Pending. Inspect the diagnostic chain and real HP pixels; establish whether a useful scan actually preceded the terminal bar's disappearance. |

Run logs and machine-readable summaries are retained alongside the original
backup: `early-charge-focused-tests.log`, `early-charge-focused-summary.json`,
`early-charge-full-tests-build.log` and `early-charge-test-summary.json`.

Installation was preceded by a verified 453-file records/settings backup and a
check that capture was not active. After installation, all 452 application
records/preferences/calibration files were byte-identical, with no added files.
The sole changed file was the Firestore cache database; before/after SQLite
`quick_check` returned `ok`, with identical table row counts. This is recorded
in `early-charge-update-verification.json`; it is not a claim that every backup
byte was unchanged. The app launch was accepted, but the device keyguard was
showing, so this installation did not establish Replay or live-battle acceptance.

The next recording should distinguish a cue that reached the reader early,
a preparation rejected for missing geometry, a window that scanned unavailable
pixels, and a window that accepted a real bar. Native OCR queue delay and the
second match's missing final preparation cue remain separate work; this report
does not promote either into a solved problem.
