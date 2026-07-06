# Future Ideas

This document is intentionally unfinished.

It exists to preserve ideas before they are forgotten.

Nothing in this document should be considered planned, scheduled, or guaranteed.

These are possibilities.

---

# Architecture

## Event Payloads

Consider replacing generic values with event-specific payloads once `BattleEvent` stabilizes.

---

# Handheld Experience

## Handheld vs Android Interaction

The search keyboard currently transitions the player from the Overdex handheld experience into a standard Android interaction.

This creates two interaction models:

- Overdex handheld
- Android touch input

The long-term goal is to remain inside the handheld metaphor whenever practical.

Possible future exploration:

- Search without immediately invoking the Android keyboard.
- D-pad-first browsing.
- Device-native text entry.
- Quick filters.
- Search shortcuts.

---

# Divination Engine

## Philosophy

Overdex should distinguish between information that is:

- **Observed**
- **Divined**
- **Reconciled**

Observed information is directly confirmed.

Divined information represents the engine's best current explanation based upon known mechanics and accumulated evidence.

Reconciled information represents previously divined values that have returned to agreement with new observations.

Possible state flow:

```
OBSERVED
    ↓
DIVINED
    ↓
RECONCILED
    ↓
OBSERVED
```

Future ideas:

- Integrity Indicator
- Observation confidence
- Divination confidence
- Reconciliation visualization
- Alternative presentation methods

The purpose is not to pretend certainty.

The purpose is to remain useful during uncertainty.

---

# Research

Ideas that require experimentation before becoming architecture belong here.

Successful experiments may eventually migrate into Architecture, Philosophy, or the Constitution.

---

# User Experience

Future interaction concepts belong here before implementation.

Examples:

- Hardware interaction
- Navigation experiments
- Accessibility ideas
- Visual language exploration

---

# Artificial Intelligence

Ideas involving AI-assisted reasoning, explanation, or recommendation belong here until they mature into concrete designs.

---

# Someday / Maybe

Interesting ideas that are worth remembering but have no immediate implementation path.

No idea is too strange to record.

Some of Overdex's defining features began here.