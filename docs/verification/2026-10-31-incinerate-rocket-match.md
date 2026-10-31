# Incinerate Rocket match review — October 31, 2026

## Evidence and ground truth

The Moondrop recording `0e62f068-13b5-493c-92cc-6c69a4b32cbf`, started
at 08:08:53 local time, contains 1,994 Articles and 275 declared artifacts.
The desktop copy matches the device SHA-256 and all 275 artifacts verify.
A subsequent 08:12:09 session (`c7dcc080-e060-4892-bfeb-cdc7a2bbaf78`)
contains 158 Articles, no media, and no recorded battle or result.

The user confirmed that Turtonator, Gourgeist, and Camerupt all had Incinerate
and attacked continuously. Near Scizor's arrival, the user saw approximately
three or four partial Incinerate animations within one actual move cycle.
No continuous screen recording was supplied for this match.

The opponent was Venonat, Dewpider, and Scizor. The retained full frames show
a Team GO Rocket battle. Sparse source frames were copied separately from
the device's artifact store to inspect calibration. Their filesystem times
are not exact capture timestamps or continuous animation evidence.

Private evidence, hashes, user corrections, and review images are under
`/home/sean/overdex-device-backups/2026-10-31-new-match/`.
Original device archives, source Articles, saved teams, owned cards, and
calibration profiles were not changed by this review.

## Opening, configuration, and result

- VS was witnessed at session +44.475 s. A preserved countdown crop clearly
  shows **2** at +51.329 s, without an intersecting Overdex overlay. GO was
  not witnessed; the recorded Match start at +53.329 s is inferred from
  countdown and combat evidence.
- There is no accepted Team Select roster in this archive. Available
  countdown pixels support the absence of setup-menu obstruction at that
  instant, but do not establish that the new party-departure detector fired.
- The configured roster remained shadow Golbat / Greninja / Victreebel,
  with the corrected Night Slash and Leaf Tornado and the same owned IDs
  verified on October 30. Actual Turtonator / Gourgeist / Camerupt observations
  were recorded as different species, without inheriting those configured
  moves or assigning unsupported specimen IDs or team slots.
- The phone does contain the named saved team `TURTOCAMERUGOURGEIST`, with
  Incinerate on all three members. The live path currently receives only
  Current Team configuration. Other named teams are not supplied as saved
  loadout reference candidates during battle. The old snapshot also names
  Gourgeist's Average form; the user's earlier size correction must not be
  silently replaced by that stale form label.
- YOU WIN was captured at +134.098 s and MatchEnded published 2,874 ms later.
  Its recent urgent OCR request waited 1,888 ms, selected a crop aged 2,545 ms
  at admission, and took 272 ms of native reading. Total reader time was
  2,866 ms. Older recovery work did not become the result source.

## Fast Moves, energy, and charge evidence

| Side | Combatant | Recorded uses |
| --- | --- | ---: |
| Player | Turtonator | 5 |
| Player | Gourgeist | 1 |
| Player | Camerupt | 2 |
| Opponent | Venonat | 6 |
| Opponent | Dewpider | 2 |
| Opponent | Scizor | 7 |

These are recorded uses, not ground-truth totals. Only one Turtonator use is
present near Scizor's arrival, at +99.872 s. The archive does not contain a
burst of three or four credited Incinerates in one cycle. The stronger failure
here is incomplete recognition and counting; sparse frames cannot validate
every rejection of the reported stutter.

Turtonator's Incinerate identity was published from source time +78.910 s,
about 25.6 s after the inferred Match start. Gourgeist and Camerupt did not
receive exact Fast Move identities. Only two player Incinerate energy credits,
40 energy in total, were recorded, despite a named Dragon Pulse costing 55.
This supports missing credited generation, not fabricated precise attack times.

Get Ready at +81.189 s and Dragon Pulse at +88.045 s bracket one recognized
player charge sequence. No Fast Move uses or generation credits were recorded
inside that interval. Other sparse frames show QTE activity outside that
recognized sequence, so this is not a complete charge-transition record.
Attack Incoming at +115.072 s reached custody 4.924 s later and has no retained
named opponent charged-move conclusion.

All 41 audio measurements stayed within their archived active-species legal
search scopes. The bundled sound manifest lacks Confusion and Rock Smash.
Their requested legal alternatives remain visible in testimony, but cannot
be scored. Strict contextual audio attribution requires complete comparison
coverage, preventing Camerupt's strong Incinerate comparisons from resolving
its identity. Similarity values around 0.97 are matcher scores, not calibrated
probabilities of correctness.

The supplied Pokemon Moves directory also lacks Confusion and Rock Smash;
there is no faithful named source asset to add from that collection. All 72
currently bundled references (71 distinct move names) match their supplied
original bytes. The live path preserves every scored legal comparison; its
coverage failure is not a top-five candidate truncation.

The charged-move name crops are visibly misplaced: their top is about 79.8%
of frame height, while the native labels in the retained battle frame are
near the bottom, below the buttons. Sixteen of 17 label readings were empty;
one read `IRId`. The private `crop-placement-check.png` overlays the archived
rectangles on a retained full frame. The user's adjusted profile was preserved.

Venonat, Dewpider, and Turtonator faints were attributed to the outgoing
combatants. No Gourgeist faint was invented when Camerupt replaced it.

## Follow-up priority

1. Correct the charged-move label crop placement against a clean source frame
   and verify labels in a fresh match. Check the shared control/fill territory
   independently rather than moving every related region together.
2. Supply immutable per-match saved-loadout candidates from named teams and
   owned cards when Current Team differs. Preserve form uncertainty and
   disagreements; do not infer an owned UUID or slot from a species name,
   overwrite Current Team, or promote a saved prior into an observed attack.
3. Complete legal-pool audio reference coverage where faithful source assets
   exist. A missing comparison is not evidence that its move was absent.
4. Recheck Incinerate counts and charge boundaries with continuous video after
   those input gaps are addressed. Keep duration/energy bounds and raw
   independent measurements intact.

This was an evidence review. No production code, device installation, or profile
write was performed; an Android build is not needed for this documentation.
