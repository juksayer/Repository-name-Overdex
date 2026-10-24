# First release: a dependable battle training companion

Started October 23, 2026. This is the release scope and acceptance checklist,
not a claim of Play approval or completed live validation. The
[battle priorities](battle-priorities.md) remain the technical backlog; the
[feature inventory](../reference/current-feature-status.md) covers broader work.

## Product promise

Help trainers learn before battle, remember what they could observe during it,
and review what happened afterward. Users should be able to graduate from
assistance to recording alone. Observation, reconstruction, and best-play
estimates must remain distinguishable.

The initial target is a small, dependable closed beta. Public distribution
depends on the acceptance checks and release review below, not the number of
features implemented. Complete and commit one bounded improvement at a time.

## Scope

| Include in the first-release target | Defer expansion |
| --- | --- |
| Existing Pokédex, owned cards, saved teams and moves | New binder/shell decoration and broad registration automation |
| Clear start, stop and next-match controls; reliable local saves and export | Tournaments and social feature expansion |
| Calibration that survives reopening and app updates | New recognition readers without a measured need |
| Faithful Replay, known species/forms, faints, resources and uncertainty | Automatic supercuts, video rendering and advanced coaching tools |
| Explicit recording-only choice; optional assistance with estimates labelled | Trainer Comms launch until sharing, account lifecycle and moderation are ready |

This table does not delete existing development features. The public build must
expose only the selected scope; that packaging work is still pending. Live
assistance remains subject to distribution-policy review. Calling it training
does not establish approval.

## Evidence contract

- Preserve what was measured, when, by which source, and whether observation was
  available. Keep raw Articles separate from later inference and Replay smoothing.
- During a gap, continue a separate best-play forecast using legal move duration,
  energy gain and cap. Known charge/QTE and forced-choice phases pause that
  forecast; uncertain phase timing must remain an explicit assumption or range.
- Never turn elapsed time alone into witnessed Fast Moves or measured energy.
  Reconcile later evidence by appending conclusions, not rewriting testimony.
- Display a move as confirmed only when its evidence supports identity and side.
  Honest unknowns are preferable to a confident wrong move or wrong combatant.
- Recording-only means no battle advice presented to the user, while agreed
  observation and recording continue. Verify that hiding the HUD does not stop
  witnesses. Reduced-assistance presets are optional follow-up work.
- No automated gameplay input, private game-state access, or game modification.
  Audit actual behavior before making these claims in a store listing.

## Work order and acceptance

### 1. Reliable attacks, energy and damage — next battle work

- [ ] Use the existing controlled recordings to separate real repeated attacks
  from border-only repeats, lag and animation stutters. Report false, missed and
  unresolved attacks separately, including the deliberately idle intervals.
- [ ] Validate charge-button fill height, including the darker second fill,
  against hand-labelled settled frames. Preserve multiple possible energy
  values when a fill does not resolve them; do not silently correct the ledger.
- [ ] Check energy caps, charged spends, QTE pauses, switch continuity and faint
  resets on recorded cases. An opponent forecast must never become Replay fact.
- [ ] Recheck species/form/side handoff and final charged impact on both phones'
  perspectives. Validate on held-out battles after tuning on the controlled set.

Starting evidence: the [settled-fill review](../verification/2026-10-23-controlled-settled-fill-review.md)
found two attack events only 1.093 seconds apart in an Incinerate stretch, with
different supporting signals. Resolve that ambiguity before trusting exact
energy or adding more readers. The [live fill reader](../verification/2026-10-23-live-charge-fill-height.md)
and offline analysis already exist; automatic energy correction is not accepted.

October 23 progress: the [Incinerate late-flash fix](../verification/2026-10-23-incinerate-late-flash.md)
joins the demonstrated duplicate while preserving distinct compressed hits. All
1,070 tests pass; real-battle acceptance and the broader count/gap checks above
remain open. The next comparison should check the updated count against settled
fill measurements without automatically replacing the recorded ledger.

October 24 progress: the [four-match Pixel review](../verification/2026-10-24-pixel-match-review.md)
found HUD sprite cards preventing native Poké Ball crop recovery. Location
validation now excludes those pixels just like the downstream witness. The next
live check must confirm resource/faint/result updates; the recorded cases also
expose a separate Dragon Breath/Iron Tail cadence ambiguity and a centered
single-charge-button gap. These remain open before trusting precise energy.

