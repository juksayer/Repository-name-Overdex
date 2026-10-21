# Galvantula / Lunge recording review — 2026-10-12

Canonical checkout: `/home/sean/AndroidStudioProjects/Overdex`, branch `odx-recovery`.

## Actual recordings

Both compact exports opened and all declared artifact hashes verified. Times below are seconds after the recording began, including pre-match navigation.

| Export | Match start | Lunge announcement episodes | Recorded result |
| --- | --- | --- | --- |
| `2026-10-12_19-47-20-571-compact.odxmatch.zip` | 82.561 (GO) | 106.345, 126.140 | WIN at 161.414 |
| `2026-10-12_19-52-38-448-compact.odxmatch.zip` | 21.418 (countdown inference) | 52.665 | WIN at 89.502 |

- Match IDs: `ceed1d01-56b8-4ce3-8224-fef32a2420b5` and `4d1d0e7f-d6a5-49e4-91f9-2313f9abeb45`.
- Configured player lead: Galvantula, Volt Switch, Discharge, Lunge. Opponent identity testimony includes Sneasel, Sealeo, and Vaporeon.
- The preserved announcement visibly says **`gbl used Lunge!`**. OCR transcribed that text correctly. Subsequent readings repeat it, occasionally clipping the nickname.
- The old charged-move inference required a resolvable species name in the announcement. It rejected the nickname and emitted no named Lunge use or corresponding energy debit. The generic charged-move announcement still appeared.
- Neither archive contains `StatChangeSoundMeasured` or `StatChangeObserved`. Both stat witnesses were operating, but witness availability alone does not establish uninterrupted PCM processing or successful recognition.
- Five existing fast-move-triggered audio snippets near Lunge were inspected. A local spectral-score probe did not produce a confident stat-cue match. These 1.2-second snippets were not captured around the charged move and cannot establish that the debuff sound was absent from gameplay.
- Three distinct Lunge announcement episodes are evidenced. This is not a claim that every charged move was captured.

## Changes

- An unknown nickname, or a clipped name in `used Lunge!`, can resolve through the two active combatants' reference movepools only if both are known and exactly one can perform the observed move. The derivation cites both identities and the original announcement. Known conflicting species text and ambiguous/mirror candidates remain unresolved.
- Identity lookup uses the announcement's capture time, so delayed OCR does not debit a later active Pokémon. Repeated announcement readings retain raw testimony but produce one use within the existing four-second episode window.
- Each generic charged-move announcement requests a bounded stat-sound probe: up to one second of pre-roll and three seconds of post-roll, mono PCM at 8 kHz. Maximum WAV size is 64,044 bytes per probe; this does not save whole-match audio.
- Probes publish `StatChangeSoundChecked`: requested, accepted, no confident match, input gap, late request, or storage failure. Rejected rankings are diagnostics, never observed effects. Checks cite their WAV, retaining the short evidence window in compact exports.
- Continuous and triggered readers deduplicate the same acoustic occurrence. Acceptance remains similarity >= 0.91 and margin >= 0.055; thresholds were not lowered to force these recordings to match.
- Audio alone still does not determine the recipient. A compatible attributed charged move and the reference effect are required before replay shows the stat change on a side.
- Existing recordings remain unchanged; this update does not manufacture missing stat-change events in them.

## Validation and deployment

- Full unit suite: **591 passed**, zero failures/errors/skips. New coverage includes nicknames, clipped names, mirrors, conflicting names, delayed OCR, diagnostic ambiguity, and compact serialization of rejected checks.
- Debug app and instrumentation APKs built successfully.
- Installed with `adb install -r` on Moondrop `MDPH00124112500414`, with MediaProjection confirmed inactive immediately beforehand.
- Physical-device instrumentation: **5 passed**. Four injected reference cues retain their timestamps and resolve to replay effects; an accepted probe and the continuous reader yield one measurement; a silent probe retains its WAV without inventing an effect; a dropped PCM sequence cancels the probe.
- Matcher benchmark on this run: median 20.35 ms, maximum 38.59 ms, within the 200 ms continuous scan interval.
- All 11 saved calibration/profile/template/HUD files were backed up and remained byte-identical after installation and tests. Overdex was reopened afterward.
- APK SHA-256: `ea4ea5d0194d73cf2653cef94aff48913741d6f5f4e7a340f18bb42a8ebad0e3`.
- Local recordings, inspection output, test logs, and calibration backup: `/tmp/overdex-lunge-review-20261012/` (not committed evidence).

