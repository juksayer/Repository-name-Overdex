# Witness job labels and passive Timeline — October 28, 2026

The user requested job/output names without a redundant `WITNESS` label and
clarified that the Timeline should not reason about records in its custody.

## Responsibility audit

`RealityTimeline` already only appends supplied Articles and returns snapshots.
`TestimonyCustody` preserves supplied payloads, provenance, timing, and sequence.
Interpretation occurs in Match-owned consumers, which append separate derived
Articles. No reasoning engine was moved into either ledger.

The legacy Compose `model/BattleTimeline` generated the `BATTLE_TIMELINE` Logcat
lines. Its formatting labeled every Article `[OBSERVATION]`, including derived
conclusions. That formatting now belongs to `BattleRecordDiagnostics`, outside
the ledger, and uses the neutral `[ARTICLE]` category.

## Naming and archive compatibility

Observer display names, startup messages, literal log tags, and Timeline list
headings use job names without `Witness`. Source display removes only the
standalone underscore-delimited `WITNESS` token, including inside
`ANNOUNCEMENT_WITNESS_OCR`. Signal display omits the redundant `Witnessed` suffix
or `Witness` prefix. Example output:

```text
[ARTICLE] ATTACK_INCOMING signal=AttackIncoming
[ARTICLE] ANNOUNCEMENT_OCR signal=RawTestimony
[ARTICLE] BATTLE_INTERPRETER signal=MatchStarted
```

Stable source IDs, observer IDs, payload classes, and archive serialization are
unchanged. Archive details retain the exact SOURCE ID for provenance inspection.
Renaming a displayed job never changes routing or rewrites an old recording.

## Remove repeated recognition from incoming warnings

`PersistedAttackIncomingWitness` already recognizes and groups incoming-charge
warnings. It now emits typed `AttackIncoming` with the original announcement's
wall/monotonic times and evidence reference instead of making another consumer
recognize the same text again. The raw announcement remains preserved separately.
The typed warning follows the existing Match accounting path. The legacy raw
adapter remains for older producers, and cannot duplicate a typed warning.

## Validation

- Focused warning/publication/interpreter/Fast Move checks: 56 tests passed.
- New integration checks preserve raw text and provenance, deduplicate one
  warning per episode, and exercise Fast Move pause/resume at charge boundaries.
- Diagnostic checks cover job/signal labels, unchanged source identity and
  evidence, neutral derived-record labeling, and uninterpreted raw text.
- Production diff inspection verifies unchanged SourceId/ObserverId constructor
  expressions and witness ID assignments. Independent review found no blocker.
- Full suite: **1,249 tests across 208 suites**, zero failures/errors/skips.
  Debug APK build and `git diff --check` passed.
- Verification log: `/home/sean/overdex-device-backups/2026-10-28-witness-jobs/verification.log`.

Phone installation and live Logcat verification have not been performed for this
change. Existing active sessions and the user's Moondrop use were not interrupted.
