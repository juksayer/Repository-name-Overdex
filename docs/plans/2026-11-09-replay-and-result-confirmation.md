# Replay framing and deferred result-confirmation notes

Recorded November 9, 2026 from the user's request and two annotated screenshots.
**Status: replay visual changes implemented after the user's subsequent request
to proceed; result-confirmation questions remain deferred.** The visual scope
preserves the existing replay clock, attack motion, evidence and archives. See
the [verification record](../verification/2026-11-09-replay-framing.md) for completed
unit/native checks and installation on both phones. Detailed physical replay
inspection remains a separate acceptance boundary.

## Ask the trainer when the recording cannot settle a question

The user proposed asking for confirmation when automatic evidence cannot resolve
something the trainer knows, such as a tie or a forfeit. A trainer's answer is a
useful additional source; uncertainty does not have to remain unresolvable just
because the capture missed the decisive screen.

For implementation, keep **result** (win, loss, tie, unknown) separate from
**ending reason** (for example, opponent conceded or the player forfeited). A
recording can establish a win while leaving its reason unknown. Explain the
specific missing fact in plain language, rather than asking the trainer to
diagnose the recognition system.

The user's subsequent clarification keeps match records **read-only**. An answer
or disagreement must be a separate attributed note associated with the match and
the time it was supplied; it cannot rewrite recorded events, result, or ending
reason. Do not invent an exact event time that the trainer did not give. Show
both the recorded conclusion and the trainer's account when they disagree.

An optional post-battle question remains useful. During live play, the user's
requested opportunity is a short yes/no question during an actual shield choice
only while more than three source-adjusted seconds remain. A replay timer or old
queued reading cannot establish that opportunity. Timing, wording, conflict
handling, separate note storage and the question interface are not implemented.
See the [reader attention and ending policy](2026-11-09-reader-attention.md).

## Replay framing and move-icon perspective

The two user requests are charge-sequence framing and move-icon perspective.
Player Pokémon remain on the left, opponent Pokémon on the right.

1. **Anchor charge-sequence resizing at each Pokémon's highest point.** Its top
   stays fixed as it grows or shrinks; its bottom rises or lowers. Each keeps
   its own top anchor; this does not align both Pokémon to a shared height.
   Both sprites now use their actual nonzero-alpha top edge, including centered
   Fit letterboxing, as the resize pivot. Aspect ratio and existing attack
   rises/returns are preserved; anchoring removes resize drift without pinning
   the combatant's attack motion. Faint/defeat poses retain their center pivot.
2. **Bring the combatants slightly inward during the charged sequence.** Move
   the player's Pokémon a few pixels right and the opponent's a few pixels left.
   The existing cursor-driven charge focus remains 20 physical pixels right for
   the player and 10 left for the opponent, with the existing smooth transition
   and normal placement outside charge preparation. Pausing or reversing replay
   does not retain an unrelated focus pose.
3. **Keep attack icons in the space between the Pokémon.** A player move's type
   icon should land in front of the opponent sprite, on its side facing the
   player. An opponent move's icon should originate there too. Neither should
   appear to start or land behind the opponent. Fast and charged icons now use
   the opponent's measured facing edge with a 10 percent inset into its visible
   width, including Turtonator's horizontal-flip correction. Both paths share
   an upper-body height at the visible top plus 45 percent of visible height,
   scaled around the top pivot; short, wide art does not inherit a shoulder
   height above its body. The draw order is opponent, attack glyphs, player:
   icons appear in front of the opponent while incoming attacks remain masked
   by the player's opaque back-facing artwork.
   Paths include the combatants' focus offsets. Unconfirmed types retain neutral
   rings; an unattributed charged action remains centered.

Native acceptance checks cover the size transition in both directions, both
attack directions, sprite shapes and mirrored art. Physical replay inspection
remains a separate acceptance boundary; consult the verification record rather
than treating implementation as proof of those checks.

## Original visual references

Both supplied PNGs are retained byte-for-byte at their original 1080 × 2400
resolution under this plan's reference directory. These are annotated design
references, separate from app assets and recorded battle evidence.

| Reference | Original source | SHA-256 |
| --- | --- | --- |
| [Charge-sequence size and position](references/2026-11-09-replay/chargemovezoomreplay.png) | `/home/sean/Pictures/chargemovezoomreplay.png` | `80e84d2c0a6629353d577487024040bf6bb252ace81965b2306ed4b529345704` |
| [Move-icon perspective](references/2026-11-09-replay/movetypeiconsperspective.png) | `/home/sean/Pictures/movetypeiconsperspective.png` | `ab9ea940adb4bd2336d001fb380c4c26b74dae926dbb32033050565d6479b4d9` |
