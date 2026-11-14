# New Moondrop finishing-move review — November 14, 2026

## Outcome

Finishing moves are not consistently connected to faints yet. In the newest
completed match, Replay presents Earth Power, Aqua Tail and Shadow Ball at their
impact endpoints with the correct actors. The subsequent Rapidash, Camerupt and
Floragato faints have correct owners, but none is associated with its preceding
charge. The final Dragonair faint and completed finishing move are absent.

This is a diagnostic review at source revision `f233861f`, not a recognition fix.
Production behavior and installed builds are unchanged.

## Preserved inputs

Inputs and generated reports are on the 1 TB backup drive:

`/mnt/overdex-backup/archives/overdex-device-backups/2026-11-14-new-moondrop-review/`

| Moondrop archive | Articles / retained artifacts | Classification |
| --- | ---: | --- |
| `2026-11-14_10-44-46_43449380-8b2e-4e98-b975-9b1bb3ad99a4` | 3,019 / 392 | Rocket win; Cubone, Snorlax, Tyranitar |
| `2026-11-14_11-17-39_c952b8e5-7f11-4a47-b06d-2e565424547b` | 812 / 79 | Team selection / VS attempt; no accepted Match Start, attack, faint or result |
| `2026-11-14_11-20-14_bd8af3b0-8ce5-4a2b-9917-19eb11fe91c8` | 3,728 / 468 | Great League win; Dragonair, Rapidash, Floragato |

All 7,559 original Articles were preserved. All 939 retained artifacts passed
manifest byte-count and SHA-256 verification. ZIP hashes are in `integrity.json`.
Original source ZIPs remain unchanged; analysis uses copies in `inputs/`.

## Newest completed match

Times below are relative to recorded Match Start. Impact is the current Replay
presentation endpoint, not an invented source damage timestamp. HP is the
receiving Pokémon's Replay HP at that endpoint.

| Move and receiver | Replay impact | HP at impact | Replay faint pose | Association |
| --- | ---: | ---: | ---: | --- |
| Camerupt Earth Power → Rapidash | 1:35.995 | 56.08% | 1:38.796 | Missing |
| Dragonair Aqua Tail → Camerupt | 1:52.222 | 52.13% | 1:52.580 | Missing |
| Gourgeist Shadow Ball → Floragato | 2:07.838 | 60.85% | 2:10.005 | Missing |

All three charge projectiles reach progress 1 with the correct actors in the
actual Replay model. Their faint ownership is correct despite delayed ball-count
recognition. Dragonair and the player's Gourgeist return alive without being
incorrectly assigned these losses. The model produces zero finishing-charge
associations for this archive.

### Earth Power → Rapidash: smallest isolated next case

Charge preparation begins at 1:31.040, the accepted move is at 1:34.195, and
Dragonair replaces Rapidash at 1:39.446. A later single ball-count loss at
1:57.257 identifies Rapidash. No competing Fast Move occurs in that exchange.

The current associator requires a fresh shield reading between charge preparation
and the move source. There is none, although opponent shields were already zero
at 1:11.253 and 1:28.330 and remain zero at 1:39.446 and later. This missing fresh
bracket prevents association.

Complete-bar samples retain the tiny-survivor safeguard: at 1:35.479 there is one
live column alongside 109 damage columns, so it remains LIVE_COLOR. At 1:35.895,
zero live columns plus unresolved pixels remain UNRESOLVED_COLOR, not fabricated
zero HP. A retained 1:36.122 crop shows SUPER EFFECTIVE and an empty-looking whole
outline; that visual observation does not replace the reader's uncertainty.

Recommended next bounded change: allow established native zero shields to remain
usable within the same Match, with reset and conflicting-evidence guards. Retain
the other actor, timing, surviving-HP and competing-move requirements. Test this
case first; do not generally relax positive shield-count freshness.

### Aqua Tail → Camerupt

