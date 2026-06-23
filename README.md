
# Overdex

An offline-first Pokémon GO companion inspired by the feel of a real Game Boy Pokédex.

The long-term goal is to provide fast Pokémon reference data, battle information, team analysis tools, and eventually screen-assisted battle analysis through a calibration and scanning system.

## Current Features

### Pokédex

* Browse National Pokédex (#001–#1025)
* Search Pokémon
* View detailed Pokémon information
* Base Attack / Defense / Stamina stats
* Height and Weight data
* Fast move listings
* Charged move listings
* Type effectiveness analysis
* Pokémon cry playback

### Battle Information

* Fast move data
* Charged move data
* Energy generation values
* Energy requirements
* Type effectiveness
* Weaknesses and resistances
* PvP-relevant battle statistics

### Calibration System

* Select calibration targets

  * Enemy Name
  * HP Bar
  * Team Icons
  * Move Banner

* Edit calibration regions

* Save calibration settings

* Load calibration settings

* Calibration persists across app restarts

### Navigation

* Pokédex is the primary application screen
* Calibration tools are accessible from the Select button

## Data Sources

* Pokémon GO Game Master data
* Pokémon GO Pokédex dataset
* PokéAPI species data
* Local Room database for offline access

## Project Status

### Completed

* National Pokédex database
* Offline Pokémon storage
* Move database
* Type effectiveness engine
* Base stat support
* Height and weight support
* Cry playback
* Calibration system
* Calibration persistence

### In Progress

* Real Pokédex flavor text entries
* Overlay battle recognition
* Battle assistant improvements

## Design Goals

* Fast access to Pokémon information
* Offline-first operation
* Game Boy-inspired presentation
* Accurate Pokémon GO battle data
* Simple, testable feature development

## Development Philosophy

* Build first.
* Test what already exists.
* Show that it happened.
* Small commits.
* One feature at a time.

Future users will forgive missing features.

They will not forgive incorrect battle data.

