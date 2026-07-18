# Future Ideas

This document is intentionally unfinished.

Nothing in this document is planned, scheduled, or guaranteed.

Its purpose is simple:

> Preserve ideas before they are forgotten.

Ideas graduate from this document into Architecture, Philosophy, Principles, or the Roadmap only after they survive experimentation.

---

# Candidate Architecture

Ideas that may eventually become permanent architecture.

## Event Payloads

Replace generic values with event-specific payloads once `BattleEvent` stabilizes.

---

## Columnar Anchor Clusters (CAC)

A proposed spatial observation model for dynamic interfaces.

Rather than treating every frame as an isolated bitmap, the Observation Engine establishes persistent spatial anchor clusters.

Each cluster represents a logical region rather than fixed pixels.

Possible applications:

- Scrolling Pokédex pages
- Battle observation
- Replay analysis
- Droidball observation
- Future observation sources

Purpose:

Provide a stable spatial reference system that survives scrolling, animation, and changing viewports.

---

## Divination Engine

Possible future layer responsible for reasoning during incomplete observations.

Information may exist in three states:

```
OBSERVED

↓

DIVINED

↓

RECONCILED

↓

OBSERVED
```

Observed

- Directly confirmed.

Divined

- Best explanation supported by current evidence.

Reconciled

- Previously inferred information that has become directly supported.

Possible future concepts:

- Integrity indicator
- Observation confidence
- Divination confidence
- Reconciliation visualization

Goal:

Remain useful during uncertainty without pretending certainty.

---

# Handheld Experience

Ideas related to the dedicated handheld identity.

## Handheld vs Android

Reduce transitions between:

- Overdex handheld
- Android operating system

Possible directions:

- Device-native search
- D-pad-first navigation
- Hardware text entry
- Quick filters
- Search shortcuts

---

## Maintenance Drawer

Future Service LCD drawer.

Ideas include:

- Guided calibration
- Droidball confidence behavior
- Dual-resolution Droidball
- Maintenance controls
- Observation wizard
- Image-under-box editing

---

## Hardware Identity

> The instrument may bend reality to improve understanding.

> It should never break its own identity.

---

# Presentation Experiments

Possible future interface concepts.

- Replay engine
- Observation logs
- Paper Mario-style battle visualization
- Digital coaching
- Trainer DNA visualization
- Visual language experiments

---

## HUDphones

Audio presentation layer for Overdex.

The Observation Layer remains the source of truth.

HUDphones becomes another presentation layer.

Possible callouts:

- Enemy identified.
- Fast move confirmed.
- Charged move ready.
- Shield recommended.
- Switch available.
- Super effective.

Accessibility possibilities:

- Blind and low-vision support
- Reduced visual attention
- Hands-free battle awareness

Guiding principle:

Observe.

Understand.

Communicate.

---

# Collection Ideas

## My Collection → My Binders

Current recommendation:

Do not rename My Collection immediately.

Ship it.

Let it mature.

When binders become a complete organizational system:

```
My Collection
└── Binders
```

The Pokédex documents Pokémon.

My Binders document the trainer's relationship with Pokémon.

---

## Terminology

Possible future rename:

```
OwnedPokemon

↓

Specimen
```

---

# Artificial Intelligence

Ideas involving AI reasoning remain experimental until supported by architecture.

Potential directions:

- Explanation engine
- Coaching
- Pattern discovery
- Long-term training insights

---

# Technical Notes

Ideas that should not be forgotten.

```text
TODO (Architecture)

PokedexFrame currently owns BattleMemory and Matchup analysis.

Long-term:
Move ownership into a ViewModel or controller.

PokedexFrame should remain responsible only for rendering and hardware behavior.
```

---

# Someday / Maybe

Ideas with no implementation plan.

These exist because interesting ideas are easy to lose.

Examples:

- Trainer DNA
- Droidball personality
- Alternate industrial designs
- Experimental interfaces
- New observation techniques

Some of Overdex's defining ideas began here.

This document exists so future ideas have somewhere to begin.

# Design Test

When designing a new Droidball behavior, ask:

Does this reveal observation?

If no...

Do not animate it.

Does this communicate confidence?

If no...

Do not animate it.

Does this teach the trainer something?

If no...

Do not animate it.

If the animation exists only because it is entertaining...

It does not belong.

The Observation Layer is the performance.

Droidball simply makes that performance visible.

Never allow a high-confidence recommendation to be built on low-confidence observations

Principle

We have confidence. We trade it for our users' trust.

Explanation

Confidence is the currency of Overdex. Every subsystem exists to increase confidence through observation, corroboration, provenance, and predictable behavior. That accumulated confidence is exchanged for the user's trust. If a feature cannot increase confidence, it should be reconsidered.