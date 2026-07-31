# Observation Layer

**Status:** Complete (Git #152–#160)

---

## Purpose

The Observation Layer is the first stage of the Overdex battle intelligence pipeline.

Its responsibility is to observe the Pokémon GO battle screen, extract factual information, and package those observations into a structured session workspace.

The Observation Layer **does not** make decisions, predict outcomes, or recommend actions.

It answers only one question:

> "What can we confidently observe right now?"

---

# Design Philosophy

The Observation Layer is intentionally passive.

It records observations without interpretation.

Higher layers are responsible for understanding meaning.

This separation keeps recognition deterministic and allows inference systems to evolve independently.

---

# Responsibilities

The Observation Layer is responsible for:

- Capturing battle screenshots
- Running OCR and recognition
- Identifying visible battle information
- Packaging observations
- Maintaining the active Observation Session Workspace

It is **not** responsible for:

- Battle recommendations
- Energy prediction
- Opponent modeling
- Battle history
- Strategy
- Team analysis

---

# Observation Pipeline

```text
Pokémon GO

      │

      ▼

Screen Capture

      │

      ▼

Recognition

• Species
• Fast Move
• Charged Moves
• Shadow Bonus
• CP
• Future recognizers...

      │

      ▼

Observation Session Workspace

      │

      ▼

Higher Layers
```

---

# Observation Session Workspace

The Observation Session Workspace represents everything currently known about the active battle.

It acts as a shared source of truth for later systems.

Recognizers contribute observations into the workspace rather than communicating directly with one another.

This allows recognizers to remain independent and modular.

---

# Current Recognition Modules

As of Git #160, the Observation Layer includes support for:

- Species recognition
- Combat Power recognition
- Fast Move recognition
- Charged Move A recognition
- Charged Move B recognition
- Shadow Bonus recognition

Additional recognizers can be added without changing the overall architecture.

---

# Production Observers

Recognition is performed by independent observers.

Each observer owns exactly one observable aspect of the battle.

Examples include:

- SpeciesObserver
- CombatPowerObserver
- FastMoveObserver
- ChargedMoveObserver
- ShadowStatusObserver
- HealthObserver
- ShieldObserver

Observers publish observations into the Observation Session Workspace.

Observers do not communicate directly with one another.
---

# Observation Lifecycle

1. Battle begins.
2. Observation Session Workspace is created.
3. Screen captures are analyzed.
4. Recognizers publish observations.
5. Workspace is updated.
6. Higher layers consume observations.
7. Battle ends.
8. Workspace is finalized.

---

# Architectural Principles

## Single Responsibility

Recognizers recognize.

They do not infer.

---

## Immutable Observations

An observation represents something detected from the screen.

Interpretation belongs elsewhere.

---

## Independent Modules

Recognition modules should not depend on one another.

Each module contributes only the information it owns.

---

## Shared Workspace

All recognized information is collected into a single Observation Session Workspace.

Consumers read from the workspace instead of communicating directly with recognizers.

---

# Relationship to Higher Layers

The Observation Layer provides factual inputs for:

- Memory Layer
- History Layer
- Archive Layer
- Intelligence Layer
- Presentation Layer

Each layer builds on the previous one without modifying observed facts.

---

# Future Expansion

Future recognizers may include:

- HP estimation
- Switch timer recognition
- Energy indicators
- Buff/debuff detection
- Battle timer
- Shield count
- Weather
- Battle league metadata

These additions expand the Observation Layer without changing its responsibilities.

---

# Guiding Principle

> Observe only what appears on the screen.
>
> Infer nothing.
>
> Preserve everything.
