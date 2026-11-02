# Captured Fast Move references: November 1, 2026

Added user-confirmed **Confusion (Golduck), Rock Smash (Camerupt), and Bubble
(Mantine)** recognition excerpts from the two November 1 private test battles.
Music was off and SFX at 100% on the Moondrop; Pixel music was 20%, SFX 100%.
Both screen recordings contain video and Android metadata, with no audio stream.
The sounds come from verified Overdex playback PCM, not the MP4s.

The Moondrop supplies one runtime reference per move. Each is an exact integer
frame trim of an archived 8 kHz mono PCM16 WAV. There is no resampling, level
normalization, denoising, synthesized subtraction, looping, or stitching in the
import. Original videos/archives remain preserved privately. The bundled
[reference provenance](../../../app/src/main/assets/fast_move_sounds/captured-provenance-20261101.json)
binds source archive/Match/Article hashes, original clocks, exact sample-frame
bounds, confirmed equipment, derived WAV/PCM hashes, and processing notes.

## Scope and acceptance

These are recognition references. Confusion and Bubble exceed some retained
1.2-second windows; their excerpts are bounded motifs, not certified complete
sound effects. Rock Smash has a useful near-isolated envelope and quiet tail,
but all three entries remain `purpose: recognition_excerpt` and
`replayEligible: false`. Replay skips them while continuing to use complete
legacy assets. This does not add three reconstructed replay sounds.

Held-out queries comprise three samples from the other battle, three additional
Pixel/music challenges, and two quiet comparisons. Every positive comes from a
recorded Match different from its runtime reference, and none reuses the
reference PCM. The active legal pools used for these fixtures are:

| Move | Legal comparison pool across the two active combatants |
| --- | --- |
| Confusion | Confusion, Water Gun, Mud Shot |
| Rock Smash | Ember, Rock Smash, Incinerate, Water Gun, Mud Shot |
| Bubble | Bubble, Bullet Seed, Wing Attack, Counter, Lick |

The unchanged matcher accepts all six held-out positives and withholds both
quiet comparisons. The three independent-battle query scores are:

| Move | Spectral similarity | Changing-spectrum similarity |
| --- | ---: | ---: |
| Confusion | 0.9837 | 0.9295 |
| Rock Smash | 0.9575 | 0.9307 |
| Bubble | 0.9942 | 0.9792 |

These are similarities, not probabilities. This small selected corpus does not
establish general live precision/recall, both-attacker separation, or reliability
at every music level/device. Rock Smash fails the stricter full-catalogue
changing-spectrum margin against the impossible-in-this-match Sucker Punch;
its legitimate active pool separates it. Live comparisons remain restricted to
active legal moves. Existing thresholds were not lowered.

Reproducible fixtures and source provenance are in
[the test manifest](../../../app/src/androidTest/assets/fast_move_20261101/manifest.json).
The old reference/recorded-fixture regression is also checked with the expanded
catalogue. Raw testimony in the original four archives is not rewritten.

## Remaining capture requests

The library now has **75 reference files / 74 names**, covering **73 of 96**
local species-pool Fast Move names for recognition, up from 70. No reference
hashes or catalogue IDs are unresolved. This includes the three recognition-only
excerpts and does not measure complete replay sound coverage.

The five ordinary named-source gaps are **Fire Fang, Mud Slap, Poison Sting,
Steel Wing, and Zen Headbutt**. Review the existing paired Fire Fang material
before asking for another recording. The other 18 exact-name gaps remain the
16 Hidden Power type labels, Transform, and the catalogue Struggle anomaly;
these are not 18 ordinary recording requests. See the
[current missing list](missing-fast-move-audio.csv) and
[coverage summary](coverage-summary.json).

For a future full-effect recording, keep music at zero, SFX at 100%, the other
team idle, and enable **device audio** in the screen recorder. A brief visual
animation stall alone does not disqualify audio when its source and HP impacts
can still be inspected. The supplied battles are already useful; they do not
need to be repeated just to obtain these three recognition references.

## Reproduction

`tools/import_captured_fast_move_audio.py` takes a JSON plan naming a verified
source archive, AudioCaptured Article, move/species label and exact sample-frame
trim. It produces candidate WAVs and provenance in a new private directory;
it never admits candidates to the runtime manifest automatically. Its help
includes the plan schema. Runtime admission requires independent source review
and held-out evaluation. Reference/holdout overlap and identical PCM are refused.

See [verification](../../verification/2026-11-01-captured-fast-move-audio.md)
for source integrity, native checks, installation and remaining acceptance limits.
