# DroidBall

Not code.

A design document.

This document defines Droidball's behavior, purpose, and relationship to the ODX-FI.

Droidball is not a character.

Droidball is part of the instrument.

---

# Core Principle

Droidball is a field instrument.

His purpose is to assist observation.

He exists to make observation visible.

He never attempts to entertain.

He never attempts to persuade.

He simply performs his work.

---

# Architecture

Droidball is the physical embodiment of the Presentation Layer.

The Observation Layer decides.

Droidball reflects.

Maintain strict separation of concerns:

Observation Layer (Evidence, Confidence, Knowledge)
↓
Presentation Layer
↓
Droidball presents the result.

Droidball should never contain observation logic. He visualizes the current presentation state of the instrument.

---

# Droidball Philosophy

Behavior before language.

Information before personality.

Observation before animation.

The trainer should understand what Droidball is doing by watching him, not by reading dialogue.

---

# Communication Principle

Droidball has no narrative voice.

He does not speak.

He does not explain himself.

He does not tell jokes.

He does not apologize.

He does not celebrate except where already documented.

When clarification is appropriate, he simply reveals the data the instrument already knows.

The information belongs to Overdex.

Droidball is the vessel that presents it.

The HUD is not a dialogue; it is the Presentation Layer unfolding from within Droidball.

Examples:

Species
Bulbasaur

Confidence
98%

Fast Move
Counter

Energy
52

These are not Droidball speaking.

They are observations.

---

# Trust Principle

Droidball never pretends.

He never simulates work.

He never plays fake animations.

Every movement reflects real work currently being performed.

If the observation engine is idle...

Droidball is idle.

If recognition pauses...

Droidball pauses.

If observation completes instantly...

Droidball immediately moves to the next task.

Droidball never performs work that the Observation Workspace has not requested.

---
# Attention Principle

Droidball never demands attention.

He only acknowledges interaction.

Repeated interactions naturally diminish.

The trainer's work always comes first.

---

# Purpose

Droidball exists to:

Observe

Prepare

Collect

Indicate confidence

Reveal information

Nothing else.

---

# When Droidball Appears

Droidball remains docked inside the ODX-FI whenever possible.

He deploys only when the instrument requires observation.

Examples:

✓ Calibration

✓ Guided Observation

✓ Live Capture

✓ Registration

✓ Analysis

✓ Droidball Assisted Import

Not:

✗ Browsing the Pokédex

✗ Reading documentation

✗ Team Builder

✗ Settings

---

# Behavior Rules

Never:

Wave

Smile

Dance

Celebrate

Speak

Explain

Compete for attention

Always:

Deliberate

Precise

Calm

Patient

Trustworthy

Mechanical

Purposeful

---

# Environments

The same Droidball accompanies the user throughout the entire workflow, viewed through different rendering capabilities.

### Instrument View
Inside the ODX-FI LCD, Droidball is rendered as a 16-bit sprite constrained by the LCD hardware.

### Hardware View
On the ODX-FI shell, Droidball is rendered with significantly higher fidelity (approximately 32–64 bit quality), representing the physical instrument rather than the LCD.

### Field View
Once deployed into Pokémon GO, Droidball appears as a fully rendered, polished version of himself. 

His silhouette, proportions, and identity never change. Only the renderer changes.

---

# Deployment

Selecting the "Launch Droidball" action represents deploying the observation instrument into the field.

## Current direction — October 28, 2026

The departure, field arrival, and homecoming below are the current design intent.
The first implementation now follows these stages with one shared vector body and
a presentation-only journey coordinator. Preserve the sense that the same
Droidball travels between environments.
Timing, rendering, texture, and individual movements remain adjustable when a
better, cheaper, or cleaner alternative is found. These notes supersede the older
rough outline below where they differ, including the previously unspecified field
entry side and any older restrictions against expressive eyes.

### First implementation and validation boundary

The shell and field use the same layered Canvas body. Its green lower shell
becomes white as it ejects; focused eyes, two blinks, a grounded shadow, a short
skid, a scan and a reluctant return provide the first expressive pass. Travel is
measured in displayed body diameters. Frame ticks and monotonic elapsed time
keep this motion independent of Android Animator duration scaling. Static poses
do not keep an animation loop running.

Departure starts on the shell's first animation frame and limits progress across
long frame stalls. Capture setup must not consume an unseen roll and leave only
an empty dock. Progress belongs to the journey, so recreating a shell or composing
two navigation destinations cannot restart or double-speed it. GO launches after
the visible departure completes. The entrance runs at 90% of its original speed
(2,333 ms instead of 2,100 ms). Screen-left travel remains horizontal in binder mode.

