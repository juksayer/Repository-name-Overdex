# Outstanding acceptance tests

Audited November 4, 2026, after replay-ending fix `e2b9f36c`, against the current
`odx-recovery` checkout. This consolidates known open checks from the plans,
verification reports, and current call paths. It is not a count of undiscovered
bugs or a request to repeat every historical test. Grouped rows can share one
recording; the completion evidence for each row remains separate.

The latest full JVM run passed 1,705 tests with no failures, errors, or skips;
the debug build and Moondrop replay check passed. Those are completed checks.
The remaining work is mainly real-battle acceptance, broader device coverage,
failure handling, and features whose implementation is still pending.

## Status and recording setup

- **Open:** the relevant path exists; the named acceptance case is outstanding.
- **Partial:** useful tests already passed, but the specified case/coverage remains.
- **Needs work:** a known implementation gap or experiment must be addressed before
  accepting that feature. Existing media can still supply a baseline.
- **Deferred:** outside the immediate battle-reliability / first-beta scope.

For controlled recognition captures: use the same tested build on both phones,
record the actual species/form and equipped moves, music at zero, SFX at 100%,
and retain both Overdex archives. Enable **device audio** in the screen recorder
and check that a short playback actually has sound. The November 2 supplied
MP4s had no audio stream, although Overdex's PCM survived. Pointer Location and
the Overdex HUD should be hidden for clean calibration references. Separate
pointer-on measurement videos are useful supplementary evidence.

Treat the user's latest stutter description as **audio stuttering while the
visual attack remains unaffected**; keep earlier recordings' own labels intact.
Always preserve observation separately from a best-play forecast. Count false,
missed, wrong-side, and unresolved attacks separately.

## Battle accuracy — highest priority

| ID / status | Outstanding test | Evidence needed to close it |
| --- | --- | --- |
| B01 — Open | Final **Fast Move** KO and possible final faint cry, on both sides | Screen/audio through the result: last real impact, HP depletion/disappearance, faint/ball change, any cry, and result, each with its own time. Replay must show the recorded final attack and correct faint without adding an attack from the result alone. |
| B02 — Open | Final **Charged Move** KO, on both sides | Record QTE/shield choice through release, damage, faint, and result. Confirm one named move/spend, finishing projectile before KO, correct actor, and no premature end. Synthetic finishing Earth Power already passes; new capture acceptance remains. |
| B03 — Partial / needs work | Ending/result matrix | Win, loss, Rocket result, and a draw if naturally available; concession during ordinary play and QTE. In particular, early concession with more than one opponent remaining and unavailable music needs capture-gate work. Last concession's Win and interrupted QTE are verified; this does not close every ending case. |
| B04 — Partial | Hand-counted attacks versus idle, pauses, overlapping attacks and stutter | One attacker at a time, counted run, five seconds idle, repeat, then reverse sides and include both attacking. Check each perspective for extra/missed/wrong-side uses. Stutter, connection gaps, switches and QTEs must not award impossible attacks/energy. |
| B05 — Partial | Prompt Fast Move identity and confidence | On held-out battles measure first supported hit/new appearance to correct legal move/side and confirmation. Test overlapping audio and ambiguous cases. A favored low-confidence name must remain distinguishable from confirmation; one side's sound cannot establish the other's move. |
| B06 — Partial | HP geometry supports damage and cadence | Compare before/after settled bars with known moves; check that a late charged impact is not another Fast Move, an idle hit reaction does not establish an attack, and the first real hit after QTE/replacement is preserved. Unknown stats require damage ranges rather than a forced label. |
| B07 — Partial | Current charged-sequence witnesses | Fresh Get Ready / Attack Incoming / shield choice / no-shields examples, including capture started after GO. Check source-time transition, correct actor/name, one spend, raw progression text and resumption of ordinary play. Current OCR exists; the independent earliest visual detector is still D02. |
| B08 — Open | Haptic QTE progression | Phone stationary, vibration enabled then disabled, same visible QTE grades. Align sensor pulses with Nice/Great/Excellent and distinguish handling motion. Three-grade haptics are unvalidated supporting evidence, not an established start clock. |
| B09 — Partial | Charge fill and energy/HUD decisions | Counted attacks with settled checkpoints: light first fill, darker second fill, cap, cheapest and expensive moves, one-Fast-Move-away highlight, ready red, confirmation marks, one spend, post-QTE return, switch continuity and faint reset. Fill height must not silently overwrite recorded accounting. |
| B10 — Partial | Species/form/copy handoff | Ordinary and Alolan Raichu, preferably same Fast Move; confirm lead, front/rear sprites, typing, legal pools, separate energy/HP/faint and owned-card history after a same-name switch. Include repeated same-species members; unresolved opponent forms must retain alternatives. Basic roster/form recognition already passed. |
| B11 — Partial | Retained badge and resource counts on Pixel | Launch from overworld, enter battle, witness 3→2→1/faints and native badge hide/return/QTE. No scenery retained, extra bright balls restored, or departing team data inherited by replacement. Updated fixtures pass; fresh Pixel field acceptance remains. |
| B12 — Open | One unlocked charged move | Real battle with the single centered button: circle/name/touch/fill/effectiveness territory, ready state, and safe layout transitions without invented energy. Synthetic centered-control regressions are already present. |
| B13 — Partial | Minimal HUD in a real battle | Moving scenery, native announcements and badge overlap: shorter sprites, transparent area, abbreviated move names/type edge, readiness/check/hazard visibility. Native text stays readable and Overdex text is not its own OCR evidence. Synthetic 18-color/overlap checks already passed. |
| B14 — Partial / needs work | Active type icons across held-out cases | Both sides, single/dual types, all 18 types over time, clipping/fade, switches and delay. Current field evidence is 124/127 complete pairs; three faded Grimmsnarl images incorrectly became Dark-only. Repair that retained-image case first, then assess new field coverage. |

