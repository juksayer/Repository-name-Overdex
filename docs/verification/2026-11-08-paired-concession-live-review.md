# Paired concession: live result recognized, routine admission still too slow

The updated Moondrop successfully records the new concession as WIN through the
new result-probe path. It preserves the actual result crop, native OCR, typed
phrase and one MatchEnded with the selected frame's source clocks. This is
physical-device evidence that the new path operates. It does not meet a prompt
result-recognition target: a routine OCR request waited 44.031 seconds, and the
accepted source frame was already 8.081 seconds old at admission.

## Recordings and build boundary

The reviewed desktop copies are:

| Device | Match | Scenario | Result |
| --- | --- | --- | --- |
| Moondrop | `11c872e8-c32b-400b-a2fe-7ab2fd62ade8` | Turtonator versus Swampert then Quagsire; opponent concedes | WIN |
| Pixel 4 | `d97a1a09-80f0-4616-b09f-48f292e7f627` | Other perspective of the paired battle | No accepted result before user Stop |
| Pixel 4 | `48a36312-70d1-4232-a9dd-6d69e1c98a89` | Rocket battle, roster selected without configured moves | WIN |

The Moondrop APK hashes to
`06763275a96331573ac2164e00302f6bdd7bcead819c84e4a269abe03b06851e`,
installed November 8 at 08:51. The Pixel APK hashes to
`d5be8e6719e1780b01b3e7d8c949ff44b2a069140aafb895d813cbbf42566ab2`,
installed November 6 at 10:52. The Pixel has the older result pipeline, so its
missing concession result does not test the new probe change.

All three archives use format 2 with media included. Compact retention may omit
unselected CropCaptured Articles. Retained probe-crop counts do not establish
the actual acquisition cadence or prove a capture gap.

## Moondrop result evidence and latency

Times below are relative to this archive's MatchStarted. That start is the
countdown-and-combat inference at confidence 0.75, not a directly witnessed GO.

The result chain is:

1. `MATCH_OUTCOME_PROBE_CAPTURE`, crop Article
   `9d6d4166-2c80-44d3-878f-3bb74200631d`, source +67.646654081 seconds.
2. `MATCH_OUTCOME_TEXT_WITNESS`, Article
   `b756eb03-ef0a-408b-9d0b-0350aa250901`, actual RAW reading `YOU WIN!`.
3. `YOU_WIN_WITNESS`, Article `c07da301-f924-45f6-a424-a88ecabc2200`.
4. `MatchEnded(WIN)`, Article `4a5e3e60-de5a-4fc3-b4dd-464b83ed9f47`.

Each link retains monotonic source time `176572844295985` and perceived wall
time `1794150189755`. The crop bounds are X 100–568, Y 627–888 on a 672 × 1493
source. Its source PNG hashes to
`33b038830f3c230f6b10716790352ac86238efd2aeeece9e0578b15059585493`.
The native reading cites that crop; the typed phrase cites the native reading;
the MatchEnded predecessor is the typed phrase.

The completion diagnostic reports `lane=PROBE priority=NORMAL`,
`selection=TEXT_KEYFRAME`, `queueMs=44031`,
`captureToAdmissionMs=8081`, `nativeReadMs=536`, `totalMs=8669`,
`overflowTotal=4`, and `expiredTotal=4`. Raw OCR was published 8.630 seconds
after its source; MatchEnded was published 8.775 seconds after its source.
The queue wait describes the pending request, while capture age describes the
newer frame selected only after admission. Those are different measurements.

The only recorded opponent faint is Swampert at +21.215 seconds, supported by
the ball-count decrease. Quagsire is subsequently identified. The concession
result does not manufacture a final Quagsire faint or finishing hit.

## Supporting video boundary

The supplied recording is `Screen_recording_20261108_090131.mp4`: 816 × 1808,
114.927 seconds, with no audio track. Independent pixel comparisons support an
approximate mapping of video time = archive-relative source time +28.7–28.9
seconds:

- The saved countdown 3 crop at source −3.000 seconds matches video
  approximately 25.70–25.75 seconds, mean pixel error 3.73/255 after scaling.
- The saved GREAT crop at source +13.175531385 seconds matches video
  approximately 41.97–42.03 seconds, mean error 5.18/255.
- Within that established mapping, the selected result crop matches video
  approximately 96.33–96.37 seconds, mean error 5.22/255.

The video briefly displays YOU WIN near 70 seconds, passes through the reward
animation, and displays persistent result text by approximately 77.5 seconds.
The accepted selected evidence therefore comes from a later visible result
frame. Earlier capture coverage is unresolved: compact retention omits
unselected crops, while the overflow and expiry counters demonstrate that
earlier queued samples can be discarded. The video does not justify calling
this an acquisition gap or rewriting the accepted result's source clock.
Repeated avatar poses make the result image alone an ambiguous timing anchor;
the independent countdown and GREAT anchors are necessary. Droidball's final
panel appearance is not used as the MatchEnded timestamp.

## Pixel Rocket recognition, without configured moves

The roster is Gardevoir, Arcanine and Espeon. There are no
`PlayerTeamSlotConfigured` Articles supplying move names. The following are
actual formal FastMoveIdentified conclusions:

