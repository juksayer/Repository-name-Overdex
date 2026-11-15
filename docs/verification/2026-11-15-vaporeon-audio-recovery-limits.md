# Vaporeon retained-audio recovery limits

The existing Replay recovery selects only clips beginning in the final 40 s of
the match and with duration at most four seconds. The trainer's Vaporeon entry
is earlier, and the original ending `CHARGED_MOVE_EFFECT` clip is 13 seconds.
The current waveform matcher accepts at most four seconds per query, so using
that ending clip needs overlap-aware bounded slicing with original timestamps,
content deduplication, and resource limits. No filter or runtime was changed.

In an exploratory comparison of 600 ms reference fragments with the original
retained clips, a 0.9 s-offset Vaporeon reference fragment matched the entrance
clip at approximately 0.769; the strongest selected Incinerate fragment scored
0.630. In a bounded ending search, a Vaporeon fragment scored approximately
0.890 while a Water Pulse fragment scored 0.901. Their offsets differ and these
scores alone cannot establish that the sounds are alternatives at the same
instant; the overlap and close match show why lowering a global threshold is
not justified. This probe uses selected offsets and references, not exhaustive
competitor coverage or the production matcher; comparisons are not confidence
probabilities. Original sound and visual effects remain independent evidence.

The previously reviewed full recording supports the trainer-labelled entrance
and faint context. It does not make the weaker archive-only audio match an
accepted production cry, nor does the final result alone prove a particular
move. A safe implementation still needs bounded slicing, source-time distinct
occurrence tests, simultaneous-sound comparisons, living-reentry and missed
frame controls, and a separately supported move-to-faint association carried
through the shield phase. The originals were not rewritten. Exploratory script
and scores are in the 1 TB backup directory `2026-11-15-moondrop-0931-review/`.
