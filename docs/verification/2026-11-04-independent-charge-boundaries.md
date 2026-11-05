# Independent charged-sequence boundaries — November 4, 2026

## Finding and validation boundary

The strongest early visual clock in the reviewed recordings is the coordinated
fade of the player's charged-move controls and inactive Pokémon cards. It is
earlier than the QTE camera, moving bubbles, shield-choice panel, pop sounds,
and haptic progress milestones. It does not require reading announcement text.

This is a recorded-video investigation and implementation specification, not a
new production detector or a live recognition-accuracy result. No historical
Articles, Match clocks, attack counts, or energy were rewritten. The Android
application is unchanged by this investigation.

Keep two events distinct:

1. **Normal controls begin leaving:** a candidate boundary from ordinary battle
   play into charged preparation. This is the useful early Replay boundary.
2. **A particular charged interface appears:** positive evidence of the player's
   bubble task or the opponent's shield choice. Preserve its own later time.

Use HP context before treating a fade as ambiguous: both active Pokémon must be
alive for QTE/shield choice, whereas a faint follows HP depletion. Fresh,
positively nonzero measurements of both current combatants across the boundary
therefore supply the independent faint-exclusion evidence missing from the
initial investigation. The same control fade with depletion/faint evidence is a
different event, not a competing explanation for healthy combatants.

A replacement, menu, obstruction, or result can also change controls. The later
positive interface observation validates and classifies an earlier candidate
without pretending that bubbles or the shield panel were already visible at
the earlier time.

## HP context gate — user correction

Distinguish the game's HP state from what the sampler managed to see:

| HP evidence at the control transition | Treatment |
| --- | --- |
| Both current combatants have fresh, positively nonzero HP, with continuity across the fade | Eligible charged-preparation candidate; a faint is inconsistent with that observed continuity. Use the later interface to establish player QTE versus opponent shield choice. |
| HP depletion or a corroborated faint/resource decrease affects either current combatant | Associate the fade with faint/replacement; do not promote it to charged preparation for that appearance. |
| Either HP bar is missing, stale, obstructed, too close to pixel tolerance, or belongs to a departing appearance | HP context is unknown. Do not substitute an old positive value or declare zero from disappearance. |

Measure both bars from the same shared source frame where possible and retain
their Articles/times around the fade. A positive reading before a final hit is
not proof of positive HP afterward. Likewise, a replacement's full bar must not
be combined with the departing Pokémon's controls or identity.

The HP condition applies to sequence entry. A later charged impact can deplete
the defender's HP; that later faint does not invalidate the correctly observed
earlier QTE/shield start. Keep those source times separate when checking conflicts.

An empty HP bar need not survive long enough to be sampled. Existing faint
inference therefore also uses the side's Poké Ball count decrease and appearance
context. Missing an exact zero frame does not establish survival. Conversely,
an unmeasurable bar is not a zero-HP observation.

This gate uses existing independent `ActiveHpBarMeasured` geometry/fill and its
Article source timestamps; the live HP reader measures both sides from one frame.
The proposed transition inference must additionally bind these measurements to
the current appearances and invalidate them at faint/switch/capture boundaries.
The gate is specified here, not yet wired into a new production phase detector.

## Source-time observations

All times below are seconds from the original video's presentation timestamps
(PTS), **not elapsed Match time**. Boundaries were inspected in consecutive
native-size source frames, with additional reduced contact sheets for context.
The short brackets describe visible fade onset, not the game engine's internal
turn boundary or a claimed live-device timing accuracy.

| Source / episode | Last full card CP text | First visible fade | Card CP text gone | Later interface |
| --- | ---: | ---: | ---: | --- |
| November 3, player sequence 1 | 26.969633 | 26.985900 | 27.113300 | Camera/Get Ready about 27.75; bubbles about 29.25 |
| November 3, player sequence 2 | 44.970033 | 44.990078 | 45.144856 | Camera/Get Ready about 45.73 |
| November 4, opponent sequence 1 | 40.438856 | 40.456744 | 40.591322 | Shield panel appears about 41.90–41.97 |

The fixed white CP-letter pixels provide an opacity measurement without OCR;
the native frames also show the controls fading with those cards. The controls
are fully or nearly fully faded by the end of each small interval. The enlarging
selected charge button and finger ring remain separate phenomena.
A tap/enlargement alone is intent evidence, not an accepted charged move.

