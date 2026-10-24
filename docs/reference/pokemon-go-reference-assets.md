# Pokémon GO recognition asset inventory

Inspected October 21, 2026. Source supplied by the user:
`/home/sean/Downloads/pogo_assets-master/`.

This is a source audit and implementation guide. It does not claim that supplying
an image or sound has enabled a new live recognizer. The original files remain in
place; this audit adds no artwork, audio, or processing load to the APK. See the
[ordered battle backlog](../plans/battle-priorities.md) for implementation order.

## Verified contents

Counts are recursive file counts in the supplied directories. Sizes are MiB;
variants, forms, costumes, and duplicates are included in the file counts.

| Source directory | Files | MiB | Best use |
| --- | ---: | ---: | --- |
| `Images/Types` | 54 | 0.16 | Exact type-glyph references for active badges and a separately validated QTE classifier. |
| `Images/Pokemon` | 5,733 | 106.56 | Team Select/card artwork references with explicit form and appearance labels. |
| `Images/Pokemon - 256x256` | 6,005 | 167.86 | Existing Team Select descriptor source; broader coverage than the other Pokémon image directory. |
| `Images/Combat` | 73 | 7.39 | League/cup symbols, shield button, leader icons, and VS backgrounds. |
| `Sounds/Pokemon Moves` | 346 | 22.42 | Move-sound references and alternate recordings; inspect duplicates and non-PvP families before importing. |
| `Images/Pokedex` | 18 | 0.17 | Shiny/gender/height/weight markers, mostly useful when registration resumes. |
| `Images/NPCs` | 31 | 15.62 | Leader/opponent context, where a small calibrated portrait crop adds useful evidence. |
| `Images/Badges` | 344 | 15.23 | Achievement artwork; lower battle priority. The verified main-league references are in `Combat`. |
| `Images/Mega Portraits` | 166 | 21.24 | Mega appearance references, if a relevant identification gap is demonstrated. |
| `Images/Mega Portraits 512x512` | 126 | 17.59 | A second portrait collection; not identical coverage. |
| `Images/Type Backgrounds` | 19 | 0.67 | Detail-page backgrounds: 18 named types plus default. These are not Fast Move particle animations. |

The three supplied Rocket markers total 16,758 bytes:

| File under `Images/Rocket` | Dimensions | Intended reference use |
| --- | --- | --- |
| `shadow_icon.png` | 93 × 86 | Purple aura/marker appearance. Validate the actual location and scale in a captured card. |
| `ic_shadow.png` | 55 × 62 | Outlined shadow-symbol template. |
| `ic_purified.png` | 64 × 64 | Purified-symbol template and a useful alternative to reject when testing shadow recognition. |

## First reuse: identity and type

### Preserve forms already present in the sprite catalog

The current `team_select_sprite_catalog.tsv` contains **3,130 descriptors covering
963 numeric Pokédex IDs**. It already includes `26 / fALOLA` and `26 / fALOLA.s`.
The source files include `Addressable Assets/pm26.fALOLA.icon.png` and its shiny
variant. More artwork is not required to retain that existing form evidence.

The initial audit found two limitations to address together. The subsequent
[form-preservation implementation](../verification/2026-10-21-team-select-forms.md)
addresses these paths; the first Moondrop roster, move-list and slot-swap checks
passed, with broader device and live battle validation still pending:

1. [TeamSelectSpriteMatcher](../../app/src/main/java/com/example/overdex/battle/observation/TeamSelectSpriteMatcher.kt)
   returned a `variant`, but its acceptance margin compared against the next
   **different species ID**. That does not establish separation between regional
   forms of the same species.
2. [PersistedPlayerTeamRosterSpriteWitness](../../app/src/main/java/com/example/overdex/battle/observation/PersistedTeamSelectWitnesses.kt)
   resolved `getPokemonById(visualMatch.speciesId)` and published a roster slot
   containing the species name/ID without the matched variant. Preserve form
   evidence and uncertainty through this boundary before using it for legal
   movepools, types, or energy calculations.

The [catalog generator](../../tools/generate_team_select_sprite_catalog.py)
already derives small color/shape measurements from the 256-pixel Addressable
Assets directory. Reuse it and its provenance rather than packaging both large
image collections for recognition. Match card references to card screenshots;
battlefield poses and effects require separate evaluation.

Coverage is not complete merely because there are thousands of files: the two
image directories name 927 and 963 numeric Pokédex IDs respectively, with maximum
ID 1019. File counts include variants. The directory named `256x256` contains
5,909 images at that size and 96 at 512 × 512, so an importer must inspect actual
dimensions rather than trusting the folder name.

### Use exact type icons as candidate references

All 18 types have three 64 × 64 filename variants:

- `POKEMON_TYPE_<TYPE>.png`
- `POKEMON_TYPE_<TYPE>_BORDERED.png`
- `ico_<number>_<type>.png`

