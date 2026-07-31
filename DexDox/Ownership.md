# Ownership Audit

Purpose

Every concept in Overdex should have exactly one owner.

Ownership defines responsibility.

Responsibility defines boundaries.

When ownership is unclear, architecture begins to drift.

---

# Rules

Every concept should have one primary owner.

Other layers may consume information.

Other layers may transform information.

Ownership never transfers.

If multiple layers appear to own the same concept, reconsider the design.

---

# Knowledge

Owns:

- Pokémon species
- Moves
- Typing
- Base statistics
- Forms
- Evolution
- Static game data

Does not own:

- Current battle
- Observations
- Recommendations
- Trainer history

Question:

Can this information exist before a battle begins?

If yes, Knowledge probably owns it.

---

# Observation

Owns:

- Raw observations
- Evidence
- Confidence
- Observation provenance
- Observation sessions
- Active observation workspace

Does not own:

- Battle history
- Trainer preferences
- Recommendations

Question:

Was this directly observed?

If yes, Observation probably owns it.

---

# Memory

Owns:

- Current battle state
- Working memory
- Temporary calculations
- Live battle context

Does not own:

- Permanent history
- Static knowledge

Question:

Would losing power erase this without harming the trainer's long-term record?

If yes, Memory probably owns it.

---

# History

Owns:

- Battle timelines
- Ordered events
- Chronology
- Session reconstruction

Does not own:

- Rendering
- OCR
- Species database

Question:

Does ordering matter?

If yes, History probably owns it.

---

# Archive

Owns:

- Persistence
- Storage
- Retrieval
- Long-term preservation

Does not own:

- Interpretation
- UI
- Recommendations

Question:

If every ViewModel disappeared, would this still be worth keeping?

If yes, Archive probably owns it.

---

# Intelligence

Owns:

- Recommendations
- Pattern recognition
- Matchup analysis
- Prediction
- Divination
- Coaching
- Explanation

Never owns:

- Facts
- Evidence
- History

Question:

Is this an interpretation?

If yes, Intelligence probably owns it.

---

# Presentation

Owns:

- CRT
- HUD
- Audio
- Trainer communication
- Replay rendering
- Visual language

Never owns:

- Battle state
- Knowledge
- Recommendations

Presentation communicates.

It never decides.

---

# Cross-Layer Ownership

Knowledge
↓

Observation
↓

Memory
↓

History
↓

Archive
↓

Intelligence
↓

Presentation

Information flows downward.

Ownership does not.

---

# Ownership Smells

A class probably has the wrong owner if it:

- Stores knowledge and observations together.
- Performs OCR while rendering UI.
- Generates recommendations during persistence.
- Knows about Android widgets inside the engine.
- Mutates history while presenting it.
- Requires another layer to explain its own data.

---

# Ownership Questions

When reviewing any class:

What does this class own?

What does it consume?

What does it produce?

Could another layer own this more naturally?

Does this class know something it should merely observe?

Does this class remember something it should archive?

Does this class interpret something it should merely present?

---

# Litmus Test

Ownership should be explainable in one sentence.

If ownership requires a paragraph...

ownership is probably wrong.



OBSERVATION
owns:
  Observations
  Evidence
  Provenance
  ObservationSession
  ObservationWorkspace

REFERENCE KNOWLEDGE
owns:
  established game facts used as evidence/context

        Observation + Reference Knowledge
                     ↓
INTELLIGENCE
  weighs available evidence
  calculates Confidence
  interprets Confidence
                     ↓
PRESENTATION
  represents Confidence
  communicates it to trainer


The Observation Session Workspace represents the observations and supporting evidence accumulated during an active observation session.
| Responsibility                                      | Owner                      | Why                                                              |
| --------------------------------------------------- | -------------------------- | ---------------------------------------------------------------- |
| Physical input (keyboard, touch, controller, voice) | Observation                | Normalizes platform-specific events into observations.           |
| Intent                                              | Intent Layer               | Converts observations into device-independent intents.           |
| Session lifetime                                    | SessionManager             | Owns the current application session.                            |
| Navigation history                                  | SessionManager             | Owns the workspace stack.                                        |
| Active workspace                                    | SessionManager (derived)   | Computed as the top of the workspace stack.                      |
| Workspace state                                     | Individual Workspace       | Each workspace owns its own domain state.                        |
| Selection                                           | Individual Workspace       | `selectedPokemon`, `selectedRegion`, etc. No universal cursor.   |
| Domain logic                                        | Individual Workspace       | Acts on intents within its own domain.                           |
| Navigation requests                                 | Workspace → SessionManager | Workspace requests navigation; SessionManager mutates the stack. |
| Presentation                                        | Droidball                  | Embodiment of the Presentation layer.                            |
| CRT/LCD/Overlay/LEDs/Sound/Haptics                  | Droidball                  | Different presentation media, one presenter.                     |
| Rendering                                           | Droidball                  | Consumes exposed workspace state and presents it.                |


Then I'd capture the architectural rules we've discovered alongside it:

Rule 1

Observation never assigns meaning.

It reports:

A pressed
Screen tapped
Voice command heard

Nothing more.

Rule 2

Intent is device-independent.

Whether it came from:

keyboard
touchscreen
controller
voice

the workspace receives the same intent.

Rule 3

Modules expose state.

They do not render themselves.

Rule 4

Presentation belongs to Droidball.

Everything the trainer experiences is mediated through Droidball.

Rule 5

SessionManager owns navigation, not workspaces.

It owns:

workspaceStack

and exposes

activeWorkspace

as derived state.

Rule 6

Prefer authoritative state over duplicated state.

For example:

workspaceStack
↓
activeWorkspace

instead of storing both.

Navigation Context Ownership Invariant

Only the component that currently owns the active navigation context may dispatch navigation 
intents. Intents originating from stale contexts must be ignored rather than allowing navigation 
state to become inconsistent.