## Remaining live acceptance

A new recorded Lunge is needed to determine whether the actual gameplay cue matches the references. This review found and fixed the nickname attribution fault, but did not demonstrate successful live debuff recognition. Injected reference audio is a pipeline check, not proof of recognition through Pokémon GO's live sound mixture.

## Follow-up: two real recordings, 20:26 and 20:31

This follow-up supersedes the live-recognition limitation above, while leaving the original review as a dated account of the earlier build.

| Internal recording | Articles | Verified artifacts | GO / result, seconds from recording start | Recognized stat sounds |
| --- | --- | --- | --- | --- |
| `2026-10-12_20-26-18.odxmatch.zip` | 2,864 | 741 | 37.817 / WIN 110.443 | 0 |
| `2026-10-12_20-31-55.odxmatch.zip` | 4,865 | 1,280 | 24.233 / WIN 146.131 | 3 attack-down cues |

The second match actually recorded attack-down matches at +39.517, +92.046, and +107.528 seconds, with similarities 0.946, 0.949, and 0.960. These scores are matcher similarities, not accuracy percentages. Original archive hashes/artifact hashes were verified; the recordings have not been edited.

Neither original recording contains named charged-move derivations or attributed stat effects. The configured player lead was published directly by `appendTeamSelectLeadAtMatchStart` and fed to Fast Move inference, but omitted from `ChargedMoveAnnouncementInference`. Consequently the nickname fallback lacked one of its two required active identities, even though the Timeline already knew Galvantula. The local lead now reaches that interpreter too. This fixes the missing connection without relaxing nickname ambiguity rules.

Several charged-move probe requests also exceeded the old two-second delivery limit. Separately, the user confirms that announcements and effects may themselves happen seconds apart. Changes:

- Retain 20 seconds of volatile 8 kHz mono PCM (320 KB) and extract each requested window around the announcement's original capture time. The retained diagnostic window spans six seconds before to seven seconds after, allowing a cue onset up to six seconds away plus its waveform tail. Each WAV is bounded to 208,044 bytes; continuous unmatched audio is not saved.
- Correlate compatible named moves and sounds within six seconds in either direction, independently of their publication order or latency. Multiple compatible moves remain ambiguous rather than using proximity to choose a recipient. Repeated delivery of the same article does not create a second candidate.
- Keep the continuous listener operating at its existing cadence and thresholds. A long diagnostic window does not make that listener wait for completion before reporting a sound. Audio outside retained history and input gaps remain explicit diagnostic outcomes.
- Replay identifies the affected combatant at the cited charged move. A Pokémon entering between the move and its sound must not inherit the departing Pokémon's animation.

### Follow-up validation

- Full unit suite: **598 passed**, zero failures/errors/skips; debug app and instrumentation builds successful.
- Physical Moondrop: **7 instrumentation tests passed**, covering the four reference cues, bounded probes, real capture gaps, continuous/probe deduplication, and a sound five seconds before text with text delivered another four seconds later. Continuous matcher benchmark: median 15.844 ms, maximum 17.975 ms, against the 200 ms scan interval.
- A new regression fixture contains only selected original articles and three unmodified WAVs (about 117 KB), with original timing, ordering, and SHA-256 provenance. It does not supply inferred moves or stat effects. The device test routes configured team, GO, species, actual OCR, and retained audio through Match, sound recognition, attribution, archive mapping, and replay.
- That test yields three distinct Galvantula Lunge uses and two attributed opponent attack decreases. The third heard decrease has no nearby captured named move and correctly remains unassigned. Duplicate OCR readings do not create another move. The test passed again against the final APK after the replay recipient guard.
- Original archives remain unchanged and are not automatically reprocessed by the viewer. These corrections apply to new observations; the regression test is a separate reconstruction, not a claim that old files now contain missing derivations.
- Both recordings include a WIN result, but publication lag was approximately 13 and 21 seconds. That remains a separate observation-latency issue; it was not fixed in this change.
- Final APK SHA-256: `4fe9088fa6ff3a68eced4fc88e92d1eeacd046a3767e46d78756e51bd407ba35`. Installation used `-r` after confirming no active MediaProjection. This build also removes the previously revoked OpenAI key from the application package; the DEX/resource secret-pattern scan was clear.
- The installed APK hash matches the final build. All 11 calibration/profile/template/HUD files remain byte-identical after both installs and device tests. Overdex was reopened afterward.
- Local recordings, build/test logs, and calibration backups: `/tmp/overdex-lunge-followup-20261012/`. Actual live audio recognition is demonstrated for these three clips; complete live cue coverage and the improved delayed-probe behavior still need a new battle on this build.

