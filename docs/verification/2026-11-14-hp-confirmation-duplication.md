# HP confirmation and border-pulse overlap: recorded test

November 14, 2026. **Diagnostic slice complete; no production rules or installed builds changed.**
The existing paired Pixel recording confirms three real opponent Incinerate hits
where current Replay schedules five neutral attack animations. One extra animation
comes from the frame that also confirms the earlier HP drop; the other comes from
a camera-transition border pulse. Simply merging nearby evidence or reusing a
confirmation frame is not safe enough to ship: both lose real attacks in controls.

## Source and independent video check

- Video: `/home/sean/Desktop/Screen_recording_20261113_183448.mp4`.
- Pixel match: `f9fa49c6-033e-4932-ae5e-38167d662159`, preserved in the
  November 13 paired archive. No new match or trainer testimony was injected.
- Artifact directory:
  `/mnt/overdex-backup/archives/overdex-device-backups/2026-11-14-hp-confirmation-duplication/`.
- Original archive hash matches the prior provenance. All **1,839 articles** in
  the regression fixture exactly match the original ZIP's JSON, including clocks,
  payloads, predecessors and evidence references. Source video hash, labels and
  reproduction paths are in `provenance-and-labels.json`.

Reviewed the recording from 82–97 seconds, decoding **506 frames**. An offline
whole-outline probe produced 827 bar candidates across both sides; these are
analysis candidates, not newly accepted Overdex measurements. Attack effects
obscure some entry frames, so those color estimates are not trusted as HP values.
Context images, distinct animation sequences, and settled whole-bar plateaus
support the three hits below. The full recordings and source archives are unchanged.

| Event | Video time | Archive time after Match Start | Current Replay |
| --- | ---: | ---: | --- |
| First hit; HP settles near 85% | 85.842 s | 43.260 s | One recovered HP attack |
| Same settled HP confirmed; separate border pulse | Between first and second impacts | 44.317 s | A second attack animation |
| Second hit; HP settles near 71% | 88.410 s | 45.988 s | One attack |
| Third hit; HP settles near 56% | 90.859 s | 48.344 s | One attack |
| Attack Incoming / camera movement; HP remains near 56% | Around 92 s | 49.659 s | Another border-only attack |

Video and archive clocks are separate. The three HP changes suggest approximately
+42.5 seconds from archive-relative time to video time, with roughly 0.2-second
sampling variation; this is not exact frame synchronization. The video visibly
contains three Incinerate sequences and no additional impact in either suspect
interval. The second extra pulse is a camera/phase false-positive candidate, not
another instance of the specific HP-confirmation reuse mechanism.

## Exact evidence reuse

The first recovered HP attack cites:

- `a7e61664-bac5-4797-aaa1-9ce15d5272af`: HP 0.85106385 at +43.260093675.
- `1a2e65d2-e12d-48e0-a400-ba56d32eb227`: the same HP at +44.317454197,
  confirming the earlier decrease.

At exactly the latter source timestamp:

- `67aa33ee-b63e-4aa6-a97c-3beebc06ab7a`: PRESENT player-bar pulse.
- `5e35dfff-53ed-4f21-ad9f-3fa3c6919ff9`: opponent Fast Move use derived from
  that pulse.

The source-time gap is **1.057360522 seconds**, outside Replay's existing 450 ms
fusion neighborhood. HP-step recovery backdates its occurrence to the initial
lower reading, while the independent pulse remains a separate use at confirmation.
Both therefore survive and receive short neutral flights because opponent move
identity/duration is unresolved. This is duplicate presentation of the first
video-confirmed hit, not a duplicated raw Article. The raw observations are useful
and should remain independently preserved.

The other pulse is `f666daf2-9a01-4901-8232-a0e34201337c` at +49.659284052,
with use `4e02b3d2-8d07-4f8d-a32c-06a7e5790637`. HP is still approximately
0.563, but its measured rectangle moves from roughly `(42,746)-(234,764)` to
`(72,799)-(266,816)`, then farther toward the middle for the charge camera.

Both suspect pulses have zero orange fraction. **That is not a rejection rule:**
valid blue border flashes also have zero orange. The broad-blue positive detector
regression remains passing. Similarly, unchanged measured HP cannot reject a real
small hit below pixel resolution, and a final hit need not leave a stable empty bar.