### Final-cry hypothesis — B01/B02

The user expects the fainting Pokémon to cry before the match ends and will
check the next match. Record whether it is audible, its timing, whether the
screen recording or other retained audio contains it, and whether it resembles
the entry reference or needs its own reference. The cry matcher accepts a
`POKEMON_FAINTED` cue, but the faint-triggered PCM request is not currently wired.
That capture path needs implementation before relying on automatic final-cry
retention; enable device audio in the screen recorder for this next check.

Compare a normal KO with a concession when possible. A cry may corroborate a
faint; it cannot by itself establish the exact finishing move or energy, and
an absent/unrecorded cry is not proof of a concession. Keep the recorder running
through the visible result rather than stopping at the last attack.

Sources: [ending audit](../verification/2026-11-04-concession-and-final-hit-replay.md),
[battle priorities](battle-priorities.md),
[charge attention](../verification/2026-11-04-charge-phase-attention.md),
[charge camera](../verification/2026-10-28-charged-hp-camera.md),
[fill review](../verification/2026-10-23-second-brutal-swing-fill-review.md),
[field menu/idle case](../verification/2026-10-29-field-menu-and-idle-player.md),
[type follow-up](../verification/2026-11-04-saved-party-recall.md),
[cry cue contract](../../app/src/main/java/com/example/overdex/battle/audio/BattleCryListeningCue.kt)
and [current cue-request dispatch](../../app/src/main/java/com/example/overdex/battle/observation/Match.kt).

## Audio references and discrimination

| ID / status | Outstanding test | Evidence needed |
| --- | --- | --- |
| A01 — Open | Fire Fang reference | First inspect existing Mawile recordings for a clean usable source; only record again if necessary. Opponent idle, SFX-only capture, several distinct uses plus silence; hold out different uses for validation. |
| A02 — Open | Zen Headbutt reference | Same clean setup. Preserve exact equipped move and source/clip hashes; a distinct-battle query should validate the reference rather than only matching itself. |
| A03 — Partial | Poison Sting generalization | Reference already added; validate on a different battle, not another occurrence from the source battle. |
| A04 — Partial | Mud-Slap versus Mud Shot | Both reference names exist, but held-out Mud-Slap windows remain acoustically ambiguous. Use known equipped moves, HP/cadence and isolated attackers; retain uncertainty if audio does not separate them. |

Confusion, Rock Smash, Bubble, Mud-Slap, Poison Sting and Steel Wing reference
captures have already been processed. They are not six missing recording tasks.
The latest checked-in audit has two ordinary named gaps, A01/A02. Sixteen typed
Hidden Power aliases need separate type-family validation; Transform and the
Struggle catalogue anomaly need a catalogue/behavior decision. The separate
charged-audio audit lists 47 exact-name gaps; collecting those is lower priority
than current attack and ending accuracy, not 47 immediate homework assignments.

Sources: [latest capture coverage](../audio/2026-11-02-captured-references/README.md),
[Fast Move gaps](../audio/2026-11-02-captured-references/missing-fast-move-audio.csv),
[charged gaps](../audio/2026-10-31-coverage/missing-charged-move-audio.csv),
[held-out findings](../verification/2026-11-02-captured-fast-move-audio.md).

## Calibration and device coverage

