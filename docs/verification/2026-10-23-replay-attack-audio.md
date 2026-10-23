# Replay attack audio — 2026-10-23

Replay now reconstructs named attacks using bundled local audio: 72 existing Fast Move clips and 183 charged-move clips from the user's Pokemon GO collection. Charged filenames were matched exactly after punctuation normalization against charged entries in the local move catalogue. These mappings are asset labels, not new recognition evidence.

Sounds follow the same scheduled Fast Move cycles and completed charged attacks used for animation. Unknown names and unavailable assets remain silent. QTE preparation alone does not trigger a charged attack sound. Original Articles and recorded audio remain unchanged.

The LCD SFX toggle defaults on and persists independently of event BLIPS. Playback uses media volume. Pause, seeking, disabling SFX, leaving Replay, and backgrounding stop attack streams. Only moves used in the current replay are loaded; up to eight streams can overlap. Playback rate follows the speed setting within SoundPool's 0.5–2x range; beyond that range, trigger timing still follows the replay but sound pitch/duration is clamped. Scrubbing does not reconstruct attack sounds.

Four supplied WAV files had zero RIFF and data lengths. Bundled copies have these lengths repaired without modifying sample bytes; the manifest retains original and repaired hashes. Original supplied files are unchanged.

Validation:
- 1,061 unit tests passed, no failures or errors.
- Added boundary-crossing, reverse/stationary cursor, skipped-frame, unknown-move, and incomplete-QTE audio tests.
- All 255 WAV clips and manifest hashes validated.
- Debug APK built; all 183 charged WAV assets are uncompressed and available through AssetManager.openFd.
- Audible balance, timing, and on-phone LCD layout still require device playback acceptance.
