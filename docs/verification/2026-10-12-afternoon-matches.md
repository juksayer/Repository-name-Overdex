# October 12 afternoon match review and navigation index

This is an evidence review and documentation/skill update. It does not claim a
new application fix, live HUD acceptance, or complete Fast Move accounting.
Registration remains paused. No phone calibration was changed and no APK was
installed during this review.

## Sources and validation

- Device: Moondrop MD-PH-001, ADB serial `MDPH00124112500414`.
- Installed package last-update time: October 12, 2026, 12:37:41. The prior build
  and installation are documented in
  [capture fairness and calibration](2026-10-12-capture-fairness-and-calibration.md).
- Copied the two new internal archives with `run-as`; originals remain on the
  phone. The portable Download/Odxmatches compact exports were also inventoried.
- Ran `inspect_odxmatch.py --verify-artifacts` on both internal archives. Their
  manifests, article totals, and every declared artifact hash passed.
- Inspected typed Articles and cited/source images. Retained a few source images,
  the profile snapshot, calculated metrics, and hashes in
  [evidence/2026-10-12-afternoon](evidence/2026-10-12-afternoon).
- Times below are seconds from the earliest monotonic timestamp in each record,
  not from GO. Approximate screenshot positions use source-file write times;
  these do not establish an exact capture or result timestamp.

## What reached the Timeline

| Measure | 14:23 archive | 14:28 archive |
| --- | ---: | ---: |
| Internal filename | `2026-10-12_14-23-37.odxmatch.zip` | `2026-10-12_14-28-32.odxmatch.zip` |
| Match ID | `365a76ac-356d-442b-a6e8-71ed66418a66` | `8f7a92a0-d38f-47f2-aef2-bfb97c9408f9` |
| Bytes | 5,837,901 | 8,336,094 |
| Articles | 1,707 | 2,303 |
| Verified artifacts | 398 | 575 |
| Recording span | 105.014 s | 144.052 s |
| Accepted Match Start | 21.317 s | 23.260 s |
| Canonical player Fast Move uses | 4 | 3 |
| Canonical opponent Fast Move uses | 19 | 19 |
| Player HP measurements | 111 | 109 |
| Opponent HP measurements | 44 | 25 |
| Faint Articles | 2 | 3 |
| MatchEnded / result Articles | 0 | 0 |

Both starts use `COUNTDOWN_AND_COMBAT_START_INFERENCE`: a witnessed countdown
glyph plus later combat corroboration. These are explicitly inferred boundaries;
neither record contains a witnessed GO glyph.

Both records attribute **Sneasel, Sealeo, and Vaporeon to the opponent** and
**Turtonator and Camerupt to the player**. Gourgeist appears in configured-team
context; that does not establish that it entered these battles.

First observed opponent identities:

| Identity | 14:23 capture time | 14:28 capture time |
| --- | ---: | ---: |
| Sneasel | 19.767 s, announcement | 22.718 s, announcement |
| Sealeo | 43.017 s, badge | 37.219 s, badge |
| Vaporeon | 65.539 s, announcement | 55.145 s, announcement |

In the first recording, the initial Sneasel announcement took **4.38 seconds**
from `perceivedAt` to `recordedAt`. That alone misses the 1.5-second live identity
target. Correct identity in an archive is not proof of an immediate HUD update.
Sealeo's first badge testimony in that record arrived after 1.19 seconds;
Vaporeon's announcement after 0.96 seconds. These are publication delays, not
measurements of the first frame in which the Pokémon became visible.

Faints are attached to the departed combatant:

- 14:23: Sneasel at 44.595 s, Sealeo at 66.434 s.
- 14:28: Sneasel at 35.221 s, Sealeo at 58.486 s, player Camerupt at 61.730 s.

The first record correctly retains Sneasel's faint after Sealeo's species Article
has arrived. Balls and shields now produce evidence, although intermediate shield
counts are incomplete and counts read after the battlefield disappears need
separate scrutiny.

## HP coverage and the missing player attacks

The opponent bar is the recipient-side evidence for **our** attacks. It has long
measurement gaps while the player's bar continues producing samples:

- 14:23: opponent HP gap 28.621–38.650 s and 43.820–70.070 s.
- 14:28: opponent HP gap 29.469–38.687 s and 40.916–58.711 s; no opponent HP
  measurement after 58.711 s, despite later visible combat.

The saved opponent search region maps to approximately **X 312–576, Y 381–863**
on the 672×1493 retained source frame. The actual opponent bar can move beyond
both horizontal edges:

- [Camerupt attacking Sealeo](evidence/2026-10-12-afternoon/14-23-050.png): bar
  approximately X 395–587. Its right end extends beyond the saved region.
- [Sealeo lunging](evidence/2026-10-12-afternoon/14-28-040.png): bar approximately
  X 264–457. Its left end extends beyond the region.

These are actual captured game frames, not a CRT calibration rendering. The
clipping is directly visible and explains why this crop cannot reliably retain
the complete border during these attacks. The player's bar remains within its
own separate search region. The black rectangle is the excluded overlay area in
the captured frame; it is above the bars in these examples.

