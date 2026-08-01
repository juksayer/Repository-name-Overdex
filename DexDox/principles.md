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

## LCD Display


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

PokedexFrame should not own application state.

Long-term:
Active modules own their data and behavior.
PokedexFrame owns only the instrument shell.

Responsibilities include:

- Rendering the physical instrument
- Routing hardware controls
- Hosting the CRT workspace
- Hosting the Instrument LCD
- Rendering hardware indicators
- Droidball presentation

PokedexFrame never owns battle state,
knowledge, observations, recommendations,
or module-specific logic.

It presents the currently active module.

The Instrument LCD is a presentation surface.

Its contents are owned by the active module,
not by PokedexFrame.
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

Recommendations should reflect the trainer, not just the metagame.

Cognitive Load Principle

During a battle, assume the trainer is operating at or near maximum cognitive load. Every feature must justify its presence by reducing the amount of information the trainer must actively remember, infer, or compute. Overdex exists not to present more information, but to carry part of the battle's mental workload so the trainer can remain focused, adaptable, and in flow.
Trainer Effort

Trainer effort is an architectural metric.

Every architectural decision should ultimately reduce one or more of the following:

Physical effort
Cognitive effort
Time
Repetition
Uncertainty

Architectural improvements that do not reduce trainer effort should justify themselves through another constitutional principle (such as correctness, safety, or maintainability).

Constitutional Principle — Trainer Effort

Trainer effort is an architectural metric.

Every architectural decision should ultimately reduce one or more of the following:

Physical effort
Cognitive effort
Time
Repetition
Uncertainty

Architectural improvements that do not directly reduce trainer effort shall justify themselves through another constitutional principle, including but not limited to:

Correctness
Safety
Reliability
Maintainability
Trustworthiness

Trainer effort is not the only architectural metric.

It is one of the primary architectural metrics.

Constitutional Principle — One Unit of Uncertainty

Every manual interaction shall resolve exactly one uncertainty.

Manual interactions exist to increase certainty, not merely advance workflow.

Good:

Unknown species
↓
Choose Bulbasaur
↓
Species known

Good:

Unknown fast move
↓
Choose Vine Whip
↓
Fast move known

Poor:

Species known
↓
Next
↓
Confirm
↓
Save

Those interactions advance the workflow without increasing certainty.

They should be removed or justified.

Architectural Interpretation

This principle naturally maps onto the existing Overdex architecture.

Observation resolves observational uncertainty.

Knowledge resolves interpretive uncertainty.

Intelligence resolves decision uncertainty.

Presentation communicates certainty.

Workflow should request trainer intervention only for uncertainty the system cannot reasonably resolve.

Evaluation

Every proposed feature should answer two independent questions.

Architectural Evaluation

Did the implementation improve:

Correctness?
Reliability?
Maintainability?
Ownership?
Trustworthiness?
Trainer Evaluation

Did the implementation reduce:

Time?
Physical effort?
Cognitive effort?
Repetition?
Uncertainty?

Both evaluations should succeed.

Architecture alone is insufficient.

Reduced effort alone is insufficient.

The system should improve both whenever reasonably possible.

Constitutional Guardrail

No metric of trainer effort may increase without a documented architectural justification.

If a proposal introduces:

additional screens
additional button presses
additional decisions
additional confirmations
additional waiting

it must explicitly identify the constitutional principle that justifies the increased effort.

Otherwise, the change should not be accepted.
Restoration Principle

When restoring existing functionality, implementation shall first restore the original architectural contract before considering redesign, optimization, or enhancement.
Interpretation Principle

Evidence may be interpreted exactly once at each architectural boundary.

Monotonicity Principle

Each architectural layer shall reduce uncertainty without increasing it.

Architectural layers exist to reduce uncertainty. User interfaces exist to reduce trainer effort. Neither shall increase the responsibility of the other.
Work orders should minimize interpretation.

If a reviewer can reasonably ask,

"What exactly counts as redundant?"

Domain Vocabulary Ownership

Every module owns its own vocabulary.

A significant domain term introduced by one module becomes part of that module's language and shall not be reused as the primary name of a component in another module.

Modules should communicate their ownership through language rather than package structure.

A contributor should be able to identify a file's domain from its filename along
Vocabulary Uniqueness

When introducing a new module, prefer a new domain-specific noun over reusing an existing architectural noun.