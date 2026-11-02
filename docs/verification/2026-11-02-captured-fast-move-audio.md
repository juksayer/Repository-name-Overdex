# Captured Fast Move audio: November 2, 2026

## Sources and integrity

The user confirmed Gastrodon / Mud Slap, Drapion / Poison Sting, and Skarmory /
Steel Wing, music zero and SFX full on both phones, and the opposing team idle.
The second battle used continuous attacks. Retained source material privately
under `/home/sean/overdex-device-backups/2026-11-02-audio-library` without
modifying/deleting anything on the phones or the Desktop videos.

| Archive | Match ID | Articles | Artifacts |
| --- | --- | ---: | ---: |
| Pixel battle 1 | e9248848-fa32-40f7-8333-2554ca6e08b7 | 5,811 | 510 |
| Moondrop battle 1 | ac4655c2-efff-47dd-ac00-d6efa4ff7a7b | 5,265 | 444 |
| Pixel battle 2 | f590dec3-62bf-4a92-b540-291831d06d4e | 2,035 | 251 |

Also preserved two small Moondrop pre-battle records with no artifacts. All five
ZIPs and **1,205 declared artifact hashes** verified, and on-device archive
hashes remained identical after installation. Extracted 158 WAV occurrences
with original source clocks, channels, rates and hashes. Original PCM stays
unchanged; AudioCaptured perceivedAt is the beginning of its retained window.

Both MP4s have H.264 video plus two metadata tracks, **no audio stream**:
`Screen_recording_20261102_112012.mp4` (223.36 seconds) and
`Screen_recording_20261102_112438.mp4` (117.50 seconds). Both show the Moondrop
attacker against idle Swampert / Quagsire / Obstagoon. First video has all three
requested attackers; the second goes directly Gastrodon → Skarmory, with Body
Slam and Brave Bird QTEs excluded from candidate visual contexts. The missing
second Moondrop archive does not prevent other-battle Mud Slap / Steel Wing
checks, but there is no other-battle Poison Sting query.

## Changes and selected outcomes

Three source-verified exact PCM-frame excerpts are added, all recognition-only
and excluded from replay effects. They retain rate/channels/amplitude, source
archive / Match / Article / artifact hashes and exact integer trim bounds.
No resampling, denoising, subtraction, normalization, synthesis or looping.
All three reference excerpts contain zero digitally clipped PCM samples.

The eight held-out/negative queries exercise the actual unchanged matcher:

- Two Steel Wing queries from the second battle separate correctly within the
  complete active legal pool. Wing Attack is excluded before fitting because
  the local active-species pools cannot use it.
- Two Poison Sting attacks from Pixel battle one separate correctly. These are
  different physical attacks 14.6 and 37.8 seconds after the Moondrop reference,
  not the second phone's simultaneous capture of the training occurrence.
- Two second-battle Mud Slap queries remain ambiguous against Mud Shot. The
  baseline matcher without the new reference incorrectly accepts Mud Shot on
  those offline windows. Adding the genuine alternative withholds the answer.
  This does not prove old live inference confirmed Mud Shot; missing typed
  Hidden Power references and side/context gates remain separate limitations.
- Silent countdown and low-level quiet comparisons remain unconfirmed.

The source label and expected acoustic acceptance are stored independently.
An ambiguous, human-labelled recording is not forced to become an audio-only
identity. Similarity is not probability, and a close 416 ms sequence does not
prove the entire sound effect is identical. See [capture notes and remaining
gaps](../audio/2026-11-02-captured-references/README.md).

No archived raw testimony or existing move pools were rewritten. The live
matcher uses active legal pools; the full-catalogue audit remains diagnostic.
The library now has 78 files / 77 names, with 76/96 local species-pool names
covered for recognition, no broken hashes or unresolved catalogue move IDs.
Fire Fang and Zen Headbutt remain the two ordinary source gaps; typed Hidden
Power, Transform and Struggle remain separate inventory issues.

## Validation and installation

- Full Kotlin/JVM unit suite: **1,620 tests / 242 suites**, zero failures,
  errors or skips. Debug and debug-test APK builds passed.
- Full-catalogue diagnostic: 78 references, 68 correct accepted reference labels,
  10 withheld, **zero wrong accepted labels**; all 11 older recorded fixtures pass.
  This self/reference audit is not a live accuracy denominator.
- API 34 emulator: **OK (4 tests)** for the new eight-query native loader check,
  the prior captured-reference check, and the two older audio/custody tests.
  Separately, selected-row navigation completed **OK (7 tests)**; see
  [scrolling verification](2026-11-02-selection-scrolling.md).
- Installed on Pixel 4 `99261FFAZ0017J` and Moondrop `MDPH00124112500414` after
  verifying neither had an active service. Both launched successfully and their
  installed APK hashes matched the tested artifact. No fatal exception logged
  for either newly installed running process.
- Readable critical-state backups preceded installation. Current Team,
  calibration and HUD layout settings stayed byte-identical (6 critical files
  on Pixel, 17 on Moondrop). Logical Pokédex/owned-card rows remained identical:
  1,026 species on each, 0 owned cards on Pixel and 29 on Moondrop. Pixel's
  Firebase heartbeat and Moondrop's Firestore listener/document cache refreshed;
  no old credentials or remote cache were restored. The five source archives
  stayed hash-identical.
- Moondrop physical selection check successfully reopened the last equipped
  Gastrodon Fast Move, navigated all 17 rows upward/downward, and canceled
  without changing the saved team. Pixel launch checked; no phone
  instrumentation or live test battle was run.

Tested and installed APK SHA-256:
`62e4cf468e1cba0b27f20f6fcef7e16ac8708221a1c55b5f1569365d52944fd2`.

Future live recognition speed, general precision/recall, overlapping attackers,
and all animation-stutter conditions remain acceptance checks. Poison Sting
still needs a different-battle check before claiming that broader scope.