The charge source is 1:50.422 and Gourgeist replaces Camerupt at 1:53.230. The
later count loss at 1:59.437 resolves Camerupt. Player shields were already zero
at 1:26.328 and 1:39.295. There is no fresh reading during preparation/source;
the following zero reading at 1:56.423 also falls outside the existing two-second
post-replacement bracket. There is no competing Fast Move in the exchange.

### Shadow Ball → Floragato

The retained announcement at 2:05.460 clearly reads “Gourgeist used Shadow Ball!”;
OCR records `Shadow Bal!`. Named energy evidence at 2:06.038 supplies the Replay
move. A native zero-HP observation follows at 2:07.964
(`d9615cd8-f123-4da6-ad62-346ee8def818`), then the count loss at 2:10.005 identifies
Floragato before Dragonair returns around 2:10.861.

The current associator only handles count evidence recorded at or after the
replacement, so this earlier count fails that guard. Shield freshness is also
missing. A conflicting original opponent Fast Move claim at 2:10.396 precedes
the replacement despite the count-resolved faint at 2:10.005. Preserve that
claim and resolve its ownership/timing before broadening this path.

### Other and final losses

Turtonator's faint belongs to Turtonator, but its pose at 0:33.768 is not paired
with a supported finishing hit. Its loss is resolved from later count evidence.

The final Dragonair ending is a separate missing-evidence case. Last accepted
player Fast Move evidence is at 2:14.705. Attack incoming appears at 2:15.356,
but the charge sequence remains uncompleted; Replay does not invent a throw.
Opponent HP remains about 13% through 2:24.932; the player's Gourgeist has about
6.91% HP at that point. There is no later accepted attack, terminal zero HP,
zero-ball count or Dragonair faint. WIN is recorded at 2:52.384. The ending uses
a result-based defeated pose, not an independently recorded faint.

The user subsequently recalled **“Seed Bomb, I believe”** as the final hit.
Record this as tentative user testimony identifying the next recognition target.
It is not an archived completed Seed Bomb, and the archive must not be rewritten
to pretend it observed that move or its impact. WIN alone cannot establish the
finishing move or exclude concession.

## Rocket match

The retained player badge says `meltard`; the final announcement at 2:08.494 says
“meltard used Double Iron Bash!” Replay instead uses the configured
Turtonator/Incinerate identity. No accepted actual player species replaces it.
This upstream identity mismatch prevents a trustworthy move/performer review;
do not infer the species merely from the nickname.

Cubone and Snorlax have resolved faints. Tyranitar has no independent final faint;
WIN at 2:23.227 supplies the ending defeated pose. The final source announcement
is presented as an unnamed charge from the configured player actor, with impact
at 2:10.294. There are zero finishing-charge associations.

Five earlier compressed charge intervals have a near-impact frame but no charge
action at the exact endpoint because the next Fast Move begins there. This is
a secondary presentation-boundary risk, not proof that the whole attack vanished.

## Reproduction and verification boundary

`RecordedFinishingMoveReviewTest` is an opt-in diagnostic using the production
Replay model and GameMaster reference data. It exports source/presentation
charges, Fast Move cycles, knockout tracks, associations, markers and scenes at
charge apex/impact and faint boundaries. Reverse seeking must reproduce the same
scenes; serialized source Articles and ZIP bytes must remain unchanged.

```sh
OVERDEX_FINISHING_REVIEW=/mnt/overdex-backup/archives/overdex-device-backups/2026-11-14-new-moondrop-review \
JAVA_HOME="$PWD/.jbr" ./gradlew :app:testDebugUnitTest --tests '*RecordedFinishingMoveReviewTest'
```

Passed: **1 diagnostic test, three archives, zero failures/errors/skips**. This
proves deterministic reconstruction and source preservation, not correct battle
recognition. Reports, selected source crop contact sheets and the test log are
in the backup directory above. No corresponding November 14 continuous recording
was located in Desktop or Videos/Overdex, so this review validates archived crops
and Replay model behavior rather than a frame-by-frame video comparison. No
production code changed, and no APK build or installation was needed.
