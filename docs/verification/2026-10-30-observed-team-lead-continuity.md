# Observed Team Select lead continuity — October 30, 2026

## Correction to the Rocket review

The accepted menu already knew Golbat was first. The nickname was not a reason
to discard that identity or to make slot-two Greninja the first player actor.
The defect was a missing handoff from observed Team Select into active tracking
when no GO/countdown MatchStarted was recognized.

In recording `9425d5d1-ced0-4c77-be94-b51cd4265956`, accepted Team Select is
source +72.605 seconds; the witnessed party at +73.687 is Golbat, Greninja,
Victreebel in slots 1–3. Fomantis is identified on the opponent side at +86.599.
Persistent player HP loss is measured at +88.589, before the first recorded
player-caused opponent damage at +89.438. These are seconds from recording
start, not an observed GO clock. No MatchStarted exists in the source archive.

Previously Match published TEAM_SELECT_LEAD_INFERENCE only when MatchStarted
was derived. The menu retained the party correctly, while use, energy and audio
tracking had no active player until the later Greninja badge at +176.657.

## Changed behavior

The normal GO handoff remains. If GO was missed, valid persisted HP loss can now
carry the witnessed first slot into the active identity pipeline before that
loss is processed for use/cadence. This requires an accepted Team Select surface,
a same-attempt observed lead preceding the damage, and an opponent identity at
or before the source time. Saved configurations, full HP bars, cries and VS alone
do not create a battle appearance.

The derived Golbat appearance cites the accepted party, observed slot one,
opponent identity and actual damage. It retains the damage's source time,
explicit slot one, and appearance boundary; it does not fabricate MatchStarted.
The existing identity projection then supplies Golbat and configured Wing Attack
to use, cadence, energy and audio consumers. Nickname OCR remains independent raw
testimony and does not force rediscovery of the already selected species.

A later Greninja observation opens a separate slot-two interval with Water
Shuriken; it cannot acquire the lead's earlier attack history. An existing player
identity always takes precedence over a delayed opening cue. Source-time guards
also prevent a faint, missing Poké Ball, replacement check, rejected/rescanned
party, restart, ignored/stopped session or result from resurrecting slot one.
Newer conflicting slot-one source evidence also blocks an older reading delivered
late within the same party scan. Session/scan closure guards apply even when the
delayed combat cue was captured before that closure.
There is no arbitrary time limit on how long a trainer may wait at Team Select.

## Validation

Eleven Match-level integration tests cover observed Golbat without GO, source and
predecessor preservation, downstream configured moves, Greninja as replacement,
configuration-only/no-party negatives, non-combat/invalid damage/no-opponent
negatives, faint/resource loss, switches followed by late GO, restart/stale
selection, deduplication, compatibility with the existing GO path, same-party
reordering in both delivery orders, and delayed GO/damage after Stop, ignored
screen or Match End.

- Focused lead/roster/player-identity/cadence checks passed: 102 tests, zero
  failures/errors/skips.
- Full `:app:testDebugUnitTest` passed: 1,501 tests, zero failures/errors/skips.
  `:app:assembleDebug` passed.
- Read-only code review identified the delayed roster-order and stopped-session
  cases above; the guards and regression tests were added before the final full
  run.
- Installed with data preserved on idle Moondrop `MDPH00124112500414`
  (MD-PH-001). No Droidball service was active before installation. The installed
  APK SHA-256 equals the built APK:
  `c70b8e4899f7d5e2e886eaa60880f89c9838bf32e406b3fca2bace6089ad2e52`.
- Fresh live battle acceptance remains pending. The integration tests reproduce
  the observed evidence sequence; they do not establish live capture latency or
  complete Fast Move counts on the phone.

The original recording and raw articles remain unchanged. Private evidence is
under `/home/sean/overdex-device-backups/2026-10-30-new-match-and-homework/`.
Installation metadata is under
`/home/sean/overdex-device-backups/2026-10-30-observed-lead-continuity/`.
