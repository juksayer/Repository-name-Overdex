# Retained ending audio: Seed Bomb and Dragonair — November 14

## Finding

The audio survived. Two preserved windows strongly match different parts of
**Seed Bomb**, followed by a third window strongly matching the surviving tail
of **Dragonair's cry**. The estimated reference onsets are about **2:32.6** and
**2:35.0** after Match Start. This independently supports the user's recollection
of Seed Bomb at the ending.

This is retrospective acoustic evidence, not a new live recognition rule. It
does not change the archive, add a Replay attack or automatically declare a KO.
A cry identifies a sound/species; its purpose and move-to-faint attribution
still require the existing appearance, phase and outcome evidence. In this
match the retained history has Gourgeist facing the last Dragonair, followed by
WIN. The sound sequence supports the proposed final KO reconstruction, while
the original missing move/faint conclusions remain missing.

## Input and method

Archive: `bd8af3b0-8ce5-4a2b-9917-19eb11fe91c8`.

SHA-256: `e12961a030758d39d6affbb1fcb5fe743941d5bfec32c66191f21a6f07497cb1`.

Inputs, verified reference hashes, output tables and unchanged audio copies:

`/mnt/overdex-backup/archives/overdex-device-backups/2026-11-14-new-moondrop-review/ending-audio-analysis/`

The diagnostic compares all 85 preserved audio Articles from this archive,
including earlier captured controls, against 29 reference entries: all available
reference effects for Gourgeist and Dragonair's legal charged and Fast Moves,
plus cry references/variants for the six participating species. No requested
move reference is missing. Duplicate label/hash entries are collapsed by the
diagnostic. Other-species/form cries are competing controls, not new identities.

Three comparisons are kept distinct:

1. The current complete-waveform cry matcher and its unchanged acceptance policy.
2. The current spectral-sequence matcher, used diagnostically across effects and
   cries. Its Fast Move acceptance policy is not a validated charged-move policy.
3. A sliding 600 ms waveform-fragment comparison, including each reference's
   final 600 ms. These scores describe partial references, not complete cries.

Long original control clips are analyzed in bounded, overlapping windows up to
four seconds. Their times retain the original capture origin plus window offset.
Short ending clips are not joined, gap-filled, normalized for playback or edited.
Scores are similarities/correlations, **not probabilities**.

## Ending measurements

| Original clip start | Best 600 ms waveform match | Correlation | Best competing correlation | Interpretation |
| --- | --- | ---: | ---: | --- |
| 2:21.745 | Aqua Tail | 0.9554 | 0.1322 | Earlier incoming charged effect; not Seed Bomb |
| 2:23.344 | Aqua Tail | 0.8955 | 0.1858 | Later portion of that effect |
| 2:24.348 | No convincing waveform identification | 0.2042 | — | Do not force a charged-effect/cry label |
| 2:32.146 | Seed Bomb, reference offset 0.0 s | 0.99986 | 0.3012 | First retained part of Seed Bomb |
| 2:33.733 | Seed Bomb, reference offset 1.4 s | 0.99988 | 0.1836 | Later retained part of the same Seed Bomb |
| 2:35.333 | Dragonair cry, reference offset 0.398375 s | 0.97256 | 0.2884 | Retained cry tail |

Spectral comparisons independently rank Seed Bomb first in both Seed Bomb clips
(0.9678/0.9742 original-spectrum similarities and 0.9357/0.9321 changing-spectrum
similarities). Dragonair's cry is first in the last clip (0.9742 and 0.9465).

The two Seed Bomb fragment alignments estimate reference starts at 152.583 and
152.570 seconds, about 13 ms apart. They represent **one effect**, not two uses.
The timing estimate comes from aligning reference offsets, not a recovered
`used` announcement or an exact damage timestamp.

Dragonair's tail alignment estimates cry onset at 155.024 seconds. Its retained
clip begins at 155.333, roughly 309 ms after that estimated onset. The complete
reference lasts 998 ms; the missing beginning prevents a valid whole-waveform
match. The complete matcher scores the last window only 0.0702, below its 0.5
pilot threshold. Lowering that threshold would not solve the missing-prefix
problem safely.

