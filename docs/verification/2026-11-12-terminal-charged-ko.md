# Final charged KO without an empty HP outline

Status: a bounded final charged-collapse inference is implemented in the shared
live/Replay source-history path. Recorded-video and original-archive regressions
pass; a new live battle is still required for device acceptance. This extends,
and does not replace, the normal-field red-to-empty and independent count paths.

## What qualifies

`TerminalParticleFrameMeasurer` now retains compact bright-component geometry
from the connected components it already computes. It performs no additional
full-image read and retains no Bitmap. These optional numeric fields travel in
`TerminalParticleSample` through the existing bounded witness, custody batches
and archive DTO. Old archives decode with an empty flash list.

`TerminalCollapseSequences` requires a source-ordered particle cluster, a nearby
bright component, contraction to at most half its peak area, and two readable
frames where that component is absent. Multiple bounded seeds avoid letting an
earlier unrelated cluster hide a later valid one. The maximum episode is 1.5 s;
a source gap over 250 ms, unavailable reading, changed geometry/stream, conflicting
frame or excessive feature count invalidates continuity. Clearing a bright
component is not itself evidence that a sprite vanished.

`TerminalChargedFaintResolver` additionally requires all of:

- An original Match Start and a trusted accepted win/loss label.
- One independently measured remaining Pokémon on the receiving team in this
  Match, and no subsequent contradictory count of additional survivors. This
  historical count can remain supporting evidence while the event-driven reader
  sleeps; it is not reissued as a fresh measurement. Reset/Match boundaries still
  invalidate its use. The original 30-second expiry was removed after the
  player-side coverage audit exposed its conflict with event-driven reading.
- Unambiguous side-located identities for both the recipient and named attacker.
- A named, accounted charged move from the announcement interpreter, after the
  same particle assignment began and no more than 6 s before the particle seed.
- The matching result within 20 s after the sequence clears. Result recognition
  can be several seconds later than the finishing animation.
- No intervening entry/switch, resumed supported Fast Move, new charge phase, different independently read
  combatant, positive receiving HP during/after the collapse, conflicting source
  evidence, opposite result, or second competing terminal sequence.

A QTE sequence before the named hit does not qualify. A result alone, particle
cluster alone, hand gesture, absent HP bar or predicted damage does not qualify.
The scheduling recipient is not treated as independently witnessed identity.
No empty outline or final native zero-ball frame is required.

## Live state, Replay and corrections

The new basis is `FINAL_CHARGED_COLLAPSE_AND_RESULT`. `LiveTeamLossHistory` feeds
both the live faint witness and the existing opponent HUD reconciliation.
`ReplayTerminalChargedFaintProjection` uses the same resolver on original source
inputs. Both use the final clear sample's capture clock, not its batch-publication
clock or the later result clock. The original numeric batches and archive entries
remain unchanged; derived faints cite their supporting articles.

Replay shows the faint and a depleted HP endpoint at that supported finish. It
does not add a measured empty bar, an intermediate damage curve, zero Poké Balls,
a charge or a Fast Move. Previously supported attacks retain their source times.
This endpoint is inferred depletion, not a pixel measurement of zero HP.

Live and Replay exclusions remain revisable. Later independently identified
survival removes the inferred KO and permits identity/move tracking to recover.
Old archived visual-faint conclusions cannot suppress a separately valid HP
resolution. Count-confirmed and HP-confirmed losses take priority over adding
another terminal marker for the same actor.

## Evidence and validation boundaries

Numeric sequence fixtures use the study's fixed opponent territory (0.28,0.40)–(0.92,0.75),
not every possible calibration profile. They retain all 18,852 source frames from five November 11
recordings, including source hashes and original MP4 PTS. The detector yields
three ordered effect sequences: Cramorant's final collapse, Leavanny's final
collapse, and a Leavanny-match QTE lookalike. Phase qualification rejects the QTE.
The other recordings include the living Carbink recall, Roselia concession,
shields, entry effects and ordinary attacks; they produce no ordered candidate
under this specific tested feature definition. This is a limited corpus, not an
exhaustive recall/concession classifier.

Fourteen retained native/capture-sized crop fixtures compare the Kotlin flash
geometry with an independent OpenCV measurement. Forty-five further cropped
frames exercise the actual Kotlin particle/flash reader at 672×1493 with the
factory opponent territory; both finishes survive a 20 Hz sample limit.

### Expanded factory-crop control scan

The complete five recordings were subsequently passed through the compiled
production pixel reader with a maximum 20 Hz selection, INTER_LINEAR resize to
672×1493 and factory opponent bounds (168,343)–(672,1209): **12,241 frames**.
The host callback supplied raw ARGB pixels in place of `Bitmap.getPixels`; the
reader's component/particle/flash algorithm was unchanged. This run bypasses
phone assignment and overlay-availability gates, and cannot establish phone CPU
cost or live capture coverage.

