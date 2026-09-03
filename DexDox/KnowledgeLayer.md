# Match Understanding

## Philosophy

Overdex does not deal in facts about the live Match.

Reality produces events and evidence. Overdex takes custody of evidence,
measures and characterizes it, and publishes claims about Reality as
Articles. Those Articles become part of the Timeline.

Match Understanding uses that published record together with applicable
Reference Knowledge to maintain Overdex's current best understanding of
the Match. Intelligence reasons from that understanding and may produce
further conclusions, predictions, and recommendations.

The core distinction is:

1. **Reference Knowledge**: What canonical information is available to
   Overdex about an entity or system?

2. **Observations**: What did Overdex measure from the available evidence?

3. **Articles / Timeline**: What claims did Overdex publish, and when did
   those claims enter custody?

4. **Match Understanding**: What does Overdex currently believe about the
   Match, given the published record, confidence, and relevant Reference
   Knowledge?

5. **Intelligence / Inference**: What can Overdex conclude by reasoning
   across Match Understanding, Reference Knowledge, the published record,
   and prior reasoning?

The system must preserve the distinction between what happened in Reality,
what evidence entered custody, what Overdex measured, what Overdex
published, what Overdex currently believes, and what Overdex infers.## Epistemic Authority and Custody

### Reality

Overdex doesn't take custody of Reality's events. It takes custody of
evidence and publishes claims about Reality as Articles.

Reality is outside Overdex's authority.

A game event occurs and produces evidence. That evidence originates in
Reality. Overdex may take the evidence into custody, measure it, and
preserve it, but does not thereby become authoritative over the Reality
that produced it.

Reality may also present information that appears factual to a human
observer. Overdex does not automatically promote that information to
truth.

```text
REALITY                              OVERDEX

Subject
   │
   │ something happens
   ▼
Event
   │
   │ produces
   ▼
Evidence ───────────────────────────► custody
                                        │
                                        ▼
                               characterization
                                        │
                                        ▼
                              Observation(s)
                                        │
                                        ▼
                         Subject + Verb + Predicate
                                        │
                                        ▼
                                     Article
                                        │
                                        ▼
                                    Timeline###
                                     
### Measurement

A Measurement is Overdex's characterization of evidence.

It answers:

> "What did Overdex measure?"

A Measurement retains enough provenance to identify its evidence source,
instrument, time, measured value, and confidence.

A Measurement is not the underlying evidence itself and does not infer
what that evidence means tactically.
### Reference Knowledge

Reference Knowledge is canonical information available for consultation.

It answers:

> "What information does Overdex have about the thing we are observing?"

Reference Knowledge does not become a claim that the live Match is currently in that state.

### Match Understanding

Match Understanding is Overdex's current best representation of the live
Match.

It is mutable because new Articles and relevant Reference Knowledge may
change what Overdex currently believes.

It may contain current believed species identities, current believed move
sets, estimated energy, active Pokémon, shield state, confidence,
relationships between entities, and other state derived from the
published Match record.

Match Understanding is not final truth.

New information may increase confidence, decrease confidence, supersede an
earlier understanding, reveal a contradiction, or withdraw an earlier
conclusion.

The Measurements, evidence, and Articles that produced an understanding
remain available in Timeline custody so Overdex can explain how and why
its understanding changed.


### Intelligence

Intelligence reasons over the published record, Reference Knowledge,
Match Understanding, and prior reasoning.

Intelligence does not manufacture facts.

It produces inferences and conclusions, each with appropriate confidence
and supporting provenance.

Examples include estimating enemy energy, identifying likely move
availability, predicting future Match behavior, evaluating a Decision
Point and its Choices, and determining whether a shield is likely
advisable.

When an inference or conclusion is preserved, it may be published as a
new Article without altering the record from which it was derived.

## Evidence → Publication → Understanding → Intelligence

Reality
   │
   │ Event produces
   ▼
Evidence
   │
   │ measured / characterized
   ▼
Measurement(s)
   │
   │ inform
   ▼
Subject + Verb + Predicate
   │
   │ published as
   ▼
Article
   │
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
Inference / Prediction / Decision Analysis
   │
   ▼
Recommendation

Intelligence output
       ↓
Subject + Verb + Predicate
       ↓
Article
       ↓
Timeline

Reference Knowledge enriches Overdex's understanding of the published record. It does not substitute for evidence or measurement of the live Match.

## Confidence

Overdex does not need to declare something a fact in order to make it
useful.

Measurements, published claims, current Match Understanding, and
inferences may each carry confidence appropriate to what they represent.

Confidence belongs to the statement being made at that epistemic level.
It is not automatically inherited from the confidence of its inputs.

These values do not progressively become facts. They express how strongly
Overdex supports a particular measurement, claim, understanding, or
conclusion given the information available at that time.


## Contradiction and Supersedence

Contradictory Measurements and published claims must not be erased merely
because later information has higher confidence.

For example:

```text
Article A
Claim:
    Opponent appears to be Swampert
