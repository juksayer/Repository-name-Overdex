# Registration field boxes and new archive review — October 11, 2026

Checkout: `/home/sean/AndroidStudioProjects/Overdex`, branch `odx-recovery`.
Device: Moondrop MD_PH_001, serial `MDPH00124112500414`, 1080 × 2400.

## Registration behavior

- One detail layout, with 16 field boxes. No Summary/Moves template switching.
- One Fast Move Row; two independently addressed charged-move rows.
- Existing normalized positions remain authoritative. An explicitly saved
  FastMoveRow wins over SummaryFastMove; old preference keys are retained.
- Scroll GO between the boxes, then tap the desired field. That request reads only
  its crop. It does not OCR the whole screen or require unrelated page anchors.
- The service removes all guide windows and hides its control panel before admitting
  a fresh frame. Cancelled, timed-out, stopped and replaced requests cannot update a
  new draft. Returning to Overdex hides the registration windows.
- USE READING explicitly accepts the result. Confirmed fields persist in one UUID
  draft across scrolling; field corrections supersede earlier readings. A bounded
  history retains old readings without evicting still-current fields.
- SAVE CARD finishes the draft explicitly. Optional status/size fields can be added
  before saving. Shiny, favorite and gender icons use user confirmation, not a new
  visual classifier. HP, gender and headings remain in scan evidence; the existing
  owned-card schema determines which fields appear on the card.
- CP, HP, candy, type labels, evolution, move heading, moves and move-list end have
  distinct roles. Automatic layout learning/following is not implemented.
- Screenshot READ THIS BOX uses the same field parser; whole-page screenshot
  reading remains an explicit, separately reviewed action. GlassShield is unchanged.

## New match archives inspected

These are user recordings, separate from the previous turn's 11:42 menu-only test.
Both ZIPs pass the archive inspector's structural and manifest-count checks.

| Archive | Articles | GO offset | Individual Fast Move uses | HP measurements |
| --- | ---: | ---: | ---: | --- |
| `2026-10-11_12-24-13.odxmatch.zip` | 1,549 | +275.01 s | 6 | 38, all opponent |
| `2026-10-11_12-31-54.odxmatch.zip` | 1,490 | +27.20 s | 1 | 19, all opponent |

Both contain live species testimony for Sneasel, Sealeo and Vaporeon. The first
also records player Greninja then Lucario; the second records Turtonator then
Camerupt. The second identifies Sneasel's Ice Shard at +32.03 s from bar-motion
cadence. Vaporeon's Water Gun identity comes from its reference movepool, not a
demonstrated complete count of uses. Neither records MatchEnded. The second
records a Sealeo faint at +71.88 s, after Vaporeon's identity at +61.40 s; faint
timing still needs investigation. No battle inference changes are included here.

## Validation boundary

- Initial full unit suite: 508 tests, zero failures/errors/skips. Debug and instrumentation APK builds passed.
- Initial installed APK SHA-256: `be326fb627c71f94f0453045452d38695f894978282b3f30cc6d41181c21055c`.
- Seven physical-device instrumentation checks passed: isolated screenshot OCR,
  legacy fast-move calibration migration, shared live/screenshot geometry,
  binder/team persistence and archive-index behavior.
- Capture templates, battle calibration, selected calibration profile, HUD layout
  and current team were byte-identical before and after both installations.
- The initial install was checked while the phone was locked. The unlocked follow-up
  below verifies live scrolling and field reads; no test card was saved.

Logs, read-only archive copies and preference backups are in
`/tmp/overdex-registration-20261011/`; none are application assets.

## Unlocked Moondrop follow-up

- The user's live confirmations recorded Garchomp, CP 4447, Gible candy, Dragon Tail,
  Earth Power and Twister. Subsequent Mewtwo readings included CP 4364, Mewtwo candy,
  Psycho Cut, Psystrike and Shadow Ball. These came from selected field crops at
  672 × 1493 capture resolution with the saved 1080 × 2400 normalized layout.
- Independently tapped CP on Garchomp's page: OCR returned 4447. Cancelled that
  reading. Scrolling at the right edge moved GO's detail page beneath stationary
  guides. Returning to Overdex hid all guides and exposed STOP SCAN; stopping
  released MediaProjection. Capture-template preferences remained byte-identical.
- This check exposed a draft transition problem: the two Pokémon's fields could
  replace one another inside the same unsaved card. The UI did not offer NEXT
  POKÉMON until saving or a whole-page boundary conflict.
- NEXT POKÉMON is now offered for unfinished scans, with confirmation. The old
  unfinished draft is retained internally by UUID; it is not inserted as an owned
  card. A changed name, CP or candy reading offers UPDATE THIS CARD or NEW POKÉMON.
  A recognized species name from a different candy family also blocks saving until
  corrected or explicitly confirmed as a nickname through species selection.
- Guide windows stay hidden during field review, preventing overlapping guides from
  covering the panel's confirmation controls. They return on cancel/accept, with
  the control panel restored above them. Its composition is recreated on window
  reattachment, with expansion retained in service state; retaining the old window
  composition had left the controls unresponsive during validation.
- Final unit suite: 510 tests, zero failures/errors/skips. Debug and instrumentation
  APK builds passed. Fixed a flaky serialization test to use the production
  `encodeDefaults = true` setting rather than omit a wall-clock default timestamp.
- Final installed APK SHA-256:
  `ee6100947a4a0e68b2515bfc4ecde7db0e809fb2398ce2be48fb3d7b0e8ef514`.
- On the rebuilt phone app, the existing mixed Garchomp/Mewtwo draft is flagged and
  SAVE CARD is unavailable. The draft, capture templates, battle calibration,
  selected profile, current team, HUD layout and live guide layout were all
  byte-identical before/after installation.
- Repeated the live CP read with the changed-header guard: 4447 offered UPDATE
  THIS CARD and NEW POKÉMON; no guide windows were present during review. Cancel
  restored the guides and did not mutate the draft. Unrelated collection-screen
  text in the CP box was rejected as "No clear CP".
- On the final build, PROGRESS expanded after reattachment and accessibility window
  order placed the panel above all guides. NEXT POKÉMON was visible on the unfinished
  draft. No new draft or owned card was deliberately committed during validation;
  finishing one clean registration remains a user acceptance step.

Follow-up logs and backups: `/tmp/overdex-registration-live-20261011/`.
The temporary shell UI inspector reads accessibility windows without restarting
Overdex or disabling secure capture flags. Screenshot black masks are expected
for secure overlay windows and are not the guide appearance on the physical phone.