In the first player example, moving bubbles arrive more than two seconds after
the early control transition. In the opponent example the shield panel arrives
roughly 1.5 seconds after fade begins. Using either later interface as the
beginning of all charged preparation would preserve much of the replay's wait.

The exit/running icon remains visible through these **early entry/Get Ready**
frames. It disappears later in the player's bubble stage: the November 3
`nov3qte/008.png` source-derived frame visibly contains NICE and bubbles but no
exit icon. Exit disappearance is therefore later supporting evidence, not a
universal earliest start clock. Do not confuse the battle running-person glyph
with the supplied menu door/arrow asset merely because both are called exit.

## Independent positive confirmation

- **Shield choice:** match the distinctive tessellated pink/blue shield graphic
  and its panel context. The supplied `Images/Combat/ShieldButton.png` is a useful
  reference. Reading the surrounding sentence should remain an independent
  confirmation, not a prerequisite for measuring graphic appearance.
- **Player bubble task:** observe repeated moving circular type glyphs and the
  task's visual layout. Each type may follow a different trajectory. The first
  positively observed glyph belongs to the bubble-task timestamp; it can
  confirm an earlier preparation boundary. A single colored circle, swipe ring,
  or type icon elsewhere on the battlefield is insufficient.
- **Camera/HP geometry:** relocation and scaling corroborate charge preparation
  and help associate its actor. A camera change alone also occurs around entries
  and faints; it is not enough to declare a charged sequence.
- **Decision dial:** useful additional timing evidence, but a dial also appears
  during replacement selection. It cannot independently mean shield choice.

When no shields remain, the shield graphic may be unavailable. This case needs
separate labeled examples before claiming graphic-only opponent-phase coverage.
Likewise, a player with no remaining inactive cards cannot supply card-loss
evidence. Keep these unavailable lanes explicit rather than manufacturing loss.

These are concrete exclusions in the reviewed source: the November 4 faint at
about 59.05 seconds fades both controls and cards, then opens replacement
selection. It demonstrates why HP/faint context accompanies the fade, not why
faint and charge remain ambiguous when both combatants are positively alive.
Drapion at 97 seconds has no inactive card at all; another opponent
charge follows around 99.5 seconds. Card disappearance therefore corroborates
an early candidate when available, but cannot be mandatory for every combatant.

The controlled two-phone November 2 recording adds a different 816 × 1808
source: ordinary controls/cards are visible at 26 seconds, already gone in the
first retained native transition frame at 27.610100, and Get Ready is visible at
28.135467. Exit is still fully visible at 29.117189 and fades by 29.242800. The
earliest retained transition frame is already after control loss, so this example
does not establish an adjacent-frame onset bracket. It does confirm that exit
loss would timestamp preparation over 1.5 seconds too late.

## Audio and motion findings

The existing result-music recognizer covers Win, Loss, Battle, and Team Select;
it has no production QTE-onset reference. The supplied sound filenames contain
no explicitly labeled QTE-onset/shield-onset/pop reference. An unlabeled or
procedural source may still exist; this is not proof that the sound is absent.

The November 3 first pop activity is around 29.25 seconds. Between Get Ready
and the first pops, the recorded audio is almost silent. In the November 4
shield episode the prominent attack sound arrives near 47.5 seconds, during
execution, well after entry. Neither first pop nor loudness supplies the wanted
early boundary in these examples.

Raw accelerometer pulses remain independent testimony. The existing QTE haptic
inference describes Nice/Great/Excellent milestones, not the beginning of the
sequence. Do not subtract a fixed assumed QTE length to invent an earlier time.
Fast Move silence alone also cannot distinguish charge preparation from idle
play, muted audio, lag, or interrupted playback.

## Current implementation audit

- `battle/replay/ReplayChargeTransitions.kt` already estimates an earlier
  presentation boundary from two stable charge-button circle measurements,
  sustained loss, continuous coverage, and a later typed phase cue. This is
  replay-only reconstruction and still depends on a cue such as Get Ready.
- `LivePlayerChargeMoveEnergyWitness.kt` currently samples circle geometry every
  500 ms. `UNKNOWN_RIM` combines missing, clipped, faded, and distorted cases;
  it must not become categorical proof that controls disappeared.
- `QteTypeIconSampling` starts after `GetReadyWitnessed`. Its present raw crop
  collection cannot independently discover a missed phase.
- Exit Icon has a calibration region and crop contract but no runtime visual
  recognizer. Adding capture alone would not solve the timing gap above.