Exact retained source Articles:

- Seed Bomb first part: `2f840a89-7148-44a1-a24c-1dc36e528738`.
- Seed Bomb later part: `1aae02ab-5198-40b6-b789-ebae2291d262`.
- Dragonair cry tail: `b0ff96b6-8521-4db9-8377-56f3c19ec70f`.

## Controls and recognition boundary

The unchanged whole-cry matcher recovers earlier real cries in this archive,
including Dragonair entry (0.7337 and 0.8187), Camerupt (up to 0.9789), and
Floragato (0.9950). These independent captured controls show that the references
and comparison pipeline work on this device's recording.

Deliberately truncating 310 ms from the earlier, captured Dragonair entry
reproduces the failure: complete cry 0.8187 → truncated whole-cry comparison
0.0538, while its surviving 600 ms tail still scores 0.7582. Synthetic reference
self-comparisons also pass as basic implementation sanity checks; these are not
live accuracy claims.

The original ending sound measurements searched only Incinerate, Dragon Breath
and Iron Tail. Seed Bomb was not in their Fast Move search scope, and both Seed
Bomb windows were rejected as Fast Move matches. That rejection was correct for
that reader's assignment. The charged-effect assets currently serve Replay
playback rather than a live charged-effect recognizer.

The truncated retained clip explains why **retrospective complete-cry matching
on that clip** fails. It does not establish why the live reader, which normally
uses longer windows, failed to preserve a complete final cry. Unretained live
buffers cannot be reconstructed from these clips; scheduling and live context
remain separate questions.

## Verification and next bounded step

```sh
OVERDEX_ENDING_AUDIO_REVIEW=/mnt/overdex-backup/archives/overdex-device-backups/2026-11-14-new-moondrop-review/ending-audio-analysis \
JAVA_HOME="$PWD/.jbr" ./gradlew :app:testDebugUnitTest \
  --tests '*RecordedEndingAudioReviewTest' \
  --tests '*CryWaveformMatcherTest' --tests '*CryWaveformAcceptanceTest'
```

Passed: **8 tests, zero failures/errors/skips**. The opt-in regression checks
both Seed Bomb fragments, the Dragonair tail, the unchanged whole-cry rejection,
the deliberately truncated real entry control, and input/reference hashes.

No production rules, installed build, archived Articles or source audio changed.
No new APK is required for this diagnostic result.

The next bounded implementation should expose this retained audio as separately
cited retrospective charged-effect and partial-cry evidence, then test the
move/faint association against the established appearance and outcome history.
Do not lower the global cry threshold, promote two windows to two attacks, or
treat a partial cry alone as proof of fainting. Additional negative windows and
entry/switch controls are needed before enabling a general partial-cry policy.

## Implemented retrospective recovery

`ArchivedEndingAudioRecognizer` now reads bounded original playback WAV entries from the opened full archive and verifies their hashes, sizes and audio metadata. It compares all available legal Fast and Charged Move references for the recorded species, configured/recorded extra move names and species cry variants. Fast effects stay in the comparison: a recovered Fast effect remains an unsided candidate for causal inference, not an automatically counted attack. Reference possibilities are never evidence of use. Missing references are reported; missing references relevant to either active Pokémon prevent terminal attribution.

`ReplayRetainedAudioRecovery` uses 600 ms waveform fragments, including the actual reference tail. Charged recovery requires correlation at least 0.98, a 0.15 lead over a differently named reference, two separately captured/nonidentical windows, disjoint reference and query portions, and onset agreement within 80 ms. Partial-cry recovery requires at least 0.95 with the same margin; its purpose remains unresolved. These are deliberately narrow retrospective guards validated on this recording, not newly validated universal/live acceptance thresholds. Each fragment retains the source Article ID, original audio hash, reference hash, query/reference offsets, duration, correlation and runner-up. No source samples are padded or stitched.

