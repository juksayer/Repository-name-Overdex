# Interrupted QTEs and finishing hits — November 4, 2026

## Findings

The Moondrop recorded the reported concession as a Win during Camerupt's QTE.
Its last charged move was not recorded as completed, and Malamar has no faint
Article. Result text establishes the Win; the user's account supplies concession
as its cause. The exact moment the opponent conceded was not independently
measured.

Two replay defects were reproduced and repaired:

- An open QTE's synthetic expiration could fall after the result and cause Replay
  to hide even the preparation before that result. Preserve the witnessed
  preparation until the result, without promoting it to a completed attack.
- Cadence smoothing could project an already recorded final Fast Move slightly
  beyond a faint/result boundary, then discard it. A known-duration finishing
  cycle can now fit within the existing timing tolerance using its recorded
  impact time. A hit exactly sharing its recipient's faint timestamp belongs
  to the outgoing interval. This retains an existing use, not another attack.

Completed charged attacks released before the result retain their projectile,
sound, and impact-to-faint alignment. Result-only defeat now uses an upright,
full-color withdrawal/fade rather than the witnessed-faint fall and color drain.
Actual faint presentation remains unchanged.

The user reports that finishing attacks are generally absent. These repairs
cover demonstrated rendering losses, but some recordings themselves lack the
terminal hit. This is not a claim that all finishing attacks are now captured.

## Original concession record

Internal archive copied by USB from Moondrop `MDPH00124112500414`:

`2026-11-04_19-15-05_fdb8b893-fc1a-4363-8601-86d6f2217f7f.odxmatch.zip`

- 14,341,415 bytes; SHA-256
  `d45d2ec7dec5de1b140c48fc9d898bb96fd60ef42b1e311c17df2c223fb4f63b`.
- Local/phone hashes agree; ZIP and all 539 declared artifacts verify.
- 3,419 Articles; GO/MatchStarted is present.
- Two adjacent small archives contain setup only, not additional battles.

All times below are seconds after the archived GO clock, not publication times.

| Evidence | Time | Meaning |
| --- | ---: | --- |
| Camerupt / Malamar HP, same frame | 99.286043 | Measured fill approximately 78% / 65%; both positive before QTE |
| Get Ready | 99.848853 | Player preparation, Article `19b1052b-e30a-4603-8036-faf7f6f982fa` |
| Opponent / player remaining balls | 102.522593 / 102.857563 | One / two, respectively; no final zero |
| YOU WIN / MatchEnded | 107.835720 | Result Article `78f687cb-150c-4f74-a909-b136e28005f2` |

The Win follows `YOU_WIN_WITNESS` and `MATCH_OUTCOME_TEXT_WITNESS`; the retained
outcome crop clearly reads YOU WIN. The later QTE-progression crop is blank sky,
so it supplies no Nice/Great/Excellent grade. No accepted final Camerupt
charged move, Malamar faint, or aggregate QTE vibration pattern is present.
Old positive HP must not be described as continuously measured through the Win.

Private source copies, extracted evidence, backup, regression, and build logs
live under `/home/sean/overdex-device-backups/2026-11-04-concession-match/`.
Source archives on the phone are retained and unchanged.

## Projection rules and regressions

`MatchReplayModel` retains all original timed Articles. A separate action view
uses source times through the first result to prevent later charge evidence
from upgrading an earlier unfinished QTE, spending energy, assigning its actor,
or adding grades/haptics. Late publication of pre-result testimony remains
eligible. Result time ends an open QTE; it does not erase it before that time.

`ReplayFastMoveSchedule` keeps ordinary charge/switch window behavior. Terminal
fallback is limited to an existing known-duration use and the existing jitter
allowance, cannot overlap the previous impact, and cannot move the hit beyond
the terminal boundary. Recipient availability also matters: a stray use after
its recipient's faint must not become an accepted finishing hit. No attack,
energy, damage, or duration is added to the original Timeline.

Regression coverage includes:

- Completed finishing Earth Power with a result before its rendered impact.
- Open QTE before/at/after the result, post-result spend and grade exclusions,
  late publication, source immutability, and reverse scrubbing.
- A recorded 3.9-second impact, cadence projection to 4.0, and faint at 3.95.
- Exact hit/faint ties, insufficient attack windows, ordinary-window negatives,
  post-faint rejection, and concession with no attack to render.
