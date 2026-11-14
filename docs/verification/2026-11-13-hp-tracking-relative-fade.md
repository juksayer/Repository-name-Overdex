# Complete-bar tracking and relative fading — November 13, 2026

Status: offline experiment completed, not a production recognizer. This follows the [complete-bar color study](2026-11-13-hp-color-fade-study.md). Source videos/frames, archives, production code and installed builds were not changed. All generated media and measurements are on the 1 TB drive.

## Outcome

The tiny surviving Sealeo sliver remained measurable and did not arm a terminal candidate. Neither ordinary-switch control nor either real screen-transition control produced a terminal candidate. The controlled tests distinguished an isolated bar fade from whole-screen dimming, rejected incomplete bar captures, and kept a translated full bar tracked.

However, the additional brightness-fade requirement is NOT ready to be a mandatory KO condition. It lost most positive cases and geometry alone crossed a Pokémon identity boundary. The broad comparison is a test of that hypothesis, including its failures, not a claim that KO recognition has been solved.

| Outcome reviewed independently | Windows | Terminal-color armed windows | Any relative-fade evidence | Raw two-frame fade windows |
| --- | ---: | ---: | ---: | ---: |
| KO | 13 | 12 | 2 | 2 |
| nonlethal | 12 | 0 | 0 | 0 |
| switch | 2 | 0 | 0 | 0 |
| screen_fade | 2 | 0 | 0 | 0 |

Raw two-frame hits require time/identity review, even inside a correctly labelled KO window. Rejected hits after the Sealeo-to-Vaporeon boundary: `[{'name': 'p_sealeo_fast_ko', 'seconds': 48.587389}]`. Remaining hits: `[{'name': 'cramorant_retained_ko', 'seconds': 179.708056}]`. The actor-bounded Sealeo rerun clears the pending state on the independently visible species change; it is reported separately rather than silently replacing the failed geometry-only run.

## Coverage and provenance

Twenty-five original comparison windows (13 KO sequences, 12 nonlethal windows), plus two ordinary switches and two screen transitions, were processed frame by frame. Total decoded/preserved frames in these windows: 4594. These windows are not independent matches and some overlap. The five continuous source recordings and 39 retained native Cramorant frames are the same source set as the preceding study. Their SHA-256 hashes were rechecked against the preceding manifest. Cramorant's retained images are sparse samples, not a continuous original video; gaps remain gaps.

Source identity, source PTS/time base or retained-frame filename, measured rectangle, geometry strength, color counts, track segment, photometric ratios and rejection state are preserved in per-window JSON. No experiment output is submitted as live testimony. Source times below are video seconds, not Replay clock times. Side/phase windows are manually selected; this is not an autonomous species/side recognizer.

[Source/script manifest](/mnt/overdex-backup/archives/overdex-device-backups/2026-11-13-hp-tracking-relative-fade/source-manifest.json) · [Results](/mnt/overdex-backup/archives/overdex-device-backups/2026-11-13-hp-tracking-relative-fade/results.json) · [Per-case CSV](/mnt/overdex-backup/archives/overdex-device-backups/2026-11-13-hp-tracking-relative-fade/case-summary.csv)

## Methods tested

**Geometry tracking.** Start only from a complete bright outline (minimum channel 155, neutral-channel spread no more than 65), with the same rails/end-cap coverage of at least 90% used in the preceding study. Expand the upper territory to 18% of frame height for flying bars. While a track is recent, allow complete outline candidates at brightness thresholds 110, 70 or 40 to follow a fading bar. Both rails and both end caps are still required in the current frame. These relaxed contours are research geometry candidates, not valid zero-HP readings.

Match recent geometry within 250 ms, width change at most 12%, height change at most 45%, horizontal displacement at most 22% of frame width and vertical displacement at most 16% of frame height. Competing matches closer than the ambiguity margin are rejected. A gap or ambiguous/new acquisition creates a new segment; it does not prove departure. The search is not optical flow or parallax reconstruction. These permissive experimental tolerances exposed the identity-crossing counterexample below.

**Terminal color.** The preceding study's complete-bar orange-prefix rule is unchanged. At least three bright-frame measurements spanning 100 ms, gaps at most 150 ms, arm a pending candidate. No live-colored remainder is allowed. A color candidate is not a faint.

**Relative fading.** Once armed, remember rail brightness, brightness over the orange extent, nearby scene brightness in four external patches, and 36 coarse scene tiles. A one-frame fade requires both rail and fill brightness ratios below 0.72, agreement within 0.22, local background ratio at least 0.75 and bar/background relative ratio below 0.78. Broad scene dimming (median tile ratio below 0.72 with at least 65% of tiles dimmed) vetoes the local-fade interpretation. A two-frame version requires two qualifying frames separated by at least 25 ms and no more than 200 ms.

