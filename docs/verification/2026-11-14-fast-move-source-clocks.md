# Fast Move source clocks and the 2026 rules

The [Pokémon GO 2026 Trainer Battle update](https://pokemongo.com/news/pvp-updates2026?hl=en)
says damage and energy resolve at the end of a turn, switches precede damage,
and charged effects precede Fast Attacks finishing during the charged sequence.
These are game-rule expectations when that ruleset applies, not direct evidence
of a particular screen frame, exact server turn boundary, successful hit or KO.
The archived Sealeo example is a Team Leader match; applying every PvP rule to
that mode remains unverified.

The [PvPoke battle engine](https://github.com/pvpoke/pvpoke/blob/master/src/js/battle/Battle.js)
queues and processes simulated actions. Its algorithm and visual timeline are
reference implementations, not observations or authoritative server code. Its
[2022 Fast Move guide](https://pvpoke.com/articles/strategy/guide-to-fast-move-registration/)
predates the official 2026 change; use it cautiously for historical mechanics.
Overdex's bundled `gamemaster.json` reports timestamp `2026-06-18 01:53:01`
and lists Incinerate as five turns/2500 ms. This is the timestamp of the bundled
data, not proof those values are currently incorrect, nor evidence that any
particular Incinerate happened. Data freshness and ruleset version require a
separate audit before changing reference values.

## Concrete Replay evidence gap

`FastMoveUseEvidence.recipientAudioSupport` can pair a source-timed recipient
visual artifact with matching audio. `ReplayFastMoveSchedule` currently places
the existing decorative projectile's impact at that witness clock. In the
recorded Sealeo exchange, that presentation landing occurs before a subsequent
independent red-HP measurement; the independently verified empty bar follows.
The cue establishes a supported move under its existing guards, but does not
supply a measured damage-resolution timestamp. Neither the 2026 rule nor the
move's legal duration can by itself assign the move a KO.

`ReplayFastMoveAction.damageObservationTimesNanos` now exposes the unmodified
source clocks of HP damage observations attached to that scheduled move.
An empty set explicitly means no such attached observation, not that damage did
not occur. `impactAtNanos` remains presentation timing; source visual/audio
IDs and their clocks remain intact. This metadata is groundwork only: no Fast
Move-to-KO associator consumes it yet, and no landing time, faint, raw archive,
or live inference changes. The exact Sealeo regression asserts that its
paired visual/audio evidence has no attached damage observation. Controlled
schedule tests verify that attached damage clocks remain distinct from the
presentation clock. Switches, delayed evidence, unobserved attacks, continuous
or post-charge fast moves, and nonlethal hits still require separate checks in
the eventual association implementation.
