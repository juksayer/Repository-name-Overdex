# Recorded cry-sequence experiment — November 12, 2026

## Result

The two recordings contain recognizable species cry waveforms at all eight manually reviewed faint moments. Two different opponent cries within a short window are NOT sufficient: an ordinary Swampert → Abomasnow switch produces a counterexample. However, tracking the active species and repeated cries produces a useful positive pilot result: in the non-mirror recording, repeated active-species cries line up with all four reviewed faints, while the two ordinary switches do not generate a preceding repeated-active cry candidate.

This is a retrospective experiment, not a measured live accuracy rate or a production rule change. It demonstrates that the audio contains useful information and identifies a more specific hypothesis to validate. It does not establish that every repeat cry means faint or uniquely identify the damaging move.

## Inputs and preservation

Originals, read-only:
- `/home/sean/Videos/Overdex/Moondrop-2026-11-12_15-53-26-vCy2v5.mp4` (247.772 s).
- `/home/sean/Videos/Overdex/Moondrop-2026-11-12_16-05-21-wzGrCr.mp4` (149.590 s).

SHA-256 digests and actual decoded durations are in `waveform-results.json`. All generated media, scripts and numeric results are in this directory on the 1 TB drive. No archive articles were modified or replayed as observations. Original archive faint labels and Overdex overlay X marks were not treated as ground truth. Native game sprites, banners, HP, replacement prompts, native ball counts and later survival supplied the manual labels.

## Method

`scan_cries.py` decodes each recording and each selected bundled cry to mono 8 kHz PCM. It measures absolute, sliding, energy-normalized waveform cross correlation using FFT convolution. The six species were chosen from the recorded teams: Turtonator, Camerupt, Gourgeist, Golbat, Swampert and Abomasnow. There are 18 manifest reference entries, including duplicate sources and variants. `evaluate_cases.py` verifies their actual SHA-256 values against the manifest.

Both original speed and 0.8 speed were explored. Reported strong matches use original speed. Scores are correlations, NOT confidence probabilities. Duplicate assets/variant matches are not separate cries. A form-specific Gourgeist reference matches one recording better; this acoustic choice does not authorize adding an inferred size label to the user's Pokémon card.

Timestamped contact sheets were inspected at one- or two-second spacing near transitions and half-second spacing at countdown. This locates and corroborates transitions; it does not claim frame-exact KO times. Audio start offsets are numeric template positions in the recording. They are not exact damage-resolution timestamps.

The current production `CryAcousticMatcher` in `WavPcm16.kt` compares normalized RMS envelopes (48 reference bins against 96 query bins); it does not perform this waveform comparison. Consequently this pilot does NOT validate the current production cry rankings or phone processing cost.

## Manually reviewed faint cases

Correction from container inspection: the numeric cry times below are seconds from decoded AUDIO onset, not universally video/container time. Add 0.348521 s for recording 1553; add 0 for recording 1605. They are not Replay times. The video stream in 1605 starts at container 0.140089 s; visual sheets are approximate sampled seek positions. GO is visible at approximately 87 s in 1553 and 35 s in 1605 (half-second sampling).

| Recording | Side / Pokémon | Cry start | Correlation | Independent visual support |
|---|---|---:|---:|---|
|1553|Opponent Turtonator|118.179|0.9405|Dragon Pulse sequence, departure, native count decrease and Gourgeist replacement|
|1553|Player Turtonator|165.219|0.6551|Shadow Ball sequence, empty player field, forced replacement prompt|
|1553|Opponent Gourgeist|182.733|0.9970|Low HP, visible fire attack, disappearance and Camerupt replacement with fewer native balls|
|1553|Opponent Camerupt|233.933|0.9990|Rock Tomb sequence followed by a long still interval, shrinking/disappearance, win screen|
|1605|Player Turtonator|74.615|0.9949|Poison Fang sequence, disappearance and forced replacement prompt|
|1605|Opponent Golbat|93.943|0.7997|Low HP before its Shadow Ball, player shield, opponent departure and Swampert replacement|
|1605|Opponent Abomasnow|107.895|0.9714|Low HP during normal combat, disappearance, Swampert returns with one native ball remaining|
|1605|Opponent Swampert|137.015|0.9218|Low HP after Seed Bomb and further normal combat, shrinking/disappearance and win screen|

These are eight selected, visually reviewed moments across two recordings, not eight independent trials of a frozen detector. The mirror recording cannot assign a Turtonator or Camerupt cry to a side from species alone; side labels above come from video.

## Negative controls and timing-window test

1605 provides two ordinary switches:
- Player Camerupt → Gourgeist near 98–99 s: Camerupt has positive HP before departure and remains in the inactive card afterward. Gourgeist cry at 98.785 s. No extra Camerupt match above the exploratory 0.5 threshold at departure.
- Opponent Swampert → Abomasnow near 100–101 s: Swampert has positive HP, opponent native remaining count does not decrease for this switch, and Swampert returns alive near 110–111 s. Swampert entrance cry at 97.740 s, Abomasnow entrance cry at 101.217 s. No extra Swampert match above that threshold between them.