**Surviving fill.** Preserve native pixel measurements and check lower-brightness live-color support when following a dim bar. A connected leading live-colored sliver vetoes the candidate. Background bleeding into a translucent bar can also trigger this conservative veto; that ambiguity is a measured failure, not a resurrection event.

These thresholds are exploratory. The tests were not a blind holdout, and mobile processing cost was not benchmarked. Repeated filtered views of one frame are not independent evidence.

## Critical checks

### Tiny surviving Sealeo sliver

At 41.299011 s, the mask still measures **2 live-colored source columns** alongside the orange damage region. At 42.905467 s, it measures **4 live-colored columns** after damage settles. Across the entire nonlethal 40–44 s window, no terminal candidate or fade confirmation was produced. The earlier illustration used a slightly different hue cut and counted four columns in the impact frame; this experiment retains the actual mask and count rather than assuming those counts must be identical.

[Native-source comparison](/mnt/overdex-backup/archives/overdex-device-backups/2026-11-13-hp-tracking-relative-fade/p_sealeo_nonlethal_charge-review.png)

### Ordinary switches

Player Camerupt to Gourgeist, 95.5–99.5 s, and opponent Swampert to Abomasnow, 98.4–102 s, from the Moondrop 16:05 recording were reviewed independently. Both retain nonterminal HP evidence around recall/entry; neither arms a terminal candidate. Missing bars are not assigned zero. [Source overview](/mnt/overdex-backup/archives/overdex-device-backups/2026-11-13-hp-tracking-relative-fade/moon1605-overview.jpg)

### Whole-screen fading

Real transitions from the two Moondrop recordings were tested. An independent scene check finds broad dimming in both. The local bar path produces no KO candidate in those control windows, but there are few/no HP pixels left at that point; those negatives alone would be weak evidence.

To challenge that limitation, eight explicitly synthetic sequences use real native source frames: orange-only Cramorant and the surviving Sealeo sliver, each with whole-frame dimming, bar-only dimming, incomplete capture, or whole-frame translation. The isolated orange-bar fade is detected; global dimming of the SAME orange bar is vetoed; all sliver variants remain nonterminal; fragments never acquire a complete bar; translated complete bars remain tracked. These variants test mechanics, not additional recorded matches.

[Controlled-test results](/mnt/overdex-backup/archives/overdex-device-backups/2026-11-13-hp-tracking-relative-fade/control-test-results.json) · [Real scene measurements](/mnt/overdex-backup/archives/overdex-device-backups/2026-11-13-hp-tracking-relative-fade/real-screen-fades.json)

The initial bar-only fixture dimmed an inner detected rectangle while leaving a bright outer edge. Its failed positive-control run is preserved. The corrected fixture covers the full bar and margin. The fragment fixture was also made explicit as a partial capture padded to native dimensions so leftover source borders cannot accidentally supply missing edges. These are fixture corrections, not added battle observations.

### Identity crossing: an actual failure found

The geometry-only Sealeo run emitted a late two-frame fade at 48.587389 s. Native game badge text still says Sealeo at 48.188533 s and changes to Vaporeon at **48.220044 s**. Thus the late fade cannot be counted as a correctly timed Sealeo KO. The tracker had retained old pending state across visually similar geometry.

A second run supplies that independently annotated source-time identity boundary and resets geometry, color and fade state. The old terminal candidate is absent throughout Vaporeon's entry. This test uses human annotation of game text; it does not pretend that this script recognizes text automatically. It verifies the consumer contract needed with the existing identity lane.

[Exact badge frames](/mnt/overdex-backup/archives/overdex-device-backups/2026-11-13-hp-tracking-relative-fade/sealeo-vaporeon-identity-boundary.png) · [Boundary rerun](/mnt/overdex-backup/archives/overdex-device-backups/2026-11-13-hp-tracking-relative-fade/sealeo-with-identity-boundary.json) · [Passing assertion](/mnt/overdex-backup/archives/overdex-device-backups/2026-11-13-hp-tracking-relative-fade/identity-test.log)

### Why fade brightness missed KOs

Cramorant supplies a visible local fade: its first measured fade frame is around 179.550 s while the surrounding scene remains comparatively stable. Later translucent pixels mix with scenery and can resemble live colors. Other bars disappear or lose complete measurable geometry before the proposed two-frame test can pass. In daytime scenes, fading into bright grass need not produce a large brightness drop. Attack flashes and changing camera/background also disturb a simple brightness ratio.

The two-frame brightness condition therefore cannot replace the complete-bar color cue or become a prerequisite for every KO. A sparse retained frame sequence is additionally insufficient to establish original consecutive-frame cadence. Complete geometry improves measurement coverage in some windows, but its relaxed low-light version is not proven against every false contour.

