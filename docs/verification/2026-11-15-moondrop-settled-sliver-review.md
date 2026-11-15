# Moondrop settled-sliver trials

## Sources and trainer account

The trainer reports leaving Sealeo and Vaporeon at a settled red sliver, then
using one Fast Move for each KO. They also confirmed that Turtonator, Camerupt
and Gourgeist all use Incinerate; the archived configured move must not be
discarded as an assumed configuration error. An earlier assistant comment
calling Gourgeist's configured move an error was incorrect.

Two separate Moondrop matches are reviewed. The first (`a29c9e9d-...`,
11:59) has no screen recording. The second (`5181caa0-...`, 12:12) has the
trainer-supplied video `Moondrop-2026-11-15_12-12-38-mtIRSv.mp4`, native
1072×2400 with an AAC track. Both archives were copied to the 1 TB drive,
matched to their device SHA-256, and passed verification of all declared
artifacts: 374 and 301 respectively. Original data and the video were not
modified.

The new recording is one match with two KO sequences, not two independent
matches. Its excerpts provide a source-separated comparison to the earlier
November 13 windows, which were already used to develop the experimental
tracker and are not a blind holdout. No model was trained and no recognition
rules were changed.

## First match: retained crops and audio

Times in this section are source monotonic seconds after recorded GO. The
archived opponent whole-bar reader measured Sealeo's low red sliver around
43.811–44.894 s. Different retained result-probe crops show a red sliver at
56.260 s and an orange-looking sliver with an attack effect at 59.071 s. Both
of these particular crops cut off part of the bar: they support a visual
comparison but cannot establish an orange-only **complete** bar or zero HP.
Vaporeon's entrance was observed at 60.861 s; the ball-count-based Sealeo
faint was recorded at source time 66.059 s. The offline Replay projection
reconstructs the Sealeo faint near replacement, but has no supported final
player Fast Move in the 56–60 s interval.

The normal HP reader last published Vaporeon red at 112.468 s. An independent
`MatchOutcomeCrop` at 119.799 s contains the complete Vaporeon outline with a
red sliver; another at 123.492 s contains the complete outline and visible fire
effects. Running the actual whole-bar reader on those preserved pixels yields:

| Crop source time | Live columns | Damage columns | Result |
| --- | ---: | ---: | --- |
| 119.799 s | 2 | 1 | Red HP sample; not orange-only |
| 123.492 s | 3 | 2 | No numerical HP sample; not orange-only |

These two images do not include the later, trainer-described complete
red-to-orange transition; the final hit time cannot be determined from them.
The file also has a localized Vaporeon cry at 124.611 s and a win at 129.746 s,
but no recorded final Vaporeon faint or supported final player Fast Move. An
offline Replay audio pass recovered two distinct Vaporeon cry occurrences at
approximately GO+61.579 and GO+124.613 s, consistent with entrance and the
reported ending. Multiple overlapping audio fragments from the latter are
support for *one* occurrence, not additional cries. The user's observation and
result context are not rewritten as automated visual testimony.

The HP diagnostics show why absence of a measurement is not a negative frame.
In the 54.858–59.912 s window the broad opponent locator received 12 enabled
frames, found no assignable opponent bar in 12 location attempts, and recorded
45 skipped enabled sequence positions. In the 120.163–125.347 s window it
received 16 enabled frames, found no assignable opponent bar in 16 attempts,
and recorded 51 skipped positions. These are application publication-sequence
diagnostics, not counts of game-rendered frames or proof that a particular
retained crop's frame reached the locator.

## Recorded match: source frames and archive

Independent comparisons of three source-timed archive crops with the recording
place recorded GO approximately 30.2 s into the video's PTS clock. The best
matches were within roughly 0.1 s of each other; repeated/held frames and
capture delays limit this to an approximate alignment, not frame-exact sync.

The native recording shows Gourgeist's final fire sequence against Sealeo.
The offline full-outline scan finds a red sliver at video 115.716 s, mixed
red/orange at 115.784 s, and two orange columns without a bright live column
at 115.867–116.407 s; Sealeo then disappears. The experimental orange-only
gate requires three columns, so it does not arm. Full-bar pixels, not just a
cropped fragment, were inspected for this recording. The source recording
nevertheless contains compression and attack effects; this observation does
not establish an exact game-turn damage instant.

Vaporeon's bar retains 16 bright red median columns at video 167.446244 s;
at 167.517789 s it has 16 orange columns plus one live-colored edge column.
The interval between those sampled frames is approximately 71.5 ms. A
subsequent shrink and disappearance are visible. In the native frame, the
edge column is HSV `(24, 140, 178)` in OpenCV units, at the classifier boundary;
it could be an edge/background mixture, but this test cannot prove that it is
not remaining HP. The trainer additionally recalled seeing a red sliver
during a different faint. Thus neither deleting the edge pixel nor asserting
survival from one pixel alone is justified.