Absence of a template match is not proof of absent audio. These are observed detector outcomes on these files.

| Consecutive different opponent cries | Gap | Actual transition |
|---|---:|---|
|1553 Turtonator → Gourgeist|4.970 s|Faint → replacement|
|1553 Gourgeist → Camerupt|2.347 s|Faint → replacement|
|1605 Golbat → Swampert|3.797 s|Faint → replacement|
|1605 Swampert → Abomasnow|3.477 s|Two entrances separated by an ordinary switch|
|1605 Abomasnow → Swampert|2.240 s|Faint → replacement|

A maximum-gap threshold of 3 s includes only two of these four KO pairs. At 3.5 s it also includes the switch; at 5 s it includes all four KO pairs and the switch. This is an explicit counterexample to the bare two-different-cries rule, not a population false-positive estimate. Counting all mixed audio without side separation is worse: the player's Gourgeist entrance occurs between the Swampert and Abomasnow cries.

## Elapsed match time and stateful cry sequence

User refinement: elapsed time and number of cries in succession may explain their purpose.

The opening Turtonator and Golbat cries in 1605 precede GO; match phase therefore supplies useful initial-entry context. Mid-battle, elapsed time alone does not decide purpose: ordinary switches and faints both occur in this recording's approximately 59–75 s elapsed interval.

`sequence_pilot.py` processes the detected original-speed matches above an exploratory 0.5 threshold in source order, deduplicates repeated reference assets, and keeps one active species per side. Side mapping is supplied from the independently inspected, disjoint rosters; it is NOT learned from audio. GO is approximated at 35 s. It emits candidates only:
- Cry before GO: opening entry.
- Cry matching the currently active species: departure candidate; await confirmation.
- Different species after a candidate: replacement.
- Different species while another remains active: transition without a preceding departure candidate.

In 1605 this yields four repeat-active departure candidates corresponding to the four reviewed faints, and two different-species transitions corresponding to the ordinary switches. Output: `sequence-pilot.json`. This supports testing a stateful model rather than a raw cry counter. It is NOT proof that repeat-active cries uniquely mean faint, especially with missed clips, repeated entrances, same-species mirror matches, or other cry-producing circumstances. The final Swampert faint has no following replacement cry, so a mandatory two-cry requirement would miss it.

## KO move attribution

A cry identifies a sound occurrence/species candidate. Once the faint is independently established, it can help bound the victim's departure time. The preceding compatible damage-producing move is then a KO candidate.

The recordings do not justify always awarding the KO to the last move animation or banner. Golbat departs after its OWN Shadow Ball is shielded; pending player damage must be considered. The final Camerupt disappearance is delayed well after the Rock Tomb banner, so a short mandatory cry-after-move timeout would be unsafe here. The 1605 recording also contains visible effects continuing across species changes; those frames do not independently prove the source of each final Fast Move.

Thus the first user rule remains conditional: confirmed faint + compatible attacker/target/source-time damage history may support KO attribution. These cry experiments do not resolve every individual finishing move. No missing attack was invented. A separate damage/move trace is needed for ambiguous causes.

## Reproduction

Run with Python dependencies in this folder (NumPy 2.5.4, SciPy 1.18.1; Pillow available in system Python):

```
PYTHONPATH="$PWD/python-deps" python3 scan_cries.py
python3 evaluate_cases.py
python3 sequence_pilot.py
```

Run from this directory. The scripts contain explicit read-only source paths and the retained ffmpeg path. `make_sheet.py RECORDING START STEP COUNT` recreates timestamped visual evidence. `case-results.json` contains reference hash checks, labels, numeric scores and the gap sweep.

## Recommendation and boundary

Next bounded implementation candidate: a small active-roster waveform matcher feeding a source-ordered cry/appearance history, initially as supporting evidence. Preserve uncertainty for mirror sides and capture gaps. Use native count/departure/survival checks to confirm faints before assigning KO credit, and retain pending damage through charge/shield phases.

No 1,000-species visual collection is needed for this experiment. No production recognizer, installed build, archive, HUD or inference threshold changed. Desktop offline results do not establish device latency or audio-only reliability. The experiment answers the proposed shortcut with a concrete negative control and identifies a better-supported sequence hypothesis; production readiness remains unproven.

## Follow-up timestamp correction and Kotlin reproduction

The first PCM scan discarded stream start metadata. `ffprobe` establishes 1553 video start 0 and audio start 0.348521 s; 1605 audio start 0 and video start 0.140089 s. The 1553 cry onsets therefore require +0.348521 s when compared to container seeks. Within-recording cry gaps and the switch counterexample are unaffected. Original scan JSON remains audio-origin-relative. `kotlin-fixtures/cases.tsv` explicitly uses corrected container times, and `kotlin-waveform-results.tsv` records the independently localized offsets.