| Recording | Frames | Ordered visual sequences, video seconds | Reviewed interpretation |
| --- | ---: | --- | --- |
| 211335 | 1,752 | 181.401 | Final Cramorant charged collapse |
| 112107 | 1,730 | 93.540, 95.023, 111.061, 111.928; 118.179 | Four QTE lookalikes; final Leavanny charged collapse |
| 124455 | 2,803 | 133.555 | Rock-type QTE; no candidate during living Carbink recall |
| 161531 | 2,819 | None | Includes the shield and Roselia concession controls |
| 190345 | 3,137 | None | Includes entries and ordinary attacks |

This wider crop exposes more QTE lookalikes than the earlier narrow numeric
study. It confirms why visual contraction/clearing cannot itself establish a
faint. The two positive original archives, enriched only in test copies with
these production-reader measurements, resolve the correct final owners and
depleted Replay endpoints at ±1-second alignment. Live source-history and Replay
agree, including reversed delivery. With the actual final collapse withheld,
the two final-charge Leavanny QTE sequences remain visible to the sequence
detector but produce no KO. No production code changed for this expanded check;
the installed APK is unchanged.

All **21 focused tests across four suites** passed with no failures, errors or
skips: sequence detection, recorded charged finishes, production pixel fixtures
and the earlier wrong-owner faint regressions. The earlier full-suite/APK result
below remains the installed production baseline; this test-only follow-up did
not require rebuilding or reinstalling the app.

Numeric fixtures retain original PTS and source hashes, exact reader class/JAR
hashes, raw-output hashes, frame gaps and all selected negative readings. Their
publication identifiers and video-relative wall clocks are test-only metadata.
They are regression inputs to the sequence/inference layers, not a substitute
for re-running the pixel reader after future algorithm changes. Probe sources,
raw output and focused verification logs are retained in
[/home/sean/overdex-device-backups/2026-11-12-terminal-factory-controls](/home/sean/overdex-device-backups/2026-11-12-terminal-factory-controls).

### Archive and live-acceptance limits

The two original `.odxmatch` timelines are retained byte-for-byte as compressed
test resources with ZIP/member provenance. They still do **not** resolve these
final KOs on their own: neither recorded the new measurements. Test-only enriched
copies add the separately measured video sequence. Cramorant's coarse video
alignment and Leavanny's previously located GO alignment both tolerate ±1 s in
the regression. This does not manufacture original on-device observations or
claim exact synchronization between independent recordings.

Regression coverage includes delayed/reversed/shuffled delivery, missing required
evidence, QTE-before-hit rejection, positive HP, switch/entry/phase boundaries,
conflicting source IDs, ambiguous identities, extra survivors, survival retraction,
shared live/Replay timing, unchanged originals and no double faint markers.

The normal-field HP paths and other recorded battle regressions remain required.
A fresh recorded battle must establish real capture availability, concurrent CPU
cost, correct owner/finish timing, preservation of attacks and plausible Replay
HP before the broader KO/HP goal can be considered complete. Current recorded
positive pixel coverage is for opponent-side finishes; player-side final charged
finishes need their own real-video acceptance case.

The subsequent [player-side scan](2026-11-12-player-terminal-coverage.md) tested
existing real-video cases and found that this visual sequence does **not** cover
their final player KOs. It also removed the unrelated 30-second expiry on a stable
last-Pokémon count. Opponent success must not be generalized to player-side
recognition or all final charged KOs.

## Final verification and installation

The final full suite passed **2,855 tests in 376 suites**, with no failures,
errors or skips, and the debug APK built successfully. This includes the later
batch-envelope regression and explicit rejection when supported Fast Moves
resume after the charge. The checks used JBR and the normal Gradle debug tasks.

Installed on Pixel 4 `99261FFAZ0017J` with data-preserving replacement, after
confirming Droidball was stopped. The installed APK's SHA-256 matches:

`86dbe69e2ca1fc5c0e8547cd7402cac1a5d9e6b2ab8e51e131f9611f8872ac9d`

The app launched successfully and Droidball remained stopped. Of 102 checked
files, 100 were unchanged, including saved matches, battle records, calibration
and app preferences. Two Firebase runtime heartbeat/authentication preference
files changed on launch; their private values were not inspected or restored.
No files were added in the checked directories. The Moondrop was not operated.
Build logs, targeted XML results, APK provenance and the before/after comparison
are retained in
[/home/sean/overdex-device-backups/2026-11-12-terminal-charged-ko](/home/sean/overdex-device-backups/2026-11-12-terminal-charged-ko).

Next acceptance: start Droidball on Pixel 4, approve screen sharing, play a Team
Leader battle whose final opponent is finished with a charged move, and keep
capture running through the result. A silent companion video is sufficient for
this visual check. Check the final opponent's faint marker and Replay depletion,
then compare the newly retained numeric samples with the actual finish. A missing
marker, remaining HP after the supported finish, a wrong owner or an extra/missing
attack remains a failure to investigate. A separate concession/control match is
needed before claiming general terminal accuracy.
