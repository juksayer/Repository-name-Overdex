# Overdex Ownership Audit

> *Architecture is discovered through ownership, not class names.*
>
> The purpose of this audit is to identify the authoritative owner of every responsibility in the system before making architectural decisions. Components are justified by the unique authority they possess—not by the methods they expose.

---

# Architectural Taxonomy

Every architectural component belongs to one of three categories.

## 🏛 Foundational Components

Foundational components define architectural boundaries.

Removing one would require the architecture itself to be redrawn.

Examples:

- Application
- Observation
- Intent
- Workspaces
- Knowledge
- Droidball

---

## ✅ Independent Components

Independent components own unique authority within an architectural boundary.

Removing one leaves a capability without an owner, but does not redefine the architecture itself.

Examples may include:

- BattleMemory
- MediaManager
- CalibrationManager

---

## 🌿 Derived Components

Derived components derive their authority from another owner's responsibility.

They do not justify their own existence.

Their existence is justified by a Foundational or Independent component.

Examples may include:

```
Observation
    ├── CaptureTemplateManager
    ├── RegionDetector
    └── OCRCoordinator

SessionManager
    └── NavigationController
```

---

# Ownership

## Application

### Authority

Composition root of the system.

### Boundary

Inside:
- Application lifetime
- Manager registration
- Workspace registration
- Global configuration
- Service composition

Outside:
- Domain logic
- Navigation
- Presentation
- Observation

### Owns

- ApplicationState

### Mutates

- Application lifetime
- Global composition

### Publishes

- `ApplicationState`

---

## Observation

### Authority

Exclusive ownership of physical observation.

### Boundary

Inside:
- Keyboard
- Touch
- Controller
- Voice
- OCR
- Sensors
- Future physical input

Outside:
- Intent
- Navigation
- Domain logic
- Rendering

### Owns

Observation pipeline.

### Mutates

Observation state.

### Publishes

- `Observation`

Observation reports facts.

It never assigns meaning.

Example:

```
A Pressed
Touch at (140,80)
Voice Heard
OCR Text Found
```

---

## Intent

### Authority

Translation from observations into application intent.

### Boundary

Inside:
- Intent generation
- Device abstraction

Outside:
- Observation
- Domain logic
- Navigation implementation
- Presentation

### Owns

Intent generation.

### Mutates

Intent production.

### Publishes

- `Intent`

Example:

```
NavigateUp
NavigateDown
ActivateSelection
SearchSpecimen("Charizard")
GoBack
```

---

## SessionManager

### Authority

Exclusive ownership of application sessions and navigation state.

### Boundary

Inside:
- Session lifetime
- Navigation history
- Workspace stack

Outside:
- Workspace logic
- Presentation
- Observation

### Owns

Authoritative state:

```
workspaceStack
```

Derived state:

```
activeWorkspace
```

### Mutates

Only SessionManager may mutate:

```
workspaceStack
```

### Publishes

- `SessionState`

---

## Workspace

### Authority

Exclusive ownership of domain state.

### Boundary

Inside:
- Domain logic
- Selection
- Workspace state

Outside:
- Navigation implementation
- Rendering
- Observation

### Owns

Examples:

```
PokedexState.selectedPokemon

CalibrationState.selectedRegion

ReplayState.selectedEvent

TournamentState.selectedMatch
```

There is intentionally **no universal Cursor object**.

Selection belongs to each domain.

### Mutates

Its own domain state.

### Publishes

- `WorkspaceState`

---

## Droidball

### Authority

Exclusive ownership of presentation.

### Boundary

Inside:
- CRT
- LCD
- Overlay
- LEDs
- Sound
- Haptics
- Animation

Outside:
- Domain logic
- Navigation
- Observation
- Knowledge

### Owns

Presentation.

### Mutates

Presentation state.

### Publishes

- `Presentation`

Everything the trainer experiences is mediated through Droidball.

---

# Contracts

Each architectural layer publishes exactly one public language.

