Very much. At this point we're changing vocabulary faster than the older documents can stop using
it. A **current dictionary snapshot** would give us a fixed reference without pretending the terminology has finished evolving.

Based on what we've established so far, I'd snapshot it like this:

# Overdex Dictionary
### Current Architectural Vocabulary Snapshot
**September 4, 2026**

This document records the current meaning of significant Overdex architectural terms.

It is a **snapshot**, not constitutional authority.

Where this document conflicts with the Constitution or Terms of Ownership, those documents govern.

Historical terminology may still exist in source code and documentation. Its presence does not
make it current vocabulary.

---

## Article

An **Article** is a subject in Reality about which Overdex may acquire measurements, evidence,
knowledge, and understanding.

An Article does not originate inside Overdex.

An Article does not enter Overdex custody.

Overdex may maintain records concerning an Article, but those records are not the Article itself.

**Ownership:** Reality.

**Key distinction:**

```text
Article ≠ Overdex's record of the Article
```

Reality retains the Article.

Overdex retains what it learns concerning it.

---

## Phenomenon

A **Phenomenon** is something occurring in Reality that may become accessible to Overdex through
incoming signal.

A phenomenon is not created by Overdex.

Overdex may measure a phenomenon without initially understanding its significance.

**Ownership:** Reality.

**Status:** Current concept; exact relationship between Phenomenon, Event, and Article still
requires reconciliation.

---

## Crop

A **Crop** is a bounded region of incoming signal from which Overdex takes measurements.

Crop is the sensory boundary between Reality and Overdex.

A Crop does not determine meaning.

A Crop does not assign institutional Confidence.

A Crop does not decide whether its measurement is significant enough to preserve.

Its measurements enter custody regardless of their present understood significance.

Conceptually:

```text
Reality
   ↓
Crop
   ↓
Measurement
```

**Status:** Current.

**Supersedes sensory responsibilities previously described using:** Witness and Observer.

---

## Measurement

A **Measurement** is an Overdex-produced record of something encountered
through its sensory boundary.

Measurements are preserved before interpretation.

A Measurement may include recognizer outputs, candidate values, similarity scores,
signal properties, timestamps, Crop identity, provenance, or other properties of the encounter.

A measurement need not be correct, understood, corroborated, or presently useful in order to
deserve preservation.

```text
Measurement ≠ Interpretation
Measurement ≠ Confidence
```

**Ownership:** Overdex.

---

## Evidence

**Evidence** is preserved information used to support, oppose, or otherwise inform an
interpretation.

A Measurement may become evidence in reasoning without ceasing to be a Measurement.

Evidence describes a **role information plays in reasoning**, rather than necessarily a separate
kind of record.

**Status:** Current, but this definition should receive formal ontology review.

---

## Interpretation

An **Interpretation** is a derived account of what available evidence means.

Interpretation occurs only after its source measurements have entered custody.

An Interpretation never replaces its supporting measurements.

Different interpretations may exist at different times as evidence changes.

```text
Measurements
     ↓
Reasoning
     ↓
Interpretation
```

**Ownership:** Overdex.

---

## Confidence

**Confidence** describes how strongly available evidence supports an interpretation.

Confidence belongs to reasoning about evidence.

It is not a property of:

- an Article
- a phenomenon
- a Crop
- a Measurement

A recognizer's numeric similarity or match score is not automatically Confidence.

```text
recognizer score = measurement/process output

Confidence = strength of evidentiary support
             for an interpretation
```

Confidence may change as evidence changes.

Earlier Confidence is preserved rather than overwritten.

**Status:** Current.

---

## Knowledge

**Knowledge** is information Overdex is presently justified in holding based on available
evidence and reasoning.

Knowledge may change as new evidence becomes available.

Previous states of knowledge remain historically recoverable through preserved records.

**Status:** Current, but requires reconciliation with the distinction between
**Reference Knowledge** and **Dynamic Knowledge**.

---

## Reference Knowledge

**Reference Knowledge** is knowledge available independently of the current encounter.

Examples include Pokémon species, moves, typing, battle mechanics, and other stable reference
material.

Reference Knowledge provides context against which measurements may later be interpreted.

```text
Reference Knowledge
        +
Measurements
        ↓
Reasoning
```

**Status:** Current.

---

## Reference Data

### Reference Data is preserved historical information available as evidence for future reasoning.
Reference Data may include measurements, prior interpretations, Confidence histories, Match outcomes, and other records preserved by the Timeline.
Reference Data is not necessarily Reference Knowledge.

---

## Dynamic Knowledge

**Dynamic Knowledge** is knowledge developed or changed through encounters, measurements,
reasoning, and accumulated history.

Unlike Reference Knowledge, it may depend upon what Overdex has experienced.

**Status:** Current concept, exact ownership boundary still under review.

---

## Timeline

The **Timeline** is the permanent chronological custodian of what Overdex encounters and produces.

Measurements enter the Timeline **before interpretation**.

Subsequent reasoning products also enter the Timeline.

Therefore:

```text
Crop
  ↓
Measurement
  ↓
Timeline
  ↓
Reasoning
  ↓
Interpretation + Confidence
  ↓
Timeline
```

Timeline may preserve:

- measurements
- provenance
- interpretations
- Confidence and Confidence changes
- reasoning outcomes
- knowledge changes
- operational state changes
- relationships between source and derived records

Timeline does not silently replace an earlier record with a later understanding.

Contradiction is history.

**Status:** Current; substantially broader than older definitions of Timeline as merely an ordering
of Events or published conclusions.

---

## BattleMemory

**BattleMemory** is the mutable operational representation of what Overdex currently remembers
about a Match.

BattleMemory changes as the Match develops.

Timeline does not.

```text
BattleMemory = current mutable understanding

Timeline     = permanent chronological custody
```

