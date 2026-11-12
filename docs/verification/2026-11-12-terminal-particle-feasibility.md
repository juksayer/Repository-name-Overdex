# Final charged KOs without an empty HP outline

Status: offline visual-signal experiment. The final charged-KO path must not
require an empty HP outline. No production recognizer or installed APK changed
in this experiment; automatic final charged-KO recognition remains unfinished.

## Requirement and evidence

The trainer clarified on November 12: “we dont get to see an empty hp bar for the
final ko if it is a charge move.” The reviewed Cramorant/Rock Tomb finish is a
direct no-empty example. The recognition problem is therefore the terminal
animation sequence, not simply increasing the chances of capturing a zero.
See [VIS-03 in the rulebook](../reference/battle-rulebook.md) and the earlier
[native badge review](2026-11-11-final-native-badge-visibility.md).

A successful path must preserve the distinction between a directly measured
HP value and an inferred terminal depletion. It may eventually infer a faint
from a qualified, owned sequence without asserting that an empty outline was
seen. It must not reconstruct an exact intermediate damage trace or extra Fast
Move from that conclusion.

## Reproducible experiment

Scripts, source and frame hashes, MP4 presentation clocks, numerical results,
and review contact sheets are in the
[study directory](/home/sean/overdex-device-backups/2026-11-12-terminal-particle-study).
No observations were inserted into a match archive. Video PTS below is not the
archive's wall or monotonic clock.

The first comparison used 506 lossless native 816×1808 frames in nine selected
intervals: Cramorant, Tinkaton, Mismagius and player Gourgeist KOs; Carbink's living
recall; Gourgeist's charge-camera movement; Shadow Camerupt occlusion; a living
Mismagius charge sequence; and Roselia's reported concession. The 214 frames
from the [earlier motion study](2026-11-11-terminal-motion-feasibility.md) were
reused; four additional intervals supplied 292 frames.

The prototype finds fresh compact white components, checks for a diffuse cool
halo, then checks radial brightness falloff and a small two-dimensional cluster.
It operates in a manually specified broad opponent territory. Its output is a
candidate cluster, not a species, recipient, disappearance, recall or faint.
The optimized scan produced exactly the original halo candidates on all 506
selected frames before it was used on full recordings.

Full-recording scans cover **18,852 decoded frames**:

| Recording suffix, November 11 | Frames | Role in experiment |
| --- | ---: | --- |
| 21:13:35 | 1,901 | Development; includes Cramorant final charged KO |
| 16:15:31 | 5,880 | Development; includes shield/QTE effects and Roselia concession |
| 12:44:55 | 4,793 | Development; includes living Carbink recall |
| 11:21:07 | 3,028 | Additional recording, parameters frozen; includes Leavanny final charged KO |
| 19:03:45 | 3,250 | Additional recording, parameters frozen; two matches with no opponent KOs |

The scans include menus, QTE and post-match screens deliberately, to expose
alternatives. This is not a labeled accuracy benchmark, and the additional
recordings are not a statistically independent validation set. Their event
timing was not used to select the detector's pixel thresholds.

## Findings

- Cramorant produces spatial cluster candidates at **180.816033** and
  **180.888578** seconds, during the manually reviewed collapse. The strongest
  group has eight selected components. Annihilape's earlier collapse also
  produces a group at 161.553–161.744 seconds.
- With unchanged parameters, Leavanny's final charged KO produces candidates
  at **117.322556** and **117.574311** seconds. This supports investigating the
  signal beyond the original Cramorant example.
- White-component counts alone also react to effectiveness text, pale Pokémon
  features, pointer rings, QTE effects and shields. Spatial filtering removes
  many horizontal text groups, but does not remove all competing explanations.
- The remaining repeated groups include a shield sequence at 54.861–54.892
  seconds in 16:15:31 and a player QTE at 81.857–81.937 in 12:44:55. In the
  additional 19:03:45 recording, repeated groups occur during Camerupt's entry
  and Pelipper's attack. There are no opponent KOs in that recording. A broad
  opponent territory can contain player-entry effects or the attacking Pokémon;
  territory alone cannot assign the receiving combatant.
- The selected Roselia concession and Carbink living recall do not produce a
  qualifying spatial group. That does not prove immunity to other concessions
  or recalls. The same detector also misses known KOs, including the selected
  Tinkaton and Mismagius cases. Lack of particles cannot establish survival.
- A separate larger-component/orb probe remains exploratory. It does not yet
  establish the proposed order of dispersed particles → compact collapse →
  disappearance, nor the reverse order for an entry. The manual visual sequence
  must not be presented as an implemented automatic reader.

## Sampling consequence

The strongest Cramorant cluster exists in two decoded frames only about 73 ms
apart. A source-frame decimation experiment with 25 starting phases found at
least one of them in 18 phases at a 250 ms minimum interval, and neither frame
in the other seven. No phase retained both. A separate Leavanny experiment also
showed substantial sampling aliasing; merely increasing a fixed snapshot rate
did not monotonically improve coverage of its two isolated candidate frames.

These are optimistic offline capture-coverage simulations using classifications
already computed from full-rate frames. They do not rerun temporal detection at
the reduced cadence, include encoding/recognition delay, or measure Droidball's
actual frame supply. They do show why a two-snapshot particle requirement cannot
be assumed reliable with the current sparse PNG assignment.

If this signal is developed further, measure it on each available frame during
a bounded, source-owned receiving phase, while retaining selected images for
audit separately. Do not increase full-resolution PNG retention throughout the
match, and do not treat missed snapshots as evidence that a faint did not occur.

## Decision and next implementation boundary

Keep this as an offline candidate detector until the ordered finish sequence
can be checked. The next reader must distinguish receiving-side charged impact
from the player's QTE, a shield, an attacker's own animation, and an entry. It
also needs ownership across camera movement and independent evidence of the
terminal departure, rather than treating lost tracking as disappearance.

The final result may corroborate that the battle ended, but a win alone does
not choose elimination over concession. Positive native HP or later survival
must remain available to contradict a proposed KO. Missing HP/particles alone
cannot decide it. This preserves the user's no-empty requirement while avoiding
the earlier Carbink/Gourgeist false-faint failure.

## Validation and storage

Source hashes for all five recordings, all 506 selected-interval frame hashes,
and 179 retained high-count full-scan frames are checked in the study's
`validation.json`. Thirty candidate frames from the two additional recordings
were independently decoded again and matched by exact BGR-byte hash before
their contact sheets were reviewed. Scan scripts and parameters are retained.

The disk filled during the first additional-recording scan. That incomplete run
is not counted above. Duplicate generated PNGs from the exploratory orb scan
and incomplete run were removed, recovering about 492 MiB; their source MP4s,
PTS and result metadata remain. Completed primary scans and selected-interval
fixtures were retained. The additional recordings were then fully scanned into
compact numerical reports without saving another set of full frames. No user
recording, original match archive, phone data or prior installed APK was removed
or changed.

App code, assets and configuration are unchanged by this experiment, so no
Android rebuild or installation is claimed. Live device accuracy and CPU cost
of a future particle/sequence reader remain untested.
