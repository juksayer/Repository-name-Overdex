# Moondrop resource-count confirmation — November 11, 2026

## Observed failure

The compact archive `f66d7125-3616-4e44-86c8-c1af6469dd35` contains no opponent
Poké Ball count measurements. Its sole retained opponent ball crop, captured
at match +39.967 seconds, clearly has one faded ball and two live balls. The
existing pixel counter reads that strip as **2** without threshold changes.

Read-only inspection of the phone's loose content-addressed crop store also
found a valid three-live-ball strip and a late strip containing scenery instead
of a badge. The same counter reads those as **3** and **unknown**, respectively.
The compact export omits most uncited captures, and identical images can share
one stored artifact. Neither compact omission nor unique-file counts proves
how many captures were attempted. Exact capture clocks for the two uncited
loose images were not recovered and are not invented.

The observed timing exposes two gaps in acquisition:

- The opponent's initial assignment had already expired by about +1.1 seconds.
  The first opponent species observation was captured at +6.892 and delivered
  at +10.987. First identity previously did not retry an unresolved baseline.
- Mismagius identity was captured at +36.002 and delivered at +39.515. A usable
  two-ball crop was captured at +39.967 and preserved at +40.955; the assignment
  was closed by +43.196. A single readable candidate did not earn a further
  confirmation opportunity. The reader required two equal values and therefore
  could not publish a sequence containing only an isolated 3, then isolated 2.

These findings support a bounded acquisition correction. They do not establish
a color-classification failure, prove that every requested frame was captured,
or justify accepting a single image as a stable count.

## Change

- The first accepted Match Start and first identity on each side can retry only
  baseline resources that remain unmeasured and whose initial assignment has
  expired. Repeated identity confirmations do not keep reopening them.
- A usable candidate whose capture belongs to an assignment grants at most one
  additional three-second confirmation tail. The hard deadline is six seconds
  after that assignment opened, including recognition delay. Repeated, future,
  previous-assignment, stopped-session, or expired feedback cannot renew it.
- Actual counts still require two independently captured equal readings. The
  shared confirmer rejects duplicate or reordered source frames, requires the
  pair to be no more than six seconds apart, and resets across observation
  lifecycle boundaries. Already-captured pre-result pairs may finish recognition
  after Match End, but new post-end frames are rejected; explicit stop/ignore
  cancels queued confirmation. Missing pixels remain unknown.
- A changed count keeps the first agreeing image's capture time and both image
  references. An unchanged fresh count can retire a later assignment. The
  confirmation callback carries scheduling feedback only; it publishes no
  count, departure, or faint by itself.

Pixel thresholds and named-faint inference were not changed. Original archives
and timestamps were not rewritten.

## Verification

**61 focused JVM tests passed**, with zero failures/errors, across
`TeamResourceSnapshotGateTest`, `BattleResourceCountConfirmationTest`,
`RecordedResourceCountFaintTest`, `OpponentBattleResourceCounterTest`, and
`OpponentFaintTrackerTest`.

The new fixture directory `app/src/test/resources/battle/balls-20261111` retains
the three original loose strips and six unscaled crops from six distinct native
816×1808 frames of `Screen_recording_20261111_161531.mp4`. Its manifest preserves
video presentation times, crop rectangles, hashes, and the distinction between
video-derived recovery evidence and original app testimony.

The recorded stream regression passes the real **3 → 2 → 1** pixel readings
through the production two-frame confirmer and shared faint tracker. Later
identity/alive evidence and deliberately delayed count delivery yield exactly
**Tinkaton and Mismagius fainted**, with **Roselia alive and no player faint**.
The concession win does not invent a third loss. Capture times and both-frame
references are checked. Species identities and positive-HP survival context in
this regression are manually labeled from the independent video; they are not
outputs of live species/HP recognition. The test therefore does not establish
that the on-device pipeline acquired the identity context needed for attribution.
Separate tests cover duplicates, old observations,
ordinary unchanged counts, missing badges, first-baseline retry, expiry, one
confirmation tail, successful completion, stop, and restart.

This proves classification and attribution for preserved/video recovery inputs,
not successful future live capture. The subsequent combined verification passed
all 2,620 unit tests and the debug build, and the build was installed on the
Moondrop; see the [combined review](2026-11-11-moondrop-ko-hp-review.md).
A new physical match remains the live acceptance boundary. The original
compact still lacks the two independent count pairs and is not repaired by
relabeling its existing single image as a confirmed count.
