# Rock Tomb and the following Camerupt faint

November 14, 2026. Scope: a completed-archive Replay association between an
existing charged use and an independently resolved count-based faint.

## Reproduction

The preserved paired November 13 recordings show the same controlled match.
Moondrop `47519620-3434-4420-a4b7-51d89932c303` has Get Ready at +49.866 s,
Rock Tomb at +57.925 s, opponent Gourgeist entry at +64.408 s and a later
opponent Poké Ball count decrease at +81.939 s. The existing team-loss resolver
uniquely attributes that decrease to the outgoing opponent Camerupt.

Before this change, the existing Rock Tomb projectile completed at +59.725 s,
but the faint pose waited until +63.758 s, fitted immediately before the
replacement. The attack and confirmed faint were separate presentation events.
No extra Fast Move occurs between them in the recording. Visual review of the
Moondrop video at 95–105 seconds confirms Rock Tomb, the receiving Camerupt's
collapse/clear, and Gourgeist's later entry. The corresponding Pixel frames show
the incoming shield-choice phase, charge and replacement prompt; this visual
review is not silently inserted into Pixel's archived testimony.

## Change

`ReplayFinishingChargeAssociator` creates a separate association only when:

- The charge is completed, named, sided and targets the same resolved appearance.
- A single count-based loss is independently resolved, with a known replacement
  within eight seconds of the charge's source time. Aggregate losses and final
  result-only endings are excluded.
- Native receiving-side shield counts bracket the exchange and stay unchanged.
- No competing scheduled or source-observed Fast Move, other charge, changed
  actor, reset, stopped observation, result or surviving receiving HP contradicts
  the association. A positive HP sliver is not rounded to zero.

The existing projectile impact now starts the supported Camerupt faint and its
inferred depleted Replay HP endpoint. The charge marker says `Rock Tomb · KO`;
the KO marker seeks to that presentation impact. The association retains both
source clocks, attacker/recipient appearance, separate move/faint/shield evidence
IDs and an explicit reconstruction basis. The late recorded faint clock remains
+81.939 s. Badge measurement times and raw archives are unchanged.

This association does not feed back into attack scheduling, charge inference or
live witnesses. It creates no additional move, measured HP loss, energy spend,
ball count or faint. Source timing and the decorative impact estimate remain
separate. Eight seconds is a conservative candidate bound for this path, not a
claim about universal damage-resolution timing.

## Boundaries

Pixel `f9fa49c6-033e-4932-ae5e-38167d662159` still lacks an accepted independent
charge-side cue for this mirror episode. Its Rock Tomb ownership stays unresolved;
Moondrop's observations are not borrowed. The final losing Gourgeist faint also
remains unrecorded and is not supplied by the match result.

## Verification

- Focused regression suite: **51 passed, zero failures/skips**. The recorded case
  passes with original and reversed custody ordering. Tests cover pending and
  unscheduled competing Fast Moves, tiny surviving HP, shields, missing shield
  confirmation, switches, aggregate loss, wrong appearance, unknown performer,
  competing charge, reset, stop and result boundaries.
- Paired Fast Move marker/use counts remain **Pixel 10, Moondrop 15**.
- All **1,839 Pixel and 3,143 Moondrop original Articles** match the preserved ZIP
  timelines. Source ZIP and fixture hashes match their provenance manifest.
  Model construction, seeking and reverse seeking leave serialized inputs intact.
- Full suite: **3,022 passed, six optional diagnostics skipped, zero failures or errors**.
  Debug and Android test APK builds passed in the same final run (2m 59s).
- Physical Pixel 4 renderer checks: **OK (2 tests)**. The recorded Rock Tomb
  exchange and existing final-charge impact/hold/fall checks passed. Captures
  confirm projectile contact, Camerupt falling, Gourgeist entering alive, and
  deterministic reverse seeking.
- Pixel's complete private-data backup is readable (208,844 entries, 50 Match
  archives). Post-test database integrity, all table counts and protected
  team/calibration preferences match the pre-test baseline.
- Installed and launched the tested APK on **Pixel 4 and Moondrop** using an
  in-place update. Both pulled installed APKs match SHA-256
  `cf274be48c24de87ddaa4f837169d53b239f7cc033b8681c81a069b2a131611d`.
  Moondrop had no active projection or Droidball service before installation;
  its database integrity/counts and protected preferences also remain unchanged.
  Moondrop launch is installation proof; renderer tests ran on Pixel, using the
  immutable Moondrop fixture in test cache, not importing it into Match history.

External recordings remain in their original locations. Read-only extracts,
archive integrity checks, test logs, device backups and display captures are under:

`/mnt/overdex-backup/archives/overdex-device-backups/2026-11-14-rock-tomb-faint/`.
