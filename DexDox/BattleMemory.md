# BattleMemory

`BattleMemory` is the central source of truth for every live battle. Its primary responsibility is to remember what is currently happening accurately.

## Role in the Pipeline

```
Observation Pipeline
        │
        ▼
BattleMemory (Live State)
        │
        ▼
BattleTimeline
```

- **Input**: Receives standardized data from the **Observation Pipeline**.
- **State Management**: Updates the current health, energy, and status of both teams.
- **Output**: Generates immutable `BattleEvent` objects to be stored in the **BattleTimeline**.

---

## Philosophy

- **Accurate Recall**: BattleMemory does not guess. It distinguishes strictly between **Observed Facts** (e.g., Pokémon confirmed) and **Inferred Data** (e.g., estimated energy).
- **Single Source of Truth**: Every UI component (like the Battle Overlay) and Decision Engine asks BattleMemory for information.
- **One-Way Flow**: Information flows from sensor to memory to display. The UI never tells BattleMemory what happened.

---

## Principles

1. **Facts first**: Prefer recording the event that produced a value over the value itself.
2. **No Impossible States**: Logic prevents invalid transitions (e.g., a fainted Pokémon returning to active status).
3. **Passive Presentation**: The overlay is a read-only mirror of the state stored in BattleMemory.


## Responsibilities

BattleMemory stores observations.

BattleMemory does not determine battle outcomes.

BattleMemory does not archive completed battles.

BattleMemory exists only while a battle is active.

Battle outcome determination belongs to BattleLifecycleAnalyzer.

Permanent storage belongs to ArchivedBattle.

Distribution belongs to EventBusRoute.