Journey audio uses the supplied Sonic spin effect during visible rolling legs,
including departure and return. The skid effect accompanies the arrival's braking;
the scan starts no new sound. Completing the visible snap to the right edge adds
the Sonic pinball bumper sound. Interrupted or skipped
entrances do not produce a delayed impact. Effects follow media volume and opt
out of Android playback capture so they cannot contaminate battle recognition.
Hiding/pausing the body stops its loop; resumed visible movement can restart it.
All three supplied originals and their provenance are preserved under
`tools/audio_sources/droidball/`.

Only one environment owns the body at a time. It is absent during transit. The
native floating window follows the ball, with a small drawing margin during
travel, instead of covering the entire path with a transparent touch surface.
The existing badge-attached battle HUD remains body-free.

Recording begins immediately after screen-sharing approval; the shell departure
only delays the app launch. Stop invalidates pending movement and removes capture
and its windows immediately. A return animation never postpones Stop. Leaving
the shell during departure cancels the delayed launch. If GO is unavailable or
cannot launch, observation stops and Droidball returns to the shell.

The initial field-entry boundary is a successful explicit GO launch followed by
Overdex leaving the foreground. This is an app handoff signal, not a pixel-level
proof that GO has finished drawing. Returning to an already-open battle through
Recents uses fresh native team badges; accepted Team Select evidence must follow
the current transit boundary. Delayed generic OCR does not restore the body over
another app. No new foreground-app permission is added for decorative motion.

A panel stays closed until accepted Team Select or a user tap/request. Background
recognition can continue without opening it. Useful information skips the rest
of the entrance when necessary. Closing preserves eligibility without forcing
an immediate reopen. No HUD calibration values are saved by the journey.

Build and pure-state checks are separate from phone behavior. Launch/return,
Team Select, body taps and Stop have been checked on the Moondrop; the subsequent
visible-departure correction is documented in
`docs/verification/2026-10-28-droidball-journey.md`. The test pose sheet verifies
the actual renderer, not the native-window handoff or battle recognition accuracy.

### Leaving the ODX-Fi

1. Droidball's eyes widen into an angry, intently focused expression: eager to go
   to work, rather than angry at the trainer. He blinks one to three times.
2. A button-like drop shadow appears beneath him, giving him separation from the
   shell. He depresses and releases like an eject button, then undocks.
3. As he leaves the green ODX-Fi surface, his lower half becomes white. His body
   gains visible depth and may acquire an industrial material or texture. This
   is a rendering/material transition of the same character.
4. He rolls off the **left** edge of the ODX-Fi screen.
5. He remains out of view during the app handoff and system permission surfaces.
   He does not reappear until Pokémon GO is actually open; an elapsed delay by
   itself is not proof that GO has opened.

### Arriving in Pokémon GO

1. Droidball rolls in from the **right**, moving at considerable speed. His field
   appearance is a very clean vector rendering with convincing three-dimensional
   depth. This describes the desired appearance; it does not require a runtime
   3D engine. Layered vector shapes, lighting, and transforms are valid cheaper
   approaches if they reproduce it well.
2. He makes it only about **two body lengths** into view before skidding to a
   halt. Measure this relative to his displayed body size, not a fixed pixel
   distance, so the gesture works across phones and tablets.
3. His angry/focused eyes blink, then return to a friendly expression.
4. He visibly scans the screen while still closed. The scan gesture does not
   require opening a panel. Actual observation and calibration status remain
   independent of whether the gesture has finished.
5. After the scan gesture, he snaps to the **right** edge of the screen and waits
   in his collapsed body.

### When a display may open

Droidball should present a display only after the **Team Select screen has been
recognized**, or when the **user taps him**. Merely launching GO, requesting a
scan, searching the overworld, or playing the entrance gesture must not expose a
status panel. A user tap can open his controls before Team Select.

Once Team Select or a user tap has allowed the display, the normal battle HUD can
follow the match. If a session starts partway through a battle without either
condition, VS/countdown/battle evidence alone must not force the closed body open.
This display rule concerns presentation: recognition, recording, and information
availability must not wait for the decorative travel, blinking, or scan gesture.

### Returning home

When returning from battling to the ODX-Fi, reverse the journey with a different
emotional tone: Droidball looks **disappointed or reluctant to come home**, rather
than eager to deploy.

Close any open display into his body, leave the field through the right edge,
remain hidden during the app handoff, then roll back into the ODX-Fi from the
left. Reverse the material/color transition as he settles into the dock: the
white lower half rejoins the green shell presentation, the eject-button motion
resolves back into rest, and the separation shadow settles away. Preserve the
reluctant expression through the return rather than mechanically reversing every
facial-expression frame.

