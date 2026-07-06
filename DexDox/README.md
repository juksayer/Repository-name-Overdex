# Overdex

An offline-first Pokémon GO companion inspired by the feel of a real Game Boy Pokédex.

Overdex is designed around a simple idea:

> Learn everything possible before the battle. Observe only what changes during the battle. Infer everything else.

Rather than overwhelming the player with information, Overdex aims to reduce cognitive load by presenting only the information that matters, when it matters.

---

# Current Features

## Pokédex

* Browse National Pokédex (#001–#1025)
* Search Pokémon
* View detailed Pokémon information
* Base Attack / Defense / Stamina stats
* Height and Weight data
* Fast move listings
* Charged move listings
* Type effectiveness analysis
* Pokémon cry playback

## Battle Information

* Fast move data
* Charged move data
* Energy generation values
* Energy requirements
* Type effectiveness
* Weaknesses and resistances
* PvP-relevant battle statistics

## Calibration System

* Select calibration targets

  * Enemy Name
  * HP Bar
  * Team Icons
  * Move Banner
* Edit calibration regions
* Save calibration settings
* Load calibration settings
* Calibration persists across app restarts

## Navigation

* Pokédex is the primary application screen
* Calibration tools are accessible from the Select button

---

# Data Sources

* Pokémon GO Game Master data
* Pokémon GO Pokédex dataset
* PokéAPI species data
* Local Room database for offline access

---

# Project Status

## Completed

* National Pokédex database
* Offline Pokémon storage
* Move database
* Type effectiveness engine
* Base stat support
* Height and weight support
* Cry playback
* Calibration system
* Calibration persistence

## In Progress

* Pokédex flavor text
* Overlay battle recognition
* Battle assistant
* Team analysis tools
* Battle memory

---

# Design Goals

* Offline-first operation
* Fast access to Pokémon information
* Accurate Pokémon GO battle data
* Game Boy-inspired presentation
* Small, testable feature development
* Interfaces that teach rather than distract

---

# Development Philosophy

* Build first.
* Test what already exists.
* Show that it happened.
* One feature.
* One commit.
* One test.

Future users will forgive missing features.

They will not forgive incorrect battle data.

Overdex is built around confidence rather than certainty.

Recommendations are derived from multiple observations and become stronger as evidence accumulates.

---

# Long-Term Vision

Overdex is evolving into a complete Pokémon GO companion.

Before battle, it helps players learn, prepare, and build teams.

During battle, it observes, remembers, and assists.

After battle, it will help players review, understand, and improve.

The goal is not to replace player skill.

The goal is to help players make better decisions with less effort.
