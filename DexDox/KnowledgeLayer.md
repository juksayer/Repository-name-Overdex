# Match Understanding

## Philosophy

Overdex does not deal in facts about the live Match.

Reality produces events and evidence. Overdex measures those events through its observation systems, assigns confidence to those measurements, enriches the resulting understanding with Reference Knowledge, and uses Intelligence to draw conclusions.

The core distinction is:

1. **Reference Knowledge**: What canonical information is available to Overdex about an entity or system?
2. **Observations**: What did Overdex measure about an event in Reality?
3. **Match Understanding**: What does Overdex currently believe about the Match, given its observations, their confidence, and relevant Reference Knowledge?
4. **Intelligence / Inference**: What can Overdex conclude by reasoning across observations, Reference Knowledge, current Match Understanding, and prior inferences?

The system must preserve the distinction between what happened in Reality, what Overdex measured, what Overdex currently believes, and what Overdex infers.

## Epistemic Ownership

### Reality

Reality is outside Overdex's ownership.

A game event occurs and produces evidence. That evidence belongs to the event that produced it. Overdex does not rewrite or own the underlying evidence.

Reality may also present information that appears factual to a human observer. Overdex does not automatically promote that information to truth.

### Observation

An Observation is Overdex's measurement of something occurring in Reality.

It answers:

> "What did Overdex measure about this event?"

An Observation should retain enough provenance to identify the event, observer, time, measurement, and confidence.

An Observation is not the underlying evidence itself.

### Reference Knowledge

Reference Knowledge is canonical information available for consultation.

It answers:

> "What information does Overdex have about the thing we are observing?"

Reference Knowledge does not become a claim that the live Match is currently in that state.

### Match Understanding

Match Understanding is Overdex's current best representation of the live Match.

It is mutable because new measurements can change it.

It may contain current believed species identities, observed moves, estimated energy, active Pokémon, shield state, confidence, relationships between observed entities, and other state derived from accumulated evidence.

Match Understanding is not final truth.

A new measurement may increase confidence, decrease confidence, supersede an earlier understanding, reveal a contradiction, or withdraw an earlier conclusion.

The underlying observations remain available so the system can explain how its understanding changed.

### Intelligence

Intelligence reasons over Observations, Reference Knowledge, Match Understanding, and prior inferences.

Intelligence does not manufacture facts.

It produces inferences and conclusions, each with appropriate confidence and supporting provenance.

Examples include estimating enemy energy, identifying likely move availability, predicting a future Match Event, evaluating a Decision Point, and determining whether a shield is likely advisable.

## Observation → Understanding → Intelligence

```text
Reality
   ↓
Event + Evidence
   ↓
Observation
   ↓
Match Understanding  ←── Reference Knowledge
   ↓
Intelligence
   ↓
Inference
   ↓
Recommendation / Predicted Match Event
```

Reference Knowledge enriches Overdex's understanding. It does not replace observation.

## Confidence

Overdex does not need to declare something a fact in order to make it useful.

Every measurement and inference should carry an appropriate level of confidence.

These values are not progressively becoming facts. They represent the system's confidence in increasingly derived statements.

## Contradiction and Supersedence

Contradictory measurements must not be erased merely because a later measurement has higher confidence.

For example:

```text
Observation A
Opponent appears to be Swampert
Confidence: 0.83

Observation B
Opponent appears to be Charizard
Confidence: 0.97
```

The current Match Understanding may favor Charizard, but Observation A remains part of the historical record.

The system should be able to represent current understanding, previous understanding, supporting observations, confidence changes, and superseded or withdrawn conclusions.

## Provenance and Traceability

A conclusion should be traceable backward through the reasoning that produced it.

```text
Recommendation
   ↓
Inference
   ↓
Match Understanding
   ↓
Observations
   ↓
Reality Event / Evidence
```

Reference Knowledge should also remain identifiable as reference input to the reasoning.

This allows Overdex to answer:

> "Why did you make this recommendation?"

without pretending that the recommendation was a fact.

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

Reference enrichment should be relevant to the observed event. The Match does not need to copy the entire Pokédex into every piece of Match state.

## Ownership Rules

| Information | Owner |
|---|---|
| Event and its underlying evidence | Reality |
| Measurement of an event | Observation |
| Canonical species/reference data | Reference Knowledge |
| Current live battle state | Match Understanding / Battle Memory |
| Reasoning process | Intelligence |
| Derived conclusion | Inference |
| Player-facing action guidance | Recommendation |

The ownership test is:

> **Who owns the truth represented by this information?**

If the answer is Reality, Overdex should preserve a reference to it rather than pretending to own it.

If it is a measurement, it belongs to Observation.

If it is canonical reference material, it belongs to Reference Knowledge.

If it describes the current state Overdex believes the Match is in, it belongs to Match Understanding.

If it is a conclusion reached by comparing evidence and knowledge, it belongs to Intelligence / Inference.

## Current Architecture

The current implementation already contains pieces of this model:

```text
RealityArticle
    ↓
BattleInterpreter
    ↓
BattleEvent
    ↓
BattleMemory
    ↓
Reference Knowledge
    ↓
MatchupEngine
    ↓
DecisionEngine
```

The architecture should evolve by clarifying these ownership boundaries rather than by introducing layers solely because a diagram contains a box for them.

In particular:

- `PokemonKnowledge` provides Reference Knowledge.
- `Pokemon` is the canonical Reference Knowledge model.
- `BattleEvent` represents interpreted battle information.
- `BattleMemory` retains evolving Match state.
- `MatchupEngine` reasons about matchup relationships.
- `DecisionEngine` produces tactical conclusions.
- Observations and Reality history remain traceable outside the derived state.

## Design Principles

1. **Do not call live Match information a fact merely because the game presented it.**
2. **Preserve the distinction between Reality, measurement, current understanding, and inference.**
3. **Confidence belongs with measurements and conclusions.**
4. **Never erase contradictory evidence merely because a newer conclusion supersedes it.**
5. **Reference Knowledge enriches understanding; it does not establish live Match truth.**
6. **Battle Memory owns evolving Match state, not the underlying evidence that produced it.**
7. **Intelligence reasons across observations, reference material, Match state, and prior inference.**
8. **Every important conclusion should be traceable to its supporting observations and reference inputs.**
9. **Do not create architectural layers merely to satisfy a diagram.**
10. **When ownership is unclear, resolve the ownership question before adding code.**