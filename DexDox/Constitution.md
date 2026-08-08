# Constitution of Overdex

The Constitution defines the rules that should almost never change.

If another document disagrees with this one, the Constitution wins.

---

## Foundations

The Pokédex is the foundation.

Knowledge before observation.

Facts before conclusions.

Observe before inferring.

Trust observed data.

Respect existing architecture.

The smallest successful change is usually the correct change.

---

## Development

Build first.

Show that it happened.

One feature.

One commit.

One test.

Working code is not a problem to solve.

---

## Architecture

Each layer owns one responsibility.

Knowledge knows.

---!← TERMINOLOGY AUDIT:
"Knowledge" may predate the formal distinction between Reference Knowledge
and Dynamic Knowledge. Do not change yet. Revisit after Architecture,
Ownership, and KnowledgeLayer have been reconciled.
→

Observation observes.
ObservationDispatcher owns observer lifecycles, not observation logic

Memory remembers.

History organizes.

Archive preserves.

Intelligence understands.

Presentation communicates.

Article is the root.

Everything in Overdex is about an Article.

---

## Interface

Every pixel earns its place.

Recognition is faster than reading.

The CRT owns focus.

A confirms.

B returns.

SELECT performs contextual actions.

START launches Droidball.

---

## Trust

Never pretend certainty.

Confidence is earned.

Evidence beats assumptions.

When uncertain, say so.

Trust is the product.

Modules communicate through Articles.

---

## Litmus Test

Ask these questions before implementing a feature:

- Would this exist on a dedicated Overdex handheld?
- Does it reduce cognitive load?
- Does it preserve player agency?
- Does it strengthen the architecture?
- Can it be explained by the layer that owns it?

If the answer is "no," reconsider the design.

An Article is the canonical aggregation point for evidence and confidence.

---

## Phenomena

- Phenomena enter the institution. Presentations leave the institution. 
- Everything else is internal constitutional process.