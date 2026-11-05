# Audio position and LCD input experiments

November 4, 2026; keyboard clarification and first implementation November 5.
Audio-position work and larger keyboard layouts remain source-audited proposals.
The duplicated LCD input header has now been removed, as described below.
Fast Move fidelity remains the priority. The user is performing an ending/cry
test; do not interrupt capture or install a replacement build during that session.

## Position within overlapping move sounds

The proposed question is useful: can a recording be better explained by the
beginning of the player's known sound plus the tail of an opponent candidate,
with independent attack timing restricting their possible positions?

This is distinct from merely lengthening the audio query. The earlier bounded
longer-window/filter experiment is complete and did not show a strict-acceptance
gain. This new proposal uses relative sound position and a joint mixture model.

### Existing groundwork

- `FastMoveReferenceMatcher` v3 compares original/changing spectra over the same
  352–416 ms passage. It retains query offset, reference offset, matched duration,
  scores and reference hash. Sliding offsets advance in 16 ms feature steps.
- `FastMoveAudioWindow` supplies 1.2-second queries every 200 ms. These are
  overlapping analysis windows, not separate attack counts.
- `LiveFastMoveSoundWitness` already estimates a reference occurrence at
  `window start + query offset - reference offset` to deduplicate retained sound.
- HP-supported inference already distinguishes independent impacts from audio
  support. An audio-derived conclusion or elapsed-time forecast cannot support
  its own attack count.

Current matching does not jointly explain both sounds or maintain a calibrated
sound-playback position per current combatant. Its mono spectral magnitudes do
not preserve the waveform/stereo information needed for exact cancellation.

### Timing restrictions

**Reference position, sound duration and turn duration are separate.** Some
references are recognition excerpts, not complete start-to-finish effects. The
Mud-Slap addition is a 600 ms trim beginning 600 ms into its retained query;
reference zero is not certified attack onset. Establish an audio-to-attack
anchor and uncertainty before saying a passage is the final 300 ms of a move.

Use legal move timing to restrict possible occurrences after independent
onset/impact evidence. Do not assume every predicted turn actually produced a
sound or attack. Pauses, stutters, switches, QTEs, camera intervals and capture
gaps widen or invalidate the expectation. Same sounds at the same time may
remain impossible to attribute from audio alone.

### Small offline experiment before live changes

1. Reuse independently labeled single-attacker recordings, keeping reference
   source attacks separate from held-out test attacks.
2. Form explicitly synthetic overlaps with known start offsets and loudness
   ratios. Preserve every original PCM; derived mixes/masks are separate files.
3. Compare the actual current matcher with timing-constrained scoring of two
   aligned templates. Try weighting distinctive time/frequency portions first,
   then a simple nonnegative fit of player + opponent + unexplained sound.
   Include a player-only/no-opponent alternative; two legal pools do not prove
   two attackers are audible.
4. Deliberately perturb expected timing by 16/40/80/160 ms. Include idle, cries,
   QTE, stutter and switch/capture-gap controls. A wrong timing expectation must
   not turn uncertainty into false certainty.
5. Report legal identity, wrong-side attribution, rejection and duplicate rates,
   evidence time to conclusion, and cost. Do not change attack counts, energy,
   thresholds or production audio during the experiment.
6. Only if synthetic controls improve without extra false conclusions, assess
   held-out real overlaps. Unconfirmed move labels in exploratory November 3/4
   recordings cannot establish accuracy by themselves.

This first result can be a diagnostic comparison with source IDs, timing ranges,
expected positions and unchanged raw audio. Cleaner recognition does not require
producing a perfectly isolated, listenable track. Straight waveform subtraction
is a later, more fragile experiment.

