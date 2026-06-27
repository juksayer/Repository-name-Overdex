# Overlay

The Overdex Overlay exists to assist the player without distracting from the battle.

It should feel like a natural extension of Pokémon GO rather than a separate application.

The overlay's purpose is to display information that helps the player make decisions quickly and confidently.

---

# Philosophy

The overlay should never compete with the game for attention.

It should answer questions—not create them.

Every element on the screen must justify its existence.

If a component does not improve a decision, it does not belong on the overlay.

---

# Core Principles

The overlay should be:

* Immediate
* Trustworthy
* Minimal
* Readable
* Non-intrusive
* Consistent

The player should never need to search for information.

Information should appear where it is naturally expected.

---

# Information Flow

Battle Systems

↓

BattleMemory

↓

Overlay

↓

Player

The overlay does not own battle information.

The overlay displays BattleMemory.

No battle calculations should occur inside overlay components.

---

# Overlay Responsibilities

The overlay may display:

* Enemy team
* Friendly team
* Active Pokémon
* Remaining Pokémon
* Estimated energy
* Shield counts
* Switch timer
* Recommended actions
* Battle warnings
* Observation confidence

The overlay should not:

* Perform OCR
* Parse screenshots
* Estimate move timing
* Infer battle state
* Modify BattleMemory

---

# Visual Language

Every icon should have one meaning.

Forever.

Colors should communicate state, not decoration.

Animations should reinforce state changes, never distract.

Important information should become brighter.

Inactive information should become quieter.

---
# Decision Support

The overlay exists to improve decisions, not to display data.

Do not display information simply because it is known.

Display information only when it changes the player's current decision.

The overlay should describe the relationship between the player's active Pokémon and the enemy's active Pokémon.

It should not describe either Pokémon independently.

Whenever possible, answer questions such as:

• Can this Pokémon threaten me?
• Can I threaten this Pokémon?
• Should I shield?
• Should I switch?
• Should I farm?
• Should I throw?

If the information does not help answer one of these questions, it probably does not belong on the overlay.
# Information Priority

Highest

* Immediate battle threats
* Active Pokémon
* Charged move availability
* Switch opportunities

Medium

* Enemy team memory
* Estimated energy
* Remaining Pokémon

Low

* Battle history
* Debug information
* Research tools

Lower-priority information should never obscure higher-priority information.

---

# Screen Usage

The overlay should occupy as little screen space as possible.

Whenever possible:

* Use corners.
* Avoid covering gameplay.
* Avoid covering HP bars.
* Avoid covering charged move buttons.

Every pixel should earn its place.

---

# Interaction

The overlay should require no interaction during battle whenever possible.

Interactions should be intentional.

Examples:

* Tap to expand.
* Long press to exit.
* Drag to reposition.
* Reset Position button.

Accidental activation should be difficult.

Intentional activation should be effortless.

---

# Trust

The overlay should distinguish between:

Observed

Information directly detected.

Examples:

* Pokémon species
* Shield used
* Fast move observed
* Charged move observed

Inferred

Information estimated from observations.

Examples:

* Estimated energy
* Predicted charged move
* Possible backline

The player should never confuse inference with observation.

---
# Attention

The player's attention belongs to Pokémon GO.

Overdex borrows attention only when necessary.

Motion is expensive.

Animation should communicate state changes—not attract attention.

Acceptable animation:

• Droidball launch
• Droidball collapse
• Overlay appearing
• Overlay disappearing
• Gentle fades
• Slow energy fills

Avoid:

• Flashing
• Blinking
• Continuous pulsing
• Decorative movement

When nothing important is happening, the overlay should feel almost invisible.

# Performance

The overlay should feel instantaneous.

Frame rate is more important than visual effects.

Animations should be subtle.

Battery consumption should remain low.

Memory usage should remain predictable.

---

# Accessibility

The overlay should remain readable:

* outdoors
* indoors
* bright sunlight
* low brightness
* colorblind-friendly where practical

Critical information should never rely solely on color.

---

# Future Features

Potential overlay modules include:

* Enemy Team Memory
* Friendly Team Memory
* Battle Timeline
* Energy Tracker
* Recommended Move
* Recommended Switch
* Type Effectiveness
* Countdown Timers
* Observation Confidence
* Battle Log

Each module should be independently removable.

---

# Design Goal

The best overlay is the one the player forgets is there.

It quietly observes.

It quietly remembers.

It quietly helps.

The player should spend their attention on the battle—not on Overdex.