| ID / status | Outstanding test | Evidence needed |
| --- | --- | --- |
| C01 — Open | Clean reference packs | Moondrop, Pixel 4 and onn 10.1; TCL optional. Original full-screen frames with dimensions/system bars: Team Select/league/CP, VS, 3/2/1/GO, ordinary battle, one/two charged controls, QTE grades, shield choice, replacement choice, win/loss. Include visible targets for species, HP, resources, announcements and exit region. |
| C02 — Partial | Known-working profiles | Check all fields against actual visible targets per device, save named model/resolution profile, explicitly Keep As Working, adjust and Restore Working, restart observation and verify exact crops. A saved file or opened dropdown is not whole-profile acceptance. |
| C03 — Partial | Device/aspect filtering | On Pixel/tablet as well as Moondrop: This Screen/model folders offer suitable screenshots; saved profiles default to detected model/physical resolution. Foreign-device inclusion is deliberate, warnings are clear, and no silent scaling/activation occurs. Basic modal/filter UI already passes. |
| C04 — Partial | Target-backed LCD examples | For every preview field, retained example actually contains its cited target or says Review/unavailable. Inspect today's rectangle with a suitable reference; background or target-absent frames cannot certify bad calibration. Editing/navigation/cancel UI itself already passed. |
| C05 — Partial / needs work | League and restriction layout | Seasonal cup/standard league title, eligible types and CP limit stay visible and are read correctly. Menu geometry is implemented, but seasonal league classification has a separately recorded error. Use labeled screenshots before another classifier change. |

Do not change a live profile silently. Current witnesses retain loaded geometry
until observation restarts; record which profile/reference/build supplied a case.
Pointer-on videos measure coordinates, while pointer-free frames are the clean
recognition references. Emulator resolutions supplement physical testing but do
not establish GO capture/inset behavior on a real device.

Sources: [screenshot library](../verification/2026-10-30-calibration-screenshot-library.md),
[calibration stability](../verification/2026-11-03-calibration-stability.md),
[device filtering](../verification/2026-11-03-profile-device-filter.md),
[LCD calibration](../verification/2026-11-01-directory-lcd-calibration.md),
[party dismissal](../verification/2026-10-30-party-overlay-dismissal.md).

## Local workflows, lifecycle and other device checks

| ID / status | Outstanding test | Evidence needed |
| --- | --- | --- |
| W01 — Partial | Saved party into actual battle | One-selection saved-team apply, correct moves/forms/links, menu collapse before 3/2/1, badge-authorized HUD and first active Pokémon continuity. Saved-team UI passed; service-window/battle-start acceptance remains. |
| W02 — Partial | Owned-card import/link and history UI | Team Select unmatched/changed team → choose existing card or import → review/save/reopen → first match retains exact specimen IDs. Separately open that specimen's physical history search after a move correction. Same-ID move editing and database/history regressions already passed. |
| W03 — Open | Two real consecutive matches | In one Droidball session use Next Match, complete both, stop/restart Overdex and reopen both. Separate IDs, timelines, resources, audio/identity context and summaries; no match absorbs the other's events. Notification prebattle Next checks are already done. |
| W04 — Partial | Lock, permissions and interrupted capture | Moondrop lock/screen-off saves once, releases projection/audio/service, and does not resume on wake. Recheck latest worker-release path on Pixel. Include denial/revocation of relevant permissions and interrupted capture with truthful controls/recoverable records. Pixel's earlier lock test passed; Moondrop's actual transition did not. |
| W05 — Partial | Automatic return to summary | Complete/stop real battle → return to Overdex → that summary once. Navigate away/leave/return without repeated redirection; check after consecutive matches. Manual summary navigation passes; full process death may require manually reopening a saved summary by current design. |
| W06 — Partial | Storage failure and portable archives | In isolated fixtures/device storage: full/near-full storage, interrupted writes/export, corrupt/missing media and recovery without losing the ledger. Verify available media formats and original source hashes on export/import. Ordinary Replay-only round trip/newest-50 cleanup already passed. Never fill the user's phone or delete their files for this test. |
| W07 — Partial | Clips after original retention cleanup | On isolated storage/device, let original matches age out and reopen/play their independently saved clips/compilation. Unit source-snapshot protection passes; ordinary two-match clip creation, reordering, save/reopen and bounded playback already passed on Moondrop. |
| W08 — Open | Real Trainer Comms | Testers' own accounts: two-phone trainer-code discovery/direct conversation, then three willing registered accounts in a group. Background/network loss/reconnect/retry/sign-out/in; messages appear once in the correct room, drafts/history stay account-scoped, code change preserves account/conversation identity. No real messages have been sent as part of this audit. |
| W09 — Open | Fresh tester without developer help | Select/scanned team, suitable profile, record/stop, summary/replay, understandable uncertain/unavailable OCR/audio and estimated values. Include physical controls, short screens and large displays. Recording-only acceptance waits for D09. |
| W10 — Open | Real phone performance | Comparable battles with observation on/off: sluggishness, source-frame gaps, memory/storage growth, heat and battery; document model/build and capture settings. Include Moondrop, Pixel and low-end tablet when available. Reference self-match scores and desktop speed are not phone-cost measurements. |
| W11 — Deferred | Droidball presentation polish | Subjective loudness/timing of complete roll/skid/edge bump and behavior with Android animations disabled. Normal departure, enabled-animation tilt/blink/tap progression and playback scheduling already passed. |

