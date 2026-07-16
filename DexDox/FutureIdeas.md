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
- ## HUDphones

HUDphones is an audio presentation layer for the Observation Engine.

Rather than requiring the trainer to constantly observe the display, Overdex may communicate battle intelligence through concise spoken callouts delivered via Bluetooth earbuds, bone-conduction headphones, or other future audio devices.

The Observation Engine remains the single source of truth.

HUDphones is only another presentation layer.

Possible callouts:

- "Enemy identified."
- "Fast move confirmed."
- "Charged move ready."
- "Shield recommended."
- "Switch available."
- "Super effective."

Possible accessibility benefits:

- Blind and low-vision battle assistance.
- Reduced visual attention during PvP.
- Hands-free battle awareness.

Guiding principle:

Observe.

Understand.

Communicate.

HUDphones belongs entirely in the "communicate" layer.
- Visual language exploration

---
## Battle Haiku

An optional second flavor text for each Pokémon.

Unlike the official Pokédex entry, Battle Haiku describe the Pokémon's competitive identity rather than its biology or lore.

The purpose is not instruction.

The purpose is to capture the feeling of battling with that Pokémon.

Examples of themes:

- Patience
- Momentum
- Sacrifice
- Endurance
- Precision
- Pressure
- Adaptation

Battle Haiku should remain concise, timeless, and open to interpretation.

They should read like martial proverbs rather than strategy guides.

The Pokédex explains what a Pokémon is.

The Battle Haiku suggest what it becomes in battle.
# Artificial Intelligence

Ideas involving AI-assisted reasoning, explanation, or recommendation belong here until they mature into concrete designs.

---

# Someday / Maybe

Interesting ideas that are worth remembering but have no immediate implementation path.

No idea is too strange to record.

Some of Overdex's defining features began here.

I would make one small change.

Don't rename My Collection yet.

Ship it.

Let it stabilize.

Then, when binders become a real feature, you can do something like:

My Collection
└── Binders

or even rename the module once binders actually exist.

Right now, "My Collection" is a good description of what you've built. "My Binders" implies organization, pages, custom layouts, and curation—which I think is where you're headed, but not where the code is today.

The exciting part is this: you didn't just think of a UI. You may have discovered the organizing metaphor for the entire ownership side of Overdex. And good metaphors tend to make lots of later decisions easier.

The instrument may bend reality to improve understanding, but it should never break its own identity.

Guided calibration
Droidball confidence behavior
Dual-resolution Droidball
Expanding maintenance controls
Observation wizard
Image-under-box editing

replay engines,
observation logs,
digital coaching,
Trainer DNA,
handheld industrial design,
Paper Mario-style battle visualization

// TODO (Architecture):
// PokedexFrame currently owns BattleMemory and Matchup analysis.
// Long-term this should be provided by a ViewModel or controller.
// Keep shell responsible only for rendering and hardware behavior.

"Rename OwnedPokemon → Specimen throughout the codebase."


## Columnar Anchor Clusters (CAC)

A proposed spatial observation model for long-form or dynamic interfaces.

Rather than treating each frame as an isolated bitmap, the Observation Engine establishes stable spatial anchor clusters that persist across multiple observations.

Each cluster represents a logical region of information rather than a fixed pixel location.

Possible applications:

- Scrolling Pokémon detail pages
- Live battle observation
- Replay analysis
- Droidball observation
- Future observation sources

The purpose of CAC is not image stitching.

The purpose is to provide the Observation Engine with a stable spatial reference system that survives scrolling, animation, and changing viewports.