BattleMemory is not the permanent historical record.

**Status:** Current, pending detailed ownership reconciliation.

---

## BattleRAM

**BattleRAM** describes information supplied by Pokémon GO that is randomly accessible during
the current Match.

It is distinct from BattleMemory.

```text
BattleRAM
information available from the current Match

BattleMemory
Overdex's changing operational representation
```

**Status:** Current conceptual vocabulary.

---

## BattleROM

**BattleROM** describes persistent reference information available for battle reasoning.

It corresponds conceptually to battle-relevant Reference Knowledge.

**Status:** Current conceptual vocabulary.

---

## Match

A **Match** is one live Pokémon GO battle.

Tournaments may contain Matches.

**Owner:** Battle.

**Status:** Reserved.

---

## Intelligence

**Intelligence** is the architectural responsibility for reasoning from available evidence,
knowledge, and current state in order to produce understanding useful to the trainer.

Possible products include:

- interpretations
- conclusions
- recommendations
- predictions
- decisions

Intelligence does not alter its source evidence.

**Status:** Current, although its precise relationship to Interpretation deserves continued
reconciliation.

---

## Presentation

**Presentation** communicates Overdex's current understanding.

Presentation does not create domain truth and does not alter the institutional record.

Visual, audio, haptic, and future communication mechanisms are forms of Presentation.

```text
Presentation communicates.
It does not decide.
```

**Status:** Current.

---

## Droidball

**Droidball** is Overdex's presentation custodian/interface identity.

Droidball communicates state and understanding to the trainer but does not own the underlying 
domain authority.

**Status:** Current.

---

## Intent

**Intent** is the behavioral meaning assigned to user input.

Intent is independent of physical input device.

Equivalent actions from touchscreen, D-pad, keyboard, controller, voice, or future input mechanisms
may produce equivalent Intent.

```text
physical input ≠ Intent
```

**Status:** Current.

---

## Lineup

**Lineup** is a previously explored concept for coordinating sensory assignments.

Had that architecture continued, Crops may naturally have occupied positions within a Lineup.

Possible relationship:

```text
Lineup
   ↓ coordinates
Crops
   ↓ measure
Measurements
```

The Lineup would coordinate sensory responsibilities without itself measuring or interpreting.

**Status:** Historical / conceptually unresolved. Not currently adopted merely because Crop
now exists.

---

# Retired Vocabulary

## Observation

**Status:** Retired.

Observation became overloaded across runtime, OCR, UI, overlays, and architectural
responsibilities.

It shall not be used for new architectural concepts.

Existing usages are historical artifacts until addressed through focused vocabulary bricks.

---

## Observer

**Status:** Retired.

Observer previously represented an entity capable of perceiving Reality and producing Observations.

Its sensory responsibility is now represented more precisely by Crop.

---

## Witness

**Status:** Retired.

Witness succeeded Observer as the conceptual sensory office.

Further refinement showed that no separate witnessing entity was required between the sensory
boundary and Measurement.

Crop now owns that boundary.

Historical Witness implementations remain historical evidence and are not invalidated merely by
vocabulary retirement.

---

## Testimony

**Status:** Effectively superseded in the current epistemic model; formal retirement status
should be recorded before treating it as constitutionally Retired.**

Testimony previously described the account produced by a Witness.

The current model instead preserves Measurements produced at the Crop boundary.

Do not casually reuse Testimony for new architecture until its status is formally resolved.

---

## Reporter

**Status:** Historical / superseded architecture.

Reporter collected testimony under the older Article custody model.

Timeline-first preservation and the external-Article model appear to eliminate its former constitutional responsibility.

Formal retirement should still be recorded rather than inferred.

---

## Registrar

**Status:** Historical / under review.

Registrar previously established provenance and custody.

Provenance remains constitutionally important.

Whether Registrar remains a distinct owner of that responsibility has not yet been established under Timeline-first custody.

---

## Publisher

**Status:** Historical / apparently superseded, pending formal retirement.

Publisher previously determined when an Article was sufficiently complete to enter permanent history.

Timeline-first custody removes that gate:

```text
OLD
evidence → reasoning → Publisher → permanent history

CURRENT
measurement → Timeline → reasoning → Timeline
```

Information does not need Publisher approval before deserving preservation.

---

## Confidant

**Status:** Historical / under review.

Confidant was proposed as the office responsible for weighing support.

The responsibility survives as Confidence assessment.

Whether that responsibility deserves an independent constitutional component has not
been established.

---

# Current Epistemic Grammar

At this snapshot, the cleanest current grammar is:

```text
Reality
│
├── Article
│
└── Phenomenon
       ↓
──────────────────── sensory boundary
       ↓
      Crop
       ↓
  Measurement
       ↓
    Timeline
       ↓
    Reasoning
     ↙     ↘
Knowledge  Interpretation
               +
           Confidence
               ↓
            Timeline
               ↓
         Intelligence
               ↓
         Presentation
               ↓
            Trainer
```

That diagram should **not yet be mistaken for finalized layer architecture**. In particular,
Reasoning/Intelligence/Knowledge relationships still need reconciliation.

But as a **dictionary snapshot**, I think this is exactly what we need: current meanings,
explicit uncertainty where we haven't settled something, and retired vocabulary quarantined
instead of quietly resurrected because some 800-line Kotlin file still has `Observation` 
tattooed across its forehead.

One correction I would deliberately make to our recent work: I would **not formally declare
`Testimony`, `Reporter`, `Registrar`, `Publisher`, or `Confidant` retired in this snapshot yet**.
We have strong architectural reasons to suspect most are obsolete, but Terms of Ownership gives
retirement actual meaning. We should record what we know rather than use the dictionary to sneak 
constitutional decisions through customs.