Sources: [saved-party recall](../verification/2026-11-04-saved-party-recall.md),
[field collection](../verification/2026-10-28-field-team-collection.md),
[move editing/history](../verification/2026-10-30-current-team-move-editing.md),
[lock stop](../verification/2026-10-27-automatic-session-stop.md),
[summary handoff](../verification/2026-10-25-match-return-and-pixel-badge.md),
[retention/export](../verification/2026-10-20-archive-retention.md),
[editor](../verification/2026-10-20-replay-editor.md),
[Comms acceptance](../verification/2026-10-20-trainer-comms-foundation.md),
[sound checks](../verification/2026-10-29-droidball-sounds.md),
[tap progression](../verification/2026-10-29-droidball-tap-progression.md).

## Developer work before accepting a new feature

These are dependencies/future test areas, not requests to repeat matches until
unfinished code happens to work.

| ID | Work and subsequent validation |
| --- | --- |
| D01 | Terminal HP-loss/settling capture, bounded faint-triggered PCM capture (the cry cue is accepted but has no current producer), unknown-duration hit tied to faint, and simultaneous faint/replacement presentation pause. Inspect retained Dewpider source frames, build bounded source-backed regressions, then validate B01/B02 on a new battle. A Win cannot create a missing attack. |
| D02 | Independent earliest QTE/shield transition: positive ordinary-controls baseline, fresh nonzero HP for both current appearances, coordinated control/card loss and later bubble/shield confirmation. Test faint/switch/last-member/no-shield/single-button/overlay/capture-gap negatives, source timing and latency. Existing replay reconstruction is not this new live detector. |
| D03 | All 18 moving QTE patterns, popped-bubble counts/pitch ladder, and resulting charge-quality/damage estimates. Build labeled inventories, classifier/counting logic and held-out tests before claiming coverage. Current crop collection/progression OCR does not implement these. |
| D04 | Fast Move particle color/shape/motion/actor classifier. Label attacker/recipient Fire Fang ring/jaws and Charm hearts/ring separately; compare Incinerate/Spark, then same-move form/shadow controls. Shiny comparisons later. Offline review tools already exist; no live shape classifier is accepted. |
| D05 | Calibration scorecards: immutable revision attribution, attempt/availability telemetry, independently labeled same-frame benchmark, wrong/absent/unavailable handling, then device-scoped field-health/accuracy UI. Proposal only; no trustworthy profile score exists yet. |
| D06 | Retained-image fixes/experiments: faded Grimmsnarl missing Fairy disk; seasonal league error; faded Attack Incoming native-OCR experiment whose model/low-memory runs did not finish successfully. Use existing pixels before requesting a rerun. |
| D07 | Any adaptive longer-audio retry needs source-interval deduplication, full legal-context protection and positive ordinary-play gates. The bounded offline longer-window/filter experiment is already complete and showed no additional strict acceptance; a longer clip is not an established fix. |
| D08 | Recovery of missing/damaged charged announcements from legal moves, feasible energy, HP loss, timing/gap and audio, including mirror-match actor ambiguity. Build explicit exclusions/range evidence; do not turn a quiet gap or button tap into a completed charge. |
| D09 | Explicit persistent recording-only mode, then prove all agreed observation continues with zero live advice. Merely hiding a HUD is not acceptance of a mode that is not implemented. |
| D10 | Comms `.odxmatch` transfer and portable clips/compilations; progress/hash/membership/offline opening; account deletion, membership lifecycle, blocking/reporting/abuse controls before wider exposure. Text conversation acceptance is W08; attachments are not ready to test. |
| D11 | Editor event-search/supercuts, event-attached coaching notes, editing existing in/out points, continuous draft autosave, portable sharing and video export. Basic editor is implemented and tested; these extensions are deferred work. |
| D12 | Specimen Timeline/League Eligibility/Notes placeholders and tournaments are future feature work. Assisted-registration layout/scroll/image automation remains paused at user direction, not urgent battle homework. |

