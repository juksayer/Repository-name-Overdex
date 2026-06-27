# BattleMemory

BattleMemory is the central source of truth for every battle.

It stores only information that has been observed or confidently inferred during a battle.

BattleMemory does not perform OCR, audio recognition, recommendations, or UI rendering.

BattleMemory is not trying to predict the battle. It is trying to remember it accurately.

Its purpose is to remember.

---

## Philosophy

Every battle component should ask BattleMemory for information.

BattleMemory should never ask the overlay what happened.

Information flows one direction.

Battle Systems
↓
BattleMemory
↓
Overlay

---

## Responsibilities

BattleMemory owns:

- Enemy team
- Friendly team
- Active enemy Pokémon
- Active friendly Pokémon
- Remaining Pokémon
- Shield counts
- Estimated energy
- Observed fast moves
- Observed charged moves
- Battle timeline
- Switch history
- Battle start/end state

BattleMemory does NOT own:

- OCR
- MediaProjection
- Audio recognition
- Overlay drawing
- Recommendations
- Type effectiveness
- Move timing algorithms

---

## Information Hierarchy

Overdex distinguishes between facts and conclusions.

Observed

Examples:

- Enemy Pokémon appeared.
- Fast move animation occurred.
- Charged move banner appeared.
- HP decreased.
- Pokémon fainted.
- Shield used.

These should always be preferred.

---

Inferred

Examples:

- Estimated energy.
- Possible charged moves.
- Predicted backline.
- Recommended swap.

Inference should never overwrite observations.

---

## Enemy Pokémon Lifecycle

Unknown

↓

Observed

↓

Active

↓

Inactive

↓

Active again

↓

Fainted

↓

Battle End

Each state transition should occur only once.

Impossible state transitions should never occur.

Examples:

Fainted → Active ❌

Unknown → Fainted ❌

---

## EnemyPokemonMemory

Each enemy Pokémon remembers:

Species

Alive

Estimated Energy

Observed Fast Moves

Observed Charged Moves

Times Seen

Active Status

Additional fields should only be added if they represent battle memory, not UI state.

---

## Overlay Rules

The overlay never owns data.

The overlay receives BattleMemory and renders it.

The overlay must never calculate battle information.

---

## Update Sources

BattleMemory may receive updates from:

OCR

Audio Recognition

Manual User Input

BattleMemoryUpdater

Future parsers

Every update should modify BattleMemory instead of directly modifying UI.

---

## Design Principles

One source of truth.

Observation before inference.

UI reflects state.

State never reflects UI.

Small updates.

Visible updates.

No impossible states.

No duplicated ownership.
