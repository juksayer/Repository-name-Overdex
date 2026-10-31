# Move audio coverage and recording checklist — October 31, 2026

The main recording work is **eight ordinary Fast Moves**. Start with **Confusion** and **Rock Smash**, because their absent references blocked complete legal-pool comparisons in the latest match. Fire Fang has existing controlled recordings to review before requesting a repeat.

This inventories the **checked-in** `gamemaster.json` (catalogue timestamp `2026-06-18 01:53:01`), bundled audio manifests, and the supplied Pokémon Moves WAV directory. It is not a claim that this catalogue describes every move currently obtainable in Pokémon GO. Examples below are names/forms listed in that local catalogue; check the actual Pokémon's equipped move before recording. Do not spend TMs or assume a legacy move is available from these examples alone.

## Fast Move recording priorities

| Priority | Move | Listed cycle | Local catalogue examples | Next action |
| --- | --- | --- | --- | --- |
| 1 | Confusion | 2.0 s / 4 turns | Venonat, Butterfree, Golduck | Record isolated attacks |
| 2 | Rock Smash | 1.5 s / 3 turns | Camerupt, Poliwrath, Machop | Record isolated attacks |
| 3 | Bubble | 1.5 s / 3 turns | Squirtle, Poliwag, Poliwhirl | Record isolated attacks |
| 4 | Mud Slap | 1.5 s / 3 turns | Nidoqueen, Diglett, Dugtrio | Record isolated attacks |
| 5 | Poison Sting | 1.0 s / 2 turns | Weedle, Beedrill, Ekans | Record isolated attacks |
| 6 | Steel Wing | 1.0 s / 2 turns | Pidgeot, Fearow, Scyther | Record isolated attacks |
| 7 | Zen Headbutt | 1.5 s / 3 turns | Clefairy, Psyduck, Hypno | Record isolated attacks |
| 8 | Fire Fang | 1.0 s / 2 turns | Arcanine, Houndoom, Mawile | Review October 25 controlled audio first |

Cycle lengths come from reference `turns × 0.5 seconds`. A WAV's duration is **not** the Fast Move's battle cycle. Bubble is not a half-second move in this dataset.

The exact-name Fast Move gap is **26**, comprising the eight ordinary moves above and these 18 special/label cases:

- **16 Hidden Power type names:** Bug, Dark, Dragon, Electric, Fighting, Fire, Flying, Ghost, Grass, Ground, Ice, Poison, Psychic, Rock, Steel, Water. A generic Hidden Power WAV is already bundled. These need a review of shared audio/aliases and actual type behavior, not an automatic request for 16 new recordings. The current matcher uses exact normalized move names, so the generic label does not cover the typed names. Sound alone must not be claimed to distinguish a type that the sound does not distinguish.
- **Transform:** a special zero-damage, zero-energy action assigned to Ditto in the catalogue. Review how the action is represented and whether useful audio exists; it is not an ordinary repeated damaging Fast Move recording task.
- **Struggle:** appears in Magearna's `fastMoves` in this catalogue, but the move itself has energy cost 100 and energy gain 0. Its sound already exists in the supplied collection and the charged replay library. Review this catalogue/category inconsistency rather than recording or treating it as an ordinary Fast Move.

The generic Hidden Power entry exists outside the species Fast Move pools. There are **96 distinct names across 98 referenced Fast Move IDs**; **70 names are exactly covered**. The manifest has **72 files for 71 distinct names**, including two Charge Beam variants and generic Hidden Power. Counting WAVs as distinct covered roster moves would overstate coverage.

## First two controlled recordings

Use the two-account private battle setup. Record one move per test, beginning with Confusion and then Rock Smash.

1. Confirm the attacker's species/form and the actual equipped Fast Move. Select the matching Current Team in Overdex so the stored configuration describes the team entering this test. Include CP/form/shadow/shiny information if relevant.
2. Set Pokémon GO **Music to 0** and **Sound Effects to full** on the recording phone. Use a comfortable audible media level. Make and play a brief test recording first to confirm that the screen recorder includes device/game audio rather than only the microphone; an earlier video contained no audio track. Keep notification sounds away from the sample.
3. Run Overdex on both phones. Make a screen recording **from the attacker's perspective**, starting before VS/countdown. Keep the other account entirely idle: no attacks, charged moves, shields, or voluntary switches.
4. Leave about **five seconds idle**. Perform **six isolated Fast Moves**: tap once, let the move finish, then wait about **three seconds** before the next tap. If the opponent survives, finish with about **ten seconds of continuous attacks**, then **five seconds idle**. The isolated effects supply cleaner candidate references; the continuous portion checks repetition and overlap. Avoid charged moves in the first sample; they introduce QTE music/effects and phase transitions.
5. Record any unavoidable faint, switch, connection problem, or animation stutter in the notes. Continue recording a few seconds afterward. Preserve the original MP4 and both `.odxmatch` archives, including their audio.
6. Before asking for more takes, review whether this sample contains clear repeated effects and independent idle segments. A sample becomes a candidate reference only after comparison against another recording and negative/background examples; a filename or a high similarity score alone is insufficient.

