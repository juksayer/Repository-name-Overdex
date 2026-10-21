# Stat-change audio and Match Replay — 2026-10-12

Implemented on `odx-recovery` in `/home/sean/AndroidStudioProjects/Overdex`.

Follow-up: [the two real Galvantula/Lunge recordings](2026-10-12-lunge-review.md) exposed a nickname attribution fault and no accepted stat sounds. That review adds bounded charged-move audio probes, including rejected results; it supersedes the positive-only retention behavior described below.

## Behavior and evidence

- Bundle the four original attack/defense buff/debuff WAVs from the supplied Pokémon GO asset collection. `app/src/main/assets/stat_change_sounds/manifest.json` records their SHA-256 hashes and source paths; originals remain untouched. Combined WAV size: 79,536 bytes.
- Consume transient PCM from Droidball's existing recorder. No additional microphone, permission, or whole-match audio file. The rolling mono window is bounded to 1.5 seconds and scanned every 200 ms. Sequence gaps reset it.
- Match temporal spectral features against the four references. Minimum similarity 0.91, separation from runner-up 0.055. These are initial thresholds tested on fixtures, not calibrated probabilities or measured live-battle accuracy.
- Only matched windows become durable WAV evidence (at most 24,044 bytes each). `StatChangeSoundMeasured` preserves the reference hash, both similarity scores, and the original cue time. Stat windows are excluded from the cry candidate reader.
- A separate inference joins the sound to a compatible side-attributed charged move and the bundled move effect. A possible move effect alone never proves it occurred. Nominal stage count is reference metadata, not a capped running stage total.
- Missing or competing attribution remains explicit. Later contradictory evidence can supersede an earlier side assignment. Raw audio and testimony remain independently inspectable and cited by compact archives.
- Replay buffers the full record, displays `ATK ↑/↓` or `DEF ↑/↓` over the affected combatant for two seconds, and derives motion/fading from its cursor. Up is green; down is amber. A replacement Pokémon does not inherit the outgoing animation. Unassigned cues appear in the LCD as `SIDE UNCONFIRMED`.

## Verification

- Full unit suite: **584 passed**, no failures/errors/skips. Includes quiet and noisy resampled references, negative signals, window gaps, target attribution, delayed OCR, ambiguity revisions, compact serialization, rewind, replacement, and mirror-side checks.
- Debug APK built; SHA-256: `d2aaa994a61044823b2a19d2498a7dd1148ab1e3d417e8cc04d6472b65f9826d`.
- Installed with `adb install -r` on Moondrop `MDPH00124112500414`, while MediaProjection was inactive. Started Overdex after testing. All 11 saved calibration/profile/template/HUD files matched their pre-install hashes.
- Physical injected-PCM test: four cues produced four bounded WAVs, four sound measurements, and four correctly attributed replay indicators. Delayed Rock Tomb evidence retained the sound's timestamp (within the 16 ms analysis hop).
- Physical matcher benchmark: median **15.85 ms**, maximum **16.04 ms** across 12 scans; 200 ms scan interval.
- Physical Compose check: all four labels render with local player/opponent sprites; screenshot inspected. Two presentation windows can overlap; the test selects the matching stat/direction rather than incorrectly expecting only one active label.
- Local verification outputs: `/tmp/overdex-stat-cues/`, including build/test logs, the rendered pose sheet, and calibration hashes/backup.

## Remaining acceptance boundary

The phone tests injected the supplied samples; they did not record a new Pokémon GO battle through AudioRecord. Real playback/microphone mixtures, music, simultaneous effects, and current game timing still need a fresh match with an observed stat-changing charged move. Existing archives without these new sound measurements are not automatically re-analyzed.
