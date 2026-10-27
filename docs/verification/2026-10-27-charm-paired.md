# Paired Charm observation review — October 27, 2026

## Sources and preservation

The controlled battle pairs Pixel 4 player Gardevoir with Moondrop player
Melmetal, then Slaking and Garchomp. The supplied video is from Gardevoir's
perspective. The user describes rising hearts near Gardevoir and a many-pointed
ring descending on the recipient. These are phases of the same attack, not
evidence that both combatants attacked.

Desktop evidence directory:
`/home/sean/overdex-device-backups/2026-10-27-charm-paired/`.
Phone and desktop archive hashes match; every declared artifact was verified.
Neither source archive was rewritten or deleted.

| Device | Match ID | Articles | Verified artifacts | Archive SHA-256 |
| --- | --- | ---: | ---: | --- |
| Pixel 4 | `f5f37550-c4a1-478d-9ae4-dadf31a8fe0c` | 3,215 | 317 | `13891a9af8c00da8ec7f11ac35b8f14e2e11a08b68ace4f061c1d218ea0c5889` |
| Moondrop | `5df79cf4-211b-46df-af49-1a2c75e9996e` | 4,433 | 435 | `de979e83fe819e8a6f7ff22bff68e3c10f8f690905cf96952acc258d9a549975` |

Video: `/home/sean/Desktop/Screen_recording_20261027_094319.mp4`.
SHA-256: `a4cb0134c0c62d1b9e833e4385ca82a20b068b3a15185c4cb7cb3a33b7a0858c`.
It contains 179.058 seconds of 816 × 1712 H.264 video and a data stream,
but no audio track. The separate Overdex archives contain 48 kHz stereo audio.
The user confirmed that Pokémon GO music was off **only on the Moondrop**.
This context is preserved separately in `user-context.json`.

### Subsequent user clarification: animation stutter and missing ending

After the initial review, the user reported that Gardevoir rapidly repeated
approximately the first third of its Fast Move animation for at least one normal
Charm duration. Its exact video interval has not been localized. Do not assume
it overlaps the opening groups or explains either extra border-only event.
Repeated partial animation starts are not independently completed attacks and
must not award extra energy. Equally, stuttering visuals alone do not establish
that the game stopped registering underlying attacks. Reconcile separate HP,
energy, audio, and completed-effect observations while preserving their limits.

The user also reported that the final approximately 10–15 seconds, including
the result, are missing from the MP4. Its last decoded frame is at 179.025656
seconds and still shows Garchomp alive during battle. The missing duration is
user-estimated, not measured from the truncated file. The MP4 cannot validate
the final KO or result timing; result Articles in the two separate Overdex
archives remain independent evidence. The video ending is not a MatchEnd.

The opening labels below count completed visual episodes. They do not establish
an independent server-registered attack count, and should not be treated as
whole-match ground truth. This clarification does not change the pixel-level
HP-outline regression or rewrite the source archives.

A bounded independent recheck of video 24–56 seconds still supports seven
completed heart-to-recipient-ring episodes in each opening group, with no extra
completed ring in the settled gaps. It did not confidently localize a sustained
partial-animation restart within that window. The initial windup at 26.8–28.8
seconds is nonuniform, but the sampled frames do not distinguish a restart bug
from ordinary startup or recording variation. Do not label it as the reported
stutter without stronger evidence.

## Independently visible opening attacks

The full manual timing brackets are retained in
[charm/2026-10-27-opening-labels.json](charm/2026-10-27-opening-labels.json).

Source frames show seven completed heart-to-recipient-ring sequences in the
first group and seven in the second. These counts come from distinct visual
episodes, not taps or division by the nominal move duration. Ring onset times
below are approximate visual landmarks, not exact damage or server-turn times.

| Group | Approximate recipient-ring onsets, video seconds | Completed visual cycles | Pixel recorded uses | Moondrop recorded uses |
| --- | --- | ---: | ---: | ---: |
| First | 28.62, 29.93, 31.27, 32.78, 34.25, 35.90, 37.25 | 7 | 1 | 8 |
| Second | 43.25, 44.88, 46.38, 47.75, 49.26, 50.90, 52.27 | 7 | 3 | 8 |

Settled idle controls are visible at 22.3–26.9, 38.0–41.9, and 53.0–58.0
video seconds. Do not count the persistent purple Shadow aura as another move.
The recipient ring is a visual phase; the HP decrease can precede it.

The Pixel alignment is record-relative time minus video PTS = approximately
**45.30 ± 0.10 seconds**. Two independently compared announcement crops support
this offset: record 92.729226 seconds best matches video 47.442200 (45.287026),
and record 150.546913 best matches video 105.247789 (45.299124). An earlier
similar-background crop has ambiguous matches and is not an independent anchor.
Do not apply this Pixel offset to the Moondrop's monotonic clock.