Confidence: 0.83

Article B
Claim:
    Opponent appears to be Charizard
Confidence: 0.97


## Provenance and Traceability

A conclusion should be traceable backward through the reasoning and
information that produced it.

```text
Recommendation
   ↓
Inference / Intelligence
   ↓
Match Understanding at that time
   ├── Reference Knowledge
   └── relevant Articles
          ↓
      Measurements
          ↓
        Evidence
          ↓
        Reality
        
        
        
## Reference Enrichment

When an Observation identifies an entity, relevant Reference Knowledge can be retrieved.

Example:

```text
Observation:
Opponent appears to be Mewtwo
Confidence: 0.94

Reference Knowledge:
Mewtwo
- Psychic type
- known fast moves
- known charged moves
- other canonical reference data

Match Understanding:
Opponent is currently believed to be Mewtwo
Confidence: 0.94
Relevant reference data attached/available

Intelligence:
Compare observed behavior with Mewtwo's known possibilities
```
Reference enrichment should be relevant to the current subject, published claim, or reasoning context. The Match does not need to copy the entire Pokédex into every piece of Match state.

## Authority and Custody Rules

| Information | Authority / Source | Custody |
| --- | --- | --- |
| Reality event | Reality | Not taken into Overdex custody |
| Evidence produced by Reality | Reality | Article → Timeline |
| Measurement of evidence | Producing instrument / measurement | Article → Timeline |
| Canonical species/reference data | Reference Knowledge | Reference Knowledge |
| Current believed Match state | Match Understanding / Battle Memory | Mutable current state; changes may be preserved as Articles |
| Reasoning process | Intelligence | May be preserved through Articles |
| Derived conclusion | Intelligence / Inference | Article → Timeline when published |
| Player-facing recommendation | Intelligence | Article → Timeline when preserved |

The authority test is:

> **Who is responsible for determining or changing this representation?**

The custody test is:

> **Where is the record preserved once it enters Overdex history?**

Reality is authoritative for what actually occurs. Overdex does not take
custody of Reality's events.

Evidence may originate in Reality and enter Overdex custody without
becoming Overdex's truth.

Measurements characterize evidence. They do not become authority over
the Reality they describe.

Canonical reference material remains under Reference Knowledge.

Match Understanding is authoritative for Overdex's current belief about
the Match, not for Reality's actual Match state.

Intelligence is authoritative for the conclusions, predictions, and
recommendations it produces.

Published claims enter custody as Articles. The Timeline is the final
custodian of those Articles.


## Current Implementation

The current implementation contains historical and transitional
representations of this model.

Implementation types do not define the architectural boundaries described
above. In particular, legacy types may combine responsibilities that the
architecture now distinguishes.

Current concepts include:

- `PokemonKnowledge` provides canonical Reference Knowledge.
- `Pokemon` represents canonical Pokémon Reference Knowledge.
- `BattleMemory` maintains mutable current Match Understanding.
- `MatchupEngine` performs matchup reasoning.
- `DecisionEngine` produces tactical reasoning and recommendations.
- `BattleEvent` and `BattleInterpreter` are existing implementation
  concepts whose historical semantics should not be treated as the
  architectural definition of events, Measurements, Articles, or
  Timeline custody.

The implementation should evolve by clarifying authority and custody
boundaries rather than by introducing layers solely because a diagram
contains a box for them.

Measurements, published claims, and the history from which current state
was derived remain traceable through Articles in Timeline custody.

## Design Principles

1. **Do not call live Match information a fact merely because Reality or the game presented it.**

2. **Preserve the distinction between Reality, evidence, measurement, published claims, current understanding, and inference.**

3. **Confidence belongs to the statement being made at its epistemic level.**
   Measurements, Articles, Match Understanding, and conclusions may each
   carry confidence appropriate to what they represent.

4. **Never erase contradictory evidence, Measurements, or published claims merely because later understanding supersedes them.**

5. **Reference Knowledge enriches understanding; it does not establish live Match truth.**
   Match evidence may enrich the Reference Knowledge available for that
   Match without rewriting canonical Reference Knowledge.

6. **Battle Memory is authoritative for Overdex's evolving Match Understanding, not for Reality's actual Match state or the evidence that produced that understanding.**

7. **Intelligence reasons across the published record, Reference Knowledge, Match Understanding, and prior reasoning.**

8. **Every important conclusion should be traceable to the Articles, Measurements, evidence, and Reference Knowledge that supported it at the time it was produced.**

9. **Do not create architectural layers merely to satisfy a diagram.**

10. **When authority or custody is unclear, resolve that boundary before adding code.**