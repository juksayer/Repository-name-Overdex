# Fast Move terminal and charged-phase coverage

Two bounded observation repairs address demonstrated code conditions. They do
not establish the exact finishing attack in an incomplete historical recording.
Calibration rectangles, ordinary fusion allowances, audio thresholds, and source
archives are unchanged.

## One visible empty outline

The ordinary HP-loss detector previously required another low reading at least
90 ms later. Even when the independent terminal reader verified a complete empty
outline on one frame, that frame alone could not finish the loss measurement.
The Pokemon could disappear before the settling reading arrived.

The live numeric handoff now preserves the already measured fill color. A single
verified `EMPTY` sample may complete an HP loss only when:

- Normal Fast Move evidence is eligible; camera samples and the first normal
  return still reset the detector instead of creating impacts.
- The prior baseline is positive and at most 20%, supported by at least two
  readings, and both the latest sample and actual baseline support are at most
  500 ms old.
- Horizontal end caps match exactly, height differs by at most two pixels, both
  measurements have confidence at least .80, and no earlier loss is unsettled.
- The source clock advances. A duplicate or older source cannot emit a hit.

The pixel verifier itself is unchanged: both outline lines and caps must remain
visible, with a clear dark interior. Missing/obscured pixels and unverified scalar
zero retain their existing conservative behavior. The scalar overload used by
legacy recovery still requires ordinary settling. An isolated low reading does
not establish a settled baseline, and rebound/flash readings cannot keep an old
low baseline fresh.

This publishes an HP-loss measurement. Existing inference still determines
whether it supports a Fast Move. It does not directly name a move, award energy,
establish a faint, or declare a winner. The empty source frame retains its
original wall and monotonic clocks. No additional pixel search, image encoding,
OCR request, or audio comparison is added.

The shortcut deliberately does not cover a zero on the first camera-return frame,
changed end caps, an already unsettled earlier loss, or an interval with no usable
low-HP baseline. Those remain explicit recognition limits.

## Early camera acquisition and later charged impact

The charged HP policy had two inconsistent deadlines: a used-announcement cue
updated the phase deadline, while frame requests still expired three seconds
after the first acquired outline. A green bar acquired at preparation +2.1 s
could therefore close the reader at +5.1 s even if the actual used cue arrived at
source +8 s and a clear empty bar appeared at +8.1 s.

The policy now keeps acquisition time separate from the impact deadline. An
in-phase used cue permits capture through impact source time +3 s, capped at
preparation +15 s. Repeated later cues cannot extend that deadline. Without a used
cue the original three-second acquisition lifetime remains. Consumption, owner,
appearance, dimension, token, source-order, and unavailable-pixel guards remain.
Late testimony cannot recover an already missed frame or retimestamp it.

An isolated Kotlin/JUnit comparison of the actual policy and extracted bitmap-free
types reproduced three failures among 17 checks before the repair and passed all
17 afterward. An additional source-order probe exposed a later-delivered earlier
cue retaining the wrong deadline. Both delivery orders now choose the earlier
source deadline; all 18 isolated checks pass. Full-application verification is
reported below separately.

## Recorded evidence and unresolved phase boundary

Six original archives and all 1,634 declared artifacts were reverified by SHA-256.
The strongest located post-charge example is November 8 Pixel Combusken,
`2d298589-664d-4aba-9dbc-c2f721d52ed8`. Relative to its inferred start:

- NICE/GREAT appear at +18.120/+19.780 s.
- The first accepted post-QTE opponent HP is 12.698% at +23.236 s.
- An already canonical player loss/use at +25.722 s leaves 3.175%.
- The last low bar is at +27.194 s; ball-count faint is at +28.270 s.
- The final HP step and zero are absent. The next accepted HP belongs to the
  replacement at +30.616 s.

The available evidence does not label the precise user-reported immediate
post-charge finishing Incinerate. All five opponent HP measurements found in the
relevant later diagnostic window reached Custody/Timeline, with no pending
evictions. This is an observation-coverage gap, rather than Replay dropping an
accepted final zero. Older locator elapsed times are not performance measurements
of this new build.

