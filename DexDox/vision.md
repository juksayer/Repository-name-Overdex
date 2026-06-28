# Vision

Overdex exists to reduce the distance between **observation, understanding, and action**.

Players should not need to search websites, memorize move counts, switch between applications, or interrupt their battle to answer simple questions. The information they need should already be waiting for them.

The goal of Overdex is not to replace player skill.

The goal is to remember what humans forget, calculate what humans cannot, and quietly present the answer before it is needed.

Every feature should reduce cognitive load.

Every feature should make the game feel more effortless, not more complicated.

---

## The Foundation

The Pokédex is the foundation.

The Battle Engine transforms observations into understanding.

The Overlay provides guidance.

The Battle Log preserves experience.

Together they become something greater than the sum of their parts.

The overlay helps players win today's battle.

The Battle Log helps players win the next thousand.

---

## Observe Once

One observation should create value many times.

Information observed during battle should become reusable knowledge for:

* Battle Logs
* Statistics
* Team Review
* Team Builder
* Pivot Analysis
* Researcher Mode
* Future features that have not yet been imagined

Observe once.

Remember once.

Reuse forever.

Facts should be recorded once and shared by every downstream system.

Facts before conclusions.

Conclusions can always be recalculated.

Lost observations can never be recovered.

---

## Design Philosophy

Minimalist.

Game Boy.

Quiet.

Winning.

Every pixel should earn its place.

Never duplicate information the player can already see.

The overlay exists to reduce the number of things the player has to remember.

Motion should communicate state, never demand attention.

The overlay should feel like a natural extension of Pokémon GO, not a competing interface.

The best interface is the one the player barely notices.

---

## Battle Philosophy

Overdex should never attempt to play the game for the user.

It should simply preserve attention.

The player should spend less time remembering and more time making decisions.

Information loses value with time.

Present the earliest useful conclusion available.

Higher-confidence observations should refine existing conclusions rather than delay them.

Fast, useful information is better than perfect information delivered too late.

---

## Long-Term Vision

Overdex should feel less like an app and more like a trusted battle partner.

It should observe quietly.

Learn continuously.

Remember everything.

Speak only when it has something worth saying.

Every completed battle should continue creating value long after it ends.

The player's history should become their greatest competitive advantage.

The Pokédex should eventually know not only Pokémon, but the player's own relationship with them.

The Battle Log should become a lifelong competitive journal.

The goal is not simply to build better tools.

The goal is to help players become better battlers.

---

## Development Philosophy

Build the engine once.

Feed it knowledge before the battle.

Feed it observations during the battle.

Feed it memories after the battle.

Every observation should have multiple consumers.

Every new feature should strengthen existing systems instead of creating parallel ones.

Build small.

Test often.

Show that it happened.

One feature.

One commit.

One test.

---

## The Standard

The best compliment Overdex can receive is not:

> "That's impressive."

It is:

> "Oh... that's helpful."

And eventually:

> "I forgot Overdex was even there."

## Terminal Identity

Overdex is not intended to feel like another Android application.

It should feel like a dedicated piece of field equipment built specifically for Pokémon Trainers.

Every interaction should reinforce the illusion that the user is operating a purpose-built battle computer rather than navigating a mobile app.

The Pokédex body, LCD display, terminal interface, physical controls, boot sequence, and DroidBall all contribute to this identity.

Android should fade into the background.

Overdex should become the device.

## Boot Sequence

The boot sequence establishes the fiction that Overdex is inheriting the Pokémon world's existing information network before connecting to the user's hardware.

On startup, Overdex briefly initializes through the familiar legacy system:


Oak's PC System


Only for a brief moment.

The system then transfers control to the local device:


OVERDEX TERMINAL

Host: Pixel 8


or


OVERDEX TERMINAL

Host: SCH-1535


using the device's friendly name whenever possible.

The intent is not to imitate the Game Boy games, but to acknowledge the world's established information network before introducing Overdex as the player's personal battle terminal.

Oak's PC hands off the connection.

Overdex becomes the user's device.

## Device Philosophy

Overdex should behave like dedicated hardware.

The interface should favor physical-device interactions over conventional Android patterns whenever practical.

Examples include:

- A always activates the highlighted action.
- B always returns to the previous screen.
- The D-Pad behaves according to the active module.
- Start launches the DroidBall.
- Select performs the module's contextual action.

The objective is consistency through interaction, not through identical controls.

Each module should use the D-Pad in the most natural way for its task while preserving universal behavior for the primary buttons.

## The LCD

The LCD display is a defining part of Overdex's visual identity.

The shader is not simply a retro effect.

It represents the physical display inside the Pokédex itself.

Every application running inside the Pokédex should be rendered through this display layer.

The live battle overlay intentionally does not use the LCD effect.

During battle, clarity always takes priority over nostalgia.

## The DroidBall

The DroidBall is not merely an icon.

It is the user's deployed battle companion.

Launching the DroidBall prepares Overdex for battle.

The DroidBall represents the overlay throughout its lifecycle:

OFF

↓

Launching

↓

Closed

↓

Open

↓

Closed

↓

Shutdown

Its animations should communicate state rather than provide decoration.

Every movement should have purpose.

## Design Principle

Whenever a design decision is unclear, ask one question:

> "Would this exist on a dedicated Overdex handheld?"

If the answer is yes, it belongs.

If the answer is simply "because Android apps usually work this way," reconsider the design.