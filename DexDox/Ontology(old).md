Ontology
The ontology answers:
What kinds of things can exist in Overdex's model of the world?

Article, Event, Observation, Evidence, Relationship, etc.
These establish the grammar of the system.


Article Catalog
The Article catalog answers:
What kinds of subjects does Overdex currently know how to talk about?


Article
├── Battle
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

An Event is an objective change in the state of one or more Articles.

## Relationship

A Relationship is an association between two or more Articles. Events may establish, modify, or 
terminate a Relationship over time.

## Crop

A Crop is a bounded region through which changes in Reality become available for measurement. Its geometry is established before the Match and remains identifiable throughout the Match.

## Observation

An Observation is a record produced by an Observer describing something perceived about an Article or Event.

## Evidence

Evidence is information that supports or refutes an Observation or conclusion. Evidence may originate from one or more Observers and serves as the basis for confidence calculations.

## Knowledge

Knowledge is information accepted by Overdex as true based upon available evidence.

## Confidence

Confidence is a quantitative measure derived from the weighted combination of multiple, preferably independent, pieces of evidence. It expresses the system's confidence in a conclusion or prediction. Confidence is always numeric and is computed from evidence.

## Confidence Threshold

A Confidence Threshold is the minimum confidence required before Overdex commits a conclusion to its internal model or historical record.

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