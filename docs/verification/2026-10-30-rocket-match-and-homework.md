# Rocket match and completed audio homework — October 30, 2026

## Preserved evidence

The Moondrop's 10:59 recording (`9425d5d1-ced0-4c77-be94-b51cd4265956`)
was copied without changing the phone copy. Device and desktop SHA-256 hashes
match; all 279 declared media artifacts verify, and the manifest agrees with
3,134 Timeline Articles. The 10:37 recording contains only a short session that
stopped when the phone locked, with no battle.

The user confirmed continuous attacks during a Team GO Rocket battle. Times
below are measured from recording/session start because there is no recorded
MatchStarted, VS, or countdown glyph. They must not be presented as exact time
since GO.

Private sources, answer export, validated answer assessment, contact sheet, and
analysis are under:
`/home/sean/overdex-device-backups/2026-10-30-new-match-and-homework/`.
No private WAVs, crops, answer text, or source archives are committed here.

## Audio homework

Exported the existing browser answers without modifying them. The production
answer validator accepts all six source-bound entries against review set
`5ebbf8e737c27daea997`, including the recorded reference hashes, intervals, and
listening-guide revisions. Five entries are marked reviewed; the first retains
its earlier Ember selection and notes as a draft. The user said the homework
was finished; no status or answer was filled in on their behalf.

The Magcargo example remains unsure: the user favored Ember but could not
separate the combatants. The Snarl/Ember example is explicitly mixed, and the
Wing Attack control was heard as player-only. These are useful human listening
judgments, not new confirmed game events or automatically trained references.
The controlled Mawile example still lacks a named Fire Fang library reference;
its resemblance to other sounds cannot override legal moves or known test setup.

The user also reports a mode-specific opponent pattern for Team Leader and
Rocket battles: attacks begin immediately at match opening, while a switch,
faint, or charged move is followed by roughly 1–2 seconds of hesitation after
normal play resumes. This is reported contextual guidance, not a measured fixed
duration or a PvP rule; contrary observed attacks must remain recorded.

## New scoped matcher behavior

All 34 saved Fast Move sound measurements contain only moves in their recorded
active search scopes. No impossible move is returned. The first measurements
have only Cradily's three legal alternatives because the player has not yet
been identified; their accepted flag correctly remains false even when a
candidate would meet the acoustic thresholds alone. Only two later Water
Shuriken comparisons are accepted. A high-scoring singleton is still not
forced into a strict match.

A private JVM rerun of six preserved WAVs reproduces the archived candidate
scores to eight decimal places. An offline comparison adding the possible
player's Wing Attack exposes a near tie with Infestation in one Cradily-only
window. This supports retaining mixed-side ambiguity; it does not establish
that an opponent attack occurred. This review does not measure recall or prove
that all Fast Moves were counted.

## Correction: the opening identity was already known

The user correctly pointed out that the accepted HUD menu showed Golbat before
battle. The initial review's nickname explanation was incomplete: observed Team
Select already established slot one, and Greninja was slot two. The missing GO
handoff prevented that known opening identity from reaching attack/energy/audio
tracking. See [the continuity correction](2026-10-30-observed-team-lead-continuity.md).

## Incorrect historical cadence attribution

The initial player badge crops contain a custom nickname rather than a species
name. No active Golbat identity is recorded despite Golbat being the configured
lead. At +176.657 seconds, Greninja becomes the first resolved player identity,
with an explicit appearance boundary and configured Water Shuriken.

Queued border-cadence measurements from +154.046 and +155.082 seconds are then
replayed against that later Greninja. They produce Feint Attack after the
configured Water Shuriken Article in delivery order. The configuration is not
missing: the raw cadence path failed to consult the state's explicit appearance
boundary before applying earlier evidence to that combatant.

The fix checks the whole measured interval against the current appearance
boundary before accepting delayed/pending cadence. It preserves raw Articles
and permits earlier source-backed entry recovery where an independent earlier
appearance boundary exists. It does not force the configured move to defeat
valid later contradictions, identify a nickname from roster membership alone,
or rewrite the original archive.

## Remaining observed issues

- The nickname also prevents resolving the readable Poison Fang announcement
  at +135.283 into a named/side-attributed charged move; only the generic charge
  cue is recorded. No QTE phase is present.
- The decisive YOU WIN result was captured at +211.287 but delivered about
  68 seconds later, after Stop. A border-only Cradily use at +247.567 therefore
  appears after the retrospectively established result boundary. This is a
  result-attention/delivery and downstream boundary issue, not proof that the
  opponent kept attacking after the win. Recorded diagnostics separate 67.429
  seconds waiting for OCR admission from 0.619 seconds in native OCR. The
  outcome reader's newest/oldest queue alternation skipped the brief winning
  crop and later recovered it from an 83-item backlog. Outcome queue policy,
  rather than a higher OCR priority, is the identified next correction.
- The archive records 36 player and 28 opponent Fast Move uses, with 30 player
  uses lacking a species. These are incomplete inference outputs, not actual
  attack totals. This match cannot validate complete energy accounting.

## Validation

- 115 focused Android tests passed, including four new explicit-appearance
  regressions, early-entry audio recovery, the recorded Magcargo comparison,
  22 candidate-context checks, and live-witness preservation/invalidation.
- Full `:app:testDebugUnitTest` passed: **1,490 tests, zero failures/errors/skips**
  across 229 test classes. `:app:assembleDebug` passed.
- A stale test that expected an impossible global winner to block all legal
  preferences now explicitly verifies that repeated legal comparisons can favor
  a legal move while never assigning the impossible one.
- Accepted-occurrence retention no longer treats a weak runner-up's shifting
  alignment as a reason to repeatedly preserve the same accepted sound.
- The debug update was installed with data preserved on idle Moondrop
  `MDPH00124112500414`; its installed package SHA-256 matches the built APK:
  `a79fd341e6c9e0826791329647d640298bc2e40929116dd8b165f938294fd8a7`.
  No Droidball session was active before replacement.
- The original match and answer exports remain unchanged. A fresh live battle
  is still required to validate the corrected timing on the phone. The opening
  identity handoff is addressed in the subsequent continuity correction linked
  above, with live acceptance pending. The result-queue correction remains open.
