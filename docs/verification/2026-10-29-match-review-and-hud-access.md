# New recordings and manual HUD access — October 29, 2026

Copied today's eleven existing archives from both phones, matched phone/desktop
SHA-256 hashes, and verified every declared artifact without altering originals.
Three are battles; the other eight are prebattle/navigation records from the
Droidball checks. All 674 declared battle artifacts verified successfully.

| Device / local start | Match ID | Articles / artifacts | Recorded result |
| --- | --- | --- | --- |
| Moondrop 10:39 | `f39d6255-94a2-4a1d-9300-98266c5a7f92` | 2,751 / 364 | No MatchEnded; partial GOOD EFFORT and LOSS music support loss. |
| Moondrop 10:41 | `56c108df-31de-4077-a232-a7121f9d32d2` | 1,613 / 228 | WIN |
| Pixel 13:18 | `f1e3863a-ab85-4c8f-8257-b6c203b01179` | 823 / 82 | WIN |

Full reviews, selected untouched Articles, extracted source crops, inventories,
copy provenance, audio measurements and original logs are retained at
`/home/sean/overdex-device-backups/2026-10-29-match-review/`.

## Pixel opening delay and the narrow correction

The user reports difficulty launching the HUD. Capture and the native overlay
were created about 1.3 seconds after record start. A retained Team Select frame
already shows the scan panel. Team Select acceptance took 2.015 seconds from that
frame; its third roster slot took 9.710 seconds. The explicit HUD request was
recorded at 13:19:06.572 and battle consumers enabled 24ms later. There is no tap
trace establishing exactly when earlier attempts occurred or the first visible
Battle HUD frame.

The SEEKING TEAM SELECT panel had no manual opening action. `BattleOverlay` now
offers **START BATTLE HUD** there, using the same existing request handler as the
prebattle control. Its message explains that the HUD can be opened before scan
acceptance. Team confirmation is not required for this recovery action. The existing
native-badge display gate remains: opening the HUD is not proof a battle began,
and it must not retain overworld scenery as a team badge.

## Separate recognition failures remain

- Pixel's initial species strips contain sky. The runtime locator later moves
  the opponent strip into Android Pointer Location (`Prs` / `Size`). It changes
  temporary crop Y, not saved calibration. No stored profile snapshot exists in
  this archive, so saved-coordinate mutation is not established. Player strips
  later contain the nickname `ground`, then Latios. Only Latios was accepted;
  there is no accepted opponent active species in this recording.
- A bad Pixel strip reports 40.071s capture-to-completion, including only 1.721s
  native OCR reading. The remaining processing delay is not isolated by this
  record. Capture delivery and recognition latency are separate measurements.
- The first accepted opponent ball count enables badge visibility no later than
  13:19:34.194. Missing species after that is not proof that the badge gate stayed
  closed for the entire fight.
- Moondrop's 10:39 result crop clips GOOD EFFORT at both ends. OCR yields only
  `D EFFOR`; LOSS music is corroborating evidence but no MatchEnded is recorded.
  The 10:41 WIN succeeds through repaired `YOU WVIN!`.
- Prebattle type crops on both devices show GO's exit button and can falsely
  match Ghost. Neither Moondrop battle has a witnessed final zero-ball/faint
  interval. First-match charge/faint publication delays reach about 10–11s.
- Audio is present in all three battles: 37/23/2 non-silent retained WAVs for the
  two Moondrop battles and Pixel respectively. Pixel has species-entry clips but
  no fast-stream comparisons; its listening gate lacks accepted opening context.
  Moondrop has isolated strict Incinerate and Dragon Tail audio matches. Cadence
  identities, tie-break assumptions and single audio matches do not automatically
  authorize exact energy. Detector totals are not verified real attack counts.

The manual button correction does not repair these crop, nickname, type,
terminal-event or backlog problems. The next audit should anchor name relocation
to the native badge and reject system-overlay rows, then isolate recognition
processing delays before increasing reader work.

## Validation and installation

- Full unit suite: **1,417 tests / 225 suites**, zero failures/errors/skips.
  Debug app build passed; no additional test mirroring the visibility conditional
  was added.
- Installed on idle Pixel 4 (`99261FFAZ0017J`) and Moondrop
  (`MDPH00124112500414`). All **6 Pixel / 16 Moondrop** calibration/profile,
  HUD-layout and current-team files retain their saved hashes.
- On Moondrop, normal pre-flight and Android screen-sharing approval opened the
  floating control over a nonbattle GO screen. Tapping Scan Team Select showed
  the SEEKING panel and new Start control. Tapping it produced the existing
  `battle_overlay_opened(USER_REQUEST)` Article. No MatchStarted was invented;
  the badge-free screen retained recovery controls as intended. Screenshots and
  this separate prebattle test archive are retained with the review.
- The normal Stop Session control ended the check. Neither phone had an active
  Overdex service afterward. No new battle was played for this verification.
- Pixel's new APK installation is confirmed; the scanning-button interaction
  was checked physically on Moondrop only.

Installed APK SHA-256:
`3e3a1ddc9673f2918253a63fca94f0fc76b02f197e412e3933d2026c07cd1e2d`.
