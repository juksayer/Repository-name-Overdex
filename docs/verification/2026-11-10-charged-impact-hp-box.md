# Charged-impact recipient HP calibration — November 10, 2026

The user requested an independent box for the receiving Pokémon's HP bar after
a charged move, when the camera brings that Pokémon and bar toward the center.
The purpose is to retain measurable geometry and literal fill-color changes
without moving the normal player/opponent battlefield HP territories.

## Implemented behavior

- **Charged Impact HP** is the appended, fortieth Match Calibration choice.
  **A** advances choices; **SELECT** switches position/size. The existing drag,
  arrow adjustment, source-space labels and immediate save behavior apply.
- `chargedImpactHpRegion` starts at normalized `.25, .24, .50, .38`, or
  X 270, Y 576, W 540, H 912 on a whole 1080 × 2400 capture. It is deliberately
  search territory across camera/sprite heights, not an asserted bar rectangle.
  Tighten it using actual receiving-pose screenshots from the same device/layout.
- Preferences, named profiles and working checkpoints store this field
  independently. Old profiles fall back only for the missing field; loading
  does not rewrite existing boxes or profile files.
- The existing charged-impact reader now uses the calibrated territory for
  acquisition and bounded follow-up. Missing/clipped/invalid territory does not
  silently invoke the former fixed search. Source-frame geometry, recent
  side-owned size reference, preparation cues, overlay exclusion and ambiguity
  checks remain required.
- Existing attention limits remain: acquisition at most every 100 ms after the
  preparation delay, then a small rectangle each delivered frame after a real
  outline is found. The phase expires and terminal zero consumes its window.
  The three-second tracking cap after first acquisition remains unchanged;
  very early receiver visibility before a later impact needs further live timing
  evidence. This is not unrestricted HP searching throughout battle.
- Sparse `ChargedImpactHpCrop` pixels are preserved at most once per 500 ms
  inside that same phase window, without additional whole-frame encoding. These
  bounded raw crops survive compact saving even when recognition is unavailable.
- Literal `GREEN`, `YELLOW`, `RED` or `EMPTY` fill color accompanies a successful
  narrow HP reading. Ordinary unsampled color stays null. The archive field is
  optional, so older recordings decode without an invented color.
- Centered charged-camera samples remain unavailable to Fast Move detectors.
  A missing bar, flash, shield tint or interrupted fill does not establish zero,
  damage, a Fast Move or a faint. A clear empty outline still needs paired edges,
  both caps and a clean dark interior.
- Directory inspection has a separate eighth charged-impact crop slot. Its
  exact-frame charged geometry verifies only that slot, independently of normal
  side HP. The Directory's complete-image editor still uses its prior ordinary
  battlefield source; use Match Calibration with a charged-pose screenshot to
  position this new box. No complete-frame provenance rule was relaxed.

## New Moondrop evidence

Read-only copies of both November 10 recordings and the late November 9 record
were retained before any update. The two November 10 archives pass full artifact
hash verification. The 07:57 Rocket record has 2,167 Articles/195 artifacts and
visible YOU WIN pixels but lacks accepted Match Start/End; the 08:07 record has
1,791 Articles/148 artifacts and accepted Match Start/End with WIN. This box does
not repair the first record's separate lifecycle gap.

One retained 672 × 1493 frame shows receiving Melmetal blocking Taillow's Aerial
Ace, with the bar approximately `(265,438)–(458,458)`, above the prior implicit
Y `.34` search start. Its SHA-256 is
`072e1b4bdd7bb314fd5096170d8283b3dc9cf996d7da0d7413765a4e5c9105f8`.
Its filesystem time supplies approximate ordering only; no exact archived crop
clock was established for that image. The shield tint makes it a geometry
reference, not proof of an unobscured HP color transition or opponent-recipient
coverage.

An ordinary Melmetal source frame has exact simultaneous crop/HP testimony at
monotonic `251895789221782`, bar `(47,882)–(241,899)`, fraction `.8842105`.
Source Articles, original frames, archive hashes and scoped calibration backups
remain private under
`/home/sean/overdex-device-backups/2026-11-10-charged-impact-calibration/`.

## Validation

The 183 focused unit checks and full 2,317-test debug unit suite pass with zero
failures, errors or skips. Debug app and Android test packages build successfully.
All 19 distinct targeted API 34 emulator cases have passing final results:

- configurable higher-bar acquisition, literal green/red/empty source-time
  progression, and rejection outside a moved box;
- independent charged and terminal readers, single-frame zero retention,
  source/cue/archive provenance and no invented Fast Moves/faints;
- previously reviewed real Pixel empty-bar pixels at native/delivered sizes;
- eight-slot Directory rendering, original colors, exact historical crop
  restoration, and compact archive restoration without loose source artifacts;
- actual Match Calibration selection, move/resize/source-coordinate labels,
  independent reload, and exact preservation of every other calibration field.

Seventeen cases passed in the first 19-case run. Two test assumptions were
corrected: one still expected seven total preview slots, and the new screen
driver pressed A again before its LCD callback reflected the previous selection.
The revised test awaits each real selection update; exact saved geometry and
whole-profile equality remain required. Both corrected cases pass in their
subsequent targeted runs. No rendering or calibration assertion was relaxed.

The tested production APK was installed with an update install on **Moondrop
MD_PH_001** (`MDPH00124112500414`), after confirming no Droidball service was
running. Launch returned `Status: ok`. Installed APK bytes match the tested APK:
`77d4571c02d09e0f2f0715e4f914781d61b112751c256593dc613052ad6ce9b3`.
All 21 scoped backup files remain byte-identical after installation and launch:
13 profiles, four calibration/capture preference files, the latest calibration
reference, and the three copied new match archives. No uninstall, data clear,
instrumentation on the physical phone, commit or push occurred.

The new box is ready for user placement. Live unshielded damage color transitions
and brief empty-bar capture remain acceptance checks on new recordings after
placement; emulator success is not a real-battle accuracy result. The private
validation summary records every native attempt and its final per-case outcome.
