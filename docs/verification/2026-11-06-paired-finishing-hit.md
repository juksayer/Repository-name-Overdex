# Alternating Fast Moves and the final faint — November 6, 2026

## Ground truth and preserved evidence

The user reported a new paired battle on Pixel 4 and Moondrop, alternating Fast
Moves for part of the battle and attempting to isolate the finishing Fast Move
and faint. Quagsire's only equipped charged move remains **Frustration**. This is
not a continuous-attack count test; the number and boundaries of taps were not
scripted. No saved moves or calibration were changed during this review.

| Device | Archive | Bytes | Articles / verified artifacts |
| --- | --- | ---: | ---: |
| Moondrop | `2026-11-06_10-15-04_be8ee012-4f1b-4239-8097-e32714023d89.odxmatch.zip` | 8,557,084 | 3,633 / 440 |
| Pixel 4 | `2026-11-06_10-14-59_5486f7ad-ccd6-45ed-ac0a-76a7a0385ddb.odxmatch.zip` | 19,137,611 | 4,621 / 553 |

Device and desktop ZIP hashes agree:

- Moondrop: `62b1288ed96857662ad0e31f8bd7d3398d9fea7ef5298db6a3691cc5c1056e5b`
- Pixel: `359005eda0f07b8e1ff3ef35c120b8dfa2f48b4152e0d9117173f5e33ed5ac87`

At review time, Moondrop had APK `cbe3db3646fdb758c44e07f121981a8631e140dae13df6496ca78b04bb12bd35`
and Pixel had `5bc2a6315ac86ddbdcda996242890be773bdeca4fdcfb95d6476220236c1deaf`.
The earlier deferred-energy fix was therefore installed only on Moondrop.
Neither archive contains MatchStarted or a witnessed GO. Archive-relative times
are measured from each MatchRecordStarted, never treated as exact battle time or
compared across device monotonic clocks without alignment.

The supplied `Screen_recording_20261106_101730.mp4` is 816 × 1808 and 172.20 seconds
long. It contains **no audio stream**. It can verify visual events, not a final
cry. Overdex's separate playback-audio clips remain in the archives. Private
copies, provenance, cropped frame inspections and detailed analysis are under
`/home/sean/overdex-device-backups/2026-11-06-paired-finishing-hit/`.

## Recorded attack behavior

These are archived canonical-use counts, not ground-truth completed attacks:

| Combatant | Pixel | Moondrop |
| --- | ---: | ---: |
| Swampert | 9 | 8 |
| Quagsire | 14 | 13 |
| Turtonator | 8 | 7 |
| Camerupt | 16 | 12 |
| Obstagoon | 0 | 0 |

Neither phone invented Obstagoon attacks. Both recorded the long pause in
Quagsire's attacks while Camerupt continued. Accepted audio identities were only
Mud Shot and Incinerate; all candidates remained inside the archived active
species' legal search scopes. The phones disagree on counts, so these observations
do not establish complete recall or the exact alternating tap boundaries.

## Fixed: width jitter split one HP plateau into two attacks

Three early Pixel Turtonator observations had separate damage and border-flash
uses only 0.49–0.99 seconds apart. They cannot be separate 2.5-second Incinerates.
The energy ceiling already rejected the extra border-only credits, but the
canonical-use stream still contained duplicate corroboration.

The corresponding HP measurements show stable physical fill endpoints at
approximately x=196, 177 and 160. During the flash, the detected bar width shifts
by two pixels, changing the percentage denominator without changing those fill
endpoints. Comparing percentages multiplied by different widths incorrectly
rejected plateau continuity.

`FastMoveUseInference.hpPlateauSupport` now reconstructs the filled-edge X
coordinate from each frame. Its reference edge uses the damage endpoint and the
first frame's geometry, retaining the existing 1.5-pixel tolerance without
stacking uncertainty. The two-pixel geometry limit, source dimensions, bar
aspect ratio, sample density, endpoint coverage, phase and appearance boundaries,
and rejection of intervening damage remain in place. This needs no assumed move
identity. All predecessor evidence and the first impact's source time survive.

## Fixed: critical HP stopped keeping Poké Ball checks available during idle

The existing gate opened a three-second watch on first entering critical HP and
on each further damage tick. Repeated unchanged low-HP frames did not renew it.
Thus a deliberate pause at about 8% HP could let the watch expire before the next
attack. Both archives show the ball-capture sources turning unavailable before
the result; that is an observation-coverage gap, not proof of a faint.

Fresh low-HP frames now refresh the timed watch to three seconds after their
captured source time. Side freshness is independent. Duplicate, older, stale or
future HP cannot extend it; healthy frames from the other side cannot keep it
alive. Repeated low frames do not reset the two-snapshot queue every frame.
Replacement resets the prior critical state, the 24% rearm threshold remains,
and result/stop prevents late cues reopening observation.

