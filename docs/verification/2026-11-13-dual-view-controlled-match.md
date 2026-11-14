# Controlled dual-device match review — 2026-11-13

## Finding

The final Shadow Ball KO is visible from both phones, but neither archive contains its faint testimony. The Pixel's new complete-bar witness ran (88 observations), without publishing a sustained terminal-color observation. A concrete search-boundary defect is visible in the original archived impact crop: its bottom edge cuts through the orange bar, excluding the lower outline. The ordinary opponent HP territory does not extend farther down, so its union with the charged-impact territory cannot repair this clipping.

The user authorized moving Draggy Boxes during this review. Only the Pixel's charged-impact box was enlarged downward. Its previous configuration is preserved. No production code, recognition thresholds, phone builds, or archive entries were changed.

## Sources and preservation

- Pixel video: `/home/sean/Desktop/Screen_recording_20261113_183448.mp4` (816×1712, approximately 170.79 s; no audio stream).
- Moondrop video: `/home/sean/Videos/Overdex/Moondrop-2026-11-13_18-34-50-ivjKu8.mp4` (1072×2400, approximately 172.07 s; AAC audio present).
- User protocol: opposite sides of the same match, only one Pokémon attacking at a time. This is a supplied experimental constraint, not an independently measured guarantee about every animation.
- Review directory: `/mnt/overdex-backup/archives/overdex-device-backups/2026-11-13-dual-view-1834/`.
- Copied Pixel archive: `pixel-2026-11-13_18-34-41_f9fa49c6-033e-4932-ae5e-38167d662159.odxmatch.zip` (1,839 articles).
- Copied Moondrop archive: `moondrop-2026-11-13_18-34-43_47519620-3434-4420-a4b7-51d89932c303.odxmatch.zip` (3,143 articles).

`audit-summary.json` records archive hashes, payload counts, faint articles, and crop integrity results. All 175 Pixel and 252 Moondrop crop references passed SHA-256 and byte-count verification. The source videos and archives remain unchanged. Extracted frames and diagnostic outputs are on the backup drive.

Moondrop audio decoded successfully and has nonzero signal (RMS approximately 0.0997). This confirms a non-silent audio track, not a completed perceptual identification of game sounds or cry timing.

## Build boundary

- Pixel installed APK SHA-256: `9752e619f8ad1a8da7a8af535838933cbc50059dd8b56b6bea10b15dcf56335a` — complete-bar implementation under trial.
- Moondrop installed APK SHA-256: `e0a7e539a421de726b4ec225ec31394fbfcbe82770b5d911f9cf9f528e30233c` — different, older build.

This is paired event evidence, not a controlled performance comparison between identical implementations.

## Observed battle and archive coverage

Video times below are approximate Pixel recording positions, not Replay times. The sampled GO frame is at 42.016 s in Pixel and 38.032 s in Moondrop. Their common final Shadow Ball impact is visible near Pixel 152 s and Moondrop 148 s. This supports an approximately four-second recording offset; no exact frame synchronization is claimed.

| Video event | Pixel archive faint testimony | Moondrop archive faint testimony |
|---|---|---|
| Moondrop Turtonator leaves after the finishing hit, around 79–80 s | Absent | Present, player Turtonator |
| Pixel Camerupt faints following Rock Tomb, around 102–105 s | Present, player Camerupt | Present, opponent Camerupt |
| Moondrop Camerupt leaves after a fast hit, around 134–135 s | Absent | Absent |
| Moondrop Gourgeist faints following Pixel Gourgeist's Shadow Ball, around 152–155 s | Absent | Absent |

Pixel's earlier Turtonator-to-Camerupt change is a voluntary switch and must remain distinct from a faint. Both archives contain the correct final result: Pixel win, Moondrop loss. Correct results do not establish that finishing hits or faints were recorded correctly.

## Complete-bar observations and final impact

Pixel has 86 `LIVE_COLOR` and two `UNRESOLVED_COLOR` complete-bar observations. The latter have zero live-color columns and 8 or 13 damage-color columns, respectively; neither is an accepted zero-HP measurement or sustained terminal-color conclusion. They occur near the two opponent departures preceding the final KO and are useful regression candidates.