There are **36 distinct PNGs and 36 distinct decoded RGBA images**. The `ico_`
family duplicates the plain family byte-for-byte. Retain original names in source
provenance but avoid packaging duplicate templates.

The existing [active-type matcher](../../app/src/main/java/com/example/overdex/battle/observation/PokemonGoTypeIconMatcher.kt)
cuts templates from a composed reference sheet. Evaluate the supplied individual
icons against the same labeled crops, with consistent glyph scale, background
handling, and plain/bordered treatment. Require separation from competing types
and a rejection outcome for partial or obscured images. A reference self-match is
not a live accuracy measurement.

The [QTE sampling gate](../../app/src/main/java/com/example/overdex/battle/observation/QteTypeIconSampling.kt)
already collects bounded, calibrated samples; it does not classify them. Use
those real samples to check which supplied glyph variant matches QTE bubbles.
Known player moves may narrow likely types, but retain an unknown/other-type
outcome so an incorrect entered moveset cannot force a false match. The same type
shared by both charged moves does not distinguish their names.

These badge icons can help recognize a QTE type. They are not a library of the
moving Fire, Water, or Electric Fast Move effects. Those still need labeled real
captures, motion/side attribution, and comparisons with unrelated effects.

## Shadow and purified state

Compare marker shape and color in a known card location, at the observed scale.
Purple elsewhere on a Pokémon or battlefield is insufficient: artwork, attacks,
and backgrounds can share it. Preserve an ambiguous result when the marker is
clipped or absent from the crop; absence from a crop does not establish normal or
purified status.

The current [ShadowBonusRecognizer](../../app/src/main/java/com/example/overdex/data/observation/ShadowBonusRecognizer.kt)
reads a `+number` from a move row. It is not a matcher for these supplied icons.
Shadow-symbol recognition is useful to battle identity and damage ranges; broad
registration changes remain paused. Shiny/gender presentation can follow later.

## Sound inventory and battle context

The move directory has **346 WAV files, 314 distinct basenames, and 315 distinct
file hashes**. Comparing WAV format/data payloads also produces 315 distinct
payloads; that is exact deduplication, not a perceptual claim that every remaining
sound can be distinguished in a live mix. Some repeated basenames have different
contents, including Max variants, so never deduplicate by filename alone.

Every one of Overdex's **72 bundled Fast Move references** has a byte-identical
file in this collection. The manifest labels those references with **71 candidate
move names**. This asset submission does not expand that existing Fast Move
reference coverage by itself. The remaining files include charged and Max/Gmax
families, not a further 274 missing PvP Fast Moves.

Continue the [independent audio audit](../verification/2026-10-20-independent-fast-move-audio.md)
using actual paired recordings. A filename supplies a candidate label; clip
length does not supply PvP turn duration, and a sound match alone does not identify
the attacking side. Preserve reference hashes and compare new candidates against
all competing references, overlap, music, and deliberately idle periods.

The supplied `Sounds/Menu Sounds/se_go_PvP_vs.wav` is **1.197 seconds, mono,
22,050 Hz, 16-bit PCM**. No byte-identical file is bundled in the app's current
audio resources. The older [audio inventory](battle-audio-inventory-2026-10-15.json)
already lists a copy under Downloads. The current
[VS witness](../../app/src/main/java/com/example/overdex/battle/observation/PersistedVsScreenWitness.kt)
reads the central visual VS crop, not this sound. A separately tested audio cue
could corroborate the transition and wake entry readers; it does not establish
the exact GO/Match Start time or a Fast Move.

Main-league references are verified at `Images/Combat/pogo_great_league.png`,
`pogo_ultra_league.png`, and `pogo_master_league.png` (each 256 × 256). Shape/stripe
comparisons can supplement Team Select OCR. Cup artwork needs a mapped cup
identity and current restrictions before it can establish league eligibility.
Leader portraits likewise support context, not proof of a particular attack or
shield policy.

The [Raichu/result follow-up](../verification/2026-10-21-raichu-and-notification-controls.md)
also locates numbered league medals in `Images/Badges/Achievements`:
`Badge_52_{1,2,3}_01.png`, `Badge_53_{1,2,3}_01.png`, and
`Badge_54_{1,2,3}_01.png` contain one-, two-, and three-stripe league designs in
bronze, silver, and gold. `Badge_14_{1,2,3}_01.png` contains the glove medal.
These are separate from `CombatRank00`–`CombatRank03`. A result-summary capture
is still needed to validate their position and role as an OCR wake-up cue;
their presence elsewhere must not end a match. No image-result recognizer was
enabled in that follow-up.

## Validation and next work

- [x] Inspect image counts, dimensions, type-family coverage, duplicate pixel
  content, WAV structure, source hashes, and existing runtime consumers.
