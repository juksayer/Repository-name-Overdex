# Opponent hand / Poké Ball sampling experiment

The user requested a new Draggy Box for the opponent trainer's extended arm
during Pokémon send-out and recall. **Its location is unmeasured.** Start from
the user's Moondrop guess: full-display **X 1045, Y 600, W 25, H 80** on
**1080 × 2400**. Width interprets X 1045–1070; height 80 is an implementation
guess. Normalize by the entire source display, without subtracting system bars.
The corresponding initial Pixel 4 rectangle is X 1045, Y 570, W 25, H 76 on
its verified 1080 × 2280 display. Neither rectangle is a detected hand position.

## User workflow

1. Before the match, start a normal Droidball observation session and open its
   field menu. Choose **OPPONENT BOX**.
2. Choose **ADJUST** to drag the thin box. Toggle **MOVE / SIZE** to resize;
   arrows also support precise movement. Drag the panel by its title if it
   covers the game. **VIEW ONLY** lets touches pass through the box.
3. Choose **SAMPLE 10s** just before a send-out or recall you want to inspect.
   The box and panel hide during the request, then return with the saved-image
   count. Capture starts no earlier than 150 ms after the tap, allowing native
   overlay removal to settle. It samples at most 10 Hz, up to 100 images, and
   stops at ten seconds or match/session termination. Actual delivered counts
   can be lower. A charged move does not automatically start or cancel it.
4. Sampling uses the currently visible draft without saving calibration.
   **SAVE & CLOSE** changes only this box in the current device/profile.
   **CANCEL** closes the guide without saving the draft. An already requested
   bounded sample may finish after the guide closes.
5. Once the experiment has been selected, **OPPONENT BOX** also appears in the
   battle HUD to reopen it after closing. This temporary control resets when
   the capture service ends; ordinary sessions do not gain another HUD control.

The same saved region appears as **Opponent Hand / Poké Ball** in Draggy Boxes.
Older profiles gain only the missing default on load, without replacing adjusted
species, HP or other boxes. Named profiles and working restore points include it.

## Evidence and acceptance

This is raw sampling, **not an arm, send-out, recall or faint detector**. There
is no automatic event classification or result-music change. It adds no continuous
hand reader outside the user's short sampling requests, and it can sample before
GO without inventing Match Start.

Original crop pixels, source dimensions/bounds, and wall/monotonic capture times
are retained as `OpponentTrainerHandCrop` from `OPPONENT_TRAINER_HAND_CAPTURE`.
Normal compact match saves preserve this exact experimental source/purpose pair
independently of successful recognition. A saved-image count is not an event count.

After placement, collect known send-outs and recalls plus ordinary-play negative
examples. Check the arm/ball is actually inside the crop, how many frames contain
it, motion visibility during different camera phases, and whether similarly
shaped ordinary movement creates false triggers. Compare recall windows with
independent battle evidence while keeping that evidence separate. Reliability
must be established from these real examples before this region wakes a reader,
labels a recall, or gates result music.
