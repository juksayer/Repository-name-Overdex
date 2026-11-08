# Thunder Fang identification through a complete legal audio comparison

## Problem and repair

The Pixel Rocket match `48a36312-70d1-4232-a9dd-6d69e1c98a89` supplied species
without configured moves. Gardevoir eventually resolved to Charm, but the
player's later Arcanine remained unnamed despite repeated strong Thunder Fang
audio comparisons.

All 23 Arcanine audio measurements requested its four legal local moves: Bite,
Fire Fang, Snarl and Thunder Fang. Opponent Garchomp contributed Dragon Tail
and Mud Shot. The library had no Fire Fang reference, so every scored list was
incomplete. Fifteen global comparisons passed their numerical acceptance
checks, but that flag is not a formal move identity. Contextual inference
correctly refused both strict identity and a plausible preference while a
legal alternative remained unmeasured.

At source +54.648542 seconds, Thunder Fang scored 0.9916465 / 0.9347373 against
Snarl's 0.9193180 / 0.7377781. The original/changing-spectrum thresholds were
already satisfied. These scores are similarities, not correctness
probabilities. Bypassing the absent alternative or lowering thresholds would
not repair the reference gap.

The change adds a source-verified Fire Fang recognition excerpt. The legal
pools, score thresholds, distinct-occurrence checks, source-time appearance
guards, charge-phase guards and independent impact requirements are unchanged.
No species move configuration or original archive is edited.

## Fire Fang source and independent comparisons

The runtime reference is the complete unchanged retained 1.2-second WAV from
the original controlled October 25 Moondrop battle:

- Match `b7344c07-2c4e-43f5-9a88-4a09c7dc099f`, source +18.223218386 seconds.
- Audio Article `7a68b4c8-3a94-48cc-b9ec-3fcbea69b06b`.
- Archive SHA-256
  `101f88e7e54c276ab9fb2fc6c8c692e4c693343bb3a8c7219fc9c8433b3381e2`.
- Original and bundled WAV SHA-256
  `751ebfcd5967fd112e25dc7868f5e2df1d08279cd64940df8a7837f44b9dc82b`.
- 9,600 frames, 8 kHz, mono PCM16, no digitally clipped samples.

The user confirmed that only Mawile attacked in the original battle and that
its equipped move was Fire Fang. The selected interval decays to silence, but
begins within an effect. It is marked `recognition_excerpt` and
`replayEligible: false`; it is not a complete replay sound effect. No
filtering, normalization, resampling, subtraction, stitching, synthesis or
looping was applied. Provenance preserves exact source clocks and PCM bounds.

Two other physical attacks supplied validation of the two predefined reference
variants. Their PCM was not used to construct either reference:

| Held-out query | Scope | Actual full-pool result |
| --- | --- | --- |
| Original battle +140.594 s | Different attack, same battle | Fire Fang accepted; original/changing similarity 0.968739 / 0.968739 |
| Separate repeat +57.281 s | Separate battle, before its later attacker changes | Fire Fang accepted; original/changing similarity 1.000000 / 1.000000 |

The comparisons retain Mawile's legal alternatives and the other active
combatant's legal sounds. The repeat later contains Dragonair and Galvantula
activity; the original battle's idle constraint is not applied to the entire
repeat. The supplied MP4 contains no audio stream; the sounds are retained
Overdex playback PCM.

A shorter first-second variant was also compared. It failed the original
held-out attack's independent changing-spectrum margin. The unchanged full
1.2-second window was selected on these Fire Fang checks, independently of
Arcanine's results. These checks are therefore validation used for selection,
not an untouched post-selection test set. Neither option altered acceptance
thresholds.

The independent production-matcher comparison covered 38 distinct queries in
68 legal-pool cases, comparing the baseline and both reference variants. All
56 known-negative cases stayed rejected: music, cries, charged/type effects,
stat effects and captured silence, each compared against the Mawile/Aegislash
and Arcanine/Garchomp pools. Four entry/result cases and four weak/QTE-adjacent
Arcanine controls also stayed rejected. Two opponent-confounded comparisons
retained their prior behavior; no control newly became Fire Fang. These are
selected rejection checks, not a field false-positive rate.

