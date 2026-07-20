# The Thinking Chair

This document records the significant architectural decisions that define Overdex. It preserves the reasoning, context, and alternatives considered for every major turning point in the project's evolution.

Architecture is memory. Git remembers code; the Thinking Chair remembers why.

---

### 2026-07-15

Decision

Droidball is an Operator, not a Mascot.

Context

Early designs considered a "mascot" character to provide tips and flavor text (similar to Clippy or Rotom).

Alternatives Considered

- **The Custodian**:
- **The Helper**: A character that speaks to the trainer and offers unsolicited advice.
- **The Guide**: A tutorial-driven interface that explains every screen.

Decision

Droidball as a Field Instrument.

Reasoning

Overdex is an instrument, not a game. An instrument should be reliable, silent, and mechanical. By making Droidball a physical part of the instrument's interface (the operator of the observation layer), we maintain the "Handheld" identity and avoid the cognitive noise of a "talking" assistant.

Consequences

Droidball has no dialogue. He communicates through mechanical movement and visual results. He never gives "opinions," only reveals knowledge.

Status

Accepted

---

### 2026-07-16

Decision

The Observation Architecture (Observe -> Remember -> Understand -> Present).

Context

Initial prototypes mixed OCR logic with UI rendering and energy counting, leading to a "god object" problem and fragile battle logic.

Alternatives Considered

- **Monolithic Engine**: A single service that handles screen capture, processing, and display.
- **Event-Driven UI**: UI components reacting directly to OCR results.

Decision

Strictly Layered Responsibility (Observation -> Memory -> History -> Archive -> Intelligence -> Presentation).

Reasoning

By separating "Fact" (Observation) from "Interpretation" (Intelligence), we ensure that an error in move-counting (Intelligence) doesn't corrupt the record of what was actually seen (Observation). This allows us to improve the reasoning engine without losing the integrity of the raw evidence.

Consequences

Information flows strictly downward. A layer can never "edit" the information produced by the layer above it. Observation produces immutable facts.

Status

Accepted

---

### 2026-07-17

Decision

Replay is based on Observation Reconstruction, not Video.

Context

How should we store and replay battles for later analysis?

Alternatives Considered

- **Screen Recording**: High storage cost, impossible to analyze programmatically, captures UI noise.
- **Keyframe Snapshots**: Frequent screenshots of the battle.

Decision

Event-Driven Reconstruction.

Reasoning

An Overdex "Archive" should be an evidence log, not a movie. By replaying from recorded Battle Events (e.g., "Species Identified," "Energy Gained"), we can reconstruct the battle using a simplified, CRT-style simulation. This makes replays searchable, lightweight, and capable of being analyzed by the Intelligence layer long after the battle ends.

Consequences

Replay audio and visuals are synthetic and mechanical, reinforcing the "archived evidence" metaphor. Replays can be rendered at any resolution or style.

Status

Accepted

---

### 2026-07-18

Decision

Overdex as a Data Custodian (Pokédex vs. Binders).

Context

Should Overdex be a "helper app" for Pokémon GO, or something else?

Alternatives Considered

- **Tactical Overlay**: A tool used only during battles to show matchups.
- **Collection Manager**: A spreadsheet-style list of Pokémon.

Decision

The Digital Binders metaphor.

Reasoning

The Pokédex documents the *species* (the game world), but Binders document the *trainer's relationship* with those species (the player's world). This defines Overdex as a custodian of the trainer's journey. It moves the value of the app from "helping you win one battle" to "building a library of your competitive career."

Consequences

Introduced the `Specimen` identity. Focus shifted toward per-specimen history, notes, and lineage.

Status

Accepted

---

### 2026-07-19

Decision

Battle Preview becomes Test Battle.

Context

The screen no longer previews upcoming gameplay. It injects deterministic scenarios into the renderer to validate HUD behavior.

Alternatives Considered

- Battle Workshop
- HUD Workshop

Decision

Test Battle.

Reasoning

"Preview" implies a prediction of a future state. "Test Battle" accurately describes the screen's purpose: testing the instrument's ability to communicate complex battle states using synthetic data.

Consequences

The terminology audit confirmed that "Preview" was a legacy name. Replay and Live Battle are now the primary "providers" of real data, while Test Battle provides synthetic verification.

Status

Accepted
