# Overdex Type Icon Design Notes

## Purpose

Overdex supports two independent type presentation systems.

### GBA Style

Faithful recreation of the Game Boy Advance Pokémon games.

- Text-based badges
- Rounded rectangles
- Nostalgic
- Readability first

Example:

[FIRE]
[GRASS]

---

### Overdex Style

Overdex does **not** use text badges.

Instead, it uses a custom symbol language designed specifically for dedicated hardware.

The goal is immediate recognition rather than reading.

These symbols should feel like they belong on:

- scientific instruments
- aviation displays
- laboratory equipment
- industrial control panels
- military HUDs

They should **not** resemble:

- emoji
- Material icons
- Pokémon branding
- cartoon illustrations

---

## Design Principles

### Functional Symbolism

Represent the phenomenon rather than the object.

Examples:

Fire → Thermal emission

Water → Fluid flow

Electric → Voltage surge

Ghost → Sensor anomaly

Grass → Biological growth

---

### Zero-Literalism

Avoid drawing objects.

Draw measurements, signatures, and phenomena.

The icons should communicate what an instrument detects, not what an artist sees.

---

### Recognition Over Reading

Pokédex List:
Recognize.

Pokémon Detail:
Read.

The list should eventually rely primarily on symbols.

The detail screen should continue using text labels where explanation is valuable.

---

## Constraints

Every icon should:

- work at 16–24 dp
- remain recognizable in monochrome
- be drawable with a marker in a few strokes
- survive CRT filters and low-resolution displays
- avoid letters and words
- share a consistent geometric language

---

## Initial Concepts

Fire
- Thermal signature
- Upward energy
- Heat ascent

Water
- Fluid flow
- Wave frequency
- Laminar motion

Grass
- Biological growth
- Branching structure
- Cellular development

Electric
- Voltage surge
- Oscilloscope trace
- Circuit discharge

Ghost
- Unresolved signal
- Phase anomaly
- Sensor interference

---

## Development Process

1. Define the concept.
2. Sketch in plain text.
3. Verify the symbol works in monochrome.
4. Produce vector artwork.
5. Integrate into Compose.

Never begin with polished artwork.

The visual language should be established before implementation.

---

## Long-Term Goal

The Overdex icon set should become recognizable on its own.

A user should eventually identify an Overdex type symbol without relying on text, color, or Pokémon branding.

---

## Sensor-First Design

Overdex symbols do not describe Pokémon.

They describe what the Overdex instrument detects.

Examples:

- Fire → Thermal emission
- Water → Fluid flow
- Grass → Biological growth
- Electric → Voltage surge
- Ghost → Unresolved return signal
- Rock → Mineral density
- Steel → Structural reinforcement

When designing a new symbol, begin by asking:

> "What phenomenon is the sensor measuring?"

Do not begin by asking:

> "What does this Pokémon look like?"

---

## The Instrument's Worldview

The Overdex is not an artist.

It is an instrument.

Its symbols communicate measurable phenomena, classifications, anomalies, and sensor interpretations.

Whenever possible, prefer engineering, scientific, or diagnostic language over fantasy language.

Good:

- Thermal
- Conductivity
- Segmentation
- Phase
- Oscillation
- Attenuation
- Crystalline
- Geological

Avoid:

- Flames
- Ghosts
- Fairies
- Dragons
- Leaves
- Bugs

The symbols should describe observations rather than illustrations.

---

## Family Consistency

Every symbol should appear to have been designed by the same fictional engineering team.

The collection should feel like a standardized hardware symbology rather than eighteen independent icons.

If one symbol appears decorative while the others appear technical, redesign the decorative symbol.

---

## Recognition Test

Before implementing a symbol:

1. Remove all labels.
2. Display the icon in monochrome.
3. Reduce it to approximately 16–24 dp.
4. Verify it remains distinguishable from every other type.
5. Verify it survives CRT filtering.

If two symbols become confused at small sizes, redesign one of them before implementation.

Recognition is more important than artistic detail.

---

## Hardware Test

Ask:

> "Could this symbol be laser-etched into a field instrument?"

If the answer is no, simplify it.

Every Overdex symbol should be simple enough to be:

- engraved into aluminum
- silk-screened onto plastic
- printed on a monochrome LCD
- drawn with a marker
- recognizable without color

The symbols should feel permanent rather than decorative.

Ghost is intentionally represented by absence rather than presence.

The symbol communicates that the Overdex has localized an entity but cannot obtain a complete sensor return.

This is one of the defining characteristics of the Overdex visual language.