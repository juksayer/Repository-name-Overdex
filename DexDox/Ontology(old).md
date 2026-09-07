# Ontology

The ontology answers:

> What kinds of things can exist in Overdex's model of the world?

Examples include Subject, Evidence, Measurement, Article, Relationship,
Confidence, Time, and Timeline.

These establish the grammar of the system.


# Subject Catalog

The Subject Catalog answers:

> What kinds of subjects does Overdex currently know how to talk about?

Subject
├── Match
├── Pokémon
├── Trainer
├── Move
├── Type
├── Item
└── Team




# Core Ontology (Things That Exist)

The Overdex Ontology defines the concepts that exist within the Overdex universe of discourse. It intentionally does not attempt to model concepts outside that domain.

## Article

An Article is the stable subject to which measurements are attributed and evidence accumulates over time. Every addition preserves its provenance and chain of custody.

## Event

An Event is a change in the state of one or more Articles that occurs in
Reality through Time.

An Event exists independently of whether Overdex measures or correctly
identifies it.

Measurements may provide evidence that an Event occurred, but a
Measurement is not the Event itself.


## Relationship

A Relationship is an association between two or more Articles. Events may establish, modify, or 
terminate a Relationship over time.

## Crop

A Crop is a bounded region through which Reality becomes available for measurement. Its identity and relationship to the measured region remain identifiable throughout the Match.


## Observation

An Observation is a record produced by an Observer describing something perceived about an Article or Event.
Observation
RETIRED
ordinary-language verb/noun only
Terms of Ownership documents the retirement

Evidence / Measurement
same conceptual thing

Witness
no semantic ontology role

Crop
performs the measurement

Timeline
receives/preserves the measurement against Time


## Evidence

Evidence is the result of measuring an Article, Event, or Element through
a calibrated Crop at a particular point in Time.

The Crop identifies what is being measured. Its calibrated geometry
establishes where that measurement is taken from.

Evidence is submitted to the Timeline without requiring semantic
interpretation by a Witness.

Evidence may later support or refute reasoning, conclusions, and current
Match Understanding.

## Knowledge

Knowledge is information available to Overdex through Reference Knowledge
and information obtained during a Match through measurement and reasoning.

### Reference Knowledge

Reference Knowledge is information available independently of the current
Match.

It includes canonical Pokémon data, move pools, typing, type
effectiveness, attack and defense values, resistances, and other reference
information available before, during, and after a Match.

### Battle Memory / Working Memory

Battle Memory is information obtained and maintained during a Match.

It reflects what Overdex currently understands about that Match from
measurements, relevant Reference Knowledge, and reasoning.

Battle Memory is Overdex’s mutable working representation of a Match, built from that Match’s Timeline, relevant Reference Knowledge, and reasoning. During a live Match it tracks current understanding; afterward it may be reconstructed to inspect or simulate other reasoning paths.




## Confidence

Confidence is a quantitative measure derived from the weighted combination of multiple, preferably independent, pieces of evidence. It expresses the system's confidence in a conclusion or prediction. Confidence is always numeric and is computed from evidence.

## Confidence Threshold

Confidence expresses uncertainty. Thresholds determine how confidence-bearing information may be used or presented; crossing a threshold does not convert it into truth.

## Timeline

A Timeline is the chronological ordering of Events.


# Articles

These are known Articles within the current Overdex ontology.

- Battle
- Pokémon
- Trainer
- Move
- Type
- Item (future)
- Team (future)


# Actors

Actors perform responsibilities within the Overdex system.

- Overdex
- Droidball


# Concepts Under Evaluation

These concepts have appeared naturally during development but have not yet earned first-class ontology status.

- Presentation
- History
- Projection
- Interpretation
- Communication
- Provenance
- Observation Session
- Observer Identity
- Replay


# Deliberately Excluded

These terms are intentionally not part of the ontology because they describe implementation details or higher-level behaviors rather than concepts that exist.

- Recommendation (currently a form of Presentation)
- UI
- Screen
- Widget
- Database
- Repository
- OCR
- Energy Counter


# Design Rule

Every ontology term must be definable without mentioning implementation details.

One thing I'd like to propose, though.

I think we've accidentally mixed ontology with catalogs.

For example:

Battle
Pokémon
Trainer
Move
Type

Those aren't ontology concepts.

Those are Articles.
(END)