The reviewed match recovers one Seed Bomb at **152.583 s** and Dragonair's partial cry at an estimated **155.024 s** after Match Start. The cry's original capture begins at **155.333 s**; these clocks remain distinct. The two Seed Bomb fragment correlations are approximately **0.99986 and 0.99964**, and the cry tail is **0.97256**. Scorching Sands and Brutal Swing lack bundled sound references and remain explicitly unavailable; neither belongs to the two active Pokémon in this ending.

The new Replay-only terminal rule links this attack to the final opponent KO using stable and distinct active identities, a unique legal performer, the last-survivor count, zero shields, the partial recipient cry and the native win. It rejects competing charges, pending or unrendered Fast Move claims, independently recovered Fast effects, additional cries, replacement/observation boundaries, contradictory resource readings, missing relevant media/references and accepted positive recipient HP after the cry. A generic sound observation can be explained by the recovery only when it references the same original clip, its onset falls inside the actually matched fragment, and it was not already accepted as a Fast Move.

`ReplayPossibleDamageClaims` exposes original canonical uses, charge announcements/energy claims, damage ticks, border pulses, recipient effects and audible/accepted sound claims. It does not discard an unnamed move because it was not scheduled for animation. The causal check also receives the original and reconstructed Fast impact clocks, so a pending earlier move can remain a competitor.

This rule initially covers a missing final opponent faint followed by a native win; it does not generalize to loss, tie, simultaneous mirrors or an already recorded faint. A cry alone and a result alone still cannot establish the KO. Capture gaps remain unknown, so the outcome is labeled an **audio reconstruction**, not a directly witnessed empty HP frame or a claim of certainty. The decorative Seed Bomb flight lands at the inferred departure, while source timestamps, original Articles, audio, measured HP, measured Poké Ball counts and recorded energy remain unchanged. No new damage ticks, energy costs, QTE grades or raw announcements are invented.

The work runs when opening a full archive in Replay, off the UI thread. It does not add live capture processing. Bounds: last 40 seconds, at most 64 short playback clips, 128 references and 384 reference fragments; saturation/incomplete coverage blocks attribution rather than silently implying no competing attack. Excerpts without media retain their existing evidence-only behavior.

Verification commands use `OVERDEX_ENDING_AUDIO_REVIEW` pointing to the retained study directory. `ReplayEndingAudioRecoveryTest` checks the real ZIP through the production loader/model, immutable serialization and ZIP hash, reverse-order observations, unchanged Fast Move markers, one Seed Bomb and its landing/faint association. Permanent synthetic tests cover waveform grouping, missing/corrupt data, duplicate clips, ambiguous references, partial cries, unknown/pending attacks, shields, mirrors, survival, capture-bound sound reclassification and missing coverage. The opt-in Android test exercises the same ZIP against packaged assets without editing stored matches.

Final verification: the full unit run discovered **3,046 tests**, with **zero failures/errors and seven skipped** (3,039 executed). Debug and Android-test APK builds passed. The API 34 Overdex emulator ran the original ZIP against packaged references and returned **OK (1 test)**: one Seed Bomb, one Dragonair partial cry, the correct final association, and the Seed Bomb action at full impact when Dragonair faints. Recovery itself took 8,849 ms in that emulator run; this is not a Moondrop timing measurement. The full-source ZIP hash and serialized original Articles remained unchanged. Generated reports, APK, original fixture and backup material remain on the 1 TB backup drive under the review directory.

The Moondrop's 25 GB retained-artifact library made a new complete phone backup disproportionate for the optional instrumented test. The partial full transfer was stopped and labeled `.partial`, not used as a verified backup. A separate verified durable-data backup contains all 51 saved archive files, 350 record files, 14 calibration-profile files, databases, preferences and other non-artifact files. Instrumentation ran only on the dedicated emulator. The routine phone update uses `install -r`; no app data or retained artifacts are cleared. Phone Replay review remains a separate acceptance check.
