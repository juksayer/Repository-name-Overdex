# Fire Fang recognition reference

Added November 8, 2026, from unchanged October 25 Moondrop playback PCM.

Arcanine's complete local Fast Move pool is Bite, Fire Fang, Snarl and Thunder
Fang. Fire Fang was absent from the reference library, so contextual inference
correctly withheld Thunder Fang despite strong comparisons against the
available sounds. The missing alternative is now measured rather than omitted.
No legal pool, score threshold, side-attribution rule or timing guard changed.

The new reference is one exact 1.2-second source window from the original
controlled Mawile battle. The user confirmed that only Mawile attacked in that
battle and that it used Fire Fang. The retained excerpt begins within an
effect and decays to silence; it is **recognition-only**, with
`replayEligible: false`. It is not a complete replay sound effect. Original
rate, channels, amplitude, WAV bytes and source clocks are preserved.

The [provenance](../../../app/src/main/assets/fast_move_sounds/captured-provenance-20261108.json)
records the source Match, Article, archive/WAV hashes and exact PCM bounds.
Other attacks from the original battle and the separate repeat supply
validation of the two predefined variants; their PCM is not used to construct
the reference. They informed variant selection and are not an untouched
post-selection test set.
The original battle's idle-opponent statement is not applied throughout the
repeat, which later contains other attackers.

The [verification report](../../verification/2026-11-08-thunder-fang-identification.md)
separates reference checks, recorded-match reconstruction and future live
acceptance. No original archive is rewritten with the new conclusions.

The current [coverage inventory](coverage-summary.json) has **79 files / 78
names**, covering **77/96** local species-pool names for recognition. Its
[remaining gaps](missing-fast-move-audio.csv) include Zen Headbutt as the one
ordinary missing source, sixteen typed Hidden Power aliases, Transform and the
Struggle catalogue anomaly. These are local catalogue counts, not a current
game legality audit, complete replay-audio coverage or an accuracy percentage.

The full generated audio audit is retained privately with the experiment;
only the changed Fast Move inventory is included here.
