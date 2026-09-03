# Overdex Architecture

This document describes the high-level architecture of Overdex.

Overdex is built from responsibilities with explicit ownership
boundaries.

A layer may consume or transform information produced elsewhere.
Ownership does not transfer merely because information crosses a
boundary.

------------------------------------------------------------------------

# Core Architectural Principle

- Battle
Reality happens

- Preserve
Events become immutable Articles on the Timeline

- Understand
BattleMemory / Dynamic Knowledge forms the current Match understanding

- Infer
Intelligence reasons over that understanding

- Present
Overdex communicates what matters to the Trainer

- Review
completed history is examined, reconstructed, learned from,
and used to improve future understanding / preparation

- Battle
next Match begins

--------
ARTICLE
noun / subject

EVENT
verb / change

STATE or VALUE
what changed from / to

EVIDENCE
why Overdex recorded it

TIMELINE
when that record entered custody
------------

Reality produces an Event
↓
Evidence of that Event is collected
↓
BattleEvent is published as-is
↓
BattleEvent enters custody
↓
BattleEvent is recorded on the RealityTimeline
↓
Chain-of-custody is established

---------

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

Overdex doesn't take custody of Reality's events. It takes custody of evidence and publishes claims about Reality as Articles.

------------------------------------------------------------------------

## Reference Knowledge

Provides canonical information available to Overdex independently of the
current Match.

Reference Knowledge answers:

> "What information does Overdex have about this entity or system?"

Examples include:

- Pokédex data
- Move database
- Type relationships
- Base stats
- Evolution data

Reference Knowledge does not establish the live state of a Match.

Evidence collected during a Match may enrich the Reference Knowledge
available for that Match. This enrichment remains Match-scoped and does
not rewrite the underlying canonical Reference Knowledge.

------------------------------------------------------------------------

## Observation

An Observation is an Overdex measurement concerning the subject or
occurrence recorded by an Article.

Observation answers:

> "What did Overdex measure?"

An Article may be informed by one or many Observations from different
instruments or modalities.

Observations may include:

- visual measurements
- OCR or recognition results
- audio measurements
- input measurements
- spatial measurements
- temporal measurements

An Observation retains its measurement, confidence, provenance, and
relationship to the evidence from which it was obtained.

Observation does not own the underlying evidence and does not infer
tactical meaning.

Observation is not an architectural layer. Observations belong to the
Articles they concern and remain available as part of the Article's
provenance and chain-of-custody.

------------------------------------------------------------------------
## Memory / Match State

Preserves Overdex's evolving understanding of the active Match.

Responsibilities include:

- Battle Memory
- Current active Pokémon
- Current believed move state
- Estimated battle state
- Shield state
- Other transient Match context

Match State represents what Overdex currently believes about the live
Match.

It is mutable and may change as new Articles, observations, supporting or
refuting evidence, Reference Knowledge, and derived conclusions become
available.

Match State does not own the underlying Reality evidence, the Articles
preserved by the Timeline, or canonical Reference Knowledge.

It retains the current understanding needed by downstream Intelligence
without rewriting the historical record that produced that understanding.


------------------------------------------------------------------------

## Time, Match, and Timeline

Time is external to Overdex and continuous across Matches.

A Match progresses through Time. Beginning a new Match does not begin a
new clock.

Each Match establishes its own Timeline and chain-of-custody against that
common temporal authority.

The Match is the current moving position. The Timeline is the immutable
track left behind as Articles are published.

All instruments and modules participating in a Match use a shared
temporal reference so that evidence, measurements, Articles, and system
outputs can be correlated against the same Time.

The active Match may end while its Timeline continues. Review, editing,
compilation, and later analysis may publish additional Articles onto that
Timeline without altering earlier chronology.


------------------------------------------------------------------------
## History

Preserves Overdex's published record of the Match through time.

Responsibilities include:

- Reality Timeline
- Ordered Articles
- Chronology
- Chain-of-custody
- Match reconstruction

The Reality Timeline is the final custodian of Articles published during
the Match.

Chronological sequence is established by time itself. As Articles enter
custody, the Timeline preserves their position in that sequence. Earlier
positions cannot be rewritten by later understanding.

History preserves what Overdex recorded about Reality, as well as
record-worthy conclusions and changes produced by Overdex itself.

Later evidence, understanding, or inference does not rewrite an earlier
Article. Supporting evidence, refuting evidence, derived conclusions, and
newly revealed relationships are preserved through subsequent Articles.

History provides the record from which a Match may be reconstructed. It
does not interpret that record.

The Timeline remains authoritative after the active Match ends.

It may be retained, indexed, moved between storage systems, or retrieved
later without changing its chronology, custody, or Articles.

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

- Match Understanding
- Reference Knowledge
- Relevant Articles from the Timeline
- Prior reasoning

It produces:

- Inferences
- Predictions
- Matchup analysis
- Decision Point and Choice analysis
- Recommendations

Intelligence may create new claims. It may not revise old ones.

When an Intelligence output is preserved, that output is published as a
new Article and appended to the Timeline. Its conclusions may contribute
to later Match Understanding without changing the historical record from
which they were derived.


Timeline / Articles ─────┐
│
Match Understanding ─────┼──► Intelligence
│
Reference Knowledge ─────┤
│
Prior reasoning ─────────┘

------------------------------------------------------------------------
## Presentation

Presentation communicates Match Understanding and Intelligence to the
trainer.

It may represent:

- confidence
- uncertainty
- provenance
- recommendations
- explanations

Presentation determines how information is communicated. It does not
determine what the Match means.