The [sparse-impact identity fix](../verification/2026-10-24-sparse-impact-identity.md)
now retains faster candidates when damage intervals can contain missed hits.
The Dragonair regression stays unresolved instead of certifying Iron Tail;
1,078 tests pass. Late-start audio listening and a new battle check remain next.

### 2. Recording and device reliability

- [ ] Start before entering GO battle menus, then select a team and battle without
  capturing an overworld patch as a persistent team badge.
- [ ] Complete two consecutive matches in one session; reopen both after returning
  to Overdex and restarting it. Neither match may absorb the other's events.
- [ ] Verify app switching, screen lock, permission refusal/revocation and capture
  interruption. Controls must show whether observation is actually running.
- [ ] Preserve calibration, teams and owned-card IDs across an in-place update.
  Check full-screen versus content coordinates on both Moondrop and Pixel.
- [ ] Export, reimport and reopen a recording; check retention, full storage,
  missing artifacts and interrupted writes without losing the saved timeline.
- [ ] Compare a representative battle with observation on/off for phone slowdown,
  missed frames, heat and battery cost. Record device/build and observed gaps.

### 3. Training controls and first-run experience

- [ ] Add or verify an explicit recording-only choice before capture starts, with
  clear stop controls available during recording. Test that it records normally.
- [ ] Make assistance and estimated values understandable without developer help.
  Check that its state persists and that recording-only exposes no live advice.
- [ ] A fresh tester can select a saved/scanned team, calibrate when needed, record
  a battle and open Replay using the physical-style controls.
- [ ] Show useful explanations for unavailable OCR/audio and insufficient evidence,
  rather than requiring a developer to inspect logs.

### 4. Production packaging and Play preparation

- [x] Isolate the accessibility diagnostic service and its Android configuration
  in the debug source set; hide its release menu entries and route. Verify both
  build variants. The [packaging verification](../verification/2026-10-23-release-foundation.md)
  passed; this does not replace the wider permission review.
- [ ] Audit actual capture/audio/overlay/camera permissions and SDK behavior.
  Define backup/restore rules instead of shipping the template configuration.
- [ ] Complete prominent capture disclosure, privacy policy and Data safety
  answers from actual behavior, including cloud features and retained artifacts.
- [ ] Choose the final application ID, signing ownership, versioning and update
  strategy. Preserve existing tester data; do not casually change the package ID.
- [ ] Build and inspect the signed App Bundle, delivered size, target SDK and native
  library compatibility, including 16 KB pages. A debug APK is not this check.
- [ ] Inventory distribution rights and notices for artwork, audio, fonts, data and
  reused code. Retain local source assets while selecting release-safe resources.
- [ ] Assess live assistance against Play and Pokémon GO rules using the exact
  release behavior. Do not conceal functions or rely on the training label alone.
- [ ] Exclude unfinished Comms from the first release, or complete its account
  deletion, abuse controls, backend and sharing acceptance work before exposure.
- [ ] Prepare an honest listing, reviewer instructions and foreground-service demo;
  satisfy the testing requirements applicable to the developer account.

Policy starting points, checked October 23, 2026: Google Play's
[device/network rules](https://support.google.com/googleplay/android-developer/answer/16559646?hl=en)
include game-cheating and unauthorized service use restrictions. Its
[user-data policy](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en-GB)
governs disclosures, data handling and account deletion; its
[foreground-service requirements](https://support.google.com/googleplay/android-developer/answer/13392821?hl=en)
cover declared capture services and reviewer demonstrations. The
[intellectual-property policy](https://support.google.com/googleplay/android-developer/answer/9888072?hl=en)
requires appropriate rights to distributed content. These links are review inputs,
not an approval or a substitute for checking the final app and current policies.

## Keeping the work focused

For each change, record the user-visible problem, the smallest useful fix, the
evidence used to check it, and anything still unverified. Commit the completed
slice before beginning the next. New ideas go into the backlog unless they fix a
release blocker or replace a lower-priority item deliberately.

An unchecked item stays unchecked after a successful build. Device behavior needs
device evidence; recognition needs labelled recordings; policy readiness needs a
review of the actual release. We will not report a release percentage or date from
source-file count or the number of features alone.
