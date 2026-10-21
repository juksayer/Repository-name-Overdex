# October 14 match review: Wi-Fi interruption and hidden Poké Ball strip

Reviewed three new internal match archives from the Moondrop MD_PH_001 (Android 14). The user reported both unresponsive attacks/buttons and visible freezing in the last match. All times below are CDT on October 14, 2026.

## Archive integrity and recorded results

| Recording filename prefix / match ID | Recording span | Articles | Verified artifacts | Recorded result |
| --- | ---: | ---: | ---: | --- |
| `15-21-11` / `c6579189-d4ea-4566-9d68-d61c8fc0fbee` | 173.503 s | 3,800 | 689 | No `MatchEnded` article |
| `15-25-34` / `b95449d0-4a76-48c7-a10e-e225c7c10480` | 170.210 s | 3,886 | 1,021 | WIN |
| `15-30-48` / `17f61baa-b1fb-4eb0-aef1-1f3746333dea` | 218.262 s | 4,673 | 1,202 | LOSS |

The skill's archive inspector verified every declared artifact's integrity. The first archive is structurally valid; a missing result article does not establish whether the user stopped early or the result was missed. The last archive contains the preserved `GOOD EFFORT!` image at 15:34:16.023 and continues through 15:34:27.144.

Copies and raw read-only diagnostics are retained at `/tmp/overdex-review-20261014-slow-match/`. Raw Wi-Fi diagnostics contain network identifiers and are deliberately not included in this report or regression fixtures.

## Confirmed Wi-Fi interruption

Android's Wi-Fi connection-state history, compared with the latest match's source timestamps:

| Time | Event | Relative to GO |
| --- | --- | ---: |
| 15:31:44.578 | Observed GO / derived `MatchStarted` | 0 s |
| 15:31:59.196 | Wi-Fi reports disconnected | +14.618 s |
| 15:32:01.879 | Wi-Fi enters transition while reconnecting | +17.301 s |
| 15:32:06.648 | Wi-Fi reports disconnected again | +22.070 s |
| 15:32:12.289 | Wi-Fi reports connected | +27.711 s |

The interval from the first disconnect to the next connected state is 13.093 seconds. This is direct system evidence of a network interruption during battle. It does not establish the underlying router/provider cause, whether cellular fallback was usable, or that every freeze had the same cause. It is not evidence of a corrupt Pokémon GO installation.

Overdex continued observing during that interval: 90 crop articles, 128 HP measurements, 19 species-check articles, and seven preserved audio clips. These are observations, not 90 distinct complete video frames. The maximum gap between crop articles was 0.583 seconds; the HP measurement gap reached 1.988 seconds. New announcements were read during this period. The recording pipeline did not simply stop for the Wi-Fi outage.

The only explicit `VisualCaptureGap` in this recording lasted 0.973 seconds at 15:31:16.287, before GO. Android's retained Overdex exit history shows no process exit during these three recordings. The retained log buffer contains no fatal exception or ANR in the reviewed interval. These checks do not rule out all CPU contention or rendering stalls. A later thermal snapshot is not sufficient to establish the phone's temperature or throttling state during the battle.

## Reproduced Overdex false faint

At 15:33:41.971, the opponent strip visibly contains two dimmed balls and one live ball. At 15:33:42.322 and 15:33:42.743, the crop instead contains a pale rounded corner against black, with no visible balls. At 15:33:56.865, the same one-survivor strip returns.

The old counter treated the black background as dark Poké Ball outlines in all three slots, returned zero twice, and caused `ActivePokemonFaintedWitnessed(OPPONENT, Vaporeon)` at 15:33:42.322. Later testimony again measured one opponent survivor, and the recorded match ended in a loss. The crop disappearance is not a valid faint observation.

Changed `OpponentBattleResourceCounter.pokeBallCount` to reject edge-connected dark components wider than one ball slot or spanning the full crop height before validating the three ball silhouettes. Ball-sized outlines touching a crop edge remain valid. Visible dimmed balls still support zero survivors; an obscured or sliding badge returns unavailable (`null`). Both player and opponent witnesses use this counter and already discard unavailable candidate counts. No archive articles are rewritten, so this fix does not retroactively repair old replay testimony.

Four original crop fixtures and their source article IDs / hashes are preserved in `app/src/test/resources/battle/balls-20261014/`. The regression checks the sequence `1, unavailable, unavailable, 1`, plus a partial badge against black on either side. Existing tests cover three, two, one, and zero visible survivors. An exploratory comparison across all 443 retained player/opponent Poké Ball crops from the three recordings isolated the two disappearing-badge frames as the only changed availability decisions.

## Remaining observation limitations

- In the latest recording, raw-text article capture-to-publication delay has a median of 2.259 seconds, a 95th percentile of 4.576 seconds, and a maximum of 11.064 seconds. Typed species testimony has a median of 1.978 seconds and a maximum of 4.338 seconds. This measures timeline publication, not the direct HUD rendering latency.
- HP articles have a median publication delay of 0.123 seconds. There is a 33.722-second interval without a new HP measurement between the Rock Tomb sequence and Vaporeon/Gourgeist's appearance; absence of measurements does not prove that the battlefield or capture froze throughout it.
- The canonical fused Fast Move counts are 5, 28, and 15 respectively. They are not a verified count of every performed attack, especially in a match with interrupted interaction. Replay must not invent continuous attacks to conceal these gaps.
- The WIN result was published 8.433 seconds after its source frame; the LOSS result was published 11.086 seconds after its source frame. Both preserve their original observed timestamps.

## Validation and device boundary

- Every declared artifact in all three archives passed hash/size verification.
- All 13 resource-counter tests pass, including the recorded disappearing-badge regression and tight-crop examples. The first guard revision incorrectly rejected a dimmed ball touching the left crop edge; the existing test caught it, and the final component-size check preserves that ball.
- Full unit suite: **755 tests passed**, zero failures/errors/skips.
- `:app:assembleDebug`: **passed**. APK SHA-256: `8d0ccae3674f0db967a1493cfc1977559bcd9140fd68f281f3d2900c23a0d8ec`.
- `git diff --check`: passed.

The phone was left untouched while the user reinstalls Pokémon GO. No network settings were changed and no new APK was installed during this review. A later live battle remains necessary to validate the new hidden-badge behavior on-device.