The archived complete-bar witness recorded a corresponding Vaporeon
measurement at GO+137.376 s: zero classified live columns, 11 damage columns,
21 unmasked uncertain columns, `UNRESOLVED_COLOR`. Its high geometry confidence
does not resolve those pixels or establish zero. The older exploratory
median-column gate also does not arm for either new KO: Sealeo's orange region
is under its three-column minimum, and Vaporeon's edge column remains
live-colored. The older 12-of-13 historical KO-window result therefore does not
generalize to these two new sequences.

The live timeline records Sealeo's later loss via ball count and a final win,
but no final Vaporeon faint. It contains no player Fast Move use after GO+70.169
s in the Sealeo finishing interval and none in the final Vaporeon interval.
The offline Replay review, using archived audio and basic reference lookups,
likewise does not show either final player attack; this is not a substitute for
an on-device Replay check with crop-assisted identity recovery.

In the recorded match's 83.158–88.168 s Sealeo window, the broad opponent
locator received nine enabled frames, found no assigned opponent bar in nine
attempts, and counted 34 skipped enabled publication-sequence positions. In
the 133.705–138.759 s Vaporeon window it received 16 enabled frames, counted
32 skipped positions, and found a bar on 15 of 17 location attempts. The
counts are diagnostic windows and may cross boundaries; they are not the
number of physical game frames lost. The two examples demonstrate different
issues: locator/assignment coverage for Sealeo, and color ambiguity despite
located geometry for Vaporeon.

The retained audio also contains information not represented by a final move
event. Around GO+86.369 s it localized a Sealeo cry and recorded a repeat-cry
departure candidate. Around +88.865 s an audible sound included Incinerate
among competing reference candidates, but the reference match was not
accepted. At +89.388 s the Incinerate reference match was accepted while the
same measurement marked audible false; this is after the recorded cry, so its
capture window and reference offsets need examination before it can be linked
to the prior faint. At +136.031 s an audible clip had an Incinerate candidate
but did not pass reference acceptance. These observations preserve the raw
candidate evidence; none establishes a finishing move's source time or side
by itself. The independent video has an audio track, which has not yet been
matched separately to these finishing clips.

## Checks and next step

Four native video crops include both complete rails and caps and a 40-pixel
margin. Their video PTS, dimensions, bounds and SHA-256 are in
`native-bar-manifest.json`. The production whole-bar reader, not the exploratory
median probe, found the red samples live and refused to turn either selected
orange-looking sample into an empty HP reading. Injecting a single synthetic
red source pixel also blocked an orange-only conclusion in all four samples.
The selected orange samples still have significant uncertainty: Sealeo had
two damage columns and 297 uncertain columns; Vaporeon had one live, 16 damage
and six uncertain columns. These are sample-specific observations, not
calibration thresholds.

Seventeen focused tests passed, none skipped: the native frames, retained
archive crops, both read-only Replay reviews, previously recorded nonlethal
and tiny-survivor controls, and background-mask tests. The tests verified the
input hashes and preserved the original archives. Scripts, detailed outputs,
the full-bar review image, audio review, and logs are under the corresponding
`2026-11-15-moondrop-1159-review` and `2026-11-15-moondrop-1212-review`
directories on `/mnt/overdex-backup/archives/overdex-device-backups/`.

The next bounded experiment should compare those complete, source-timed native
frames with independent nonlethal and living-switch examples, investigating
the two-column threshold and edge-pixel ambiguity **without** weakening the
single-pixel survivor veto. Separately, the Sealeo locator's missed assignment
should be investigated using frame-correlated diagnostics. Do not promote the
later Incinerate or faint from this report into automatic testimony until the
capture, side, timing and alternatives are tested. No HP recognition rule was
changed by this study.

## Replay bar visibility

The trainer reported that bars disappeared or showed dashes. Replay previously
hid a numeric reading once its 500 ms freshness window elapsed, even if the
archive retained a valid earlier measurement. In this archive, Sealeo at
GO+83.856 and +85.402 and Vaporeon at +137.376 had unconfirmed current HP but
valid earlier classified readings. The presentation now shows a dimmed
last-seen reading with a question mark; unknown values still show a dash. It
does not change freshness or contribute to faint, hit, or KO inference. The
last-seen value must come from a valid source-time observation in the current
appearance, outside the charge camera window, and is not carried across a
different appearance. A witnessed faint remains empty. Actual current HP may
be different from the labelled historical value.

The focused checks and full debug unit suite passed (3,089 tests, 11 skipped),
and the debug APK built. The test run included the source-hash-checked native
frames and both archived-match reviews. The APK was installed and launched on
the connected Moondrop; the Replay display still needs a visual device check.
Original recording and archive hashes matched their initial values afterwards.
