# Observation Layer

**Status:** Complete (Git #152--#160)

## Purpose

The Observation Layer measures events occurring in Reality.

Its responsibility is to observe the Pokémon GO battle screen, extract
measurements, assign observation confidence, and publish those
observations into the active Match workspace.

It does not decide what those measurements mean for the battle.

It answers:

> "What did Overdex measure about what is happening right now?"

------------------------------------------------------------------------

## Design Philosophy

The Observation Layer is intentionally limited.

It measures. It does not reason.

The evidence produced by a Reality event remains outside the Observation
Layer. An Observation records what Overdex measured about that event and
preserves the provenance and confidence needed to evaluate that
measurement later.

The Observation Layer must not promote a measurement to certainty merely
because the game presented the information.

------------------------------------------------------------------------

## Responsibilities

The Observation Layer is responsible for:

-   Capturing battle screenshots
-   Running OCR and recognition
-   Identifying observable battle information
-   Producing observations
-   Assigning observation confidence
-   Maintaining the active Battle Workspace

It is not responsible for:

-   Reference Knowledge
-   Match interpretation
-   Energy prediction
-   Opponent modeling
-   Battle history
-   Strategy
-   Recommendations
-   Future-event prediction

------------------------------------------------------------------------

## Observation Pipeline

``` text
Pokémon GO
    │
    ▼
Reality Event + Evidence
    │
    ▼
Screen Capture
    │
    ▼
Recognition
    │
    ▼
Observation
    │
    │ measurement + confidence + provenance
    ▼
Battle Workspace
    │
    ▼
Higher Layers
```

------------------------------------------------------------------------

## Battle Workspace

The Battle Workspace represents the observations accumulated during the
active Match.

It is an integration point for independent observers.

The workspace does not become the owner of the underlying Reality
evidence. It provides the active Match context through which
observations are made available to downstream systems.

------------------------------------------------------------------------

## Production Observers

Recognition is performed by independent observers.

Each observer owns one observable aspect of the battle.

Examples include:

-   SpeciesObserver
-   CombatPowerObserver
-   FastMoveObserver
-   ChargedMoveObserver
-   ShadowStatusObserver
-   HealthObserver
-   ShieldObserver

Observers do not communicate directly with one another.

Each observer contributes measurements through the workspace.

------------------------------------------------------------------------

## Current Recognition Modules

As of Git #160, the Observation Layer includes support for:

-   Species recognition
-   Combat Power recognition
-   Fast Move recognition
-   Charged Move A recognition
-   Charged Move B recognition
-   Shadow Bonus recognition

Additional observers can be added without changing the overall
architecture.

------------------------------------------------------------------------

## Observation Confidence

Confidence belongs to the measurement.

A recognition result may be:

-   high confidence
-   uncertain
-   contradictory with another observation
-   later superseded

A later observation may change the Match's current understanding without
deleting the earlier observation.

The historical measurement remains available for provenance and
reconciliation.

------------------------------------------------------------------------

## Match Lifecycle

1.  Battle begins.
2.  Match workspace is created.
3.  Screen captures are analyzed.
4.  Observers produce measurements.
5.  Observations are published to the workspace.
6.  Higher layers consume observations.
7.  Battle ends.
8.  The workspace is finalized or handed to the appropriate
    history/archive infrastructure.

------------------------------------------------------------------------

## Architectural Principles

### Observation Does Not Assign Meaning

An Observation reports what was measured.

It does not decide:

-   what the measurement means tactically
-   what will happen next
-   what the opponent intends
-   whether a recommendation should be made

### Observations Are Not Reality

Reality owns the event and its underlying evidence.

Overdex owns the measurement it made of that event.

### Observations Are Not Facts

A measurement can be wrong.

Confidence communicates how strongly Overdex currently trusts the
measurement.

### Preserve Measurements

Contradictory or superseded measurements should remain available so
later conclusions can be traced back to what Overdex actually saw.

------------------------------------------------------------------------

## Relationship to Higher Layers

The Observation Layer provides measurements to:

-   Match state / Battle Memory
-   History
-   Intelligence
-   Presentation

Reference Knowledge may be consulted downstream to enrich the
understanding of an observation.

The Observation Layer itself does not perform that enrichment.

------------------------------------------------------------------------

## Guiding Principle

> **Measure what appears in Reality. Preserve the measurement. Infer
> nothing.**