# Overdex

> **An offline-first Pokémon GO companion built for players who want better information before, during, and after every battle.**

Overdex combines a complete Pokémon GO Pokédex, battle knowledge, team analysis, tournament management, and future screen-assisted battle recognition into a single Android application.

Inspired by the simplicity of the original Game Boy Pokédex, Overdex is designed to answer questions quickly, work completely offline, and grow into a comprehensive companion for competitive Pokémon GO.

---

# Core Philosophy

The project is guided by one simple idea:

> **Learn everything before the battle. Observe only what changes during the battle. Infer everything else. Remember everything.**

Instead of overwhelming players with raw data, Overdex is designed to reduce cognitive load by organizing, remembering, and presenting information when it matters.

---

# Project Pillars

## Pokédex

The Pokédex is the heart of Overdex.

Current features include:

- Complete Pokémon GO Pokédex (#001–#1025)
- Instant search
- Base Attack, Defense, and Stamina
- Height and Weight
- Pokémon typing
- Type effectiveness
- Weaknesses and resistances
- Fast Moves
- Charged Moves
- Flavor text
- Cross-referenced searches
- Evolution navigation
- Pokémon cry playback

Future improvements:

- Advanced filters
- Personal collection tracking
  
---

## Battle Database

Every Pokémon, move, and battle mechanic is stored locally for instant access.

Current features:

- Fast Move statistics
- Charged Move statistics
- Energy generation
- Energy costs
- STAB calculations
- Type effectiveness
- PvP battle information

The Battle Database forms the foundation for every future battle-related feature in Overdex.

---

## Team Analysis

Overdex is being designed to help players build stronger teams—not simply list Pokémon.

Planned features include:

- Build Around
- Team Builder
- Team Review
- Coverage Analysis
- Weakness Analysis
- Replacement Suggestions
- Battle History
- Performance Tracking

Future recommendations will consider the Pokémon a player actually owns, rather than assuming every Pokémon is available.

---

## Battle Assistant

The Battle Assistant is designed to provide useful information during battle without replacing player decision-making.

Current foundation:

- Calibration system
- Persistent calibration profiles
- Overlay infrastructure

Future capabilities:

- Enemy recognition
- Fast Move identification
- Energy tracking
- Charge Move prediction
- Farm-down recommendations
- Stay / Swap suggestions
- Battle Memory

The goal is to reduce cognitive load—not automate gameplay.

---

## Tournament System

Overdex is also being built as a complete tournament companion.

Tournament organizers will be able to:

- Create custom tournaments
- Define league restrictions
- Configure custom rules
- Generate a unique five-digit tournament code

Players simply enter the tournament code to automatically receive:

- League settings
- Cup restrictions
- Pokémon eligibility rules
- Tournament configuration

Future tournament tools include:

- Swiss tournaments
- Round Robin
- Single Elimination
- Double Elimination
- Custom Cups
- Match history
- Bracket management
- Tournament statistics

Long-term, Overdex's event detection system will assist as a digital referee by helping verify battles, enforce tournament rules, and automatically record match results.

---

## Calibration & Vision System

The calibration system is the foundation for future battle recognition.

Current calibration regions include:

- Enemy Name
- HP Bar
- Team Icons
- Move Banner

Current features:

- Move regions
- Resize regions
- Save calibration
- Load calibration
- Persistent settings across app restarts

Future versions will use these calibration regions to support screen-assisted battle recognition.

---

# Offline First

Overdex is designed to function without an internet connection.

Current data sources include:

- Pokémon GO Game Master
- PokéAPI species information
- Local Room database

Normal gameplay should never require a network connection.

---

# Architecture

Overdex is organized into independent systems.

Current systems:

- Pokédex
- Battle Database
- Calibration System
- Cry Playback

Systems under active development:

- Battle Assistant
- Team Analysis
- Tournament Tools
- Battle Memory
- Screen Recognition

Keeping systems modular makes development easier to test, maintain, and expand.

---

# Development Philosophy

Every improvement follows the same process:

1. Build first.
2. Test what already exists.
3. Show that it happened.
4. One feature.
5. One commit.
6. One test.

Small, verified improvements are preferred over large rewrites.

---

# Long-Term Vision

Overdex is not simply a Pokédex.

It is becoming a complete Pokémon GO companion.

Before battle:

- Learn
- Research
- Build your team

During battle:

- Observe
- Assist
- Remember

After battle:

- Review
- Improve
- Prepare for the next one

Whether you're checking move counts, preparing for a tournament, analyzing a team, or researching a Pokémon, Overdex is designed to provide the right information at the right time.

---

# Guiding Principle

> **Future users will forgive missing features.**
>
> **They will not forgive incorrect battle data.**