- Inactive-card fingerprints, darkness probes, and colorful HP bands do not
  positively establish card presence. Existing snapshots are not continuous
  visibility coverage.

## Bounded implementation order

1. Measure static control chrome in the shared live frame: charge-label glyphs,
   button outlines, and inactive-card borders/CP glyphs. Preserve positive
   normal-play support, confidence of each measurement, geometry, capture gaps,
   and obstruction. Restrict comparisons to those features; fill height and
   animated Pokémon interiors are deliberately unsuitable tracking masks.
2. Sample cheap pixel measurements at an initial 125 ms target while battle is
   established, including late-entry sessions. Measure actual delivered cadence
   and phone cost. Preserve sparse baseline/change/confirmation crops rather
   than encoding every frame. This target is not an accuracy guarantee.
3. Report raw **baseline appearance changed/faded** measurements. Invalid,
   clipped, obscured, shifted, or uncalibrated crops remain unknown. Require
   stable positive baseline and independently changing chrome territories before
   making a transition candidate. Apply the HP context gate above: retain fresh
   nonzero evidence for both current appearances and reject depletion/faint
   contradictions. A missing-circle search result is inadequate.
4. Add independent positive shield-graphic and bubble-task recognition. A
   pending candidate opens their short attention window; Get Ready is additional
   evidence rather than the only way these readers can run.
5. A downstream inference appends the confirmed transition to Custody/Timeline,
   retaining `lastNormalAt`, `firstChangedAt`, `confirmedChangedAt`,
   `phaseInterfaceAt`, performer when supported, and all predecessor IDs. Raw
   measurements remain independent. The Timeline stores the Articles; it does
   not perform this reasoning. Later classification does not change source times.
6. Replay consumes the durable bounded transition for preparation/focus, and
   the later interface event for bubble/shield presentation. Retain the existing
   conservative reconstruction for compatible older archives. Do not relabel a
   completed attack or remove a supported Fast Move solely to fit an animation.

If there is no trustworthy normal baseline, preserve the first positive phase
interface as an upper bound and leave the earlier preparation onset unresolved.
Do not silently replace a wide uncertainty interval with the first failed read.

Before enabling automatic boundaries, check ordinary idle play; changing energy
fills; touch rings and enlarged buttons; one centered charged move; switches;
faints/replacement dialogs; fresh positive versus stale/missing/zero HP; a final
hit between samples; a later charged impact causing a faint; appearance
replacement; last-Pokémon/no-shield cases; both phase directions;
menu/result screens; incorrect profiles; overlays; capture gaps; and delayed
publication. Validate archive round-trip and old-archive replay compatibility.

## Reproducible private evidence

Private source media and extracted pixels remain outside Git:

- Root: `/home/sean/overdex-device-backups/2026-11-04-independent-phase/`
- `video-audit/nov3qte1-native/manifest.json`, `nov3qte2-native/manifest.json`,
  and `nov4shield1-native/manifest.json` retain source frame PTS and inspection
  rectangles. Their native PNGs are unchanged extractions; contact sheets are
  labeled derivatives, not new source screenshots.
- `video-audit/*-native-strips.jpg` show the synchronous fade alongside the exit.
- `audio/findings.md`, `audio/sources.json`, spectrograms, and 50 ms level CSVs
  preserve the audio comparison. Its coarse visual timing is superseded by the
  native-frame table above.
- `pvp/` retains the additional October 23 and November 2 context checks. Source
  filenames and the displayed opponent must be checked before assigning battle
  mode; the October 23 recording appears to involve a team leader.

Primary source hashes:

- `/home/sean/Desktop/screen-20261103-150435.mp4` — SHA-256
  `4ba306b90967710aa4ec74b41a4d915cf6d6bfe53a2b7221040aafece2e1c0d5`.
- `/home/sean/overdex-device-backups/2026-11-04-pointer-calibration-videos/screen-20261104-101900.mp4`
  — SHA-256 `6911476aa3cf208f4bb3625d9f4d1a6e3bec71092e1620881ab1c14b6fb0af84`.
- `/home/sean/Desktop/Screen_recording_20261102_112438.mp4` — SHA-256
  `3b4b8df99d36b2b9d7f1d9604c50249f812dcbb1b32b8540c9cc6796ee39fb7c`.

Validation here is source-frame inspection, timestamp/provenance checking, and
current-code audit. No Android build/install is required for this documentation
change, and none is presented as proof of a new detector.
