# Controlled finishing moves: two recordings and the retained archive

The user supplied the November 7 match after the Replay fixes: two Earth Power
finishes and one Dragon Pulse finish. Review confirms those three finishes,
plus one Earth Power that Obstagoon survives. This is useful live acceptance
evidence; it does not establish that capture has been repaired.

## Source integrity and viewpoints

- Moondrop archive: `34d20b55-51d3-49d4-a47f-61c463ed66b9`, 15:30:44 local.
  Desktop and phone copies agree: 5,247,785 bytes, SHA-256
  `0a99d7dd667bd9f04565f338db19f439056725ddadcb5aaa8cd177aa3cdc0231`.
  All 306 declared artifacts verify; 2,336 Articles and 308 ZIP entries.
- Capture APK on the Moondrop matches the previous tested build,
  `4e921c40755da7f35d52ef1cef7d31a66b6a59c887e7b81b0fcccde1cd97ff7e`.
- Supplied `Screen_recording_20261107_153221.mp4`: 816 × 1808, 154.969478 s,
  2,333 variable-rate frames, no audio track. It visibly follows the attacking
  Turtonator → Camerupt team, matching the Moondrop archive's player.
- The separate Pixel recording was located and copied read-only from
  `Movies/screen-20261107-153437.mp4`: 1080 × 2280, 139.0749 s, with mono
  44.1 kHz AAC audio. It follows shadow Swampert → shadow Quagsire → Obstagoon,
  the Moondrop opponent. Device descriptions do not override visible actors.

Private originals, source hashes, actual-PTS native frames, image alignment,
audio windows and reproducible analyses remain under
`/home/sean/overdex-device-backups/2026-11-07-finishing-test-review/`.

The canonical start is `COUNTDOWN_AND_COMBAT_START_INFERENCE`, confidence 0.75,
at `147519300609945` ns, supported by countdown 2 and subsequent battle evidence.
There is no recorded GO glyph. Four independent retained-image matches align
the supplied video to this inferred start by approximately +5.38 ±0.06 s.
This is an alignment, not a replacement source clock or a newly observed GO.

## Results of the four charged attacks

Times below use the supplied attacking-view video; each recording retains its
own clock. More precise onset/impact/appearance brackets remain in the private
analysis rather than treating a sample as an exact game tick.

| Attack | Visible evidence | Canonical archive |
| --- | --- | --- |
| Dragon Pulse → Swampert | Banner around 30.3 s; HP empty by 32.6823 s; absent by 33.1765 s. | Move name and charge cue missed. Later 3 → 2 ball decrease and named Swampert faint retained. |
| Earth Power → Quagsire | Banner around 76.4 s; empty/absent by 79.1983 s. | Raw `Camerupt used Earth Powers` and generic charge cue retained; name resolution rejected. Later 2 → 1 decrease and Quagsire faint retained. |
| Earth Power → Obstagoon, survives | Banner around 100 s; Obstagoon survives with red HP. | Get Ready and approximately 69.1% → 23.4% HP retained; move name missed. |
| Earth Power → Obstagoon, final KO | Banner around 121 s; falls 124.2423–124.2806 s; absent by 124.9222 s; WIN appears around 128.4 s. | Move, final HP loss and faint missed. WIN retained. Last opponent HP is approximately 7.98%. |

The original archive contains zero named charged-energy spends, zero accepted
QTE grade texts, and no accepted zero-fill HP measurement. The two retained
faint/count source times themselves lag the video, separately from publication
delay. Replacement full bars preceding a late species badge cannot be treated
as the outgoing actor healing. All raw Articles remain unchanged.

The deliberately idle side has **zero canonical Fast Move uses**; all 16 uses
are player-side. One use still carries Turtonator's old identity after the
Camerupt entry, a separate unresolved attribution issue. All three configured
player members have Incinerate; move-name agreement does not cure stale actors.

## Cry evidence from the Pixel audio

The Pixel audio contains strong raw-waveform matches to bundled species cries
near all three finishes. Exploratory normalized absolute PCM correlations at
original speed are approximately 0.802 for Swampert, 0.945 for Quagsire and
0.979 for Obstagoon. These are waveform similarities, **not probabilities**.
The comparison uses limited species controls and is not a full-corpus accuracy
score or the production envelope matcher's output.