## Per-window audit

`Armed`, `relative`, and `two-frame` columns count frames. Segments are geometry segments, not automatically identified Pokémon appearances.

| Window | Label | Complete frames | Segments | Armed | Relative | Two-frame |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| p_sealeo_nonlethal_charge | nonlethal | 107 | 3 | 0 | 0 | 0 |
| p_sealeo_fast_ko | KO | 100 | 1 | 61 | 4 | 1 |
| p_vaporeon_charge_ko | KO | 0 | 0 | 0 | 0 | 0 |
| p_gourgeist_nonlethal_charge | nonlethal | 102 | 6 | 0 | 0 | 0 |
| p_sealeo_nonlethal_fast | nonlethal | 143 | 1 | 0 | 0 | 0 |
| m_turtonator_charge_ko | KO | 84 | 3 | 53 | 0 | 0 |
| m_player_turtonator_charge_ko | KO | 90 | 4 | 43 | 0 | 0 |
| m_gourgeist_fast_ko | KO | 93 | 1 | 19 | 0 | 0 |
| m_camerupt_nonlethal_charge1 | nonlethal | 93 | 3 | 0 | 0 | 0 |
| holdout_vaporeon_fast_ko | KO | 94 | 1 | 28 | 0 | 0 |
| shield_sneasel_ko | KO | 140 | 4 | 92 | 0 | 0 |
| m_golbat_after_shield_ko | KO | 69 | 4 | 58 | 0 | 0 |
| m_abomasnow_ko | KO | 112 | 1 | 38 | 0 | 0 |
| m_swampert_final_ko | KO | 51 | 1 | 26 | 0 | 0 |
| m_camerupt_charge_ko_full | KO | 345 | 3 | 275 | 0 | 0 |
| m_gourgeist_nonlethal_charge | nonlethal | 95 | 3 | 0 | 0 | 0 |
| p_vaporeon_nonlethal_charge | nonlethal | 132 | 2 | 0 | 0 | 0 |
| challenge_player_lowhp_fast | nonlethal | 185 | 6 | 0 | 0 | 0 |
| challenge_opponent_lowhp_fast | nonlethal | 136 | 5 | 0 | 0 | 0 |
| challenge_vaporeon_lowhp_fast | nonlethal | 122 | 1 | 0 | 0 | 0 |
| m_turtonator_nonlethal_charge | nonlethal | 50 | 5 | 0 | 0 | 0 |
| m_shield_nonlethal | nonlethal | 59 | 3 | 0 | 0 | 0 |
| m_player_turtonator_ko2 | KO | 77 | 2 | 55 | 0 | 0 |
| expanded_golbat_nonlethal_fast | nonlethal | 84 | 1 | 0 | 0 | 0 |
| cramorant_retained_ko | KO | 15 | 2 | 12 | 2 | 1 |
| switch_player | switch | 77 | 2 | 0 | 0 | 0 |
| switch_opponent | switch | 27 | 1 | 0 | 0 | 0 |
| screen_fade_moon1605 | screen_fade | 0 | 0 | 0 | 0 | 0 |
| screen_fade_moon1553 | screen_fade | 0 | 0 | 0 | 0 | 0 |

## Recommendation

Keep complete-bar tracking and source-time appearance resets as separate, necessary components. Use relative fading as supporting evidence and a screen-transition rejection check, not a mandatory gate or standalone faint decision. Preserve the last trustworthy live/damage-colored extent before transparency; mixed background colors during fade must remain uncertain rather than automatically becoming HP, zero, or revival. A downstream KO still needs independent departure/resource/identity context, and assigning its finishing move remains a separate source-time evidence association.

The next focused recognition experiment should measure loss of outline contrast/opacity, rather than require absolute brightness to fall, while retaining these sliver, switch, scene-fade and identity-boundary regressions. This report does not authorize broad threshold relaxation or changing archived events.

## Reproduction and verification

Research root: `/mnt/overdex-backup/archives/overdex-device-backups/2026-11-13-hp-tracking-relative-fade`. Runtime: `PYTHONPATH=/tmp/overdex-video-review-python:/tmp/overdex-terminal-motion-python` (versions recorded in manifest; temporary dependencies may need recreation later).

Run `tracking.py` for all 29 recorded windows, `control_tests.py` for eight controlled variants, `scene_checks.py` for the two independent scene fades, and `identity_boundary_test.py` for the appearance reset. `review.py` extracts exact source PTS frames for inspection. Then run `finalize_study.py`, which asserts coverage, geometry completeness, sliver preservation, negative outcomes, positive-control detection, identity reset, source hashes and result consistency. The positive/negative assertions deliberately do not assume that every KO satisfies the proposed fade rule.

App tests/build/install were not run: this slice changes only offline research and documentation. No commits were made.
