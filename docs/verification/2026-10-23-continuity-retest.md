# October 23 HP continuity retest (12:15)

User reports a possible fourth Incinerate and uncertainty about the count. Do not force groups to three or label an extra use erroneous solely from the test plan. Both phones were updated to 36ea52e before capture. Originals remain on-device; desktop copies are in `/home/sean/overdex-device-backups/2026-10-23-continuity-retest/`.

| Device | Match ID | Articles | Verified artifacts | SHA-256 |
| --- | --- | ---: | ---: | --- |
| Moondrop | 29d60318-c819-44ac-b1e8-1755dcd24017 | 2949 | 356 | b066e609b9b6e8f7dd79fbc70a267833bedb2809350cf2ede4e14f4435f4d930 |
| Pixel 4 | 0df627a3-9215-455c-a5ea-5faf49aa856b | 4738 | 563 | 63b23c624858ee142509c63d63b8b05fc5e4327b739271f25b5b0e13301f2084 |

Both manifests match their timelines and every declared artifact hash verified. Both contain MatchStarted. GO monotonic times: Moondrop 21115426676796 ns, Pixel 12666843529454 ns. Times below are relative to each phone's own GO, not interchangeable hardware clocks.

## Observed results

- Both record 16 Turtonator uses, in chronological groups of 3, 3, 3, 1, 3, 3. The single use is between two charged moves. No uninterrupted group of four is established, but the uncertain user count is not treated as a contradiction.
- Neither timeline contains an idle-side FastMoveUse or idle-side generated energy. The previous false Swampert damage-derived attack did not recur.
- Both record one Brutal Swing followed by three Dragon Pulses, with costs 35 + 55 + 55 + 55 = 200. Both record WIN/LOSS consistently (Moondrop WIN, Pixel LOSS).
- Each archive contains sixteen +20 Incinerate energy derivations, i.e. 320 generated energy before storage caps/spending. This is not a 320-point stored balance. No Ember energy was awarded despite the provisional identity below.
- Pixel shield-text fallback recognizes all four charge pauses: 28.69, 67.86, 81.71, 114.13 s. Its named impact cues are 35.71, 73.28, 87.73, 118.86 s. There are no FastMoveUse timestamps inside these intervals. Moondrop Get Ready cues at 26.73, 66.04, 78.66, 111.30 s likewise bound no in-QTE uses before corresponding impacts.

## Remaining accuracy boundaries

Pixel first identifies Ember at source time 7.731 s from HP-bar motion cadence, then Incinerate at 19.678 s with effectiveness support. The final energy derivations use Incinerate, including earlier uses, but early identity usefulness still needs improvement. Source timestamp is not publication latency.

Pixel's fourth use is at 17.811 s and fifth at 18.730 s; Moondrop's counterparts are 16.025 and 18.609 s. Both HP decrements are about 9.5%. The count agrees, but Pixel captures/settles one hit late and makes the interval look too short. Do not render this as evidence that Incinerate truly completed in 0.919 s. The first Moondrop use also relies on pulse/fill support rather than a damage tick (3.42 s versus Pixel's damage tick at 2.79 s).

This recording supports the HP continuity fix against the previously observed idle-side false attack. It does not prove universal accuracy or eliminate the separate charged-impact-tail risk from the earlier recording. Next targets remain early identity arbitration and accurate impact timing without rewriting raw testimony.
