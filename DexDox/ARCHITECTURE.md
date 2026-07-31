# Overdex Architecture

This document describes the high-level architecture of Overdex.

Overdex is built as a layered system.

Each layer owns exactly one responsibility.

Higher layers build upon lower layers without changing or rewriting their responsibilities.

---

# Core Architectural Principle

> **Observe first. Remember second. Understand third. Present last.**

Every layer has a single responsibility.

Facts flow upward.

Interpretation never flows downward.

---

# System Architecture

```text
                    Presentation Layer
                           ▲
                           │
                   Intelligence Layer
                           ▲
                           │
                 Dynamic Knowledge Layer
                           ▲
                           │
                     Archive Layer
                           ▲
                           │
                     History Layer
                           ▲
                           │
                     Memory Layer
                           ▲
                           │
                  Observation Layer
                           ▲
                           │
                  Pokémon GO Battle
```

This hierarchy defines the permanent architecture of Overdex.

Each layer consumes the outputs of the layer beneath it.

---

# Layer Responsibilities

## Reference Knowledge Layer

Provides static Pokémon information.

Reference Knowledge answers: **"What is generally true?"**

Examples include:

- Pokédex
- Move database
- Type effectiveness
- Base stats

Reference Knowledge exists independently of any battle.

---

## Observation Layer

Status: Complete (Git #152–#160)

Purpose:

Observe the current battle.

Responsibilities:

- Screen capture
- OCR
- Recognition
- Observation Session Workspace

The Observation Layer never performs inference.

It answers only:

> "What can we confidently observe?"

---

## Memory Layer

Purpose:

Preserve observations from the current battle.

Memory transforms live observations into persistent battle memory.

Examples:

- Observation Sessions
- Battle Memory
- Current battle state

Memory represents the present.

---

## History Layer

Purpose:

Organize remembered events.

Responsibilities include:

- Battle Timeline
- Battle Events

History represents the past.

---

## Archive Layer

Purpose:

Preserve completed history.

Responsibilities include:

- Archived battles
- Long-term storage

Archive represents permanence.

---

## Dynamic Knowledge Layer

Purpose:

Establish the system's current best understanding of the battle.

Dynamic Knowledge answers: **"What do we currently believe about this battle?"**

Dynamic Knowledge is formed by combining Observations with Reference Knowledge.

Examples:

- Confirmed opponent species
- Confirmed move sets
- Known shield count

---

## Intelligence Layer

Purpose:

Derive meaning from Dynamic Knowledge.

Possible responsibilities:

- Energy estimation
- Move counting
- Shield tracking
- Matchup evaluation
- Recommendation engine

Intelligence never edits observations.

It only derives conclusions from trusted information.

---

## Presentation Layer

Purpose:

Communicate information to the player.

Examples:

- Overlay
- Battle summaries
- Timeline viewer
- Team analysis
- Pokédex interface

Presentation never creates facts.

It displays them.

---

# Observation Data Flow

```
Pokémon GO

      │

      ▼

Screen Capture

      │

      ▼

Recognition Modules

      │

      ▼

Observation Session Workspace

      │

      ▼

Memory Layer
```

The Observation Session Workspace is the integration point for all recognition modules.

Every recognizer contributes observations independently.

Consumers read from the shared workspace.

---

# Battle Data Flow

```text
Observation Layer
        │
        ▼
Memory Layer
        │
        ▼
History Layer
        │
        ▼
Archive Layer
        │
        ▼
Dynamic Knowledge Layer  ◄───  Reference Knowledge Layer
        │
        ▼
Intelligence Layer
        │
        ▼
Presentation Layer
```

Each stage enriches information without modifying earlier observations.

---

# Architectural Rules

## Observation Never Infers

Recognition modules detect visible facts.

They do not estimate.

They do not recommend.

They do not predict.

---

## Memory Never Reinterprets

Memory records observations exactly as they were received.

Memory preserves facts.

---

## History Never Changes

Historical events are immutable.

Corrections create new events rather than modifying existing ones.

---

## Intelligence Never Rewrites Facts

Intelligence explains observations.

It never replaces them.

Confidence belongs to Intelligence—not Observation.

---

## Presentation Speaks Last

The user interface is the final consumer.

Every recommendation should be traceable back to observed evidence.

---

# Design Philosophy

Every architectural layer exists to reduce coupling.

Observation modules should not know about intelligence.

Intelligence should not know how OCR works.

Presentation should not know how recognition is implemented.

Each layer communicates through well-defined data models.

This allows individual subsystems to evolve independently.

---

# Architectural Guarantees

Overdex guarantees:

- Observation produces facts.
- Memory preserves facts.
- History organizes facts.
- Archive preserves facts.
- Intelligence derives meaning.
- Presentation communicates meaning.

Every layer has one responsibility.

Every responsibility has one owner.

---

# Architectural Decisions (ADR)

Significant architectural decisions are recorded in the [Thinking Chair](file:///home/sean/AndroidStudioProjects/Overdex/DexDox/Architecture/ThinkingChair.md). Refer to this document to understand the context, alternatives, and reasoning behind major turning points in the system's evolution.


---

# Guiding Principle

> **Observe only what appears on the screen. Infer nothing. Preserve everything.**

Everything else in Overdex is built upon that foundation.

# Navigation Ownership Invariant

Navigation operations may only mutate navigation state while the requesting navigation context 
still owns the request. Stale callbacks, delayed input, duplicated intents, replayed events, or any
other request that has outlived its ownership must be ignored rather than allowing navigation state
to become inconsistent.