Archive-relative times use the recorded Match Start source timestamp, separately for each phone. Pixel final-event evidence:

- Last accepted HP measurement: +100.355 s.
- Final charge announcement: +108.445 s; raw OCR reads “Gourgeist used Shadow Bali.”
- Charged-impact window 3 first scan: +109.095 s; no `FIRST_ACCEPTED_SAMPLE` for that window.
- Saved impact crop with orange remaining HP: +109.970 s.
- Localized Gourgeist cry: +110.376 s.
- Subsequent impact crops show the smaller sprite, particles, and disappearance.

This localizes a failure before accepted final-impact HP testimony. It does not demonstrate that Replay independently dropped a correctly accepted final HP sample. Announcement, cry, and disappearance remain independent evidence, not substitutes for an invented empty outline.

The charge-preparation diagnostic also shows a 4.383-second recognition delay for its first final-QTE preparation cue, and 9.314 seconds for another cue supporting that window. Timing is a separate contributing risk. The saved orange frame proves that the image capture path did see terminal-color pixels; it does not prove every live scan received precisely that frame.

## Search boundary and calibration change

Pixel captured source size: 672×1419.

- Charged-impact rectangle: `(228,439)–(447,887)`.
- Ordinary opponent-HP rectangle: `(325,341)–(653,792)`.
- Reconstructed live shared territory: `(228,341)–(653,887)`.

`LiveActiveHpBarWitnesses.checkChargedImpactFrame()` uses `ChargedImpactHpBarMeasurer.sharedTerritory()` with these calibrated territories. The function takes their bounding union when they overlap. It therefore widens this search but does not extend it below y=887. In `pixel-final-crop-109.970.png`, the top rail and orange fill are present at the crop bottom while the lower rail is clipped. Complete geometry cannot be established from this territory on this frame.

The active phone profile was independently read and matches these archived coordinates: `Google Pixel 4 test2nov12`, ID `b9b7bcdc-f89b-45b5-a58e-5916d2a1f3f8`.

Authorized adjustment:

| Setting | Before | After |
|---|---:|---:|
| x | 0.3399999 | unchanged |
| y | 0.30999994 | unchanged |
| width | 0.32500017 | unchanged |
| height | 0.31500006 | 0.36000006 |
| bottom as fraction of frame | 0.625 | 0.670 |

The bottom moves approximately from y=887 to y=951 at captured resolution, or y=1425 to y=1528 on a 1080×2280 screen. The diagnostic illustration `pixel-final-calibration-boundary.png` marks the old and new normalized limits on the independent video frame; it is explanatory, not a replacement for the exact archived crop.

Before writing, Droidball had no running service. The old profile was backed up as `pixel-calibration-before.json`. The new profile was written atomically after stopping Overdex, read back byte-for-byte, and Overdex was relaunched. Only `battleRegions.charged_impact_hp.height` and the profile's save timestamp changed. `pixel-calibration-after.json` and `pixel-calibration-readback.json` preserve the written and verified configuration. Moondrop calibration was not changed.

## Validation boundary and next narrow test

Completed: paired visual inspection, archive/crop integrity checks, actual build identification, exact archived crop inspection, source-path inspection, calibration-only diff validation and device readback. No production code changed, so no new build or unit suite was run for this review.

Not yet demonstrated: that the expanded search accepts the entire final sequence in the production reader; that the terminal-color predicate reliably separates lethal from nonlethal hits; that the missing faint and finishing attack now reach Replay.

Next test should run the existing complete-bar reader over these saved sequences with the old and expanded territories. Include the nonlethal charged hit, the fast-hit KOs, the final Shadow Ball, and the voluntary switch. Preserve frame timestamps and whole-bar requirements; measure geometry acceptance separately from color classification and faint inference. The recordings provide enough evidence to begin this test without asking for another match. Do not loosen the complete-bar check merely to accept the clipped crop.