Sources: [ending gaps](../verification/2026-11-04-concession-and-final-hit-replay.md#remaining-recording-work),
[independent transition specification](../verification/2026-11-04-independent-charge-boundaries.md),
[visual review](../reference/fast-move-visual-review.md),
[scorecards proposal](calibration-scorecards.md),
[audio experiment](../verification/2026-11-04-coherent-fast-move-audio.md),
[audio-position and LCD-input proposals](audio-position-and-lcd-input.md),
[feature boundaries](../reference/current-feature-status.md),
[Comms plan](trainer-comms-sharing.md), [release scope](first-release.md).

## Release checks — not more battle homework

These are our outstanding preparation/validation tasks from the release plan,
not a fresh statement of current store-policy requirements. Recheck official
rules against the actual final release when doing that review.

1. Audit actual permissions, capture/audio behavior, SDKs and feature exposure;
   include implemented recording-only behavior and gate unfinished Comms.
2. Define and verify backup/restore and device transfer for cards, teams,
   calibration, recordings, caches and device-specific state. Current backup
   XML rules remain sample templates; ordinary in-place data preservation is
   already verified and does not prove restore onto another phone.
3. Finish capture disclosure, privacy policy, Data safety and applicable account
   lifecycle behavior from what the product actually stores/transmits.
4. Set application identity, signing ownership, version/update strategy; inspect
   signed App Bundle, delivered size, SDK/native-library compatibility and
   16 KB page support on the final packaged build.
5. Audit distribution rights/notices for artwork, Pokémon/Sonic audio, fonts,
   data and reused code; retain local source assets while selecting release assets.
6. Review actual live-assistance behavior against applicable distribution/game
   rules; the training label alone does not establish acceptance.
7. Finish the listing, reviewer instructions and capture-service demonstration;
   complete the testing requirements applicable to the developer account and
   the intended beta/release channel.

The debug-only accessibility diagnostic service exclusion already passed
debug/release packaging inspection. It is not a missing implementation. Final
signed-artifact inspection and broader permission review remain distinct.
Source: [first-release checklist](first-release.md#4-production-packaging-and-play-preparation).

## Already done — avoid duplicate homework

- Confusion/Rock Smash/Bubble and Mud-Slap/Poison Sting/Steel Wing source capture
  processing; the original 1.093-second Incinerate duplicate investigation/fix;
  the bounded longer-audio/filter experiment.
- Pixel Start/Stop spacing, manual HUD request/Ready feedback, basic notification
  actions and ordinary Stop; fresh real battle sequencing is W01/W03.
- Moondrop LCD crop navigation/drag/nudge/cancel, profile chooser/review dialog,
  Current Team move scrolling and basic same-ID move corrections.
- Isolated persistent card deletion/failure/preservation tests plus the phone
  confirmation/cancel path. Deleting a user's real card is not mandatory testing.
- Basic multi-match clips/notes, reorder/save/reopen/continuous bounded playback,
  and Replay-only archive export/import. Broader failure/retention cases remain.
- November 4 original concession archive, pre-result QTE, neutral result
  withdrawal, reverse scrubbing and installed-data preservation. That specific
  replay ending is accepted; missing terminal source evidence remains D01.

Older October 20/23 inventory paragraphs and unchecked release bullets are dated
snapshots. For example, active-type normalization is repaired, centered single
controls are implemented, and the QTE progress crop now has an OCR consumer;
their remaining physical coverage is listed above rather than treating those
components as absent.

## Suggested order without restarting every old script

1. Keep recording the next ordinary ending through its result and check the
   possible cry (B01/B02). Inspect the existing terminal interval first (D01).
2. On matched phone builds, one short isolated-attacker session can cover counted
   moves/idle/stutter, a needed reference and a finishing Fast Move. A second
   ending can exercise a finishing charged move. State what was actually done.
3. Finish clean device packs and a checked working profile before another large
   location/QTE inventory. Reuse good source recordings already retained.
4. Implement the independent early phase detector and source-backed terminal
   capture fix, then collect held-out acceptance battles rather than repeatedly
   tuning and validating on the same recording.
5. Run lifecycle/storage/Comms/usability/performance checks separately; keep
   destructive failure scenarios isolated from the user's data.

Moondrop has the November 4 ending build installed. The latest Pixel build was
not verified during this inventory; align versions before paired acceptance.
Tablet physical acceptance is still pending. TCL is optional expansion.