There is also a **tracker hypothesis to reproduce before changing thresholds**.
The opponent measurements near 40.916 and 58.711 s have widths of 159 and 160
pixels, while the complete visible bar is about 192 pixels. The edge-recovery
logic in `ActiveHpBarMeasurer` can search for an interior endcap near a clipped
crop edge; `ActiveHpBarTracker` then accepts widths only within 90–110% of the
initial lock. A shortened lock would reject the later complete bar even when it
fits inside the crop. The [later Vaporeon frame](evidence/2026-10-12-afternoon/14-28-085.png)
shows a complete bar within the region, yet no later opponent measurements were
recorded. This is supporting evidence for the hypothesis, not a completed
pixel-level reproduction of the algorithm.

The canonical use counts are incomplete and may also include duplicate
corroboration. For example, the second record emits separate Vaporeon uses at
70.459/70.690 s and 71.814/72.015 s from pulse/cadence versus damage evidence.
Review capture-time fusion and late delivery before treating those close events
as separate attacks. A count of 19 is not a verified total of 19 actual moves.

## Move names and energy

- Player Incinerate is supplied by the configured team.
- Vaporeon Water Gun is identified from its unique legal reference move pool.
- Sneasel's first-match hypothesis changes from Ice Shard to Feint Attack on
  sparse cadence evidence. The second record proposes Feint Attack.
- Sealeo has no accepted Fast Move name in these records.
- The second record recognizes Turtonator's Brutal Swing and records its
  35-energy spend. There are 4 and 11 `FastMoveEnergyDerived` Articles respectively.

This supports displaying recorded, qualified energy only. Missing player attacks,
possible duplicate opponent attacks, and unresolved/provisional identity prevent
reliable battle advice based on a precise current energy balance.

## The result was visible but the result watcher stayed closed

Both matches visibly end in victory:

- [First match YOU WIN](evidence/2026-10-12-afternoon/14-23-085.png).
- [Second match YOU WIN](evidence/2026-10-12-afternoon/14-28-100.png).

Neither archive has result-crop activity or a MatchEnded Article. In the current
`MatchOutcomeCaptureGate`, one remaining team member plus measured red HP opens
a result check, as does a witnessed depleted team against a surviving team.
The second match's opponent HP measurements stop at about 92% while Vaporeon is
the last member. Its later depleted state is not measured, and no final ball
decrease opens the alternative route. The result watcher therefore has no
accepted trigger even while YOU WIN remains plainly visible.

The next result change needs a bounded fallback for lost/stale HP after the final
team member is established. It should enable observation of the result text;
it must not equate one living Pokémon on either team with a declared winner.

No QTE milestone inference is recorded in either match despite raw motion pulses.
There is not enough accepted cue evidence here to replay Nice/Great/Excellent
as observed facts.

## Calibration and retention findings

- Compared the selected profile with the snapshot from the earlier October 12
  review. Species, team-info, and resource positions have no new 84-pixel shift;
  tiny differences are floating-point serialization. The announcement region
  was moved upward by 0.015 normalized Y (36 physical pixels), and a player
  shield region is now explicitly stored. Do not interpret those separate edits
  as all identity boxes drifting.
- The latest calibration reference automatically refreshed from this afternoon's
  battlefield. It is no longer only the previously seeded October 10 image.
- The compact archives contain no retained HP source frames. The app's artifact
  store does contain sparse full battlefield PNGs, including the examples above.
  `MatchArchiveArticleSelector` omits raw artifact Articles that are not cited by
  retained observations. Missing PNGs in a compact archive are therefore not
  sufficient evidence of missing capture.
- Late announcement OCR reads `-INTUEOR/` near return to Overdex. This is a
  capture-exclusion concern to inspect against its cited crop, not a Pokémon or
  move observation. Do not use it to infer any battle state.

## Navigation and skill changes

Preserved the supplied index as
[source material](../reference/overdex-terms-and-component-inventory.source.md)
and added the [verified navigation index](../reference/overdex-navigation-index.md).
The Overdex skill routes terminology and ownership questions there. Its archive
guide now distinguishes compact retention from witness operation and configured
rosters from observed active species.

Corrections include nonexistent method names, the custody/artifact ordering,
append-order versus capture-time ordering, service/session ownership, the actual
HUD fields, and the different roles of use counting, move identification, and
energy inference. No registration work was resumed.

Documentation validation: 164 local links resolved, 17 documented method
declarations were checked against source, the supplied inventory copy is
byte-identical, and retained evidence hashes were verified. The skill passed
`quick_validate.py`; `git diff --check` passed. No application code changed in
this review, so no new Android build or installation was performed.

## Next implementation checks

1. Reproduce the clipped/shortened opponent-bar lock on these retained frames;
   support the measured horizontal excursion and reject partial locks without
   silently rewriting the user's calibration profile.
2. Check late pulse/damage fusion for duplicate uses; compare a controlled idle
   interval and known attacks before trusting energy totals.
3. Give final-team result observation a bounded fallback when HP tracking goes
   stale. Verify it on both retained YOU WIN frames and on ongoing battles.
4. Recheck species publication latency and live HUD response on the next recording;
   all three correct opponent names in an archive do not establish live success.
