# Audio review context and homework selection — October 30, 2026

## Result

The audio sheet now shows the player's source-backed equipped move, the active
opponent, and its legal Fast Moves before listening. Original global sound
comparisons remain unchanged. A library resemblance is not an attacker identity.
Legal opponent references omitted from the original three comparisons are added
separately, without invented scores or alignment windows. Out-of-pool references
are labeled acoustic lookalikes and collapsed under diagnostics.

The original review-set ID remains `5ebbf8e737c27daea997`; original recording and
reference hashes, candidate letters, scores, and source provenance remain intact.
Existing answers and notes were backed up and restored without resetting them.
Supplemental context has an independent revision that binds its reference hashes,
source Articles, catalogue snapshot, human reports, and curation rationale. New
supplemental-reference answers must match that revision on import. Legacy A/B/C
answers remain accepted without claiming they saw the later guide.

The reusable generator accepts separately sourced listening notes and review-task
curation through `--listening-context`. Unassessed comparisons default to diagnostic
examples rather than becoming opponent homework solely because global scores tie.
Only audited `needs_review` cases enter the default queue. All examples and old
answers remain accessible through **Show diagnostic examples**.

## Which sounds and whose move?

| Original case | Player context | Opponent | Current homework eligibility |
| --- | --- | --- | --- |
| 1 | Golbat / configured Wing Attack | Combusken | Diagnostic: Ember clearly leads legal alternative Peck; close global competitor is the player. |
| 2 | Same | Combusken | Duplicate mixed-side diagnostic. |
| 3 | Golbat / configured Wing Attack | Magcargo | The sole tentative homework candidate: legal alternatives are closer, but audio is weak and player-dominated. |
| 4 | Golbat / configured Wing Attack | Combusken | Accepted player Wing Attack control, not ambiguous opponent homework. |
| 5 | Scrafty / user-confirmed Snarl | Flareon | Diagnostic: Ember leads Fire Spin; close global competitor is the player. |
| 6 | Idle Galvantula / equipped move unknown | Mawile / user-confirmed Fire Fang | Resolved opponent and missing-library diagnostic, not homework. |

Case 3 asks only for legal opponent sounds and offers **No opponent attack
distinguishable**. It is valid to decline a move identity if the opponent cannot
be heard. Known-player playback remains available to orient the listener, without
pretending to subtract it from the recording.

Golbat's active lead comes from the confirmed party, not player badge OCR. The
context helper distinguishes configured, human-confirmed, inferred, and unknown
moves. It excludes stale teams, distinguishes regional names, handles duplicate
species with differing moves, and reports changes within a clip. A switch or
matching/unnamed faint makes whole-clip movepool restrictions unresolved.

Combusken was labeled Ember during the match at **+4.383 s** and **+7.279 s**,
but both records explicitly retained a higher-energy tie-break assumption. This
is distinct from firm confirmation. Nonetheless, these snippets are poor legal
opponent homework:

| Case | Ember original / changing score | Peck original / changing score |
| --- | --- | --- |
| 1 | 0.965326 / 0.805240 | 0.899532 / 0.635645 |
| 2 | 0.966806 / 0.850349 | 0.899872 / 0.669257 |

Missing hard confirmation does not by itself make an example useful for human
review. A close global ranking against a known player sound is a different question
from ambiguity among the opponent's legal moves.

## Longer recordings: evidence boundary

All six selected recordings contain **1.2 seconds**. Auditing all **106 referenced
WAVs** in the three exact archives found no overlap or containment that could extend
any selected clip. Seven 13-second charge-event recordings cover other moments.
The supplied October 25 video is a different repeated match and contains no audio
track. The current clips therefore cannot be made longer faithfully.

Exact PCM checks found 34 overlaps elsewhere. Their sample alignment differs from
capture-clock alignment by up to 19.1 ms, so timestamps alone are insufficient for
joining future source windows. No gaps were removed or filled to manufacture a
longer recording. Longer homework requires retained continuous audio covering
the selected event.

Private evidence, unchanged source backups, human-context configuration, and QA:
`/home/sean/overdex-device-backups/2026-10-30-audio-context-review/`.
The refreshed browser and Desktop HTML copies are about 3.2 MB and work offline.

## Production audio audit: a separate remaining issue

This change updates the offline review tool; it does not change Android recognition.
The production path currently:

1. Compares the complete sound catalogue and accepts/rejects its global leader.
2. Selects that global candidate again for preference/identity inference.
3. Checks legal ownership before any audio-derived FastMoveIdentified publication.

An impossible global leader can block a legal runner-up. There is no contextual
runner-up re-ranking in the audited path. The global plausible-candidate retention
check can also omit a rejected window when the global leader fails its changing
score floor, even when a legal runner-up would pass plausible floors. Filtering
only saved comparisons afterward cannot recover omitted windows.

No arbitrary off-pool identity bypass was found. Configured identities intersect
the species movepool, and cadence/effectiveness paths begin with legal candidates.
Bare opponent names may intentionally retain the union of unresolved forms.
Future contextual audio decisions must preserve global raw measurements, respect
each side's legal choices, and allow mixed/no-attack outcomes rather than forcing
a winner from the remaining legal list.

## Validation

- **30 Python tests** passed for source times, context provenance, stale teams,
  form identity, switch/faint boundaries, legal reference additions, stable review
  identity, and the existing signal/audio generator behavior.
- **18 Node tests** passed for legacy answers, revised guide provenance, atomic
  import rejection, and unchanged exact-excerpt playback.
- Generated application JavaScript parses successfully; whitespace checks pass.
- Browser checks verified player-reference playback, the single-case homework
  queue, accessible diagnostic examples, legal Ember/Peck references, preserved
  old notes/review counts, and supplemental-answer export with guide provenance.
- No source archives, Android code, matcher thresholds, devices, or Timeline
  Articles were changed. No Android build/install is required for this tool change.