- Neutral result withdrawal versus unchanged witnessed-faint poses on both sides.
- Private regression against the entire original concession archive: Camerupt
  versus Malamar preparation, Win cutoff, retained two/one resources, no final
  strike/sound/spend/faint/grade, and deterministic reverse scrubbing.

## Validation and installation

- Full debug JVM suite: **1,705 tests across 251 suites**, no failures, errors,
  or skips. This includes the private original-archive regression. The debug APK
  built successfully in the same invocation.
- Independent review caught and verified the post-KO recipient guard. Final
  review found no remaining introduced correctness issue in the bounded patch.
- Moondrop replacement install succeeded with Droidball confirmed stopped first.
  Installed APK SHA-256 matches the built APK:
  `741130462778e9a71eb3bc7761b01ccaf5b47d4f6440450aa59d4cd198f4aeb7`.
- A readable 5,899,264-byte backup protects 341 files covering databases,
  preferences, records, calibrations, registration, and replay edits. No paths
  disappeared after installation/launch. The owned database still contains 29
  owned cards and 1,026 species. The match archive inventory is unchanged.
  All backed-up files except the Firestore local cache retain identical hashes.
- The actual ODX-Fi directory launches with its retained battle calibration
  crops. No phone instrumentation, app-data clearing, or uninstall was used.
- Physical navigation: ODX-Fi → BATTLE → Match → Match Summary opens the original
  record as YOU WIN with a 1:47 battle duration. Inspect Timeline / Replay →
  Open Replay verifies the archive's 41 species crops and opens the battlefield.
  Seeking to record time 05:18.2 shows Camerupt in QTE focus against Malamar,
  with two/one members and one/zero shields. At 05:25.2 the Win is visible and
  Malamar remains upright/in color during withdrawal, with no opponent HP bar.
  At 05:27.2 Malamar is gone and the resource counts remain unchanged. Scrubbing
  backward restores exactly the earlier sprite bounds, resources, and paused
  clock with no Win overlay. Screenshots/XML are retained privately. The phone
  is left paused at 05:18.2; Droidball remains stopped.

The regression verifies the replay projection of the real archive. A new live
battle is still required to measure terminal-hit capture; these replay-only
changes cannot restore a source observation that was never recorded.

## Remaining recording work

The earlier `f6c20fa4` recording has a Win but no terminal Aegislash faint/hit;
its last normal opponent HP readings remain around 50%. Its absence is not
proof of a dropped replay projectile.

The `39589a75` recording has no MatchEnded. An earlier Dewpider faint at
157.868 seconds provides a useful terminal interval: measured HP remains
7.4468% through 157.045 seconds, with no zero reading. A raw pulse at
156.933912 seconds (`e049600b-9d44-4b83-8ae5-c99a84d363f1`) was consumed by
the preceding Gengar use at 156.431914 seconds
(`06404241-4f9f-42b3-99e0-d7608887cecb`). Earlier pulses precede their HP
losses by roughly 0.3–0.4 seconds. This supports investigating a missed final
loss; it does not establish another Fast Move without source-frame validation.

The HP measurer requires saturated fill and does not emit a directly measured
empty bar. The settled-damage detector also requires a following settling
sample. A bar disappearing immediately after depletion can therefore lose its
last damage measurement. Next slice: validate this interval against retained
source frames, then combine independent pulse/faint evidence with appearance,
charge-phase, and timing exclusions. A Win alone must never create a final hit.

Unknown-duration neutral uses still start at their source time; an unknown use
exactly tied to faint has no display interval before that boundary. This patch
does not invent an attack duration to solve that separate limitation.

A simultaneous faint and replacement also remains a distinct presentation gap.
`ReplayKnockoutTrack` fits the fall 650 ms before replacement, and
`ReplayDecisionTimers` reconstructs a decision pause over that interval. When
both raw events share one timestamp, that synthetic pause can leave too little
outgoing time for a recorded two-second finishing cycle. Reconcile that
presentation pause with witnessed impacts in a separate slice; this patch does
not change replacement-pause reconstruction.

Result-crop attention also remains conditional. This concession was eligible
because the opponent had one member left. Early concessions with multiple
members and unavailable result music need separate capture validation.

The earlier [independent visual phase-boundary investigation](2026-11-04-independent-charge-boundaries.md)
remains a specification; these replay repairs do not implement that new detector.