Both MatchStarted Articles were inferred from countdown/combat support, not a
direct GO observation. Pixel's inferred start falls near video 21.97 seconds;
the first visible GO is between 21.216 and 21.317. Thus aligning both recordings
only by their MatchStarted Articles would hide timing uncertainty.

## Two separate counting failures

The whole archives contain 54 Pixel and 73 Moondrop Gardevoir uses. Neither
records a Fast Move by the idle recipient team. These whole-match totals are
not certified ground truth; the opening 14 completed visual cycles are the
manually labeled control. Each recorded HP damage tick has exactly one use
descendant.

### Pixel: intermittent HP measurements are discarded after a gap

Pixel has 53 HP-backed uses and one border-only use. In the first groups,
retained losses are mostly four or five source pixels, above the ordinary
3.5-pixel uncertainty floor. This is different from the previously repaired
persistent three-pixel Fire Fang loss.

Examples of successive opponent HP measurements, approximately video-aligned:

| Earlier reading | Later reading | Gap | Later fill |
| ---: | ---: | ---: | ---: |
| 28.289 s | 29.658 s | 1.369 s | 0.941799 |
| 29.658 s | 31.115 s | 1.457 s | 0.914894 |
| 31.115 s | 32.654 s | 1.539 s | 0.888889 |

`ActiveHpBarDamageTickDetector` reacquires its baseline after a gap exceeding
1.25 seconds, preventing an unseen combined loss from becoming one invented
attack. Here that conservative rule also discards visible individual losses
because the opponent measurements are intermittent. Player HP readings exist
inside these gaps: the shared HP worker was running, but it did not publish
the opponent bar on those frames.

Replaying the current pixel measurer on independently recorded video frames
reproduces missing bars. During the blue pulse, sky pixels join the border:
one sampled top outline spans 193 pixels while its bottom merges with sky to
span 231 pixels, exceeding the approximately 220-pixel allowed maximum. Other
frames split the top outline. The blue color predicate already accepts those
pixels; simply broadening its color threshold would not solve this geometry
problem. These video-derived frames approximate the live capture scaling; they
are not the exact unpublished MediaProjection frames.

Increasing the gap limit alone would not establish whether one or several
attacks happened. Check the locator against video pixels before changing the
gap policy or capture rate. Missing measurements, rejected measurements, and
missed attacks must remain distinct.

### Moondrop: separate phases can become separate uses

Moondrop has 70 HP-backed uses and three border-only uses. Its first damage
use `3954f368-2659-4f47-ba1d-c1a763b04409` is followed 803.854 ms later by
border-only use `2d8ae065-2b1e-43f6-9f5a-fa57987ed06c`. At that point the
move duration is unresolved and the default 450 ms merge allowance separates
them. The first video group contains seven completed visual cycles, compared
with eight recorded uses. The later stutter report requires treating this as
a visual-versus-inference discrepancy, not independent proof of server counts.

The second group ends with HP-backed use
`31e5a736-5265-4200-833c-e68deea1d0c1`, then border-only use
`558f3fa1-49cd-416b-b42f-6e3712c6c0e9` 1,138.6 ms later. Charm is known by
then, but the 1,050 ms allowance still separates the phases. HP remains at its
post-hit plateau until the next attack group. The video again shows seven
completed visual cycles, compared with eight inferred uses.

A larger global window is not yet justified. The first border flash is also
only 714 ms before the next HP loss; nearest-neighbor matching can associate it
with the wrong attack depending on publication order. Any correction must cover
adjacent attacks, delayed publication, unknown identity, and independent
same-kind losses without rewriting the raw testimony.

## Audio, identity, and battle end

The SFX-only Moondrop accepted 28 of 31 audible sound measurements as Charm.
Its repeated legal-side audio identified Charm with publication around 15.0
seconds after its own recorded MatchStarted. The Pixel accepted none of its
36 sound measurements; Charm led 16 rankings but competing sounds remained too
close. With music enabled, its cadence/effectiveness hypothesis favored Magical
Leaf until later effectiveness against Garchomp established Charm, published
around 165.6 seconds after its own recorded start. Matcher similarity is not a
probability of correctness. This comparison supports keeping music off for
isolated reference tests; it does not justify lowering acceptance thresholds.

Both archives record Melmetal and Slaking faints and opposite final results
(Pixel WIN, Moondrop LOSS). Neither contains a final Garchomp faint Article.
No charged-move announcements or energy-spending Articles were found.

## Acceptance boundary

This review establishes a reusable 14-cycle visual control, successful idle
side attribution, an audio-mixing comparison, and two distinct counting defects.
It does not establish a general Charm visual classifier or certify the whole
match count. Keep both visual phases, audio, HP, and border evidence independent
while correcting the detector and downstream reconciliation.

