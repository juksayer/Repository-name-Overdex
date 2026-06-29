Screen Capture
│
▼
Observation Pipeline
│
▼
BattleMemory
│
▼
BattleLog
│
├── Overlay
├── Statistics
├── Team Builder
├── Pivot
├── Researcher Mode
└── Battle History


The BattleEngine may know everything. The overlay should say almost nothing.

==========================

# Overdex Battle Architecture

```
Screen Capture
        │
        ▼
Observation Pipeline
        │
        ▼
BattleMemory
        │
        ▼
BattleEvent
        │
        ▼
BattleTimeline
        │
        ├── Battle Timeline UI
        ├── Battle Summary
        ├── Decision Engine
        ├── Overlay
        ├── Statistics
        ├── Team Builder
        ├── Researcher Mode
        └── Battle History
```

## Responsibilities

### Observation Pipeline

Detects facts from the game.

It observes.

Nothing more.

---

### BattleMemory

Maintains the current state of the active battle.

It knows what is currently happening.

Whenever something meaningful changes, it records a BattleEvent.

---

### BattleEvent

An immutable record of a single observed fact.

Examples:

* Battle Started
* Pokémon Identified
* Charged Move Thrown
* Shield Used
* Pokémon Switched
* Battle Ended

BattleEvents never perform inference.

They only describe what happened.

---

### BattleTimeline

A chronological journal of BattleEvents.

It is intentionally "dumb."

Its only responsibility is preserving the ordered history of the battle.

---

### Consumers

Everything else reads from the BattleTimeline.

No downstream module should maintain its own duplicate battle history.

Examples include:

* Battle Timeline UI
* Battle Summary
* Decision Engine
* Overlay
* Statistics
* Team Builder
* Researcher Mode
* Battle History

---

## Design Philosophy

Observe once.

Record facts.

Infer later.

Present only what matters.

The Battle Engine may know everything.

The Overlay should say almost nothing.


Architecture Revision — Battle Timeline

The original architecture treated BattleLog as the primary record of a battle.

As the project evolved, BattleEvent and BattleTimeline were introduced to separate
current battle state from chronological battle history.

BattleMemory now owns the current state of the battle.

BattleTimeline owns the immutable sequence of observed facts.

This separation allows multiple systems—Battle Summary, Decision Engine,
Overlay, Statistics, and future analysis tools—to consume the same event stream
without maintaining duplicate history.

Architecture Revision — Battle Timeline

The original architecture treated BattleLog as the primary record of a battle.

As the project evolved, BattleEvent and BattleTimeline were introduced to separate
current battle state from chronological battle history.

BattleMemory now owns the current state of the battle.

BattleTimeline owns the immutable sequence of observed facts.

This separation allows multiple systems—Battle Summary, Decision Engine,
Overlay, Statistics, and future analysis tools—to consume the same event stream
without maintaining duplicate history.

/ Future:
// Battle statistics displayed here will come from Battle History.
// Species data and career data remain separate.

High-impact conclusions should be based on multiple independent observations whenever possible