| Layer | Consumes | Publishes |
|--------|----------|-----------|
| Application | — | ApplicationState |
| Observation | Physical Events | Observation |
| Intent | Observation | Intent |
| SessionManager | Session / Navigation Intent | SessionState |
| Workspace | Intent | WorkspaceState |
| Droidball | SessionState + WorkspaceState | Presentation |

Layers communicate only through these published contracts.

---

# Constitutional Rules

## Rule 1

Observation never assigns meaning.

It reports facts.

---

## Rule 2

Intent is device-independent.

A workspace should not know whether an intent originated from:

- Keyboard
- Touch
- Controller
- Voice
- Trainer Comms
- Automation
- Future input devices

---

## Rule 3

Modules expose state.

Modules do not render themselves.

---

## Rule 4

Presentation belongs to Droidball.

Everything the trainer experiences is mediated through Droidball.

---

## Rule 5

SessionManager owns navigation.

It owns:

- Session lifetime
- Navigation history
- workspaceStack

It exposes:

- activeWorkspace (derived)

---

## Rule 6

Prefer authoritative state over duplicated state.

Example:

```
workspaceStack
        ↓
activeWorkspace
```

instead of storing both.

---

## Rule 7

Each architectural layer has one public language.

```
Application
    publishes ApplicationState

Observation
    publishes Observation

Intent
    publishes Intent

SessionManager
    publishes SessionState

Workspace
    publishes WorkspaceState

Droidball
    publishes Presentation
```

Nothing reaches across layers.

---

## Rule 8

Ownership implies mutation.

Everyone else observes.

Example:

Only SessionManager mutates:

```
workspaceStack
```

Everyone else observes:

```
SessionState
```

Only a workspace mutates:

```
selectedPokemon
```

Everyone else observes:

```
PokedexState
```

---

## Rule 9

Every top-level component shall justify its existence by owning unique architectural authority.

Authority may include:

- Authoritative state
- Exclusive mutation rights
- Coordination responsibility
- External resource ownership
- Lifecycle ownership

If two top-level components exercise the same authority, they shall be examined for consolidation or separation.

---

## Rule 10

Managers must justify their existence.

A class named *Manager is presumed to be an implementation detail until it demonstrates unique architectural authority.

A manager survives the ownership audit only if it owns one or more of:

- Authoritative state
- Exclusive mutation rights
- Coordination responsibility
- An external resource or lifecycle

If another component already owns those responsibilities, the manager should be considered for consolidation.

---

# Component Audit Template

Every top-level component is evaluated using the following template.

```md
## Component

### Authority

What unique architectural authority does this component possess?

---

### Boundary

Inside:
...

Outside:
...

---

### Owns

Authoritative state.

---

### Mutates

What it alone may change.

---

### Publishes

Its single public language.

---

### Consumers

Who consumes that language?

---

### Removal Test

What loses its authoritative owner if this component disappears?

---

### Audit Status

🏛 Foundational

✅ Independent

🌿 Derived

🔄 Candidate for consolidation

❓ Needs investigation
```

---

# Architectural Tests

Every proposed component should be evaluated using these questions.

## Ownership Test

Who mutates this?

---

## Language Test

What single public language does it publish?

---

## Authority Test

What unique authority does it possess?

---

## Boundary Test

What is inside its responsibility?

What is explicitly outside it?

---

## Removal Test

What loses an authoritative owner if this component disappears?

---

## Consolidation Test

Does another component already own this authority?

If so, why should this exist independently?

---

# Admission Test

Before introducing a new top-level architectural component, it must answer the following questions.

## Authority

What unique architectural authority does this component own?

## Ownership

What authoritative state does it own and mutate?

## Language

What single public language does it publish?

## Boundary

Which architectural boundary does it belong to?

## Necessity

Why can an existing component not own this authority?

## Derivation

Why is this not a derived component of an existing owner?

---

Only after these questions have been satisfactorily answered should a new top-level architectural component be introduced.

If the answers demonstrate that an existing component already possesses the required authority, the proposed component should instead become a **Derived Component** or be consolidated into that owner.