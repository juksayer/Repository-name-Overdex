# Battle implementation priorities

Updated October 22, 2026. User direction: prioritize improvements that help before,
during, or after a battle. Correct observations and timely decisions take priority
over unrelated presentation work. This is the active ordered backlog; the
[feature inventory](../reference/current-feature-status.md) records broader gaps.
The [first-release checklist](first-release.md), added October 23, defines which
of these capabilities need to be dependable for the initial training/recording beta.

## Work order

Effort is a relative estimate, not a delivery promise. Existing implementations
are starting points; a task is not complete merely because a class or test exists.

| Order | Task and player benefit | Relative effort | Completion evidence |
| --- | --- | --- | --- |
| 0 — current quick win | **Clear the HUD:** remove repeated species names, shorten sprite cards, enlarge sprites. Leave more battlefield visible and reduce competing text. | Small | Build passes; actual overlay shows readable sprites, confirmed moves and faint state, with no printed species names. Badge alignment and deliberate long-press dragging still work. |
| 1 | **Get species, form, and side right promptly.** Preserve regional form identity through Team Select, configured teams, observations, legal movepools, HUD and Replay. Use existing OCR, cries and type evidence before adding another expensive reader. | Medium | Paired-match cases distinguish normal/Alolan Raichu, retain correct types, and never transfer the departing Pokémon's evidence to its replacement. Measure source-to-HUD delay and whether identity arrives before the earliest plausible charged move; 1.5 seconds is a diagnostic target, not the definition of success. |
| 2 | **Count real Fast Moves and resolve their names.** Audit the existing independent audio reader alongside recipient HP loss/border pulses, attacker HP motion, player charge fill, and effectiveness testimony. Handle inactivity, animation stutters, overlapping sounds, switches and QTE pauses. | Medium–large | Compare hand-labeled counts and actor/move identity with both phones' recordings. Report missed attacks, extra attacks and unresolved identities separately. Deliberately idle periods produce no invented observed attacks. Reference-audio self-matching is not a live accuracy score. |
| 3 | **Make energy and damage useful for decisions.** Port the small relevant PvPoke timing/stat/damage calculations with attribution; compare against its JavaScript using identical inputs. Use move-specific registration timing when correlating sound, motion, HP and energy. Track damage/energy ranges when identity or stats are uncertain. | Medium | Agreed mechanics cases cover start versus hit timing, shadow/buff modifiers, energy cap and charged costs. On real recordings, no energy appears during QTE/forced-choice inactivity, no charged move is spent twice, and displayed perfect-play forecasts remain distinct from measured accounting and Replay. |
| 4 | **Finish every battle faithfully.** Audit faint ownership, resource counts, forced-switch/shield timers, final charged impact, KO-before-replacement and outcome/save boundaries. | Medium | Replay agrees with the two perspectives on final attacks, fainted slots, shields and result. Back-to-back matches each remain reopenable after returning to Overdex. Treat these as validation targets, not a claim that every current recording fails. |
| 5 | **Make pre-flight information trustworthy.** Reuse saved-team selection, manual Team Select rescan and move editing. Compare entered team/form/moves with battle observations and surface disagreements without silently overwriting the user's card. | Medium | A saved team and an unsaved scanned team can both reach a ready state before battle; conflicting observations remain reviewable. Broad assisted-registration redesign stays paused. |
| 6 | **Turn recordings into useful review.** Event search and supercuts, event-linked coaching notes, specimen-ID battle metrics, then portable match/clip sharing through Trainer Comms. | Medium–large | A selected specimen/move/KO search finds the right clips; edits and annotations survive reopening; a recipient plays a shared recording without the sender's local paths. Original testimony remains intact. |
| 7 | **Test visual recognition where it fills a measured gap.** First evaluate the small QTE-type crop and fast-effect color/motion on labeled captures. Separately pilot rendered 3D references for Sneasel, Sealeo and Vaporeon only if species failures remain. | Research first | Use held-out real screenshots with lighting, partial visibility and effects. Measure wrong matches, unresolved cases, added latency and phone cost against the existing readers before expanding. A rig or colored effect alone does not establish species, attack type or actor. |
| 8 | **Add team advice using the existing PvPoke foundations.** Cached matchup coverage, counters, alternatives and owned-Pokémon move-upgrade advice. | Large | Recommendations respect current league/form/move data and declared shield/energy assumptions. Full rankings are precomputed or requested outside the live capture loop. Dated strategy articles do not supply current meta rankings. |

## Current slice

- [x] Inspect the active HUD and its existing device checks.
- [x] Remove visible species labels from battle cards; retain accessible names.
- [x] Reduce card height from 48 to 44 dp and increase sprite area from 38 to 42 dp,
  subject to the width of each card. Preserve outside padding and the badge seam.
- [x] Reuse the resolved local sprite URL while identity is unchanged, rather than
  reopening the asset on each presentation update.
- [x] Trim fully transparent margins in the HUD's cached image request, so the
  visible Pokémon fills the card. Preserve aspect ratio and original sprite files.
- [x] Run the full unit suite and build the final debug APK: all 990 tests passed;
  the app and device-test APKs built successfully after the display-time trim.
