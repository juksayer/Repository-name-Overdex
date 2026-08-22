# Ownership Audit

## Purpose

Every concept in Overdex should have exactly one primary owner.

Ownership defines responsibility.

Responsibility defines boundaries.

When ownership is unclear, architecture begins to drift.

------------------------------------------------------------------------

# Core Rules

Every concept should have one primary owner.

Other layers may consume information.

Other layers may transform information.

Ownership does not transfer merely because information crosses a
boundary.

The ownership test is:

> **Who owns the truth represented by this information?**

------------------------------------------------------------------------

# Reality

Owns:

-   Events
-   Underlying evidence produced by those events

Reality does not belong to Overdex.

Overdex may preserve references to Reality evidence and measurements of
it.

------------------------------------------------------------------------

# Reference Knowledge

Owns:

-   Pokémon species data
-   Moves
-   Typing
-   Base statistics
-   Forms
-   Evolution
-   Other canonical static game data

Does not own:

-   Current Match state
-   Observations
-   Recommendations
-   Live battle conclusions

Question:

> Can this information exist before the current Match begins?

If yes, Reference Knowledge probably owns it.

Reference Knowledge provides context. It does not establish live Match
truth.

------------------------------------------------------------------------

# Observation

Owns:

-   Measurements of Reality events
-   Observation provenance
-   Observation confidence
-   Active observation session
-   Battle Workspace as the active observation integration point

Does not own:

-   Underlying Reality evidence
-   Reference Knowledge
-   Match conclusions
-   Recommendations

Question:

> Did Overdex measure this from a Reality event?

If yes, Observation probably owns the measurement.

Observation does not assign tactical meaning.

------------------------------------------------------------------------

# Memory / Match State

Owns:

-   Current battle state
-   Working Match understanding
-   Temporary battle calculations
-   Live battle context

Does not own:

-   Underlying Reality evidence
-   Canonical static knowledge
-   Permanent history

Question:

> Is this the current state Overdex believes the Match is in?

If yes, Match State probably owns it.

------------------------------------------------------------------------

# History

Owns:

-   Ordered battle events
-   Battle timelines
-   Chronology
-   Match reconstruction

Does not own:

-   OCR
-   Reference Knowledge
-   Presentation

Question:

> Does the ordering of events matter to the meaning?

If yes, History probably owns that ordering.

------------------------------------------------------------------------

# Archive

Owns:

-   Long-term persistence
-   Storage
-   Retrieval
-   Completed battle records

Does not own:

-   Interpretation
-   UI
-   Recommendations

Question:

> Is this information worth preserving after the active Match ends?

If yes, Archive probably owns its persistence.

------------------------------------------------------------------------

# Intelligence

Owns:

-   Inference
-   Prediction
-   Matchup analysis
-   Decision Point analysis
-   Recommendations
-   Explanations derived from evidence
-   Confidence in derived conclusions

Does not own:

-   Reality evidence
-   Observations
-   Reference Knowledge
-   Historical records

Question:

> Is this a conclusion reached by reasoning across available
> information?

If yes, Intelligence probably owns it.

------------------------------------------------------------------------

# Presentation

Owns:

-   CRT
-   HUD
-   Audio
-   Trainer communication
-   Replay rendering
-   Visual language

Does not own:

-   Match state
-   Reference Knowledge
-   Observations
-   Recommendations

Presentation communicates.

It does not decide.

------------------------------------------------------------------------

# Confidence Ownership

Confidence is not a single global property owned by one layer.

The owner is the claim or measurement to which the confidence applies.

Examples:

``` text
Observation
    └── observation confidence

Match Understanding
    └── confidence in current state

Inference
    └── confidence in conclusion

Recommendation
    └── confidence in recommendation
```

This prevents Intelligence from becoming the owner of every uncertainty
in the system.

------------------------------------------------------------------------

# Evidence Ownership

Evidence belongs to Reality and the event that produced it.

Observations point back to that evidence through provenance.

Overdex owns the measurement made from the evidence, not the evidence
itself.

This distinction must remain intact.

------------------------------------------------------------------------

# Cross-Layer Flow

``` text
Reality
   ↓
Observation
   ↓
Match State / Understanding
   ↑
Reference Knowledge
   ↓
Intelligence
   ↓
Inference
   ↓
Presentation
```

History and Archive preserve the relevant records across time.

Ownership does not flow downward or upward.

------------------------------------------------------------------------

# Ownership Smells

A class probably has the wrong owner if it:

-   Stores canonical knowledge and live Match state together.
-   Claims ownership of Reality evidence.
-   Performs OCR while rendering UI.
-   Generates recommendations during persistence.
-   Knows about Android widgets inside the engine.
-   Mutates history while presenting it.
-   Converts uncertain measurements into unqualified facts.
-   Requires another layer to explain its own data.

------------------------------------------------------------------------

# Ownership Questions

When reviewing any class:

1.  What does this class own?
2.  What does it consume?
3.  What does it produce?
4.  Could another layer own this more naturally?
5.  Does it know something it should merely observe?
6.  Does it remember something it should archive?
7.  Does it interpret something it should merely present?
8.  Does it own evidence that actually belongs to Reality?
9.  Does it turn a measurement into a conclusion without preserving the
    measurement?

------------------------------------------------------------------------

# Litmus Test

Ownership should be explainable in one sentence.

If ownership requires a paragraph, ownership is probably wrong.

------------------------------------------------------------------------

# Existing Workspace Rules

The Match workspace represents the observations accumulated during an
active Match.

Session management remains separate from domain state.

Responsibility                                Owner
  --------------------------------------------- ------------------------------
Physical input normalization                  Observation / input boundary
Intent                                        Intent Layer
Session lifetime                              SessionManager
Navigation history                            SessionManager
Active workspace                              SessionManager, derived
Workspace state                               Individual Workspace
Selection                                     Individual Workspace
Domain logic                                  Individual Workspace
Navigation requests                           Workspace → SessionManager
Presentation                                  Droidball
CRT / LCD / Overlay / LED / Sound / Haptics   Droidball
Rendering                                     Droidball

------------------------------------------------------------------------

# Navigation Context Ownership Invariant

Only the component that currently owns the active navigation context may
dispatch navigation intents.

Intents originating from stale contexts must be ignored rather than
allowing navigation state to become inconsistent.

------------------------------------------------------------------------

# Architectural Rules

### Rule 1

Observation measures.

It reports:

-   A pressed
-   Screen tapped
-   Voice command heard
-   Visible battle information

It does not assign tactical meaning.

### Rule 2

Intent is device-independent.

Whether it came from:

-   keyboard
-   touchscreen
-   controller
-   voice

the workspace receives the same intent.

### Rule 3

Modules expose state.

They do not render themselves.

### Rule 4

Presentation belongs to Droidball.

Everything the trainer experiences is mediated through the Presentation
layer.

### Rule 5

SessionManager owns navigation, not workspaces.

It owns:

``` text
workspaceStack
```

and exposes:

``` text
activeWorkspace
```

as derived state.

### Rule 6

Prefer authoritative state over duplicated state.

For example:

``` text
workspaceStack
↓
activeWorkspace
```

instead of storing both.