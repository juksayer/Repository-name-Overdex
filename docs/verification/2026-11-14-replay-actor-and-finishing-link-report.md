# Trainer report: Replay actor ownership and finishing-hit association

Status: reproduced in the paired November 13 Pixel archive; two bounded Replay corrections implemented and focused tests passed. Finishing-move attribution and the rapid neutral impact sequence remain unresolved. Report received November 14, 2026, after the background-mask work. The trainer reports new match(es) on Pixel and possibly Moondrop. The Camerupt mirror example was played on yesterday's build; do not attribute it to the newly installed background-mask build without archive/build provenance.

## Reported regression cases

1. Replay shows an already-fainted Turtonator throwing Camerupt's Incinerate. Investigate the move occurrence's original actor, side, appearance and source time, then compare the actor selected for Replay. Do not assume this is an audio-classifier failure or simply suppress every late effect: an earlier supported move can have a pending impact, but cannot inherit a replacement's move.
2. Turtonator appears to confuse the engine with Ember or a similar identity while, according to the trainer, everyone in this test uses Incinerate. Preserve that as match-specific trainer context. Check whether the conflicting name originates in raw audio candidates, accepted move identity, saved-card configuration or Replay fallback. Do not treat the reported team configuration as proof of another move use, or overwrite the raw alternative candidate.
3. In Camerupt versus Camerupt, the final hit is visible but not associated with the faint. The trainer reports that faint recognition instead came from Poké Balls and possibly a cry. A charged move is shown being used, but does not visibly land in Replay. The trainer requests associating the victorious Camerupt's existing last move with the immediately following opposing faint rather than inventing a new hit or damage tick.

## Narrow review and acceptance criteria

Identify the exact archive/build and source-time intervals before changing recognition. Trace each existing move through recorded use, actor/recipient ownership, impact/presentation and faint association. A move-use record, a faint record and an association between them are distinct claims.

- Camerupt's supported Incinerate remains assigned to the correct Camerupt appearance and side. Turtonator cannot acquire that occurrence because recognition or rendering finished late.
- Inspect the Ember/Incinerate conflict at the layer where it first appears, retaining the original candidates and uncertainty.
- An existing, uniquely supported move directed at the same opposing appearance can be associated with an independently established faint without requiring a recorded empty outline or inventing a damage amount. Preserve the move and faint evidence references separately.
- Source-time proximity is supporting evidence, not a universal last-move-wins rule. Check competing pending Fast Moves, shields, switches and mirror-side ambiguity. A cry alone does not identify the attacker or distinguish the two Camerupt.
- Where the association is supported, Replay should carry the existing move to the correct recipient and connect it to that recipient's faint. No duplicate attack, guessed damage, rewritten archive timestamp or reassignment to a previous actor.

The original report above supplied the review targets. The investigation and changes below preserve the source archives. See MOVE-01/02, AUDIO timing/appearance rules, HP-11 and the proposed finishing-association entry in the [battle rulebook](../reference/battle-rulebook.md).


## Reproduction and corrections

The trainer clarified that **Pixel 4 Replay** showed the Moondrop opponent's
Turtonator throwing three or four rapid attacks. The matching archive is Pixel
`f9fa49c6-033e-4932-ae5e-38167d662159`, paired with Moondrop
`47519620-3434-4420-a4b7-51d89932c303`. The newer November 14 Pixel archive is a
Mewtwo Rocket battle and is not this reported case. Both paired archives were
recorded before the November 14 background-mask installation.

All times below are relative to each archive's own Match Start, not shared clocks.
The separate phone observations were not merged into either archive.

| Evidence / behavior | Pixel | Moondrop |
|---|---|---|
| Mirror entry | Go, Camerupt at +40.662 was incorrectly attributed to PLAYER, already Camerupt | An early Go, Camerupt was incorrectly attributed to OPPONENT, already Camerupt |
| Independent badge transition | Opponent Turtonator +31.264 → Camerupt +44.885; player remains Camerupt | Subsequent repeated entry text at +41.329 is within eight seconds of player Camerupt badge +49.115 |
| Before correction | At +43, opponent was still Turtonator in the production Replay model | The early entry was unresolved by historical species-to-side lookup |
| After correction | At +43, opponent is conscious Camerupt and the supported opponent action remains present | Repeated entry text can use the uniquely changing player side |
| Rock Tomb ownership | Mirror announcement at +57.418, with damaged OCR spelling; no independent preparation cue in this episode | Get Ready +49.866 and clean Rock Tomb text +57.925 |
| Charge correction | Remains unresolved; no side is forced | Player Rock Tomb is recovered, retaining both announcement and preparation references |

