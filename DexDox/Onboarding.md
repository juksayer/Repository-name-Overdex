# Overdex Mental Model

Overdex is an evidence-first field instrument.

Its architecture separates:

- what exists in Reality,
- what Overdex measures,
- what Overdex preserves,
- what Overdex knows,
- what Overdex derives,
- and what Overdex communicates.

These responsibilities must remain distinguishable.

---

# Reality

Reality exists independently of Overdex.

Articles and phenomena belong to Reality.

Overdex does not take custody of an Article.

It takes custody of the measurements it produces concerning Reality.

---

# Crop

A Crop is a bounded region of incoming signal from which Overdex takes measurements.

Crops form the sensory boundary of Overdex.

A Crop measures.

It does not interpret.

It does not assign institutional Confidence.

It does not decide whether a measurement is important enough to preserve.

---

# Measurement

A Measurement records something Overdex encountered through a Crop.

Measurements may include:

- recognized text
- visual features
- audio matches
- candidate values
- match or similarity scores
- manually supplied measurements
- signal properties
- timestamps
- provenance

A Measurement is not an Interpretation.

A recognizer score is not automatically Confidence.

Measurements are preserved before Overdex decides what they mean.

---

# Timeline

The Timeline is Overdex's permanent institutional record.

Measurements enter the Timeline before interpretation.

The Timeline also preserves later contributions, including:

- interpretations
- Confidence and Confidence changes
- reasoning outcomes
- changes in Dynamic Knowledge
- decisions
- relevant state changes
- provenance
- relationships between source and derived records

The Timeline is append-only in principle.

Later understanding does not overwrite earlier records.

Contradiction is history.

If Overdex encountered it, preserving it does not depend upon Overdex
already understanding why it matters.

> Overdex preserves what it measures so that future Overdex can
> understand what present Overdex cannot.

---

# Knowledge

Knowledge describes what Overdex knows.

There are two primary forms.

## Reference Knowledge

Reference Knowledge is what Overdex knows independently of the current
encounter.

Examples include:

- Pokédex data
- Move data
- Type data
- Base stats
- battle mechanics
- other established reference material

Reference Knowledge is available before a Match begins.

## Dynamic Knowledge

Dynamic Knowledge is Overdex's current, evolving understanding produced
through encounters.

During a Match, Dynamic Knowledge may include:

- opponent identity
- observed moves
- shield state
- estimated energy
- revealed team composition
- current Match state

Dynamic Knowledge may change when new evidence becomes available.

The Timeline preserves how that understanding changed.

---

# Reference Data

Reference Data is preserved historical information available for future
reasoning.

When a Match concludes, its Timeline records remain available as
Reference Data.

Reference Data may include:

- previous measurements
- previous interpretations
- Confidence histories
- Match outcomes
- opponent history
- previous decisions
- other preserved encounter records

Reference Data is not automatically Reference Knowledge.

Reference Data is what Overdex can consult from its history.

Reference Knowledge is what Overdex knows.

---

# Reasoning

Reasoning evaluates preserved evidence using available knowledge and
Reference Data.

It may produce:

- interpretations
- Confidence
- derived knowledge
- detected conflicts
- predictions
- conclusions
- recommendations
- decisions

Reasoning never alters its source measurements.

Derived information must remain distinguishable from the evidence from
which it arose.

---

# Confidence

Confidence describes how strongly available evidence supports an
Interpretation.

Confidence belongs to reasoning.

It does not belong to:

- an Article
- a phenomenon
- a Crop
- a Measurement

A recognizer may produce a numeric match or similarity score.

That score is evidence.

It is not automatically institutional Confidence.

As evidence changes, Confidence may change.

Earlier Confidence is preserved rather than silently rewritten.

---

# Intelligence

Intelligence uses available Knowledge, Reference Data, preserved evidence,
and current understanding to help the trainer.

Examples include:

- Matchup analysis
- Decision support
- move counting
- energy estimation
- Team analysis
- Tournament tools
- statistics
- future AI systems

Intelligence does not manufacture evidence.

Its conclusions remain traceable to the evidence and knowledge that
support them.

---

# Presentation

Presentation communicates Overdex's current understanding to the trainer.

Presentation may be:

- visual
- audio
- haptic
- or another future interface

Droidball is the presentation custodian.

Presentation does not own domain authority.

Presentation does not alter measurements, Knowledge, or the Timeline.

Presentation communicates.

It does not decide.

---

# Current Information Flow

The basic epistemic path is:

Reality
↓
Crop
↓
Measurement
↓
Timeline
↓
Reasoning
↓
Interpretation / Confidence / Dynamic Knowledge
↓
Timeline
↓
Intelligence
↓
Presentation
↓
Trainer

Reference Knowledge and Reference Data may be consulted during reasoning.

The Timeline is not merely the final destination of accepted conclusions.

It is the first institutional custodian of measurements and the permanent
record of how Overdex's understanding develops.

---

# Before, During, and After a Match

## Before

Overdex has:

- Reference Knowledge
- Reference Data from previous encounters

## During

Overdex produces:

- Measurements
- Timeline records
- Dynamic Knowledge
- Interpretations
- Confidence
- decisions and recommendations

## After

The live Match ends.

Its preserved Timeline records remain available as Reference Data.

Future reasoning may use that history to develop new Knowledge that was
not available during the original Match.

---

# Guiding Principle

Measure once.

Preserve once.

Interpret many times.

Present only what helps.

Preserve first.

Interpret second.

Never allow interpretation to overwrite what was preserved.

---

# Vocabulary

Current architectural vocabulary should be preferred over historical
terminology.

Current:

- Crop
- Measurement
- Timeline
- Reference Knowledge
- Dynamic Knowledge
- Reference Data
- Interpretation
- Confidence
- Intelligence
- Presentation

Retired terminology may remain in existing code and historical
documentation until addressed through focused vocabulary work.

Do not introduce new architecture using retired vocabulary.

Do not rename or remove historical implementations merely to make their
names match current terminology.

Vocabulary cleanup is its own architectural task.

---

# Architectural Restraint

Assume existing code has history.

A class name is evidence of implementation, not proof of current
architectural authority.

Do not redesign working code merely because newer terminology exists.

Before changing ownership:

1. Determine what the component actually does.
2. Determine what state or lifecycle it owns.
3. Compare that responsibility with current architecture.
4. Preserve behavior before changing vocabulary or structure.

Working code is not a problem to solve.

The smallest successful change is usually the correct change.

---

# AI Workflow

AI conversations should be treated like Git branches.

Each implementation task receives a narrow conversation.

Do not allow unrelated architectural work to leak into implementation.

Before implementation, establish:

DO NOT TOUCH
- Explicit boundaries outside the task.

ONLY CHANGE
- The smallest area necessary.

OBJECTIVE
- One sentence describing the completed behavior.

Implementation workflow:

1. One feature.
2. One discussion.
3. One implementation.
4. One review.
5. One test.
6. One commit.
7. Close the implementation thread.

Architectural investigation is different from implementation.

Investigation may examine history, compare evidence, and question current
ownership without authorizing code changes.

A finding is not permission to refactor.

---

# Working Rule

When current code, old documentation, and current architecture disagree:

Do not guess.

Preserve the evidence.

Determine which generation each represents.

Resolve ownership before changing implementation.