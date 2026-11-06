# Unsaved Rocket team, Charm and delayed replacement — November 6, 2026

## User ground truth and preserved source

The user reported a Team GO Rocket battle with an unsaved team, no imports or
new cards, continuous Gardevoir attacks, and no charged attacks from either side.
They confirmed Gardevoir's Fast Move was **Charm**. Audio stutter occurred during
the second or third opponent; its exact start and end remain unlabelled.

The Moondrop archive is
`2026-11-06_07-35-42_079cebdb-d3e7-450a-9d23-7e8fa6b4f7f0.odxmatch.zip`.
The desktop and device copies share SHA-256
`200033e7bb845df3cfb2fae40078194063dcdb57f1265a7588a09c1eb609c9e2`.
It contains 1,189 Articles and 134 declared artifacts, all verified. The original
ZIP and Timeline remain unchanged. Private evidence and review outputs are under
`/home/sean/overdex-device-backups/2026-11-06-rocket-gardevoir/`.

Times below are relative to the **inferred** MatchStarted Article. GO was not
witnessed; countdown `1` and combat supported that boundary. These times must not
be presented as independent proof of exact GO timing.

## Successful boundaries

- Team Select identified Gardevoir, Zapdos (Galarian) and Rhyperior. Generic OCR
  also supplied “Zapdos”; the separately confirmed visual roster retained the form.
- The archive contains **zero player_team_slot_configured Articles**. Player
  information checks have no configured values, and there is no owned-card
  snapshot. No previous saved moves or arbitrary Gardevoir card identity were
  inherited. The battle is species-level evidence rather than binder-card history.
- Repeated legal-side audio independently identified Charm at source time
  **+9.945 s**, published at **+12.077 s**, confidence 0.90. Subsequent observed-use
  cadence (median **1.502 s**) supported Charm at source time **+11.462 s**,
  published at **+16.605 s**, confidence 0.99. Effectiveness evidence later agreed.
- Eight of twelve audio measurements were accepted; all eight selected Charm.
  Candidate lists remained within the archived active-species move scopes.
  Low-confidence opponent choices remained hypotheses and awarded no exact energy.
- There are **no charged-move uses, announcements, QTEs, Attack Incoming or
  shield-choice events**. Empty charged-button OCR reads, resource-count reads and
  74 motion measurements were not promoted to attacks or QTEs.

## Fixed: an unknown faint-pause end permanently rejected valid observed uses

Houndour's faint has source time **+15.405 s**, but arrived at **+23.516 s**.
Liepard's entry has source time **+16.388 s**, but arrived at **+25.518 s**.
Already-observed player hits at **+20.352 s** and **+22.354 s** reached the energy
consumer between those deliveries. The old Boolean timing result permanently
rejected them because the replacement time was still unknown.

`FastMoveEnergyTiming` now distinguishes a reserved cycle, a permanent timing
rejection, and deferral solely because a faint vacancy has an unknown end.
Deferral awards nothing. A genuine appearance retries existing pending uses on
both sides in source order; a request to scan does not close the vacancy.

The same side/species/faint checks, full reference durations, known charged or
decision pauses, existing reservations and result boundary apply to retries.
Duplicate uses do not receive additional credit. Original Articles, source times,
IDs and evidence predecessors are retained. This change counts no estimated or
unwitnessed attacks and does not rewrite already-published energy.

## Remaining evidence gaps

The original archive has **9 player uses, 15 opponent uses and six Charm energy
awards totaling 48 generated energy**. These are incomplete recorded counts,
not the true continuous-attack total. There are substantial gaps in the observed
player impact stream. The reported audio stutter is relevant context, not proof
of extra completed moves.

The first recorded player hit is at +1.236 s. It does not fit a complete 1.5 s
Charm after the currently inferred start and remains uncredited under that timing
policy. This alone cannot establish whether the original start boundary or hit
was early. The fix does not relax the duration guard to accommodate it.

Carvanha's last large HP loss has only one post-drop sample before replacement,
so the settled-damage detector cannot establish another completed use from it.
There is no change to that detector from this sparse evidence.

A delayed Liepard identity also leaves an earlier opponent use labelled Houndour.
That historical attribution issue is separate from the energy timing fix.

The archive records a win at **+38.009 s**, but the last opponent HP sample is
**+24.801 s**, about 11.64% remaining. No later opponent zero-HP observation,
finishing player hit or formal Liepard faint is preserved. A win is not proof of
an exact missing finishing-hit time. Charge-fill samples are unavailable and do
not provide an independent energy total in this battle.

## Verification

- Five new regression tests exercise the Rocket ordering, original source
  provenance, duplicate protection, either fainted side, scan versus appearance,
  a known closed vacancy, existing published credits, early cycles, bursts,
  charged phases and the result boundary. Independent source review found no
  blocker. The focused run passed **36 tests**, including the private comparison.
- A private JVM harness fed this archive's relevant inputs, in original
  publication order, to both the baseline and updated production energy consumers
  using the current game-master reference. Player identity used the same canonical
  appearance projection as Match. This reproduced the original **48** generated
  energy, while the new consumer produced **56** from seven witnessed Charm uses.
  The two deferred hits at +20.352 s and +22.354 s were recovered; +23.405 s then
  failed the full-cycle constraint. This is an energy-consumer comparison, not a
  rerun of screen/audio recognition or a claim that seven was the actual total.
  The serialized archive stayed unchanged; the private harness and legacy copies
  were removed from test sources after the check.

- Final full JVM suite: **1,788 tests / 259 suites**, zero failures, errors or
  skips. Debug APK assembly passed after the final source changes.
- Moondrop `MDPH00124112500414` had no active Overdex service or MediaProjection
  before replacement. The update was installed with data retained. Host and
  installed APKs share SHA-256
  `cbe3db3646fdb758c44e07f121981a8631e140dae13df6496ca78b04bb12bd35`.
- A readable 344-file backup protects databases, preferences, battle records,
  calibration profiles/reference, registration and replay files. The before/after
  395-file inventory also includes match archives. Every file is byte-identical
  except Firestore's local cache database: its only logical changes are the
  `target_globals.last_remote_snapshot_version_seconds` and `_nanos` metadata.
  All other contents of the sixteen-table cache, Room data, teams/cards, calibration,
  replay and match archives are unchanged. No phone instrumentation was run.

The update has not yet recorded a new real battle. Builds and bounded accounting
regressions do not establish complete live attack recognition. No calibration,
saved loadout, owned card or historical archive was edited.
