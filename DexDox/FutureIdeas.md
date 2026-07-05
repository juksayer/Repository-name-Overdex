Consider replacing generic value with event-specific payloads once BattleEvent stabilizes

## Handheld vs Android Interaction

The search keyboard currently transitions the user from the Overdex handheld experience into a standard Android interaction.

This is not inherently a problem, but it creates two different interaction models:

- Overdex handheld
- Android touch input

The more of Overdex that can be navigated with the D-pad and physical controls, the longer the player remains inside the handheld metaphor.

This should be treated as a long-term design direction rather than an immediate implementation task.

Future exploration:
- Search without immediately invoking the Android keyboard.
- D-pad-first browsing.
- Device-native text entry concepts.
- Quick filters or shortcuts that reduce the need for typing.

/*
──────────────────────────────────────────────────────────────────────────────
Divination Engine Philosophy
──────────────────────────────────────────────────────────────────────────────

Overdex should distinguish between information that is OBSERVED and
information that is DIVINED.

Observed:
Directly verified from the game state.
Examples:
- OCR identifies the opponent.
- HP bar is visible.
- A charge move animation is seen.

Divined:
Estimated from game mechanics and prior observations.
Examples:
- Estimated opponent energy after OCR interruption.
- Predicted fast move count.
- Expected HP after simulated turns.

The goal is not to pretend certainty.

The goal is to maintain the best current explanation of the battle while
clearly communicating how that explanation was obtained.

Future UI ideas:
• Integrity Indicator (Observed / Divined / Reconciled)
• Color-coded certainty
• DroidBall may describe values as "Divined" rather than "Observed"

Possible State Flow:

    OBSERVED
        ↓
    DIVINED
        ↓
    RECONCILED
        ↓
    OBSERVED

Where:

Observed
The value is currently visible or directly confirmed.

Divined
The value is inferred using battle simulation, timing,
and known Pokémon GO mechanics.

Reconciled
New observations corrected a previously divined value,
bringing the internal model back into agreement with reality.

Design Principle:

    Overdex does not remember every piece of evidence.

    Overdex remembers the single best current explanation
    of the battle.

The battle engine should be honest about uncertainty without interrupting
the player. The user decides how much to trust divined information.
*/