This changes when a witness may look. It does not change HP measurement or
convert bar absence, elapsed time or a result into an observed attack or faint.

## Final-hit evidence and remaining limits

Fine video inspection shows a small remaining HP stub at about **165.70–166.00 s**.
The bar fades at roughly **166.05–166.10 s**; no sustained empty outline
is visible. Obstagoon then enters its faint animation and disappears; YOU WIN is
visible at about **171.5 s**. These are approximate video positions. Frame
sampling and variable frame rate do not establish an exact server turn boundary.

Both archives stop their measured victim HP near 8%, contain no final zero-HP
measurement, no final use and no Obstagoon faint Article. They do preserve the
win/loss results. The visual recording therefore independently establishes an
ending that the live ledger did not fully witness.

There are two structural limits beyond the watch fix:

1. `ActiveHpBarMeasurer` requires saturated fill before accepting a bar outline;
   it cannot emit a genuinely empty neutral-outline bar. In this video, the game
   also fades the UI while the last small stub is still visible, so even a future
   empty-outline detector cannot be the only final-faint strategy.
2. Formal faint inference currently requires a Poké Ball count decrease. A cry
   can support it, but no final faint-cry candidate or zero-ball measurement is
   preserved here. A separate observed faint transition is needed when GO hides
   its controls. A result alone must remain compatible with concession.

No missing finishing attack was inserted into either archive or replay.

## Quagsire and the centered single charged-move control

The Pixel preferences retain an older team JSON plus a pending scanned roster.
The store correctly exposes an empty active loadout while that marker exists;
there are zero configured-slot Articles in this archive. The old preference is
not evidence that Quagsire was assigned Sludge Bomb during this battle.

Actual Pixel label PNGs show **FRUST** cut off at the right edge. The left crop
covers `(43,1281)–(330,1380)` in a 672 × 1419 frame, ending just before center
x=336. The right crop is considerably higher. Ten partial OCR reads faithfully
reflect those clipped pixels. Neither archive routes a CENTER label crop.

Current centered-control confirmation requires two matching rim observations
100 ms–1.25 seconds apart. Archived height samples have median gaps about 1.94 s
on Pixel and 1.85 s on Moondrop, which can prevent confirmation. Raw center
candidates are not preserved, so the exact fallback failure is not established.
The existing profiles were left untouched.

Moondrop did read “Quagsire used Frustration,” but no Frustration energy spend
was derived. Current Game Master Quagsire/Shadow movepools omit Frustration even
though its global move exists, and the label/announcement energy resolvers search
the species movepool directly. The installed Room reference pool was not queried;
this is a source-reference gap to address separately. Full unique labels and
honest ambiguity must remain required; clipped text must not overwrite equipment.

## Validation and delivery

- The final full JVM suite passed **1,797 tests / 259 suites**, with no failures,
  errors or skips. Debug APK assembly passed after both final production changes.
- Three new fusion test methods exercise all three recorded Pixel pairs in both
  delivery orders, genuine additional damage, relocated/healing/mismatched or
  incomplete evidence, and initial-offset-plus-drift tolerance stacking. Six new
  gate regressions cover long unchanged critical HP, bounded expiration,
  independent side freshness, stale/future/duplicate samples, hysteresis,
  replacement and result/stop. Existing recorded-match regressions also pass.
- Independent review caught the initial two-tolerance stacking risk. Anchoring to
  the damage endpoint corrected it before the final suite and build. The final
  source review found no blockers in either change.
- Both phones were idle, with no active Overdex service or MediaProjection,
  before replacement. The final APK was installed with data retained on
  Moondrop `MDPH00124112500414` and Pixel 4 `99261FFAZ0017J`; both installed files
  match host SHA-256
  `d5be8e6719e1780b01b3e7d8c949ff44b2a069140aafb895d813cbbf42566ab2`.
- Readable backups preserve 345 protected Moondrop files and 62 Pixel files.
  Before/after inventories include the match archives: Pixel is identical across
  all 83 files; Moondrop is identical across 395 of 396. Its only changed file is
  Firestore's cache database, whose changes are confined to query/listener/sync
  metadata in `targets` and `target_globals`. Document contents and every other
  cache table are unchanged. Room cards, teams, calibration, replay and match
  archives are unchanged on both phones. No phone instrumentation was run.
- Original ZIP hashes were rechecked after the review. No historical Article,
  missing attack, final faint, move configuration or calibration correction was
  written back to the recordings or phones.

The updated build has not yet recorded a real battle. Its new coverage and
fusion behavior are verified by source-based regressions; final-hit/faint
recognition, centered-control calibration and Frustration reference eligibility
remain separate acceptance work.