## Three approaches tested offline

No candidate below is installed or used by production. Every experimental group
retains the union of source Article IDs, source timestamps and measured damage;
none changes the archive or invents a damage amount.

| Approach | Attacks in this interval | Safety result |
| --- | ---: | --- |
| Current production reconstruction | 5 | Two more than the reviewed video |
| Merge a border pulse sharing a recovered HP step's confirmation frame | 4 | Removes the first duplicate, leaves the camera pulse; unsafe alone because a genuine second tiny hit can share that confirmation frame |
| Expand complementary-evidence merging to 1.5 seconds | 3 | Fits this clip but collapses four genuine half-second attacks to two, including the final hit as a distinct occurrence |
| Supply video-labelled Incinerate duration to the existing scheduler | 3 | Preserves all three real hits and every original reference in this clip; a diagnostic oracle, not evidence that Pixel identified Incinerate |

The known-duration experiment uses the actual production scheduler, with move name
and duration set only on temporary test inputs. The archive stays unnamed on that
side. It demonstrates that correct independently established move timing can resolve
this burst; it does not authorize assigning Incinerate from a legal movepool,
from the other side's configuration, or from the trainer's description alone.

## Preservation and regression checks

`RecordedHpConfirmationOverlapStudyTest` exercises:

1. The full saved Pixel archive through production Replay, including reversed
   article order, then the three diagnostic alternatives.
2. All source references/timestamps and the three separate HP losses survive each
   experimental grouping in the recorded interval.
3. A concrete faster-move counterexample where a wide merge loses distinct real
   attacks, including a terminal attack. Correct half-second scheduling keeps all
   four and the finishing impact at its original time.
4. A concrete small-hit counterexample where sharing an HP confirmation frame is
   insufficient proof of duplicate use. Correct scheduling retains both attacks.
5. Side and appearance/charge-window boundaries remain separate.

The focused run also includes the recorded Sealeo finishing exchange, mirrored
entry reconstruction, recipient audio alignment, the existing 30 scheduler tests,
and live HP damage/border detector tests. **68 tests passed, no failures/errors or
skips.** Thus the negative experiments behaved as predicted; this is not a claim
that those unsafe candidates passed acceptance for deployment. The finishing-hit
regression remains intact. `git diff --check` passed.

Reproduce from the canonical repository:

```sh
OVERDEX_HP_OVERLAP_STUDY=/mnt/overdex-backup/archives/overdex-device-backups/2026-11-14-hp-confirmation-duplication \
JAVA_HOME="$PWD/.jbr" ./gradlew :app:testDebugUnitTest \
  --tests '*RecordedHpConfirmationOverlapStudyTest' \
  --tests '*RecordedSealeoFinishingHitTest' \
  --tests '*ReplayFastMoveScheduleTest' \
  --tests '*ReplayRecipientAudioPairTest' \
  --tests '*RecordedMirrorReplayTest' \
  --tests '*ActiveHpBarBorderPulseDetectorTest' \
  --tests '*ActiveHpBarDamageTickDetectorTest'
```

The new tests run without the environment variable; it controls only TSV export.
`candidate-comparison.tsv`, logs, XML results, `pixel-suspect-evidence.tsv`,
`pixel-video-bars.json`, and two context sheets preserve the comparison. The video
probe can be rerun using the recorded `measure_recording.py` with the same local
analysis dependencies used by the earlier HP studies.

## Recommended correction boundary

Do not enlarge the general fusion window or suppress every pulse on a confirmation
frame. Retain explicit links between the original HP change and its confirming
samples, but require additional same-hit evidence before combining an independent
pulse. Correct same-appearance move identity/duration is one demonstrated route;
source-pixel/phase validation of the suspect pulse is another. Camera-transition
pulse qualification should be tested separately from the confirmation relation.
Missing phase testimony and tiny unresolved losses must remain unknown, not become
proof that no attack occurred.

This task adds tests, measurements and rule notes only. No APK was built or
installed, no source archives were edited, and no commit was made.

## Subsequent correction

This report describes the pre-fix diagnostic. The later
[HP confirmation repair](2026-11-14-hp-confirmation-repair.md) traces the first
extra inference to a baseline crossing a substantial camera relocation. It
retains the independent pulse, resets only HP-step recovery at that relocation,
and leaves the broad merge hypotheses unshipped.