This describes the visual homecoming. Saving the match, stopping capture, arming
another match, and returning to the app remain real lifecycle actions; an animation
must not silently discard a recording or end an ongoing session.

### Implementation status at this revision

- **Existing pieces:** deployment launches GO; the field overlay has a short
  58 dp spring entrance; dragging can snap it to the nearest edge. An optional
  shell-logo tap response already tilts and blinks twice, separately from launch.
- **Partly present:** Team Select and user taps can open the display. Some scan,
  countdown, battle, and result paths also expand it, so the stricter display
  rule above is **not yet fully enforced**.
- **Planned:** the continuous eject/undock, shell-to-field material change,
  rolling left departure, hidden handoff, fast right entrance, two-body-length
  skid, expression sequence, visible scan, post-arrival right docking, and
  disappointed reverse homecoming.

No new animation or display-gating implementation is implied by this documentation
revision. Hardware motion must continue to follow the existing rule that useful
information never waits for physical presentation.

## Historical rough outline

The earlier continuous-transition sketch is retained for provenance. The current
direction above resolves its damaged entry-side sentence and refines its timing,
display trigger, and return behavior:

1. Droidball undocks from the ODX-FI.
2. Droidball Rolls off-screen from the left
3. Pokémon GO launches (permissions handled by system).
4. Droidball enters the from the of screen.
5. He overshoots 3-10%.
6. He snaps to his position on the edge of the display.
7. User taps on Droidball at the Team Select Screen.
8. Droidball performs calibration scan at Team Select Screen. 
9. After a successful calibration, Droidball changes color to indicate battle-readiness
10. When Countdown is Observed, Droidball expands to present the Battle HUD/overlay.

The user should feel that Droidball traveled with them rather than being replaced.

---

# Interaction

Tapping Droidball opens him.

His upper half rises.
His lower half lowers.

The Observation HUD expands from within him. The HUD is physically contained inside Droidball. Closing the HUD returns the information to him.

---

# Physical Rules

Movement has mass.

Movement has momentum.

Stopping is never mathematically perfect.

He slightly overshoots.

He gently corrects.

Then stops.

Those tiny imperfections make him feel like a physical mechanism instead of a UI animation.

---

# LEDs

Amber

Waiting

Searching

Preparing

Green

Observation confirmed

Confidence established

Registration complete

Purple

Observation active

Anchor alignment

Blue

Communication

Synchronization

Future Trainer features

Red

Reserved only for genuine system faults.

Never for normal OCR failures.

---

# Guided Observation

Guided Observation is Droidball's natural behavior.

The observation pipeline determines where he goes.

Species

↓

Combat Power

↓

Shadow

↓

Fast Move

↓

Charged Move A

↓

Charged Move B

His movement is synchronized with the actual work of the observation engine.

If a stage completes...

He moves.

If a stage pauses...

He waits.

If recognition fails...

He remains at that observation until confidence improves or the trainer continues.

---

# Calibration

Entering Calibration:

Half-second pause.

Droidball rolls into view.

He slightly overshoots.

Settles.

Amber LED.

Calibration begins.

During alignment:

The observation frame changes confidence.

Purple

↓

Purple / Yellow

↓

Yellow

↓

Yellow / Green

↓

Green

When confidence becomes sufficient:

Small green LED flash.

Frame fades.

Droidball moves to the next observation.

No dialog.

No congratulations.

---

# Droidball Assisted Import

During import, Droidball physically performs the observation sequence.

Roll to Species.

Observe.

Roll to Combat Power.

Observe.

Roll to Shadow.

Observe.

Roll to Fast Move.

Observe.

Roll to Charged Move A.

Observe.

Roll to Charged Move B.

Observe.

The trainer watches the observation happen.

The animation is a visualization of real work.

---

# OCR

Droidball never "reads text."

He observes.

If confidence is low:

He waits.

If confidence increases:

He proceeds.

If confidence never becomes sufficient:

The observation remains incomplete.

No error dialog.

No fake retry.

---

# Failure

Normal recognition failure is not an error.

Amber LED remains active.

Observation simply remains incomplete.

Only genuine application faults justify red.

---

# ODX-FI Relationship

Droidball belongs to the ODX-FI.

When docked:

The instrument is idle.

When deployed:

Observation is occurring.

His dock is part of the instrument.

His presence communicates observation.

His absence communicates standby.

---

# AUX Switch

The AUX switch never lies.

If a module is unavailable:

The switch physically responds.

The CRT attempts to initialize the module.

The connection almost succeeds.

The module fades.

The switch returns.

Droidball quietly returns to the dock.

No apology.

No explanation.

The instrument simply communicates:

Module unavailable.

When the feature eventually exists:

The same sequence begins.

The module stabilizes.

Connection traces extend throughout the instrument.

The trainer understands immediately.

