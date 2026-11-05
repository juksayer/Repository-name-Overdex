# Audio position and LCD input experiments

November 4, 2026; keyboard clarification and first implementation November 5.
Audio-position work remains a source-audited proposal. The duplicated LCD input
header was removed first; a second November 5 slice adds expanded typing space,
QWERTY and local Pokémon/move swipe suggestions, as described below.
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
first slice uses the reclaimed LCD space within its then-existing key-size
limits. See the
[implementation checks](../verification/2026-11-05-keyboard-lcd-space.md).

The second slice targets a 390 dp portrait typing console: full-width LCD above
two rows of 48 dp instrument buttons. Binder typing keeps 48 dp side controls
and targets a 420 dp console, increasing the rotated keyboard's logical width.
Actual viewport height limits growth; short screens retain a CRT preview and
keyboard scrolling. Ordinary browsing retains its 170 dp panel and 56×36 dp
controls. Letter keys now grow to 48 dp high. The earlier 220–226 dp proposal
would have left ten QWERTY columns too narrow; panel enlargement alone was not
enough. Physical comfort still needs trainer review.

QWERTY is the default letter page, with ABC available. Token switching and
reopening a field preserve the chosen primary page. Numeric and whole-move
choice grids remain separate.

The user chose to keep typing inside the LCD because Android's keyboard does
not fit the instrument's appearance. The custom keyboard now compares a finger
trace with the bundled species/move catalogue off the UI thread, using measured
key positions before rotation. It proposes up to three canonical names, entered
only after the trainer taps a choice. This is a domain recognizer, not general
language prediction; nicknames and chat still support ordinary taps. Unmatched,
canceled or multi-pointer gestures enter nothing. Original text is not silently
replaced. See the [implementation and acceptance checks](../verification/2026-11-05-lcd-qwerty-swipe.md).

Remaining comfort checks include long names, font scaling and repeated real
finger gestures on each phone. Larger-screen typing and recognizer tuning can
follow that evidence. The audio-position experiment above has not been wired
into production recognition by this keyboard work.

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