------------------------------------------------------------------------
# Battle Data Flow

                         Reference Knowledge
                              │         │
                              ▼         ▼
Reality                  Match Understanding ───► Intelligence
│                             ▲                      │
│ something happens           │                      ▼
▼                             │                 Presentation
Evidence                      │                      │
│                             │                      ▼
▼                             │                   Trainer
Observation(s)                │                      │
│                             │                      ▼
▼                             │                   Reality
Article                       │
Subject + Verb + Predicate    │
│                             │
▼                             │
Timeline ─────────────────────┘

# Battle Data Flow

```text
TIME ─────────────────────────────────────────────────────────────►

Reality
   │
   │ produces
   ▼
Evidence
   │
   │ measured as
   ▼
Observation(s)
   │
   │ inform
   ▼
Article
Subject + Verb + Predicate
   │
   │ published into
   ▼
Timeline
   │
   ▼
Match Understanding ◄──── Reference Knowledge
   │
   ▼
Intelligence ◄──────────── Reference Knowledge
   │
   ▼
Presentation
   │
   ▼
Trainer
   │
   ▼
Reality



Intelligence output
│
│ if preserved
▼
Article
│
▼
Timeline

Timeline
↓
Review
↓
new measurement / conclusion / relationship discovered
↓
Article
↓
Timeline
Time is the metronome. The Timeline is the track. The Match is the train. Articles are the ties laid into the track as custody is established. Instruments aboard the Match share Time through the Time Bus and collect evidence as the Match travels through Reality.

------------------------------------------------------------------------
# Architectural Rules

## Measurement Does Not Infer

Measurement characterizes evidence.

It does not predict, recommend, or determine tactical meaning.

Observations preserve what Overdex measured, including confidence and
provenance.

## Match Understanding Does Not Own Evidence

Match Understanding retains Overdex's current best understanding of the
Match.

The evidence, Observations, and published claims from which that
understanding was derived remain in custody through their Articles and
Timeline.

Changing the current understanding does not rewrite the record that
produced it.

## Reference Knowledge Does Not Become Match Truth

Reference Knowledge describes what Overdex knows independently of the
current Match.

It does not prove that the live Match currently satisfies that
description.

Evidence collected during a Match may enrich the Reference Knowledge
available for that Match without rewriting canonical Reference Knowledge.

## Intelligence Does Not Manufacture Facts

Intelligence reasons from Match Understanding, Reference Knowledge, and
the published record.

It produces confidence-bearing conclusions, predictions, Decision Point
analysis, Choices, and recommendations.

An Intelligence conclusion may be published as a new Article. It does
not alter the Articles from which that conclusion was derived.

## Presentation Does Not Create Meaning

Presentation determines how information is communicated.

It does not determine what the Match means, what Overdex currently
believes, or what Intelligence concludes.

## Articles Do Not Rewrite Reality

An Article is an immutable claim published by Overdex.

It records what Overdex claims about a Subject through its evidence,
Observations, confidence, and provenance.

Publishing an Article does not alter the Reality it describes.

## Timeline Does Not Interpret

The Timeline preserves Articles in custody.

It does not infer, reconcile, correct, or reorganize their meaning.

Later Articles may support, refute, qualify, or reveal relationships
concerning earlier Articles without changing them.

## Time Determines Sequence

Time is external to the Match and continuous across Matches.

A Timeline maintains its own chain of custody against that common
temporal authority.

Publishing an Article establishes its position in that chain.
Later understanding cannot move an Article to an earlier position in
Time.


------------------------------------------------------------------------

## Article

An Article is an immutable claim published by Overdex.

An Article concerns:

**Subject + Verb + Predicate**

and preserves the Observations, evidence references, confidence,
provenance, and custody necessary to explain why that claim was
published.

Publishing an Article establishes its position in the Timeline.

An Article may later be supported, refuted, qualified, or related to
other Articles, but the original Article is never rewritten.



------------------------------------------------------------------------
# Authority and Custody Invariant

> **Every responsibility has one authoritative owner. Information may
> cross boundaries. Authority does not.**

Authority, source, and custody are distinct.

The authority test is:

> "Who is responsible for determining or changing this representation?"

The custody test is:

> "Where is the record preserved once it has been published?"

Reality is authoritative for what actually happens. Overdex does not take
custody of Reality's events.

Evidence may originate in Reality and enter Overdex custody.

Measurements and Observations characterize evidence without becoming
authority over the Reality they describe.

Reference Knowledge is authoritative for canonical reference information.

Match Understanding is authoritative for Overdex's current belief about
the Match. It is not authoritative for Reality's actual Match state.

Intelligence is authoritative for the conclusions, predictions, and
recommendations it produces.

Articles preserve published claims and their supporting Observations,
evidence, confidence, and provenance.

The Timeline is the final custodian of published Articles and their
chronological chain-of-custody.

------------------------------------------------------------------------
# Reality Boundary

Reality produces events and their evidence.

Overdex does not take custody of Reality's events. It takes custody of
evidence and publishes claims about Reality as Articles.

Evidence may be measured or characterized through one or more
Observations. Those measurements inform a claim expressed as:

**Subject + Verb + Predicate**

The claim, its supporting Observations, evidence, confidence, and
provenance are published as an immutable Article.

Publication establishes the Article's chronological position and custody
within the Timeline.

Interpretation occurs from the published record. Later understanding,
inference, supporting evidence, refutation, or newly discovered
relationships may produce new Articles, but they do not rewrite earlier
Articles.

------------------------------------------------------------------------

# Guiding Principle

> **Measure Reality. Preserve the measurement. Enrich with reference.
> Infer from evidence. Present with confidence.**