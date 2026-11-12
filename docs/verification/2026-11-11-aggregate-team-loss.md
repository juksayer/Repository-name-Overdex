# Aggregate team-loss attribution

## Problem and behavior

The second match in `Screen_recording_20261111_190345.mp4` has confirmed player
Poké Ball counts three and one, with no count-two observation. Its original
archive has 3,492 Articles and no named faint Articles. The previous resolver
could assign only decreases of one, leaving both supported historical losses
unassigned. The [recording review](2026-11-11-moondrop-1903-connection-review.md)
preserves the original findings and source provenance.

The resolver now treats each decrease as an unordered set of losses of the
observed cardinality. Distinct species cannot occupy two loss slots. Incomplete
identity history retains unknown slots; contradictions remain constraints.
Only named owners shared by every consistent assignment are resolved, including
partial certainty. `resolvedCandidates` carries those owners; singular
`resolved` keeps its single-decrease meaning.

Both live history and completed Replay use this result. Each named publication
retains the captured count's clock and evidence. Replay may place a visible
departure before its supported replacement, but does not alter the archived
Articles, fabricate a count-two reading, or move the count-one observation back
to an earlier faint. Later captured counts can refine the grouping without
duplicating final projected losses.

Historical aggregate losses cannot create a new entry-side vacancy, corroborate
all losses with one nearby cry, stop a living replacement's Battle Record move
metrics, or erase its latest supported hit. Live faint exclusions and opponent
HUD cells rebuild from independent history as evidence changes.

## Recorded acceptance

Fixture: `app/src/test/resources/replay/moondrop-aggregate-team-loss-20261111.json.gz`.
The adjacent provenance file records the original archive and timeline hashes.
The decompressed fixture is the complete original timeline, with SHA-256
`ea9490baf42f33737ca2418b75910143522fc4db82ef0bcd4e1e79d51944f7ce`.
No identities or faints were inserted into this fixture.

The required projection is:

- Player Turtonator and Gourgeist are the two resolved losses.
- The captured centered zero remains attached to Gourgeist.
- Camerupt's final faint remains unresolved; LOSS is not a substitute for its missing observation.
- Stunfisk and Pelipper remain conscious, and their supported attacks survive.
- Both losses cite count-one Article `cd67d8f0-e95c-4185-8d76-fd46c23519a2`
  at its original capture clock. The raw count sequence remains 3 → 1.

Tests cover the full recorded publication order, reverse/shuffled delivery,
repeated sources, partial ambiguity, inconsistent histories, ordinary switches,
both teams, delayed intermediate counts, HUD state, retained move metrics, and
unchanged archive serialization.

## Validation

- Focused attribution, Replay, live/HUD, entry-side, and Battle Record suite:
  124 tests passed.
- Full `:app:testDebugUnitTest :app:assembleDebug`: 2,658 tests across 354
  suites, zero failures/errors/skips, and a successful debug APK build.
- `git diff --check` and documentation link checks passed.
- Moondrop `MDPH00124112500414` was inactive before update. A readable
  453,884,416-byte records/settings backup contains 453 files, including 51
  archive files. The larger raw-artifact store was not copied or changed.
- Installed with data-preserving replacement at device time
  `2026-11-11 19:40:57`. Installed APK and built APK have identical SHA-256:
  `3f1e89b16bd443d65fd7077f1efeee2c8b269d65464d592857ca1678dec6c0bd`.
- Post-install comparison found 452 protected files byte-identical, including
  the application database, archives, calibration, and preferences. Only the
  Firestore cache database changed; both versions pass SQLite quick-check and
  have identical table row counts. Details are in
  `aggregate-update-verification.json` beside the backup.
- Android accepted the activity launch; the phone remained locked. No visual
  app workflow or on-device Replay acceptance is claimed.

This verifies recorded projections and installation. It does not establish
fresh live-match acceptance or a full on-device Replay run.

## Separate remaining capture issue

Read-only final-charge diagnosis is preserved at
`/home/sean/overdex-device-backups/2026-11-11-moondrop-1903-review/charged-final-cue-diagnosis.md`
with selected original Articles in the adjacent evidence JSON.

In the first match, incoming OCR finished around Match +146.443 s, but downstream
publication delayed the actionable cue to about +149.172 s. The retained charge
crop already contains only a clipped, moving outline; rejecting it as an exact
zero measurement was correct. The second match has no fresh final incoming or
shield fallback cue; its preceding charge window had expired before Mud Bomb.
The compact archive does not establish why that incoming cue was missed.

The next bounded investigation is an earlier accepted-text capture-preparation
handoff, retaining the original crop identity/clock and existing freshness and
appearance guards. This change does not widen capture windows or relax empty-bar
geometry. These final charged-hit gaps remain open after aggregate attribution.

The consumer audit also noted conservative boundaries in charged-announcement
attribution and player nickname recovery that can discard context on any faint
on that side. They were not widened in this change; preserve their protections
while assessing owner-aware handling separately.