## October 13: live follow-up and charged-move OCR repair

Four new compact exports were reviewed on the Moondrop running the preceding `4fe9088f…` APK. ZIP hashes match the device copies, manifest article counts match, and all 2,637 declared artifacts verified. Original archives were not changed.

| Export timestamp | Opponent identities in Timeline | Named charged moves already archived | Fast Move use Articles | Result publication delay |
| --- | --- | --- | --- | --- |
| Oct 12, 22:30:43 | Plusle, Electrode, Jolteon | Galvantula: Lunge | 21 | WIN, 6.184 s |
| Oct 12, 22:34:24 | Sneasel, Sealeo, Vaporeon | Galvantula: Lunge, Discharge | 27 | WIN, 7.113 s |
| Oct 12, 22:38:52 | Combusken, Magcargo, Flareon | Galvantula: Discharge; Camerupt: Rock Tomb | 23 | WIN, 11.461 s |
| Oct 13, 09:20:53 | Combusken, Magcargo, Flareon | Galvantula: Lunge | 41 | WIN, 5.766 s |

The three actual `gbl used Lunge!` announcements now produce player-side Galvantula/Lunge Articles during live recording. The configured-lead wiring fix is verified beyond the replayed fixture. These files contain all three opponent identities per match, although individual species publication still often takes several seconds. Canonical Fast Move Article counts above are recorded counts, not independent ground truth for attacks performed.

All ten charged-move audio requests retained their complete 13-second windows. None were rejected as too late or outside retained audio. None passed the stat-cue acceptance threshold, so these four matches contain zero `StatChangeSoundMeasured` and zero `StatChangeObserved`. The best attack-down candidate near the first Lunge scored 0.9043 versus the unchanged 0.91 threshold. Lower-ranked candidates are retained diagnostics, not observed stat effects; absence of acceptance does not establish that no debuff happened.

### Newly diagnosed text loss

The preserved crops visibly read `Sneasel used Ice Punch!` and `gbl used Discharge!`. OCR returned `Sneasel used lce Punch!` and `salusedDischargo`. Exact move matching rejected both. The two source PNG hashes are:

- `48803d54c69a0f0b32aee4bbaed5c0ac61f8b6f1214d41d918c5fba5fd55be24`
- `77924760c556db81866a5331286136cc098367a6ef7020f6f28ab1f4f2f9f50d`

Charged-move interpretation now permits one character insertion, deletion, or substitution for complete names of at least six normalized characters, restricted to legal charged moves of the applicable species. Exact matches take priority; equally close candidates, mirror users, missing opponent identity for nickname resolution, conflicting named species, and clipped prefixes remain unresolved. A constrained compact announcement pattern handles missing spaces around `used`. Repaired derivations carry `OCR_SINGLE_CHARACTER_REPAIR` in their basis and preserve the original text and timestamps. Heavier `Earth Power` damage such as `Earn Power`, `Eamrowe`, or `Ear` is intentionally not guessed by this correction.

Validation: **603 unit tests passed**, zero failures/errors/skips. Debug and instrumentation APKs built. **Two physical-device tests passed**: the earlier real Lunge/audio chain remains valid, and the recorded Ice Punch misread now reaches a confirmed HUD move and named opponent charged-move replay action without inventing a stat effect. Final installed APK SHA-256: `130ad275a48480f3173b88e38c79e0368c7c607c9a9e76400712043d33b80a1f`. MediaProjection was inactive before replacement. All 11 saved calibration/profile/template/HUD files remain byte-identical, including comparison with the prior install. Overdex was reopened afterward.

Remaining issues exposed by these matches: Sealeo's estimate alternated Water Gun → Powder Snow → Water Gun, and six Sealeo uses were assigned Powder Snow energy while that estimate was active. This review does not certify opponent energy accounting or complete Fast Move coverage. Result publication delay, missed stat-sound acceptance, and severely damaged move text also remain. The new OCR repair is verified using recorded text and phone pipeline tests; a new live recording is still needed to validate it during capture. Local evidence and logs: `/tmp/overdex-review-20261013/`.
