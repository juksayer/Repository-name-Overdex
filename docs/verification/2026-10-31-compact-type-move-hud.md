# Compact move labels and type-colored HUD edges — October 31, 2026

## Behavior

The battle HUD selectively shortens long move phrases. Names of 12 characters
or fewer remain complete. Examples include Thunder Punch → T. Punch,
Water Shuriken → W. Shuriken, Weather Ball (Ice) → W. Ball (Ice), and
Power-Up Punch → P-Up Punch. Type qualifiers remain visible. Unknown long
single words stay complete instead of receiving a guessed acronym.

`BattleHudMoveLabels` resolves the combined displayed Fast/Charged pool and
restores full names when abbreviations would collide. The change is display-only:
canonical move names, legal pools, OCR vocabulary, inference, energy and archived
Articles are unchanged. Full names and types remain in accessibility descriptions.
Confirmation, hazard and low-confidence states remain separate from the label.

`BattleHudMoveText` uses each move's existing `PokemonType` color in a small
shadow behind a narrow white keyline. The foreground retains the established
teal Fast Move / violet Charged Move ink. This preserves the letter colors used
by the current HUD size/color/position OCR filter. Ready text remains bright red;
one-Fast-Move-away text remains amber. The type edge does not replace those cues.

The backing and foreground share one layout box; the backing supplies no text
semantics or extra row. Font size, line height, sprite cells, badge alignment,
transparent move area, hazard percentages, estimate marks and long-press dragging
are unchanged. No new icons, assets, retained images or capture readers were added
to the production HUD.

## Validation

- Six compact-label tests cover familiar labels, typed suffixes, collisions with
  other compact or already-short names, case/canonical preservation, and every
  one of the 1,724 checked-in species/form combined move pools.
- Focused label, geometry, presentation and OCR-filter checks passed: 51 tests.
- Full unit suite passed: 1,553 tests, zero failures/errors/skips. Debug and
  instrumentation APK builds passed.
- Moondrop MD-PH-001 (`MDPH00124112500414`), physical 1080 × 2400, was idle and
  unlocked before replacement installation. The tested debug APK SHA-256 is
  `8f16fc2fab82ff5ee2f7fed63ab93244e802fe12b5dfc21054bddfe30ab5a7ef`.
- The actual native overlay was rendered with all 18 type edges on light and
  dark backdrops. Canonical accessible names, abbreviated visible labels,
  confirmation/hazard marks, and red/amber readiness are exercised. This palette
  is a synthetic display fixture, not a legal 18-move combatant.
- Final physical instrumentation passed **OK (3 tests)**: the native overlay's
  transparency/spacing/drag check, all-type palette/readiness/accessibility check,
  and audio-favorite-versus-confirmation check. Newly rendered HUD OCR produced
  41 measured words on the light backdrop and 37 on the dark backdrop; all 78
  received the existing HUD size/color/position rejection. Installed APK hash
  matches the tested artifact. Overdex was reopened normally after the checks.
  The first run passed palette and audio-confidence checks; the old transparency
  fixture incorrectly opened the manual setup menu while expecting an empty
  waiting HUD. Its setup now accepts Team Select and sends an automatic battle
  cue, matching the current presentation contract. Production presentation was
  not changed to satisfy that stale fixture.

Private logs and phone screenshots are in
`/home/sean/overdex-device-backups/2026-10-31-compact-hud/`.
A fresh Pokémon GO match is still required to judge live legibility against
moving game scenery or any effect on recognition. Rendering and unit checks do
not establish live capture accuracy.

## New normal match reviewed before this build

Moondrop archive `9d820530-23f9-4424-bb56-10d7e9085a33` contains 3,369 Articles
and 462 artifacts. The desktop copy matches device SHA-256 and all artifacts
verify. The original archive was not edited. This was not a controlled-count
test and does not validate the new HUD styling.

The observed shadow Golbat / Greninja / Victreebel roster agrees with configured
slots. Greninja's Night Slash is both configured and independently announced;
no Hydro Cannon was recorded. Opponents were Muk, Hoopa and Wigglytuff, with Hoopa
returning. Wigglytuff's Charm has cadence, repeated audio and effectiveness
support. Hoopa remains unresolved, with late audio favoring Astonish while its
legal Confusion alternative lacks a reference.

YOU WIN was captured at session +159.186 s. The recent result reader reports
2.939 s capture-to-completion, including 1.027 s admission wait and 0.600 s native
OCR. MatchEnded's wall-time publication delay is about 2.913 s. There is no
recorded countdown glyph, GO or MatchStarted; session offsets are not a known
battle clock. The only retained countdown crop is still at Team Select, so it
cannot establish whether the menu covered the actual countdown.

Existing input gaps remain:

- The league reader reports Ultra, while the retained frame says Mega Halloween
  Cup: Great League Edition / CP 1,500. Its badge crop intersects title text.
- All 41 charged-button name reads miss the move: 40 empty and one bench CP.
  All 160 fill measurements are unusable (141 unknown rim, 19 unknown separator).
- Type capture is unavailable while the visible Pointer Location strip overlaps
  the type territory. No type-crop testimony was retained. This is missing input,
  not a demonstrated type-matcher error.
- There are 55 distinct observed-use IDs and no use credited by two energy
  Articles, but accounting remains incomplete. Golbat receives 96 energy credit
  despite three Poison Fangs costing 120; Greninja receives 14 despite Night Slash
  costing 35. Those charges constrain missing generation without supplying exact
  timestamps for additional attacks. Raw use totals are not ground truth.

Calibration and recognition changes remain separate from this display update.
