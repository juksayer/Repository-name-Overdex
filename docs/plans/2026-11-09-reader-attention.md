# Event-driven battle reader assignments

User policy recorded November 9, 2026. This separates the current scheduling
implementation from follow-up detectors and interface work. Battlefield arrival
is an attention cue; **GO remains the primary Match Start observation**. Preserve
independent observations and their original source clocks.

## Current implementation slice

These changes require the verification report and a fresh live battle before
claiming faster recognition or fewer missed events.

| Reader | Assignment |
| --- | --- |
| Choose Your Team | Immediate first coherent snapshot, then once every four seconds while team selection is open; stop on battle entry. |
| Queued pre-battle readers | Recheck assignment at shared recognition admission and before publication; retained crops remain evidence even when their obsolete reading is retired. |
| Type icons | Up to three capture attempts per appearance/event cue; accepted type testimony completes the assignment. No periodic rereading after resolution. |
| Species | Stop periodic recovery after identity resolves. Reopen after appearance/faint/switch and charged-move cues. Unknown identity retains bounded recovery. |
| Shields | Initial assignment, then charge/QTE, switch or faint; accepted count testimony completes the assignment. |
| Poké Balls | Initial assignment, then affected-side switch, faint or zero HP. Red HP alone does not request repeated count reads. |
| Inactive cards | Initial assignment, then player switch or faint. |
| Decision timer | Bounded attention after faint or charged-move cues. Numeric confirmation narrows the deadline; disappearance completes only the current observed timer. |
| Switch dial crops | Short assignment after a new player appearance/switch. Same-identity metadata changes do not reopen it. Captured shade is neutral evidence, not proof of a voluntary switch or a timer value. |
| Stat-change sound | Heavy matching only in bounded charged/QTE windows; retain the existing volatile audio history for source-timed recovery. |

Resource and bench assignments use fixed three-second source-time windows rather
than continuing until an answer eventually arrives. Source-old/expired cues must
not consume a newer assignment. Capturing a resource PNG alone does not complete
an assignment; accepted testimony does. Queued native reads must remain fresh
when they gain admission to the shared recognizer.

The user approved a **bounded departure check** to avoid the circular dependency
where a missed zero-HP frame prevents ball counting until a replacement appears.
A fresh, previously tracked nonzero HP bar disappearing while the other bar is
still located can wake only the affected ball-count reader. Require repeated
missing observations, readable prior geometry, normal camera context and a
bounded window/cooldown. Reuse the locator's existing results. This scheduling
hint must not publish a faint or a recall.

The departure hint abstains across charged-camera transitions until both normal
bars return. An immediate post-charge fast-move KO can therefore remain missed
when neither its zero-HP frame nor a fresh normal low bar is captured. The
separate actual departure/recall witness remains necessary for that case.
Likewise, a failed charge-triggered species recheck of a previously resolved
identity waits for another cue; it does not resume permanent polling. These are
explicit coverage boundaries, not resolved recognition failures.

## Follow-up policy: transitions, results and player input

These requirements are recorded but not implemented by the scheduling slice:

- A white game transition should cut off VS work. Positive battlefield scenery
  or elements should open battlefield readers before GO. Existing battle-surface
  confirmation is a session latch; it does not prove current frame visibility.
  Add an independent source-timed transition/surface witness before relying on
  it for a hard cutoff or end classification.
- Enable result-music matching only after one side has one Pokémon remaining and
  an actual recall of that final Pokémon is observed. Faint/count loss, bar
  disappearance, camera motion and stopping Droidball are not recall witnesses.
  Result music remains on its current gate until that missing witness exists.
- Opponent concession candidate: normal play is interrupted by a black **game**
  surface while both sides retain Pokémon; subsequent `YOU WIN` confirms the
  result. Keep result separate from ending reason and cite independent evidence.
  Black overlays, unrelated screens and missing frames are not sufficient.
- Player concession candidate: observe the player's actual game exit while the
  opponent retains Pokémon. Ending capture or leaving Overdex alone does not
  establish the player's concession.
- Ask the player about unresolved endings. During live battle, permit only a
  yes/no question during an actual shield decision with **more than three
  source-adjusted seconds left**. Hide or expire the prompt when that condition
  ends. A delayed crop, stale dial or replay countdown cannot authorize a prompt.
- Match records stay read-only. Store player answers and reports of incorrect
  readings as separate attributed notes, retaining disagreements with the
  recording. Separate note persistence and UI still need implementation.
- Charged/QTE downtime can service appropriate queued work, provided pre-battle
  work already retired is not revived and source freshness remains explicit.

No overall CPU saving or live Fast Move recognition improvement is established
by scheduling tests alone. Measure native read/match admissions, source-to-ledger
delay and missed/extra events on the next recordings from each phone.
