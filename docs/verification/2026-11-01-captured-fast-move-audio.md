# Captured Fast Move audio: November 1, 2026

Two paired private battles provide usable recognition examples for Golduck's
Confusion, Camerupt's Rock Smash and Mantine's Bubble. The user confirmed those
moves independently of the archived inference. Moondrop music was 0%, Pixel
music 20%, and SFX 100% on both. Both original MP4s contain H.264 video and
Android metadata/data tracks, **no audio stream**. Audio comes from Overdex's
verified playback WAV artifacts.

## Source integrity and review

Copied both new compact archives from each phone without deleting or modifying
the source. All four ZIP structures and **1,182 declared artifact hashes** were
verified. A final on-device SHA-256 comparison still matched all four PC copies.

| Device / battle | Match ID | Articles | Artifacts |
| --- | --- | ---: | ---: |
| Pixel 1 | e4d79fd5-aec3-4c35-a852-db1f246653cf | 1,778 | 169 |
| Pixel 2 | 74448a41-194d-4314-acc4-829ade1965d9 | 3,082 | 269 |
| Moondrop 1 | c94670ec-aac6-4d49-994a-361598686b3b | 3,604 | 399 |
| Moondrop 2 | 6b9c1ef8-ce81-4bf1-b237-4a8aabfa48a8 | 3,693 | 345 |

The MP4s are preserved under the user's Desktop ` overdex related` folder,
including its leading space. Corresponding `(copy)` files are byte-identical.
Video sources, their SHA-256 hashes, compact packages, raw PCM occurrences,
review sheets, import plans and exact JVM diagnostics remain private in
`/home/sean/overdex-device-backups/2026-11-01-audio-library`.

The recordings show Moondrop's Golduck/Camerupt/Mantine facing Pixel's
Swampert/Quagsire/Obstagoon. Candidate visual neighborhoods in the second video
at 24–26, 124–126 and 144–146 seconds show the expected player effects, defender
HP loss, full player HP, and no observed opponent attack. Stable battle context,
user-confirmed equipment and independent repeated audio support the labels;
a legal move or a live guess alone is not the label source.

Extracted **181 WAV occurrences / 179 distinct source hashes**. No PCM sample is
digitally clipped. The two all-zero Moondrop countdown captures were excluded.
Saved PCM source `perceivedAt` is the beginning of its 1.2-second window; integer
sample offsets define the cuts. Initial OCR delivery is not a frame-perfect
video clock, and no wall-time guess rewrites archived timestamps.

Mantine was sampled at 5 fps over the late Moondrop phase and a Pixel interval.
This did not establish a sustained stretch with repeated HP loss and entirely
absent attack animation. Active bursts contain bubbles/posture changes, while
some quiet body intervals also hold HP steady. A brief animation failure remains
possible. The report is preserved without treating absent body motion as proof
of no attack or disqualifying the audio.

## Admission and changes

Three exact PCM-frame excerpts are bundled for recognition with source/derived
hashes and sample provenance. Confusion and Bubble are bounded motifs whose tails
can exceed the captured window. Rock Smash has a near-isolated envelope and
quiet tail. All three are marked **recognition_excerpt / replayEligible:false**.
Replay loading now honors the exclusion, preserves legacy eligibility, and
continues through eligible variants when an asset cannot be opened.

The importer validates archive/Match/Article/artifact identity, PCM16 format,
source duration, bounds, and exact output frames before writing to a new private
folder. It never normalizes, denoises, resamples, stitches, loops or automatically
admits a candidate. Reference/holdout overlap or identical PCM is refused.

Three other-battle samples and three additional Pixel/music samples separate
correctly within their active legal pools; two quiet samples remain unconfirmed.
None of the six positive queries comes from its reference's recorded Match or
reuses reference PCM. The unchanged strict thresholds are retained. Rock Smash's
full-catalogue diagnostic margin against Sucker Punch fails; Sucker Punch is
outside the relevant active legal pool. No full-catalogue identity claim is made.

A diagnostic over all 178 original stream windows finds zero globally accepted
candidates with the old catalogue; adding the new excerpts yields successful
matches in the intended contexts. Those retained windows are selected by the
old capture policy and are not a labelled denominator for live accuracy. The
expanded reference audit has 75 entries, zero wrong accepted reference labels,
and passes all 11 older recorded fixtures. Full-catalogue diagnostic timing is
not the live active-pool cost.

The local catalogue audit now reports **73/96 names covered for recognition**,
23 exact-name gaps, no broken hashes or unresolved move IDs. Five ordinary source
gaps remain: Fire Fang, Mud Slap, Poison Sting, Steel Wing and Zen Headbutt.
Hidden Power type aliases, Transform and the Struggle category anomaly remain
separate review items. See the [current capture notes](../audio/2026-11-01-captured-references/README.md).

## Validation and installation

- Python audio/tool suite: **65 tests passed**, including 25 new importer tests.
- Focused Kotlin matcher/replay checks passed.
- Full Kotlin/JVM unit suite: **1,618 tests across 241 suites**, no failures,
  errors or skips. Debug and debug-test APK builds passed.
- Disposable API 34 emulator: **OK (3 tests)**. The new native test accepts all
  six held-out effects, rejects both quiet windows, verifies query hashes and
  checks legal-pool outputs using the app's actual reference loader. Existing
  recorded-mixture and custody-to-Timeline tests also pass.
- Emulator legal-pool comparison median 49 ms / maximum 92 ms across the eight
  selected queries; cold catalogue preparation 2,470 ms. These are emulator
  measurements, not phone benchmarks or live species/move resolution latency.
- Updated Pixel 4 `99261FFAZ0017J` and Moondrop `MDPH00124112500414` with no
  Droidball service running. Both launched successfully and their installed APK
  hashes matched the tested artifact. Directory LCD restored the latest matches.
  Neither installed process logged a fatal exception.
- Readable critical-state backups preceded installation. Pixel's 14 preference/
  profile files stayed byte-identical; Moondrop's 29 non-refreshing files stayed
  identical, including calibration/team settings. Firebase renewed token and
  heartbeat metadata on Moondrop; stable account fields were unchanged and no
  credentials/cache were restored. Species and owned-card rows remained identical
  on both phones (1,026 species each; 0 owned cards on Pixel, 29 on Moondrop).
  All four original test archives also remained hash-identical.

Tested/installed APK SHA-256:
`7f88cc38be086eb2efd5abb4ece3a519e1256bb397cd7e76058be47dc9ca5ec0`.

No new live battle was played after this installation. Future live early-move
identification, overlapping attackers, other music levels/devices, and timing
through animation stutters remain acceptance checks. Original match Articles
were not replaced with offline conclusions. The new references supply future
matching evidence; they do not retroactively change the old Bullet Seed guesses.
