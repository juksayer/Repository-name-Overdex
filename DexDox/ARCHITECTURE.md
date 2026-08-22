# Overdex Architecture

This document describes the high-level architecture of Overdex.

Overdex is built from responsibilities with explicit ownership
boundaries.

A layer may consume or transform information produced elsewhere.
Ownership does not transfer merely because information crosses a
boundary.

------------------------------------------------------------------------

# Core Architectural Principle

> **Observe first. Remember second. Understand third. Infer fourth.
> Present last.**

The system distinguishes:

-   Reality and its evidence
-   Observations of Reality
-   Reference Knowledge
-   Current Match Understanding
-   Intelligence and inference
-   Presentation

------------------------------------------------------------------------

# System Architecture

``` text
                         Presentation
                              ▲
                              │
                         Intelligence
                              ▲
                              │
                      Match Understanding
                         ▲           ▲
                         │           │
                    Observations   Reference
                         ▲         Knowledge
                         │
                       Reality
```

History and Archive provide temporal organization and persistence around
the Match state and its events. They do not become owners of
interpretation.

------------------------------------------------------------------------

# Layer Responsibilities

## Reality

Reality is outside Overdex's ownership.

Events occur and produce evidence.

Overdex may receive evidence about those events, but does not rewrite
the underlying Reality evidence.

------------------------------------------------------------------------

## Reference Knowledge

Provides canonical information available to Overdex independently of the
current Match.

Reference Knowledge answers:

> "What information does Overdex have about this entity or system?"

Examples include:

-   Pokédex data
-   Move database
-   Type relationships
-   Base stats
-   Evolution data

Reference Knowledge does not establish the live state of a Match.

------------------------------------------------------------------------

## Observation

Measures events in Reality.

Responsibilities:

-   Screen capture
-   OCR
-   Recognition
-   Observation provenance
-   Observation confidence
-   Active Match workspace

Observation answers:

> "What did Overdex measure?"

It does not infer tactical meaning.

------------------------------------------------------------------------

## Memory / Match State

Preserves the evolving state of the active Match.

Responsibilities include:

-   Match
-   Battle Memory
-   Current active Pokémon
-   Observed moves
-   Estimated battle state
-   Other transient Match context

Match state represents what Overdex currently believes about the live
Match. It does not own the underlying Reality evidence or canonical
Reference Knowledge.

------------------------------------------------------------------------

## History

Organizes events and state through time.

Responsibilities include:

-   Battle Timeline
-   Ordered events
-   Chronology
-   Match reconstruction

History preserves what happened in Overdex's record. It does not rewrite
earlier measurements when later understanding changes.

------------------------------------------------------------------------

## Archive

Preserves completed history and long-term records.

Responsibilities include:

-   Archived battles
-   Long-term storage
-   Retrieval

Archive preserves records. It does not interpret them.

------------------------------------------------------------------------

## Match Understanding

Match Understanding is Overdex's current best representation of the
active Match.

It is not a collection of facts.

It is a confidence-bearing state derived from available measurements and
relevant reference information.

It may change as new observations arrive.

Examples:

-   Current believed opponent species
-   Observed move set
-   Estimated energy
-   Active Pokémon
-   Shield state
-   Confidence in each current understanding

A superseded understanding does not erase the observations that produced
it.

------------------------------------------------------------------------

## Intelligence

Intelligence reasons over:

-   Observations
-   Match Understanding
-   Reference Knowledge
-   Prior inferences

It produces:

-   Inferences
-   Predictions
-   Matchup analysis
-   Decision Point analysis
-   Recommendations

Intelligence does not rewrite Reality, observations, or historical
records.

------------------------------------------------------------------------

## Presentation

Presentation communicates current understanding and intelligence to the
trainer.

It may represent:

-   confidence
-   uncertainty
-   provenance
-   recommendations
-   explanations

Presentation does not decide what the Match means.

------------------------------------------------------------------------

# Battle Data Flow

``` text
Reality Event + Evidence
          │
          ▼
     Observation
          │
          ▼
   Match Understanding ◄──── Reference Knowledge
          │
          ▼
     Intelligence
          │
          ▼
       Inference
          │
          ▼
    Recommendation
          │
          ▼
     Presentation
```

History and Archive preserve the relevant records across time.

------------------------------------------------------------------------

# Architectural Rules

## Observation Does Not Infer

Recognition measures.

It does not predict or recommend.

## Memory Does Not Own Evidence

Match state retains the current understanding of the battle.

The underlying evidence remains associated with the Reality event and
its observation provenance.

## Reference Knowledge Does Not Become Match Truth

A reference entry describes what is generally known.

It does not prove that the live Match currently satisfies that
description.

## Intelligence Does Not Manufacture Facts

Intelligence produces conclusions with supporting evidence and
confidence.

## Presentation Does Not Create Meaning

Presentation communicates what the system currently understands and what
Intelligence concludes.

------------------------------------------------------------------------

# Ownership Invariant

> **Every responsibility has one owner. Information may cross
> boundaries. Ownership does not.**

The ownership test is:

> "Who owns the truth represented by this information?"

If it is a Reality event or its evidence, Reality owns it.

If it is a measurement of that event, Observation owns it.

If it is canonical reference information, Reference Knowledge owns it.

If it is current live Match state, Match state owns it.

If it is a derived conclusion, Intelligence / Inference owns it.

------------------------------------------------------------------------

# Event Processing Boundary

The current event-oriented path is:

``` text
Event
↓
Witness
↓
Testimony
↓
Reporter
↓
Article
↓
Interpreter
↓
Article
↓
Publisher
↓
Article
↓
Catalog
```

The exact implementation may evolve, but the architectural boundary
remains:

**Reality produces events and evidence. Overdex measures and interprets
what those events mean to the application without rewriting the
underlying record.**

------------------------------------------------------------------------

# Guiding Principle

> **Measure Reality. Preserve the measurement. Enrich with reference.
> Infer from evidence. Present with confidence.**