| Side/species | Conclusion | Evidence and qualification |
| --- | --- | --- |
| Player Gardevoir | Initial Charge Beam at +4.237 s, confidence 0.664 | Explicit higher-energy tie-break assumption from 1.423 s cadence |
| Player Gardevoir | Charm at +6.023 s, confidence 0.900 | Repeated unique legal-side audio; later effectiveness and audio corroboration |
| Opponent Axew | Scratch, confidence 0.937 cadence and 0.900 audio | Border pulses plus repeated legal-side audio and impact support |
| Opponent Garchomp | Dragon Tail at +37.020 s, confidence 0.900 | Repeated unique legal-side audio supported by impact |
| Opponent Gabite | Unresolved move name | Two side-attributed Fast Move uses remain unnamed |
| Player Arcanine | Unresolved move name | Seven side-attributed uses remain unnamed |

Later Arcanine-era raw audio often ranks Thunder Fang around 0.94–0.97, above
Snarl around 0.91, but the archive contains no formal Arcanine move identity.
Those candidate scores are supporting measurements, not a configured move or
a confirmed identity. No move configuration was changed during this review.

The user's later recollection is “charm, and thunder fang, i think” for
Gardevoir and Arcanine respectively. Charm agrees with the later recorded
conclusion. The uncertain Thunder Fang recollection agrees with the leading
Arcanine-era audio candidate, but does not create a configured-move fact,
retroactively add a formal identification, or authorize exact energy accounting.

The charge-button witness reads SYNCHRONOISE four times, and the progression
crop reads NICE/GREAT during several intervals. The archive contains no named
charged-move-use or charged-energy-spent event. An equipped label and QTE
activity must not be reported as a confirmed Synchronoise use. Absence of such
an event also must not be reported as proof that no charged move was performed.

The Rocket result is independently recorded as WIN from actual `YOUWIN!` OCR
at source +88.073666 seconds, published as MatchEnded 1.954 seconds later.

Exact copied archives, installed-build provenance, private result pixels,
visual alignment diagnostics and structured Article-level findings remain under
`/home/sean/overdex-device-backups/2026-11-08-paired-concession/validation/`.
This review made no changes to device data, source archives, move configuration
or recorded testimony.

## Follow-up fixes and validation

The result reader now promotes a probe with preserved text-shaped pixels into
the existing recent URGENT OCR queue. A waiting routine blank cannot keep a
later promising result behind NORMAL admission. Weak or failed visual probes
retain the bounded routine fallback. The shape measurement changes scheduling
only: native OCR and the independent phrase witness still supply the result.
Capture frequency, archived evidence and source clocks are unchanged.

The final combined source passed `testDebugUnitTest`, `assembleDebug` and
`assembleDebugAndroidTest`: 2,051 unit tests, zero failures, errors or skips.
The added regression keeps a NORMAL blank blocked while a later promising WIN
is independently admitted, and checks cancellation and source-time continuity.

The isolated `ConcessionOutcomeProbeDeviceTest` then passed **OK (2 tests)** on
the disposable API 37 Google Play emulator, using the actual native ML Kit
model and private previously retained concession pixels. The first attempt
failed its model preflight during fresh Google Play services initialization;
the retry passed after its components became available, without replacing the
reader or changing app source. Actual raw OCR was `YOU WIN!`. The priority
diagnostic reported `lane=RECENT priority=URGENT`, `promotedProbe=true`, one
native pass taking 251 ms, and original source clocks preserved through one
MatchEnded. This fixture uses synthetic capture clocks and synthetic healthy
teams with reserves; its latency is not a new live-battle measurement. The
emulator was stopped afterward.

Two independent presentation/control fixes were also checked:

- `92dd4e2b`: Battle Records gives each event title its full width and places
  the date on a separate line. The Moondrop's actual saved records displayed
  `Nov 8 · 09:00` horizontally, including long Incinerate event labels. The
  ODX-Fi shell, CRT/LCD geometry and controls were unchanged.
- `48c73904`: notification Start invokes the normal deployment permission
  path directly. On the Moondrop, expanding the actual stopped Droidball
  notification and tapping Start immediately opened Android's
  `Start recording or casting with Overdex?` prompt. No Overdex readiness
  confirmation was required. The system prompt was canceled without starting
  capture.

The combined debug APK hashes to
`038f79349dac5c354daf68776417e4839514010e5080d109e162666d0b83e54f`.
It was installed with replacement on the Moondrop and Pixel 4 after confirming
both sessions were stopped. Before installation, private data was backed up
and verified, excluding the separate loose artifact store. Every included file
remained byte-identical before relaunch: 411 on the Moondrop and 91 on the
Pixel. Installed base APKs on both devices matched the built APK hash. Both
apps reopened, no fatal Android runtime entries were observed in the checked
logs, and both ended with no Overdex service or MediaProjection running.

Screenshots, UI hierarchies, native-test reports, backup verification and
installed-APK provenance are retained privately under the paired-concession
backup directory. A fresh live concession is still required to verify prompt
result acquisition and publication under real battle load. The reviewed
Moondrop battle above predates this priority adjustment.
