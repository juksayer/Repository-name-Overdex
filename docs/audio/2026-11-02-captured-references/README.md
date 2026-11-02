# Captured Fast Move references: November 2, 2026

Added recognition excerpts for user-confirmed **Gastrodon / Mud Slap,
Drapion / Poison Sting, and Skarmory / Steel Wing**. The user confirmed music
at zero, SFX at full volume, and the other team idle on both phones. The second
battle used continuous attacks. Both supplied MP4s have video and metadata but
no audio stream; sounds come from verified Overdex playback PCM.

The first battle has both phones' archives. The second has Pixel's archive and
the attacking-side screen recording, with Gastrodon switching directly to
Skarmory. Drapion did not fight in the second recording. No second Moondrop
archive or second-battle Poison Sting evidence is invented.

## What these references establish

| Recorded effect | Held-out acoustic result | Scope |
| --- | --- | --- |
| Steel Wing | Two other-battle queries accepted against the active legal pool | Wing Attack is excluded because it is not in these active species' local move pools |
| Poison Sting | Two different attacks accepted against the active legal pool | Same battle, different physical attacks 14.6 and 37.8 seconds after the reference; no independent-battle claim |
| Mud Slap | Two other-battle queries withheld as ambiguous with Mud Shot | Both sides' legal alternatives include Mud Shot; the 416 ms sequences are too similar for an audio-only conclusion |

The old catalogue, missing Mud Slap, accepted the two selected Mud Slap queries
as Mud Shot in this offline legal-pool comparison. Adding the authentic missing
alternative prevents that false certainty. This is a matcher diagnostic, not
proof that live inference previously confirmed Mud Shot: live attribution also
checks context, candidate-pool completeness, and other evidence. Gastrodon's
16 typed Hidden Power alternatives are requested but still lack validated exact
references; those gaps remain visible to downstream completeness checks.

Steel Wing resembles Wing Attack in a full-catalogue diagnostic, but that
impossible alternative is excluded before the active-pool comparison. Two
separate-battle Steel Wing queries separate correctly. For example, spectral /
changing-spectrum similarities are 1.0000 / 0.9961 for Steel Wing and
0.9216 / 0.8378 for Air Slash. Poison Sting scores 0.9998 / 0.9958 and
0.9728 / 0.9159 on its two held-out attacks. These are similarities, not measured
probabilities. Two retained quiet comparisons remain unconfirmed. No thresholds
were reduced and no species' impossible move was added to its legal pool.

## Preservation and limits

Three exact PCM-frame trims retain original rate, channels and amplitude.
The [source provenance](../../../app/src/main/assets/fast_move_sounds/captured-provenance-20261102.json)
binds archive / Match / Article / WAV hashes, original clocks and integer
sample bounds. Source archives and videos remain unchanged and private.
No denoising, subtraction, resampling, normalization, looping or stitching is
used. All three are `recognition_excerpt` with `replayEligible: false`, since
these bounded windows are not certified complete replay sound effects.

[Eight fixtures](../../../app/src/androidTest/assets/fast_move_20261102/manifest.json)
keep the recording label separate from the expected acoustic decision; a known
Mud Slap recording must not force the matcher to confirm it. Original archived
Articles are not rewritten. These selected checks do not establish general live
accuracy, two-attacker separation, or rapid inference through every stutter.

The library now has **78 WAVs / 77 names**, covering **76/96** local species-pool
Fast Move names for recognition. The ordinary source gaps are now **Fire Fang
and Zen Headbutt**. Review existing Fire Fang material first. Sixteen typed
Hidden Power aliases, Transform, and the catalogue Struggle anomaly remain
separate issues. See [remaining gaps](missing-fast-move-audio.csv) and the
[coverage summary](coverage-summary.json); this inventory is not complete
replay-audio coverage or a live-game legality audit.

For future full-effect capture, enable **device audio** in the screen recorder
in addition to music zero and SFX full. These recordings already supply useful
recognition material and do not need a wholesale redo.