- [x] Confirm all bundled Fast Move source hashes exist in the supplied directory.
- [x] Record the form-loss boundary and distinct-species-only match margin.
- [x] Implement form evidence preservation and form-level ambiguity handling;
  see the [follow-up](../verification/2026-10-21-team-select-forms.md) for tests
  and the successful Moondrop roster/move-list and slot-swap/rescan checks.
  Battle retention checks remain (battle priority 1).
- [ ] Compare exact plain/bordered type templates on labeled real badge crops;
  report wrong, unresolved, and correct matches separately (priority 1).
- [ ] Complete the paired-match Fast Move audit before expanding audio references
  or adopting Fast Move visual-color conclusions (priority 2).
- [ ] Evaluate QTE and shadow templates on their own labeled captures, keeping
  their conclusions separate from species, move name, and actor assignment.
- [ ] Consider a VS sound witness if visual transition misses remain after the
  higher-priority identity/count work.

No runtime code or calibration changed in this inventory pass. No Android build
or phone install was needed. The audit verifies available reference material and
current code paths; new recognition behavior still needs implementation and real
recording validation.


## October 24 supplement: effect textures and animation atlases

A follow-up inspection found a relevant directory omitted from the October 21
inventory: `Images/Effects`. The earlier inventory and its validation boundaries
remain historical findings; this supplement adds source coverage, not a live
Fast Move recognizer.

The directory contains **228 PNG files totaling 7,263,268 bytes (6.93 MiB)**.
It contains particle/effect texture candidates in addition to decorative and
non-battle effects. Useful candidates include:

| File under `Images/Effects` | Verified dimensions | Potential reference use |
| --- | --- | --- |
| `snowflake.png` | 64 × 64 | Snowflake silhouette. |
| `tx_snowflake_far.png` | 128 × 128 | A second snowflake reference. |
| `ice_shard.png` | 128 × 128 | Ice-shard shape. |
| `electric_beam.png` | 128 × 128 | Electric-beam shape. |
| `arc_lightning_anim.png` | 512 × 512 | Visually inspected 4 × 4 atlas of blue lightning-arc stages. |
| `Fire_6x6.png` | 512 × 512 | Visually inspected 6 × 6 atlas of grayscale puff/fire stages. |
| `flame_ball.png` | 64 × 64 | Flame-ball shape. |
| `mud_splash.png` | 128 × 128 | Mud-splash shape. |
| `leaf.png` | 64 × 64 | Leaf silhouette. |
| `leaf_3x3.png` | 512 × 512 | Named atlas candidate; exact stage interpretation needs validation. |
| `fire2_6x3Seq.png` | 512 × 512 | Named sequence candidate; exact stage interpretation needs validation. |
| `WaterSeq3x4.png` | 256 × 256 | Named sequence candidate; exact stage interpretation needs validation. |
| `HitYelSeq.png` | 512 × 256 | Named impact sequence candidate; exact stage interpretation needs validation. |

Other filenames include `electric_bolt_additive.png`, `mud_drop.png`,
`bubble.png`, `infestation.png`, `psychic_fangs.png`, and
`Bubbles_all_2x3_0.png` through `Bubbles_all_2x3_5.png`. Their names are candidate
labels, not verified mappings to particular Fast Moves. The 228-file count is
not a count of unique moves or of confirmed battle-relevant templates.

### What the source does and does not establish

The inspected PNGs provide useful shape references and, for the visually
verified atlases, a set of effect stages. Selected PNG metadata contained only
ordinary DPI/color information. No inspected metadata maps these textures to
exact moves, runtime playback speed, particle lifespan, tint, trajectory,
attacking side, or screen placement. A grayscale Fire atlas and a blue lightning
atlas also show why source RGB values must not be assumed to equal the colors
rendered during a match.

A filename/extension inventory of the collection found no loose `.prefab`,
`.anim`, `.mat`, `.controller`, `.shader`, or `.asset` files. There are **264 raw
`.bundle` files** elsewhere, principally named for Pokémon and clothing, plus a
props bundle. Their contents were not unpacked in this audit, so additional
metadata inside those bundles remains unknown. This is not a claim that the
original game lacks effect definitions.

### Recognition use and next validation

- Use alpha silhouettes and verified atlas stages as candidates for matching
  effect shapes in real captured sequences, with scale, rotation, partial
  visibility, and background variation represented in the examples.
- Establish rendered color, motion, and side attribution from controlled live
  captures. A snowflake can support an Ice-effect hypothesis without proving
  Powder Snow or identifying who performed it.
- Keep effect-stage timing separate from PvP turn duration. Neither a PNG's
  dimensions nor its atlas frame count establishes playback rate or Fast Move
  duration.
- Test isolated attacks from each side, idle periods, camera motion, switches,
  and overlapping attacks before promoting a shape match to move evidence.
  Retain unresolved and competing explanations instead of forcing every effect
  into a move type.
- Preserve this source collection and its filenames. Derive only the small
  reference set justified by recorded-match evaluation for runtime use.

This supplement records source discovery only. It changes no runtime detector,
calibration, bundled asset, or recognition threshold.