## Bounded locator correction

The locator now applies its existing interior-contrast outline recovery to blue
pulses as well as neutral outlines. A blue candidate must also have actual blue
end-cap pixels ending against a nonblue interior on both sides. The existing
width, outline-gap, saturated-fill, and minimum end-cap support requirements
remain in force. Neither calibration nor the 1.25-second damage gap limit changes.

On the ten selected video-derived bar frames, three of the five previously
unreadable frames become measurable. At video 30.303 seconds, four widened sky
candidates are rejected and the correctly bounded bar is retained instead.
Two fragmented frames remain unavailable. White-to-blue frames at 29.685 and
30.303 seconds retain the same end caps and HP fraction. Regression fixtures
also include settled idle, blue sky, Shadow aura, Pokémon art, and touch rings.
Their exact PTS, video hash, resize, crop, and PNG hashes are preserved in
`app/src/test/resources/battle/hp/2026-10-27-charm-blue/provenance.json`.

A warmed host-only benchmark over the final 20 crops measured a median 0.774 ms
per call before and 0.925 ms after the change, an increase of 0.151 ms (about 20%).
This is a small-crop CPU comparison on the development computer, not a phone
capture-throughput result. Broader recovery scans may have a different cost.

The broader sequence check exposed two contaminated blue frames at video
36.006 and 36.104 seconds. A thin vertical blue line interrupts the green fill:
a 22–23-pixel filled segment is followed by a four-to-five-pixel gap and then
another 128 pixels of saturated fill. Stopping at the gap would report roughly
14% HP instead of approximately 84%. Blue candidates now abstain when another
substantial filled segment follows such a gap. The reader neither joins the
interrupted pixels nor calls the first fragment a new HP endpoint. The two exact
frames are retained as regression negatives; ordinary genuinely low HP remains
covered. A full-width 672-pixel recovery-band fixture and a dense unchanged
white/blue plateau control also pass independent source review.

## Independent sequence validation

An independent harness sampled 320 actual video frames over 24–56 seconds at
approximately 10 fps, resized them to the captured dimensions, and ran the old
and new pixel measurer, side assignment, and the unchanged damage detector.
Tracked opponent readings increased from 162 to 257; the longest inter-reading
gap fell from 2.505 to 0.798 seconds. The two false ~14% readings are absent in
the final guarded version. The old and new readers agree on fill for their
shared accepted frames.

The unchanged damage detector produces seven loss ticks with the old reader and
13 with the new reader across the 14 visually labelled attacks. The first group
still yields six ticks, including a combined loss; its last tick is timestamped
at video 38.809 seconds and settles at 38.918, inside the labelled 38.0–41.9
idle interval. This is a remaining timing error, so this review does not claim
zero idle-timestamped events. The second group yields seven. No additional loss ticks
occur in settled controls 39–42.5 or 53–56 seconds. Thus coverage improved, but
neither exact attack count nor exact event timing is fully repaired.

These offline counts differ from the original Pixel archive's one-plus-three
uses because this harness resamples the screen-recording video. It does not
recreate the phone's original source frames, publication scheduling, or drops,
and it does not replace the original Articles. Real-device acceptance requires
a new recording after installation.

Focused validation passed 72 tests, including nine new recorded/synthetic
regressions. Independent review found no remaining concrete issue in the final
patch and reproduced all 20 source fixtures pixel for pixel.

Final unit/build verification: all **1,157 tests across 198 suites** passed,
with zero failures, errors, or skips. `:app:assembleDebug` succeeded and
`git diff --check` passed. APK SHA-256:
`e88b2181236698b14c6a10010f52e47ac96a785c1115e43820acd606c5045d6f`.
The delayed border/HP pairing issue remains intentionally unchanged.

The independent harness, all 320 sampled crops, old/new output, damage ticks,
source hashes, and known limitations are retained in the desktop backup under
`hp-sequence-validation/README.md`. The original failed intermediate experiment
is distinguished from the final guarded results.

## Device installation

The tested APK was installed successfully on the working Pixel 4
(`99261FFAZ0017J`) and Moondrop (`MDPH00124112500414`). Both Droidball sessions
were stopped before installation. Neither foreground app was changed for an
additional UI check.

The installed APK on each device has the exact SHA-256 recorded above. Before-
and after-install hashes match for the selected calibration profile, battle
calibration, Team Select calibration, and Battle HUD layout preferences on both
phones. Installation therefore preserved these saved settings byte for byte.
Installation results and hash comparisons are retained in the desktop backup's
`installation-results.json` and `post-install-verification.json`.

No post-install live battle has been validated yet. The next code investigation
is the separate delayed border/HP pairing issue; this recording already contains
the necessary evidence, so repeating it is not needed for that investigation.
