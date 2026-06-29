# BattleEngine

The `BattleEngine` coordinates the entire data lifecycle within Overdex, ensuring that raw observations are correctly transformed into a structured record of combat.

## Data Lifecycle

```
Observation Pipeline
        │
        ▼
BattleEngine
        │
        ▼
BattleMemory
        │
        ▼
BattleTimeline
        │
        ▼
ArchivedBattle
```

---

## Responsibilities

- **Coordination**: Managing the battle lifecycle from `BATTLE_STARTED` to `BATTLE_ENDED`.
- **Normalization**: Translating varied inputs (OCR, Audio, Manual) into stable internal state changes.
- **Auditing**: Ensuring that every meaningful transition is recorded as a fact in the chronological timeline.
- **Persistence Packaging**: Facilitating the transition from live, mutable memory to a permanent, immutable `ArchivedBattle` snapshot.

---

## Intelligence Layers

The engine provides data to analytical layers that interpret the battle without modifying the record:
1. **Matchup Engine**: Evaluates the tactical relationship between the active Pokémon.
2. **Decision Engine**: Translates matchup data into high-level player recommendations.

---

## Design Philosophy

**Invisible decision support.**

The engine quietly observes and remembers so the player can focus on gameplay. It surfaces only the information that reduces cognitive load, answering the core question: **"What decision does the player need to make right now?"**