The excerpt's reference offset is relative to the retained excerpt. It does
not establish the beginning of a complete physical attack or its damage time.

## Re-running the Rocket recording

The private checker processed all 2,123 original Articles, including all 61
sound measurements, in publication order. It retained player identity
projection, raw impacts, phase observations, type evidence and source clocks.
Fresh audio measurements carry separate analysis IDs and cite their original
PCM/measurement basis; the archived Articles remain unchanged.

| Comparison | Arcanine conclusion |
| --- | --- |
| Original recorded audio measurements | Unresolved |
| Fresh production matching with the original library | Unresolved |
| Fresh production matching with the complete Fire Fang reference | Thunder Fang at GO +57.374287397 s |

Arcanine's active identity was witnessed at +53.921635 seconds. The resulting
audio identity therefore follows about 3.45 seconds later, after independently
timed supporting comparisons. Its basis is
`AUDIO_REFERENCE_REPEATED_UNIQUE_LEGAL_SIDE`, with derived confidence 0.90.
This is not a 90-percent accuracy measurement. The other eight derived move
identities were unchanged in the full recorded-match checks.

The overlapping +54.648542 and +55.843199-second windows align to the same
sound occurrence near +55.41 seconds. They are a duplicate-overlap control,
not two independent attacks. This recording does not independently establish
an audio-stutter episode. Source labels and acoustic decisions remain separate:
the user's “Charm, and Thunder Fang, I think” recollection is supporting
recollection rather than saved move configuration or certain ground truth.

## Acceptance boundary

These checks establish the missing-reference cause and a supported
reconstruction from retained real audio. They do not rewrite the saved match,
create attacks from identity alone, or establish future live recognition
latency. A new battle with this APK is still needed to check live identification
and broader two-attacker/noise conditions. Fire Fang is now available for
recognition; Zen Headbutt remains the ordinary missing named source.

Private source archives, WAVs, candidate comparisons, baseline/completed
publication-order replay reports and implementation hashes are retained under
`/home/sean/overdex-device-backups/2026-11-08-thunder-fang/` and the paired
concession verification directory. The [reference notes and current inventory](../audio/2026-11-08-fire-fang-reference/README.md)
link the checked-in provenance and remaining gaps.

## Final checks and installed build

- The focused audio suite passed 24 tests. The final full JVM suite passed
  **2,056 tests**, with zero failures, errors or skips; debug and debug-test
  APK builds succeeded.
- Five new JVM regressions verify source hashes/PCM, recognition-only status,
  distinct Fire Fang attacks, incomplete-pool rejection, the selected overlap
  history, source-time Thunder Fang identity and citations, weak/QTE-adjacent
  rejection, and no attacks or energy created by identity alone.
- The actual packaged Android loader passed **OK (1 test)** on API 37. Its
  seven source WAVs exercise two accepted Fire Fang comparisons, two accepted
  Thunder Fang comparisons, and three withheld comparisons including the
  opponent-confounded overlapping clip. The first Android assertion incorrectly
  expected global acceptance for that mixed clip; its expectation was corrected
  to match the existing recorded comparison. Runtime code and thresholds did
  not change, and the production APK remained identical.
- The private full publication-order comparison passed four standalone JVM
  checks. Its original, baseline PCM and both completed-reference variants keep
  separate reports; the full 1.2-second variant is the shipped reference.
- All 79 runtime reference hashes and seven checked-in fixture WAV hashes were
  verified. The local catalogue inventory has no unusable manifest files or
  unresolved move IDs. Relative documentation links and `git diff --check`
  passed.

The installed debug APK SHA-256 is
`98bf6c9c7ed00b040256259fb53db2f57de7a9cea852bde61d3208e699eb4651`.
The Moondrop and Pixel 4 were backed up after confirming their recording
sessions were inactive, then updated with replacement. Before reopening,
all 411 backed-up Moondrop files and all 91 Pixel files remained byte-identical,
with no added or missing files. The separate loose artifact store was excluded
from those backups. Both installed base APKs matched the tested hash. Both apps
resumed MainActivity without fatal entries in the bounded new-process startup
logs. Neither Droidball service nor MediaProjection was running afterward.

Phone instrumentation was not used. The emulator was stopped after its check.
These installation/startup checks do not replace the next real battle's live
acceptance.
