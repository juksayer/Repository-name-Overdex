# Overdex Battle Architecture

This document describes the high-level data flow and system hierarchy for the Overdex battle support system.

## Data Pipeline

```
Observation Pipeline
        │
        ▼
BattleMemory
        │
        ▼
BattleTimeline
        │
        ▼
ArchivedBattle
        │
        ▼
Battle History
        ├── Battle Summary
        ├── Statistics
        └── Tournament Tools
```

---

## Architectural Components

### 1. Observation Pipeline
The entry point for all sensor data (OCR, Audio, Manual, Prototype). Its sole responsibility is to detect facts from the game and produce standardized observations.

### 2. BattleMemory
The central source of truth for the current live battle. It maintains the "Now" state—active Pokémon, health, energy, and shield counts. Whenever a state transition occurs, it records a fact.

### 3. BattleTimeline
A chronological journal of immutable `BattleEvent` objects. It serves as the canonical record of everything that happened during the battle, stored in order.

### 4. ArchivedBattle
A finalized snapshot of a completed battle engagement. It preserves the full `BattleTimeline` and timing metadata, serving as the permanent historical record.

### 5. Battle History
The management layer for archived matches. It acts as the data provider for all downstream analysis modules.

---

## Downstream Consumers

*   **Battle Summary**: A factual review of a single match, derived directly from an `ArchivedBattle` timeline.
*   **Statistics**: Long-term performance tracking and trend analysis (e.g., win rates, lead success).
*   **Tournament Tools**: Specialized tracking and reporting for competitive environments.

---

## Design Philosophy

**Facts before conclusions.**

Overdex functions as the player's "second brain." The architecture is designed to capture objective facts accurately so that intelligence can be derived by downstream systems later.

## Battle Lifecycle (Current Design)

The Observation Pipeline records facts only.

BattleMemory and BattleTimeline do not determine battle outcomes.

### Battle Start

A battle officially begins when the Pokémon GO countdown reaches:

3

2

1

This is considered the highest-confidence indication that a battle has started.

### Battle End

A battle may terminate in one of three ways:

* Victory
* Defeat ("Good Effort!")
* Abandoned (unexpected interruption)

Victory and defeat are determined through multiple observations when possible.

Possible observations include:

* Victory/Defeat screen
* Victory/Defeat audio
* Remaining Pokémon
* Timeline consistency
* Other future observations

No single observation should be treated as absolute truth.

### Observation Degradation

If screen capture is lost, Overdex continues operating using remaining observation sources such as audio.

Confidence decreases appropriately, but battle observation continues whenever possible.

### Lifecycle Flow

Screen Capture

↓

Observation Pipeline

↓

BattleMemory

↓

BattleTimeline

↓

BattleLifecycleAnalyzer

↓

ArchivedBattle

↓

EventBusRoute

↓

Battle History

Statistics

Review Kit

Tournament Tools

Researcher Mode

Future Modules

ArchivedBattle is immutable.

All downstream modules consume the same archived battle.


# Battle Lifecycle

The Observation Pipeline records facts only.

BattleMemory and BattleTimeline never determine battle outcomes.

Their responsibility ends with recording observations.

---

## Battle Start

A battle officially begins when Pokémon GO displays the battle countdown:

3

2

1

This is considered the highest-confidence indication that a battle has started.

---

## Battle End

A battle may terminate in one of three states:

* Victory
* Defeat
* Abandonment

Victory and defeat should be determined using multiple observations whenever possible.

Possible observations include:

* Victory / Defeat screen
* Victory / Defeat audio
* Timeline consistency
* Remaining Pokémon
* Future observation sources

No single observation should be considered absolute truth.

---

## Observation Degradation

Overdex should continue operating whenever possible.

If Screen Capture is lost, the Observation Pipeline should continue using Audio.

Loss of one observation source reduces confidence, but should not immediately terminate battle analysis.

Every observation source is optional.

Confidence reflects the amount and quality of available evidence.

---

## Battle Lifecycle Flow

Screen Capture

↓

Observation Pipeline

↓

BattleMemory

↓

BattleTimeline

↓

BattleLifecycleAnalyzer

↓

ArchivedBattle

↓

EventBusRoute

↓

Battle History

Statistics

Review Kit

My Collection

Tournament Tools

Researcher Mode

Future Modules

---

## ArchivedBattle

ArchivedBattle is immutable.

Every downstream module consumes the same ArchivedBattle.

No module should modify archived battle data.

All interpretation should be performed by the consuming module or dedicated analyzers.