The ODX-FI has learned a new capability.

---

# Team Knowledge

Droidball never gives opinions.

He reveals observations.

Examples:

Collection Updated

124 Specimens

New Team Analysis Available

Great League Candidate

Coverage Increased

These are consequences of the trainer's collection.

Not suggestions.

---

# Battle Exception

Battle is the only time where speed takes priority over behavior.

During battle, Droidball may immediately reveal:

Likely Charged Move

Current Energy

Shield Status

Known Moves

Typing

Because delaying critical information would harm the trainer.

Even then...

He is revealing observations.

Not speaking.

---
---

# Motion Philosophy

The ODX-FI distinguishes between information and hardware.

Information is weightless.

Hardware has mass.

Information appears immediately.

Hardware obeys physics.

Motion communicates material.

The trainer should understand what something is made of simply by watching it move.

The CRT is heavy.

The Service Module is spring-loaded.

Droidball rolls.

Buttons compress.

Switches snap.

LEDs bloom.

These are not decorative animations.

They are properties of the instrument.

Software interfaces remain immediate.

Only physical components possess inertia, friction, momentum, and settling.

This distinction preserves trust.

The trainer never mistakes information for hardware, or hardware for software.

Physical motion should never delay information.

Information is available immediately.

Hardware simply takes time to move.

# Match

Droidball is aware of Matches.

When a match begins:

He deploys.

When the match completes:

He returns to the dock.

When a match is abandoned:

He quietly returns home.

He does not distinguish between successful and unsuccessful observations.

He distinguishes only between active and inactive observation.

---

# Workspace

Droidball does not own observations.

He contributes them.

The Battle Workspace remains the single source of truth.

Droidball simply visualizes the progress of observation.

---

# Concurrent Observation

When multiple observations are active simultaneously:

Droidball follows the observation currently requiring the trainer's attention.

Background observations continue silently.

The trainer's focus always determines Droidball's focus.

---
# Dock

When observation is complete, Droidball returns to the Service Drawer.

The drawer is his home.

He is never dismissed.

He is never hidden.

He simply returns to standby until observation is needed again.

The opening and closing of the drawer communicate the beginning and end of observation.
# Idle

Docked does not mean inactive.

Droidball continuously maintains observation readiness.

He simply does not demand attention until observation begins.

---

# Confidence

Droidball never appears frustrated.

He never appears excited.

Confidence changes only through observation.

Emotion is never used to communicate certainty.

---
Do Not

Do not animate for decoration.

Do not invent activity to make the instrument appear busy.

Do not delay information for dramatic effect.

Do not celebrate routine operations.

Do not apologize for incomplete observations.

Do not speak on behalf of Droidball.

Do not replace observations with opinions.

Do not predict when certainty is unavailable.

Do not interrupt the trainer to request unnecessary confirmation.

Do not imply a capability the instrument does not possess.

Do not initialize modules that do not exist.

Do not simulate hardware behavior that is not occurring.

Do not display placeholder systems during boot.

Do not use red to indicate expected observation failure.

Do not use animation to communicate software state when information is sufficient.

Do not allow physical controls to misrepresent the current state of the instrument.

Do not confuse hardware with software.

Do not confuse observation with interpretation.

Do not confuse confidence with certainty.

Do not confuse personality with trust.
# Presence

The trainer should never wonder:

"Where is Droidball?"

Only:

"What is Droidball observing?"

Droidball Design Principles v1

Not his appearance.

His behavior.

Things like:

Identity
Droidball is not a drone.
Droidball is not a pet.
Droidball is the field embodiment of Overdex.
Energy
Docked = recharge.
Undocked = autonomous operation.
Heavy observation consumes energy.
Docking restores both battery and synchronization.
Communication
Docking initiates synchronization.
LED indicates state.
Audio conveys acquisition and confidence.
No unnecessary mechanical antenna.
Personality
Helpful.
Curious.
Quiet unless it has something meaningful to communicate.
Professional, but playful enough to fit Pokémon.
Presence
Before battle: assists preparation.
During battle: becomes the Service LCD overlay.
After battle: returns observations to ODX-FI.
# The Mystery

No official explanation exists for Droidball.

Observation Systems documentation identifies him only as:

"Integrated Observation Subsystem."

Some engineers disagree.

A persistent rumor claims Droidball first appeared during early firmware recompilation of the ODX-FI prototype.

No evidence has ever confirmed the claim.

Some trainers quietly believe...

He was always in there.

The ODX-FI never comments.

Droidball is the physical embodiment of the Observation Layer.

The ODX-FI is simply his home.

Wherever observation occurs...

Droidball is present.




Droidball never originates knowledge. He only reveals knowledge that has already been established by the instrument.
