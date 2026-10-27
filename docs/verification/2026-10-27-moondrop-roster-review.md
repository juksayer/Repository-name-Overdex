# Moondrop match review — October 27, 2026, 12:09–12:17 local

The archive review was read-only. Source phone files were not changed or deleted; no UI, service, installation, or build actions were performed. Both ZIPs were copied unchanged and every declared artifact verified. Hashes and source/device equality are in `copy-provenance.json`; structured counts are in `archive-review-summary.json`.

## Device and build

Moondrop serial `MDPH00124112500414` had no running Overdex services at 12:09. Installed package version is generic 1.0/code 1, last updated at 11:27. Installed APK SHA-256 `d6fe00d57c043a23b6e7924988a2299689576d47b29c85882d26d2de77f0f6ed` matches the local debug APK; repository HEAD at review is `daa1704` (bound Fast Move energy by reference duration).

## New recordings

| Recording | Match ID | Articles / verified artifacts | Archive span | Start after record | Result after start |
|---|---|---:|---:|---:|---:|
| 11:51:45 | `3852510a-e985-4f3f-a466-0f46497732e0` | 4,318 / 487 | 357.711 s | 171.254 s | LOSS +185.314 s |
| 11:57:46 | `9797f931-a447-4b66-9378-c29c32924720` | 4,685 / 526 | 275.850 s | 52.214 s | LOSS +222.699 s |

These are distinct from the previous 09:42 Charm archive `5df79cf4-211b-46df-af49-1a2c75e9996e`. No identified Gardevoir combatants appear. Listed sound candidates are not witnessed moves or combatants.

First start cites an observed GO glyph. The newest start is explicitly `COUNTDOWN_AND_COMBAT_START_INFERENCE`, confidence 0.75, citing glyph 1 and an opponent-HP damage tick; no observed GO glyph is present. Times below are relative to each archive's MatchStarted Article, not an independently measured server start.

Both losses cite clear GOOD EFFORT text. Result OCR publication delay was about 1.05 s / 0.93 s respectively. No zero-player-ball observation or final-player-faint Article is retained; the result closes the match with the last observed player ball count still one. That is missing terminal evidence, not evidence that the loss was wrong.

## Confirmed roster differs from configured moves

Both archives retain configured Aegislash / Flygon / Mawile with Psycho Cut / Dragon Tail / Fire Fang. The user-confirmed Team Select rosters are Aegislash / Alolan Sandslash / Dragalge in the first match and Dragalge / Alolan Sandslash / Aegislash in the newest. Live active-species observations agree with those confirmed rosters; Alolan Sandslash resolves to its regional form.

The mismatch is explicitly recorded by PlayerBattleInformationChecked DIFFERS Articles. Neither Dragalge nor Alolan Sandslash gets a FastMoveIdentified or FastMoveEnergyDerived Article. Aegislash retains configured Psycho Cut. Thus new team confirmation established species/order but did not supply the new members' move loadouts. This explains missing player energy for those members without assuming they were idle.

## Observed uses and bounded energy

First recording: player Aegislash 9, Dragalge 9, Alolan Sandslash 10, unresolved 3; opponent Doublade 14 and Stunfisk 7. Psycho Cut receives seven 9-energy derivations; Mud Shot receives seven 9-energy derivations. Stunfisk's later audio/effectiveness identity backfills earlier use times, which accounts for up to 31.4 s publication delay in its derived energy.

Newest: player Aegislash 8, Dragalge 14, Alolan Sandslash 15; opponent unresolved 6, Mimikyu 12, Azumarill 12, Stunfisk 2. Only seven Psycho Cut energy derivations are present. Aegislash use at +26.463546 s is credited; the next at +26.990885 s (527.339 ms later) is not. This is consistent with the one-second reference timing guard rejecting impossible adjacent full cycles. First-match uncredited Aegislash uses occur at +111.683334 s and +126.322844 s near appearance/charge boundaries.

These are recorded use counts, not independently verified completed attacks. No independent exact-count protocol or MP4 was supplied for these recordings. The guard operating does not certify the witnesses' count accuracy.

## Visually verified newest-match anomalies

Original source PNGs and their exact crop geometry, hashes, and Article IDs are in `selected-crops/` and `selected-crops.json`. `selected-crops-review.png` is a labeled nearest-neighbor enlargement for inspection.

1. **Missing named Shadow Ball at +51.957 s.** The crop clearly reads “Aegislash used Shadow Ball!”; OCR produced “Aegislash used Shadow Bal!”. A generic ChargeMoveUsedAnnounced Article exists, but there is no corresponding named ChargedMoveEnergySpent. This is a missed character repair, not absent image evidence.
2. **Missing named Aqua Tail at +137.627 s.** The crop clearly reads “Dragalge used Aqua Tail!”; OCR produced “Dragalgeused Aqua Tail!”. A generic charge-use Article exists, but no named spend. The missing word separator blocks the named interpretation.
3. **Wrong outgoing-faint attribution near +202.5 s.** Opponent badge +196.227 s reads Mimikyu. Entry announcement +202.030 s says “Go, Azumarill!”; +202.502 s ball crop shows two dark balls and one live ball; +202.953 s badge reads Azumarill. Faint Article `2252ad7e-209c-4230-a880-a01d7ad96a8a` nevertheless names Azumarill at +202.502 s. The retained sequence strongly supports outgoing Mimikyu fainting and returning Azumarill surviving to the player's loss. This is a raw faint-attribution anomaly; this review did not run Replay to establish whether its reconstruction already compensates.

## Other useful boundaries

The first match records four Get Ready and four Attack Incoming cues; the newest records only one of each despite nine generic charge-use announcements. Start-of-charge coverage remains sparse. No decision-timer witness Articles are present. Fast-move energy between pause cues therefore cannot be assumed fully validated from these archives alone.

Some active-type matches are plainly unreliable support: first match reports DARK/PSYCHIC on the player while Dragalge is active; newest only has three pre-start GHOST readings. These must not be used as unqualified form/type confirmation.

Audio is present and nonzero. First archive retains six 48 kHz stereo clips plus 35 reduced 8 kHz mono clips; newest retains eleven stereo clips plus 47 reduced mono clips. This does not establish whether music was enabled or exact sound attribution. All visual-capture-gap Articles are before MatchStarted; sparse retained crops alone cannot diagnose capture gaps during battle.

## Pending user clarification

Ask which Fast Moves Dragalge and Alolan Sandslash have, and whether either was deliberately left idle. Exact-count performance cannot be inferred from casual-match totals alone. No clarification is needed to repair the two visibly readable charge announcements or investigate the documented faint attribution.

## Evidence location and follow-up

All unmodified ZIPs, extracted source crops, the inspection contact sheet, and
provenance files are retained under
`/home/sean/overdex-device-backups/2026-10-27-lock-stop-review/`.
The automatic lock/screen-off stop change is verified separately. This review
does not claim repairs to move configuration, charge-text parsing, or faint
attribution. Those are concrete follow-up targets supported by this evidence.
