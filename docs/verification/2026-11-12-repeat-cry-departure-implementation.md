# Repeat-cry departure implementation — device trial pending

User-authorized goal: a repeated cry from the active Pokémon creates a departure candidate; subsequent sequence and independent evidence determine faint status. No cry or candidate directly establishes a faint, attack, KO credit, energy change or result.

## Runtime path

`LiveLocalizedCryWitness`, registered alongside the shared PCM readers in `PokedexViewModel`, consumes `DroidballService.livePcm`. It does not open another recorder. `CryAudioWindow` retains four seconds of original interleaved PCM, with at most four analysis snapshots per second. Capture gaps and format changes reset the window. A single background worker searches the independently observed species pool, with at most 32 hash-verified reference entries. There is no 1,000-species fallback.

`CryWaveformMatcher` uses normalized waveform correlation and localizes complete reference waveforms. Pilot acceptance requires correlation >= 0.5 and a 0.1 margin over a different species aligned within 100 ms of the same onset. These are pilot acoustic thresholds, not confidence probabilities or faint thresholds. Distinct overlapping cries may both be accepted. The continuous recording caught a real regression when the first version treated all overlapping durations as competing identities: Turtonator's opening cry overlapped Golbat's, and was lost. The same-onset comparison recovers both. No timing window forces a final faint to have a replacement cry.

Accepted occurrences preserve the original PCM as a verified WAV, then reference its accepted AudioCaptured Article. The localized Article retains the source onset, reference hash, measured correlation and duration. `awaitRawArticlePublished` supplies the receipt without another article-flow subscription. Overlapping windows of one sound do not become additional cries.

`CryRecentWindows` keeps ten original-PCM windows spaced at least one second apart and no more than twelve seconds old by window end. When a new species becomes independently known, the worker checks current audio first, then performs one bounded catch-up through these retained windows. This is volatile bounded pre-roll, not whole-match audio retention. With 48 kHz stereo, the ten windows hold at most 7,680,000 sample bytes. One worker and a conflated current-window queue prevent unbounded analysis backlog; elapsed-work pacing leaves roughly two thirds of that worker's schedule for other work. Desktop timing is not proof of phone performance.

## Appearance evidence and scheduling

`CryDepartureEvidence` consumes side-specific visual species Articles and the entry-announcement species witness, excluding the decisive audio-species witness. Histories use capture time, not recognition completion. The first visual reading is an upper bound on appearance entry; the preceding last old-species reading supplies the lower bound. A cry between those readings can belong to the later observed appearance. This is an explicit uncertainty interval supported by both Article IDs, not a rewrite of either original timestamp. An old-species sighting after that cry excludes the new appearance as its owner. The repeated cry still requires independently established current-species context.

`RepeatCryDepartureRule` recomputes source order, keeps mirror sides unresolved, and never converts a candidate into a faint. A lead and the opponent's later same-species safe swap can have independent histories when the sightings distinguish them. More copies of the same sound cannot resolve a mirror.

The new `RepeatCryDepartureCandidateObserved` Article retains cry and appearance references. It requests `TeamResourceSnapshotGate.requestDepartureCheck` for the supported side, or both sides when unresolved. Existing independent count readings and the source-time team-loss resolver determine whether a Pokémon fainted. A candidate alone does not fade a sprite. The resource assignment expires after its existing three-second window; this feature does not relax that reader's freshness/confirmation rules.

The cry listening gate reuses battle lifecycle signals but deliberately ignores derived faint counts, preserving acquisition through the final departure. Results/manual stop close attention, and manual restart prevents stale work from crossing attempts. Both new payloads have archive DTOs, mapper support and archive viewer descriptions. Original archives remain unchanged.

## Verification evidence

Generated media and reports stay on the 1 TB drive:
`/mnt/overdex-backup/archives/overdex-device-backups/2026-11-12-cry-sequence-study/`.

- Eight selected recorded cry excerpts and two negative excerpts pass the Kotlin waveform checks, including ordinary entrance cries. Reference and excerpt hashes are checked.
- Continuous 1605 audio is fed through the real four-second buffer, all 18 bundled reference entries for the recorded species, acceptance policy and departure projection. It recovers eleven reviewed cries and produces four candidates: player Turtonator at 74.61475 s, opponent Golbat at 93.94275 s, Abomasnow at 107.89475 s and Swampert at 137.01475 s. Neither ordinary switch is a departure candidate.
- The continuous check runs with video-labeled appearances, the actual archived visual identity Articles, and simulated original identity delivery delays with the bounded catch-up path. All three produce the same four candidate species/sides. The source archive hash is verified; prior faint conclusions are not supplied as fresh observations.
- Archive/recording clock alignment uses independently retained PCM clips: ten alignments agree within approximately 30 ms, with one repetitive-sound alternative approximately ten seconds away. The median supplies the recording origin. Original source timestamps remain intact. See `archive-audio-alignment.json` and `kotlin-fixtures/archive-history-1605.json` for provenance and limits.
- The latest standalone continuous scan processed 449 windows at about 38 ms mean per search on this desktop; a 269 ms maximum occurred. This is diagnostic evidence only. Reports are `kotlin-fixtures/continuous-1605-kotlin-results.tsv`.
- Live-observer integration tests exercise the real Match/Custody path with injected PCM and references, source-time localization, original WAV retention, bounded count-reader requests, no invented faint/attack/result, and delayed recognition that requires revisiting entrance audio.
- Three additional boundary tests join the cry projection, real resource gate and shared faint resolver: an independent count decrease confirms a loss; an unchanged count plus ordinary switch does not; an ambiguous count loss remains unresolved despite the cry until independent survival identifies the owner. All three pass. Production code and the verified APK remain unchanged.
- Focused tests cover overlapping windows, mirrors, ordinary switches, late identities, contradictory/unknown history, bounded storage, restart, listening through derived faints, archive serialization and the old/new sighting uncertainty bounds.

The verified APK is also preserved as `final-verification/app-debug.apk` on the 1 TB drive (SHA-256 `64674c2d818c58c1f043df292c049986ecaf79c7675d0d8e0255475be43efe6e`).

Final full suite and debug build passed in 2m 53s: 2,954 tests, zero failures/errors, two skipped. Recorded fixture tests were enabled. Log: `/tmp/overdex-cry-final-verification.log`. The user subsequently connected the Pixel 4 instead (99261FFAZ0017J). No app service or MediaProjection session was active before replacement. The verified APK installed successfully with app data retained; the installed base APK SHA-256 matches the tested artifact. MainActivity launched successfully and the process remained alive, with no AndroidRuntime errors in the inspected recent log. No commit was made. A new Pixel battle is still required for live validation.

## Remaining acceptance boundary

Full suite/debug build and Pixel installation are complete. Inspect a real new Pixel battle for cry occurrences, candidates, independent count/faint outcomes and acquisition/processing latency. Acoustic reliability beyond these recordings, live mirror coverage, and phone performance remain unproven. The feature does not resolve a missing independent terminal observation by inventing a KO.