The expected cries are absent from the inspected ordinary Incinerate-hit
controls, and Obstagoon's nonfinishing Earth Power control has only 0.065
similarity. However, the same cry also matches Quagsire's and Obstagoon's entry
windows (approximately 0.982 and 0.969). The sound can corroborate an outgoing
identity and visual KO sequence; sound alone cannot distinguish faint from
entry or establish an exact faint instant. Swampert's selected entry control
remains unresolved. No external audio measurement was inserted into Timeline.

The cry PCM is also retained **inside the Overdex archive**, without a typed cry
Article. The final `FAST_MOVE_AUDIO_STREAM` clip begins at inferred-start-relative
118.753485 s and contains approximately 0.56175 s of the Obstagoon reference's
tail, at reference offset 0.95325 s and waveform correlation 0.94658. This is an
explicit partial-reference match, not a claimed full cry or precise faint time.
Archived Swampert audio also matches its cry, and the retained 13-second
`CHARGED_MOVE_EFFECT` clip contains Quagsire's cry. The final clip's source time
marks its start, as defined by the capture producer. Missing cry testimony must
not be described as missing PCM. Recognition of that retained sound and its
event interpretation remain separate work.

## Bounded fixes and remaining capture work

The legal charged-move resolver now permits a single trailing `S` after a
complete legal move name, retaining the existing OCR repair rank/basis. It does
not accept arbitrary suffixes, clipped prefixes, illegal movepools, ambiguous
performers or ambiguous typed aliases. Raw `Earth Powers` stays intact. Live
inference can resolve this future reading, while completed Replay can recover
the one preserved Earth Power from the current archive and legal catalogue.
That recovery supplies no missing other attacks, canonical energy or final KO.

Announcement readings in this match have source-to-publication delays of
roughly 5–15 seconds. The existing reader selected and decoded pixels before
waiting for OCR, and blank-frame fallback could request a second admission.
The scheduling repair selects the latest preserved crop after admission and
uses one admitted turn for both native and threshold readings, at the existing
transient priority. All preserved crop Articles remain in custody. New bounded
latency diagnostics distinguish queue wait, source age, native OCR and processing
time; improvement requires the next live recording, not a build result.

Final HP/faint acquisition remains separate: the current HP measurer requires
visible saturated fill, and the faint witness relies on a stable ball decrease.
The video confirms the third ball can remain red through the terminal KO.
Neither a future name-reading fix nor WIN may invent an empty bar or a faint.

## Verification and installation

All **1,987 unit tests pass**, without failures, errors or skips, and both
debug app and instrumentation APKs build. Focused checks cover legal-pool OCR
repair, exact original-Article replay recovery, source/crop custody, fresh
selection after delayed OCR admission, shared native/fallback admission,
failure recovery, source-age diagnostics and cancellation. Independent review
found no nested OCR admission or source-clock rewrite. Latest-only selection
can still miss a brief banner between admissions; this repair does not promise
complete announcement coverage. A new live capture must measure its effect.

The exact 42-Article reduced fixture retains original IDs, clocks, publication
order and payloads, without media. Replay recovers exactly one Ground Earth
Power from the actual `Earth Powers` Article; it does not manufacture the other
three charged uses, energy spending, final HP loss or Obstagoon faint.

The tested APK is installed and launched on the stopped Moondrop, with installed
SHA-256 `c3479a3404ae03bd097a85c72fed4d3bd8650f360afae73606bc008872d9a93b`
matching the build. No phone instrumentation was run. The readable pre-install
backup has 406 files / 331,802,624 tar bytes, excluding the untouched loose
artifact store. A streamed post-install inventory verifies all 404 other files
byte for byte; only the Firestore cache and installation profile marker change.
No inventoried files were added or removed. Teams, cards, calibrations,
preferences and all match archives are preserved.

A scheduled Timeshift run temporarily exhausted host free space after the
installation. Administrator authentication prevented stopping it from this
session. Only regenerable Overdex build intermediates were removed; source,
tested APKs, test results and captured evidence were preserved. The failed empty
post-install tar was discarded, and the post-install comparison was completed
by hashing its live tar stream without saving another large copy. No Timeshift
snapshot was deleted by this work.