The October 29 Golbat repeated-flash fixture remains a negative duplicate control:
the repeated border onset on one measured plateau does not add a use, while the
next distinct HP loss remains separate. The reported audio/visual stutter still
has no precisely labelled interval in the available sparse recordings.

`PostChargeFinalFastMoveGapTest` explicitly documents another unresolved condition:
Get Ready opens a 12-second unconfirmed charge pause, and normal HP return alone
does not close it. If used-announcement OCR is missing, an otherwise witnessed
finishing impact can remain held until a result discards it. Six synthetic probes
cover the gap and its surrounding controls; the known-gap assertion expects the
current omission. A passing suite does not mean this boundary is solved. Removing
the pause globally would admit charged damage. A future repair needs positive
ordinary-play evidence, source-time phase ownership, and matching controls in
counting, identity, energy, and Replay.

## Audio findings

Current audio capture already stays attentive through charged phases. Sounds can
resolve move identity, but do not independently supply a missing observed attack.
That distinction remains intact.

A fresh local reference audit found 79 WAVs representing 78 names; all catalogue
hashes verified. They cover 77 of the 96 names in current species move pools.
Missing alternatives are Zen Headbutt, Transform, Struggle, and 16 typed Hidden
Powers. Zen Headbutt occurs in 101 local catalogue forms. An incomplete legal
audio pool correctly blocks an exhaustive strict identity claim. Generic Hidden
Power is not silently substituted for its typed variants.

The next useful audio acquisition is a clean Zen Headbutt recording with an idle
receiver, followed by a separate-battle holdout. The next phase test is one known
attacker performing exactly one immediate post-charge Incinerate, plus an
idle-after-charge control, with continuous video/audio through the result.

## Validation

The final application passed **2,342 unit tests across 308 suites**, with zero
failures, errors, or skips. Both the debug app and Android-test APK built. The
first focused run passed 90 checks. New unit coverage includes terminal admission
and rejection boundaries, the real numeric hub/witness/Custody handoff, source
clocks, phase-blocked samples, charged-window deadlines, and the explicitly
unresolved post-charge pause. Existing recorded loss/pulse and camera regressions
remain passing.

The debug APK SHA-256 is
`8dba22caf338771c21bd26924ba7c248991b7f0782ad5de83886a63d39b09904`.
The test APK SHA-256 is
`15b6f3ee13d55746b53013e58e18c16d78575ecb5ab201f90d46326071d75a73`.

All **16 native checks passed on a disposable API 34 emulator**: five ordinary
terminal-reader cases, five charged-reader cases, two charged-impact screenshot
checks, and four independently reviewed terminal screenshot checks. They include
native/delivered Pixel geometry, the obstructed Earth Power negative, one-frame
empty publication, and the new pixel reader → numeric hub → live damage witness
handoff. The latter has exactly one empty frame followed by absent bars and
checks the original source clocks and one loss publication.

The new native handoff test initially timed out because its generated `RED` bar
measured 20.37037%, above the deliberate 20% shortcut limit. Diagnostic output
confirmed that the verified empty frame and its metadata reached the hub. A
separate approximately 12% `LOW_RED` test fixture corrected that assumption;
production thresholds stayed unchanged. The final 16-case run passes with that
fixture. Initial and diagnostic logs are retained alongside the final log.

The tests establish bounded code/pixel behavior, not accuracy on a new live
battle or a recovered attack count in an incomplete old recording. The emulator
was closed after verification; neither phone received the research build at that
time. The subsequent [reviewed Moondrop reference package](2026-11-10-moondrop-reference-collection.md)
includes these source changes and was installed on the Moondrop with verified
saved-file preservation. This installation does not close live Fast Move acceptance.

Private source selections, original hashes, exact clocks, comparison harness,
and logs are retained under
`/home/sean/overdex-device-backups/2026-11-10-fast-move-hole-research/`.
Original archives remain unchanged. No instrumentation tests were run on either
user's phone; the later Moondrop installation is documented in the reference-package
verification linked above.