One successful recording does not establish device, music, overlap, or both-side robustness. Those can be separate follow-up comparisons after a clean reference is established.

## Fire Fang evidence already available

[The October 25 paired review](../../verification/2026-10-25-fire-fang-paired.md) documents a user-confirmed test in which only Mawile attacked and the other team stayed idle. Both private archives retain audio. Review that material first. The later repeat MP4 has **no audio track**; do not spend time trying to extract missing audio from that video. Raw archives and original assets remain unchanged.

## Charged Move coverage, separately

The local species pools contain **224 distinct Charged Move names across 225 IDs**. **177 names are exactly covered**, leaving **47 exact-name gaps**. The charged manifest has 183 files/names; six names exist outside the current species charged pools. Charged sounds currently serve reconstructed replay playback, not a live charged-move audio identification witness.

The [full missing Charged Move CSV](missing-charged-move-audio.csv) includes every gap, examples, and source status. Of those 47:

- **35 have no matching named sound in the supplied collection:** Aura Wheel, Beak Blast, Behemoth Blade, Bleakwind Storm, Brutal Swing, Chilling Water, Clanging Scales, Crush Grip, Dragon Energy, Drum Beating, Flower Trick, Glaive Rush, Liquidation, Lunge, Meteor Beam, Mind Blown, Moongeist Beam, Nature's Madness, Obstruct, Plasma Fists, Psychic Fangs, Pyro Ball, Rage Fist, Sandsear Storm, Scorching Sands, Shadow Bone, Sparkling Aria, Spirit Shackle, Sunsteel Strike, Thunder Cage, Torch Song, Trailblaze, Triple Axel, Upper Hand, Wildbolt Storm.
- **10 are family/type labels:** Techno Blast (Burn, Chill, Douse, Normal, Shock) and Weather Ball (Fire, Ice, Normal, Rock, Water). Generic Techno Blast and Weather Ball assets exist in the supplied collection. Validate their shared or differing audio before introducing aliases or requesting all ten recordings.
- **Skull Bash** already has supplied asset `130-1_skull_bash.wav`; it needs review/import rather than a new recording.
- **Vise Grip** has supplied `011-0_vice_grip.wav`; review the spelling alias rather than treating it as an absent sound.

Some catalogue rows can represent unreleased/speculative entries or unusual forms. The CSV reports `any_released_example` and `all_move_ids_unlisted` so these can be separated from practical capture requests. Aura Wheel also has more than one move ID/type behind one displayed name; one name does not prove one sonic variant.

## Complete files and reproduction

- [Missing Fast Move names and categories](missing-fast-move-audio.csv)
- [All Fast Move coverage](fast-move-audio-coverage.csv)
- [Missing Charged Move names and categories](missing-charged-move-audio.csv)
- [All Charged Move coverage](charged-move-audio-coverage.csv)
- [Machine-readable report with source hashes and catalogue anomalies](move-audio-coverage.json)

Reproduce from the repository root:

```bash
python3 tools/audit_move_audio_coverage.py \
  --source-assets '/home/sean/Downloads/pogo_assets-master/Sounds/Pokemon Moves' \
  --output-dir docs/audio/2026-10-31-coverage
python3 -m unittest discover -s tools/tests -p test_move_audio_coverage.py
```

The audit mirrors runtime naming: uppercase/alphanumeric normalization, without silently removing form/type suffixes. It resolves IDs used by every species/form's `fastMoves`/`chargedMoves` through `gamemaster.json`, the data loaded by `GameMasterLoader` and used for Room move imports and regional-form repository reads. The supplied collection is inspected only for availability/alias candidates. Bundled files are checked against manifest SHA-256 hashes. Six fixture tests cover exact naming versus type families, duplicate IDs/reference variants, corrupt assets, source variants/spelling aliases, special actions/category anomalies, and unavailable source inventories.

No app source, runtime audio, original recording, or supplied asset was changed by this inventory.
