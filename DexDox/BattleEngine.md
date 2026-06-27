# BattleEngine

The BattleEngine coordinates every live battle within Overdex.

It is responsible for transforming observations into battle state.

The BattleEngine does not perform OCR.

The BattleEngine does not render the UI.

The BattleEngine exists to answer one question:

**"What is happening in this battle?"**

---

# Philosophy

The BattleEngine should never guess without evidence.

It should prefer incomplete truth over complete fiction.

Observed information always has priority over inferred information.

Every decision made by the BattleEngine should be explainable.

---

# Responsibilities

The BattleEngine is responsible for:

* Maintaining BattleMemory
* Processing observations
* Recording BattleEvents
* Coordinating battle state
* Managing battle lifecycle
* Driving overlay updates
* Supplying recommendation systems

The BattleEngine should never directly render UI.

---

# Information Flow

```
OCR
      │
      ▼
Observation Layer
      │
      ▼
BattleEngine
      │
      ▼
BattleMemory
      │
      ├── Overlay
      ├── Timeline
      ├── Recommendations
      ├── Researcher Mode
      └── Analytics
```

The BattleEngine is the bridge between observation and presentation.

---

# Battle Lifecycle

A battle progresses through several stages.

Idle

↓

Battle Started

↓

Enemy Observed

↓

Team Discovery

↓

Active Battle

↓

Switches

↓

Energy Updates

↓

Faints

↓

Battle Ended

↓

Reset

Each transition should be represented by BattleEvents whenever possible.

---

# Inputs

The BattleEngine may receive information from:

* OCR
* HP detection
* Move banner detection
* Switch detection
* Manual input (debug)
* Prototype simulation

All inputs should be normalized before updating BattleMemory.

---

# Outputs

The BattleEngine provides information to:

* BattleMemory
* Overlay
* Battle Timeline
* Recommendation Engine
* Debug tools
* Future replay system

No output should modify BattleMemory directly.

BattleMemory remains the single source of truth.

---

# State Ownership

The BattleEngine owns battle progression.

BattleMemory owns battle state.

Overlay owns presentation.

This separation should remain strict.

---

# Observed vs Inferred

The BattleEngine should distinguish between:

Observed

Information directly detected.

Examples:

* Pokémon species
* Fast move observed
* Charged move observed
* Shield used

Inferred

Information calculated from observations.

Examples:

* Estimated energy
* Predicted charged move
* Possible backline
* Recommended switch

The player should always be able to distinguish observation from inference.

---

# Design Principles

The BattleEngine should be:

* Deterministic
* Testable
* Explainable
* Modular
* Observable
* Recoverable

Given the same sequence of observations, it should always produce the same battle state.

---

# Error Handling

Missing information should not stop the engine.

If an observation is uncertain:

* preserve previous state
* lower confidence
* wait for additional observations

Never fabricate information simply to keep the battle moving.

---

# Prototype Philosophy

Prototype simulations exist to verify architecture.

Prototype code should prove:

* BattleMemory updates correctly.
* Overlay responds correctly.
* BattleEvents are recorded correctly.

Prototype code should never become production logic.

---

# Future Responsibilities

As Overdex evolves, the BattleEngine may support:

* Energy estimation
* Move counting
* Battle confidence
* Matchup evaluation
* Recommendation generation
* Replay support
* Tournament tracking
* AI-assisted analysis

These systems should consume BattleMemory rather than replacing it.

---

# Design Goal

The BattleEngine should become invisible.

When functioning correctly, every system in Overdex should feel as though it is reacting naturally to the battle.

The BattleEngine quietly observes.

It quietly remembers.

It quietly coordinates.

Everything else simply reacts.
