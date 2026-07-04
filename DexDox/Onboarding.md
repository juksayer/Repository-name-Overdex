# Overdex Mental Model

Overdex is organized as a series of layers.

Each layer has one responsibility.

Information should flow downward through the system, becoming progressively more valuable without being duplicated.

Each layer consumes the layers above it and enriches the layers below it.

---

## Knowledge Layer

The permanent reference library.

Responsible for information that is true before a battle begins.

Components:

- Pokédex
- Move Database
- Type Database
- Species Data
- Base Stats
- Evolutions

↓

## Observation Layer

Responsible for collecting information during battle.

Observations should record facts rather than conclusions.

Components:

- OCR
- Audio Recognition
- Manual Input
- Future Sensors

↓

## Memory Layer

Responsible for preserving observations while a battle is in progress.

Components:

- BattleMemory

BattleMemory represents the current battle state.

↓

## History Layer

Responsible for recording completed battles.

Components:

- BattleTimeline

The timeline records events exactly as they occurred.

↓

## Archive Layer

Responsible for long-term storage.

Components:

- ArchivedBattle

Archives should remain immutable once written.

↓

## Intelligence Layer

Responsible for transforming stored facts into useful conclusions.

Components:

- Matchup Engine
- Decision Engine
- Statistics
- Team Analysis
- Tournament Tools
- Future AI Systems

Facts should be recorded once.

Conclusions should be calculated here.

↓

## Presentation Layer

Responsible for communicating information to the user.

Components:

- Battle Overlay
- Pokédex UI
- Battle Review
- Future Interfaces

Presentation should never become the source of truth.

It should display knowledge rather than own it.

---

# Guiding Principle

Observe once.

Remember once.

Archive once.

Analyze many times.

Present only what helps.

Every observation should have multiple consumers.

No information should exist in only one place.

---

# AI Workflow

AI conversations should be treated like Git branches.

Each feature receives its own conversation.

Do not reuse implementation threads.

Workflow:

1. One feature.
2. One discussion.
3. One implementation.
4. One review.
5. One commit.
6. Close the conversation.

Start a new AI conversation for the next feature.

Never continue implementation work in completed threads.

The objective is to prevent stale context, unrelated architectural changes, and accumulated assumptions from influencing future work.

Every conversation should begin with a clean understanding of exactly one task.