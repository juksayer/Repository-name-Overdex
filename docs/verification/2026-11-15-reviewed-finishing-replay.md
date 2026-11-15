# Video-reviewed finishing events in the Moondrop Replay

The trainer described two Incinerate KOs after leaving Sealeo and Vaporeon on very low HP. The original match archive `5181caa0-caa6-4535-8b11-26ec3a78cfaa` and the separate `Moondrop-2026-11-15_12-12-38-mtIRSv.mp4` recording were reviewed together, without modifying either. The video contains the player's fire animation, the final red-to-orange-looking HP transitions, and the two departures. Independently retained audio matches overlapping Incinerate and Water Gun. Sealeo also has an archived cry and a later count-based faint. Vaporeon's edge pixel and absent archived faint remain unresolved by the automatic readers; the user described the outcome and the video shows its departure before the win.

Four **reviewed correction entries**, not automatic witness testimony, are stored separately from the archive. Each faint explicitly links its finishing move, identifies its own recipient, and is attributed to `Recording review`. Times below are approximate Match Start offsets based on a video/GO alignment near 30.2 seconds; retained notes name the video observations and their uncertainty.

| Entry | Side and species | Approximate match time | Review basis |
| --- | --- | --- | --- |
| Incinerate hit | Player Gourgeist | 85.63 s | Sealeo's final orange transition near video 115.867 s; matched fire audio and video |
| Faint, linked to that hit | Opponent Sealeo | 86.42 s | Shrink and particles around video 116.6–117.0 s; separate cry and count loss |
| Incinerate hit | Player Gourgeist | 137.30 s | Vaporeon's orange-looking transition near video 167.518 s; matched fire audio and video |
| Faint, linked to that hit | Opponent Vaporeon | 139.40 s | Shrink and particles around video 169.6–170.1 s; trainer-described KO |

The portable correction file and generation script are retained under `/mnt/overdex-backup/archives/overdex-device-backups/2026-11-15-moondrop-1212-review/`. It has the original match ID and source provenance in every entry. It does not claim an observed zero HP frame, an exact damage tick, or an automatically recovered Vaporeon cry. Reopening the same saved match displays the correction entries; they do not apply to a different match. The original archive and video hashes are checked before and after.

Replay previously required an exact spelling match between a correction's actor name and the archived combatant. The saved team uses `Gourgeist (Average)`, but the reviewed correction has only `Gourgeist`: a bare name now matches the corresponding archived form without asserting its size. Two explicitly different forms remain distinct. A named move absent from that species' catalogue can use a matching type from other match references only when the type is unambiguous; this supplies an icon, not a new move or legality claim.

The original saved-match test applies the reviewed correction file to the original Replay projection and checks each player Incinerate lands with its Fire type, the correct opponent faints with Incinerate credit, reverse seeking clears the later faint, and another match is not altered. Separate projection and attribution controls cover nonlethal moves, living switches, duplicate hits, wrong actors, differing forms and unmatched causes. The automatic mixed-audio and HP witness behavior is unchanged; this review does not claim general automatic final-KO recognition.

The final debug unit suite passed: 3,099 tests, 11 skipped. The debug build was installed and launched on Moondrop `MDPH00124112500414`. Four separately stored corrections and the portable source file were added to its app storage and individually read back and verified; the on-device original archive still has SHA-256 `927c311c86d9ee8d834007db3acb22730b37c62a0040995be40e7a6b4fbc28fe`. The saved-match projection was checked automatically; a visual device Replay check remains pending.