- [x] Install on the Moondrop and check the actual Compose overlay in a controlled
  scene: known/unknown slots, accessible species, enlarged artwork, legible aligned
  moves, no printed species name, and deliberate long-press movement all passed.
  See the [October 21 verification](../verification/2026-10-21-compact-hud.md).
- [ ] In the next live battle, confirm readability against GO's badge and battlefield,
  including fainted cards. Compare recognition results before claiming the smaller
  footprint improved OCR; the controlled layout test does not establish that.

Next substantial slice: species/form preservation and the paired-match Fast Move
audit (orders 1–2). Finish and measure one bounded change before adding readers.

October 28 progress: [loss/flash phase pairing](../verification/2026-10-28-fast-move-phase-pairing.md)
now uses measured post-loss HP continuity to associate delayed phases without
requiring an already-resolved move name. The recorded Charm opening reprocesses
as seven plus seven paired uses with original source IDs/times retained. This
repairs one duplicate-count path; complete Fast Move observation, earlier phase
boundaries, dependable player-fill measurements, and live acceptance remain open.

October 22 progress: the [player form handoff](../verification/2026-10-22-player-form-handoff.md)
now retains scanned lead identity, resolves later appearances from independent
button evidence, and keeps the two Raichu forms separate in move, energy, faint
and Replay charge-button tracking. Recorded regression and emulator integration
checks pass. Next acceptance is a real mixed-form battle; opponent-form/type-crop
work and the paired-match Fast Move count audit remain open.

## Supplied asset reuse

The [October 21 asset inventory](../reference/pokemon-go-reference-assets.md)
maps the supplied Pokémon GO artwork/audio to existing readers and open work.
Use it within the ordering above:

- **Identity first:** retain regional forms already present in Team Select's
  descriptor catalog, including Alolan Raichu. The first
  [form-preservation slice](../verification/2026-10-21-team-select-forms.md) retains
  variants and compares competing forms within a species. The first Moondrop
  roster correctly distinguished shiny Alolan Raichu from ordinary Raichu;
  the user reported instant recognition and correct move lists. The subsequent
  slot-swap/rescan also produced the correct order and ordinary first-slot move
  list. Battle retention checks remain; owned-card, opponent-form and artwork
  coverage remain broader work.
- **Exact type references:** evaluate the 18 plain and 18 bordered type icons
  against real active-type crops. The other 18 supplied type files are duplicates.
  Reuse labeled QTE samples for a separate QTE classifier; badge matching alone
  does not validate moving attack-effect recognition.
- **Audio validation:** all 72 current Fast Move reference WAVs already occur in
  this collection. Prioritize live paired-recording discrimination and attribution
  over adding duplicate references. The supplied VS sound is a later transition
  cue candidate, separate from GO/Match Start evidence.
- **Shadow evidence:** test the exact shadow/purified marker shapes in their
  expected location rather than using purple alone. This supports battle state;
  it does not resume the broader registration redesign.
- Prefer these small 2D references before the optional 3D-rendering pilot. Keep
  source assets intact and package only validated references or derived features.

## Evidence and performance rules

- Retain raw observations and their capture times. Append conclusions with their
  supporting evidence; do not rewrite history to match a simulation.
- Species confidence does not automatically establish side, move, attack count,
  or energy confidence. Regional form remains part of identity.
- No reader needs to run at full rate throughout the entire session merely because
  it may be useful once. Use entry, battle and charge cues, fresh-frame admission,
  bounded queues and shared capture; retain witness-availability diagnostics.
- Audio analysis can remain armed from any countdown glyph through the terminal
  battle boundary, while move interpretation excludes QTEs and other non-fast-move
  phases. Five distinct faints, an accepted result, or manual stop ends attention.
- Judge changes by measured phone cost and held-out match correctness. Successful
  builds, desktop timings and reference self-comparisons are separate checks.
- Preserve supplied model and reference assets. Render/prepare 3D references on
  the computer; only package the recognition data justified by the pilot.

## Deferred until the battle work above is dependable

- Full-catalogue 3D training, a 3D Replay renderer, and cosmetic animation expansion.
- Further Pokédex/shell/mascot decoration without a clear battle workflow benefit.
- Broad assisted-registration automation and unrelated feature expansion.

Trainer Comms and the Replay Editor retain their existing work; this ordering
prioritizes battle usefulness rather than deleting or restarting those features.

## Reference starting points

- [Architecture map](../../PROJECT-MAP-v2.md) and [component navigation](../reference/overdex-navigation-index.md).
- [Independent audio implementation and limits](../verification/2026-10-20-independent-fast-move-audio.md).
- [Paired-match review](../verification/2026-10-20-paired-self-match-review.md).
- [Trainer Comms sharing plan](trainer-comms-sharing.md).
- PvPoke source supplied by the user: `Pokemon.js`, `Battle.js`, `DamageCalculator.js`,
  `TeamRanker.js`, `Ranker.js` and `RankerOverall.js`. Preserve MIT notices for reused
  code; verify the mechanics and source-data revision used by comparisons.