Primary research supports feasibility, not Overdex-specific accuracy:
[known-signal alignment by cross-correlation](https://docs.scipy.org/doc/scipy/reference/generated/scipy.signal.correlate.html),
[temporal spectral templates for overlapping sources](https://www.merl.com/publications/TR2004-104).

## LCD keyboard space and QWERTY

The shared shell currently allocates a 170 dp lower panel and two 56 dp control
columns. Buttons are 56×36 dp. Four keyboard rows have 6 dp gaps and a 20–32 dp
height clamp; merely enlarging the panel cannot enlarge keys beyond that cap.

The active shared LCD path is `ui/components/PokedexComponents.kt`, not the
similarly named simple `InstrumentLCD` function in `ODXFiShell.kt`. The user
clarified that the duplication was across displays: the LCD keyboard header
repeated information already on the CRT, rather than two confirmation rows
inside the LCD.

The November 5 first slice removes that optional 9 sp header and its 2 dp gap.
Flows with an existing CRT field keep it. Pokédex/owned-binder searches, directory
species search and Trainer Comms now put their otherwise sole query/draft
preview on the CRT. The shared CRT content stays at one stable composition call
site, so opening the keyboard does not reset a binder's selected card. The
keyboard uses the reclaimed LCD space within the existing key-size limits;
shell dimensions and keyboard layout have not changed. See the
[implementation checks](../verification/2026-11-05-keyboard-lcd-space.md).

The useful first layout experiment is keyboard-only: move the CRT's lower edge
up, enlarge the LCD, and narrow control columns while retaining large physical
targets. A candidate is a roughly 220–226 dp lower panel, 48 dp control columns,
and 40–44 dp letter-key ceiling. Those numbers are trial values; measure short
screens and text scaling before selecting them. Change parent columns and button
geometry together; preserve ordinary/binder layout and the CRT GlassShield.

Keep one compact field/value preview. Remove duplicated confirmation/instruction
text where actually found. Preserve one clear finish action plus physical A/B
behavior. Reclaiming a row should enlarge the keys or result area visibly.

QWERTY can be an optional primary letter layout, preserving the current layout
for comparison. The controller currently toggles LettersLayout ↔ TokenLayout and
several workflows reset LettersLayout explicitly. A new constant alone would
fall back to ABC after changing pages. Preserve numeric and move-choice grids.
QWERTY has more keys across a row, so test horizontal targets as well as height.

The custom grid has no gesture recognizer. Swipe-to-type inside it requires its
own path recognition and correction UI. Species/move catalogue suggestions are
a useful limited-vocabulary input source. Another experiment is a real LCD text
field using the installed Android keyboard; gesture availability then belongs
to that keyboard, not the custom LCD grid. Release and restore shell navigation
focus correctly. Ordinary keyboard dictionaries may not know the domain names.
See [Android text input](https://developer.android.com/develop/ui/compose/text/user-input).

Validate geometry in the emulator: long field text, ABC/QWERTY/token switching,
SELECT/START labels, numbers, two-column move choices, END/B behavior, save once,
focus restoration, binder rotation and LCD touch ownership. Then obtain a phone
check after the user's battle recording has finished. QWERTY/swipe and the larger
panel geometry are still proposals; the November 5 header fix does not implement
them.

## Turning if/then ideas into useful evidence rules

Record four things: the visible/audible cue, independent context and time range,
the proposed conclusion, and counterexamples that should prevent it.

| If / context | Testable use | Limit |
| --- | --- | --- |
| Independently supported player move and plausible sound position | Compare opponent candidates while accounting for expected player content | Player-only sound and misalignment remain alternatives |
| Known move duration plus a witnessed hit | Narrow the next timing search range | Elapsed turns do not create observed attacks or energy |
| An acoustically distinctive opponent tail overlaps a quieter/different player passage | Weight that passage more heavily in a joint comparison | A source excerpt needs an onset/impact anchor first |
| Both current HP bars are freshly positive and controls leave ordinary play | Candidate charged transition with later interface confirmation | Missing/stale HP and faint/replacement contexts stay unknown/different |

Witnesses keep measuring their own signals. Inference combines those signals and
preserves alternatives; Timeline stores the resulting testimony and provenance.