`archivedEntryTransition` uses the first subsequent independent badge on each
side, within the existing eight-second lookup window. Exactly one side must change
to the announced species. It rejects missing other-side evidence, intervening old
badges, simultaneous changes, contradictory same-time reads and observation resets.
Both archive order and reversed custody order pass the recorded Pixel regression.
Original serialized articles remain byte-for-byte equivalent after model use.

The charge correction uses the existing ten-second preparation window and retains
independent cue article IDs. It rejects conflicting player/opponent preparation,
changed appearances, relevant active faints, ordinary uses, and intervening separate
charge episodes. A delayed count faint for outgoing Turtonator during Camerupt's
QTE does not invalidate Camerupt's ongoing preparation. It does not create an
energy-spend record or damage/faint testimony.

## Remaining gaps, explicitly not fixed by these changes

- Pixel has **unnamed neutral** impact animations at approximately +43.260,
  +44.317, +45.987, +48.344 and +49.659. These use 450 ms decorative flights because
  opponent move identity/duration was not accepted. They are not accepted Ember
  uses. The archive contains rejected `best=Ember` audio alternatives; all accepted
  `fast_move_identified` records in the paired archives name Incinerate.
- The first recovered HP step is supported by two equal 0.85106385 player-HP
  readings at +43.260/+44.317. The latter frame also supplies a separate canonical
  border-pulse use. This is a concrete possible duplicate/settling-tail path.
  Neither doubling the merge window nor forcing every opponent attack to
  Incinerate is justified by this evidence alone. No counts were deleted or timing
  thresholds loosened in this slice.
- Pixel's Rock Tomb has unresolved mirror ownership. Pixel records player
  Camerupt's faint only later (+73.596), after Gourgeist's entry (+64.670). The
  paired video supports the real knockout, but importing the other phone's
  preparation cue silently would fabricate a local evidence chain.
- Moondrop now has a named, sided Rock Tomb attack, but this does not establish a
  finishing association or retime its late opponent-faint record (+81.939). That
  association remains separate work. This slice does not claim final-hit tracking
  complete.

## Reproducible checks and artifacts

- `RecordedMirrorReplayTest`: full recorded Pixel entry regression, reversed
  ordering, archive immutability, and differing Rock Tomb evidence on the two phones.
- `ReplayEntryTransitionTest`: five focused cases for independent bracketing and
  ambiguous/conflicting input rejection.
- `ReplayChargedMoveOcrRepairTest`: five cases including mirrored phase ownership,
  conflicts, switches, expired preparation, and late unrelated versus active faints.
- Final focused run: **12 passed**, zero failures. The initial recorded Pixel test
  failed with Turtonator instead of Camerupt before the correction.
- Optional `RecordedMirrorReplayReviewTest` exports actual actor/action paths at
  100 ms intervals; it passed with both external archives. This component harness
  uses canonical species and move references, but does not run Android's archived
  crop OCR or prove rendered-device behavior.

External artifacts are under
`/mnt/overdex-backup/archives/overdex-device-backups/2026-11-14-replay-finishing-association/`:
original/exported timelines, before-failure and after logs, per-phone Replay dumps,
and `pixel-first-camerupt-hits.jpg` (video frames 83–88.75 seconds). Bundled gzip
fixtures retain the full timeline; `provenance.json` records source archive hashes.
The original source media and archives remain unchanged. No commit was made.

Fixture integrity was also checked directly against the preserved ZIP entries:
all **1,839 Pixel** and **3,143 Moondrop** articles match their original JSON
objects, including source times and references. Compressed regression resources
contain neither substituted conclusions nor injected trainer labels.

## Full verification

- Full suite: **2,997 tests, 2,991 passed, six optional diagnostics skipped,
  zero failures/errors**. The external paired-archive diagnostic was exercised
  separately in the focused run.
- Debug build passed in the same verification run; `git diff --check` passed.
- APK SHA-256: `d5282cded9529196337a03956c903617a6589fa617463d82cfafd5e67e321724`.
- This verifies component reconstruction and build correctness; a fresh phone
  Replay review remains the visible-behavior acceptance check. Live witnesses and
  whole-bar/tiny-surviving-HP requirements were not changed by this slice.

## Device installation

Installed and launched on Pixel 4 (`99261FFAZ0017J`) and Moondrop
(`MDPH00124112500414`). Neither had active capture/services at the pre-install
check. Previous APKs were backed up under the external artifact directory;
replacement preserved app data. Both pulled installed APKs exactly match the
verified build hash above. Both launches returned `Status: ok` and a live process.
`install-results.json` and per-device logs preserve these checks. No new live
battle or on-device Replay rendering acceptance is claimed.
