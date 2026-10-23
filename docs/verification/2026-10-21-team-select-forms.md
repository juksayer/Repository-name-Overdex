# Team Select form preservation

October 21, 2026, `odx-recovery`. This is the first bounded implementation from
the [asset inventory](../reference/pokemon-go-reference-assets.md), focused on
retaining form evidence and selecting the right battle reference.

## Behavior changed

- The Team Select sprite matcher now checks the closest competing form of the
  same species as well as competing species. Shiny/gender artwork variants of
  the same form are grouped together. A tied/insufficiently separated form is
  rejected rather than asserted. The score remains a heuristic, not a measured
  probability of correctness.
- Accepted sprite variants resolve against the actual Game Master form name.
  Regional aliases include Alola/Alolan, Galarian, Hisuian, and Paldea/Paldean;
  other exact matching form labels, including Gourgeist sizes, can resolve too.
  Unknown tokens do not silently resolve to the ordinary form.
- Form-specific repository lookups retain their full name. Unqualified names
  and shared Pokédex numbers do not invent the first catalogue form's label.
- Roster testimony and archive records retain the matched sprite variant and
  form-separation score. Older records without those fields still deserialize.
  Later bare-name OCR cannot erase an already witnessed form from the same scan.
  The original OCR and source-crop testimony remain separate and unchanged.
- Pre-flight's existing team/move editor receives the form-specific name and
  its legal moves. This does not redesign assisted registration or migrate
  owned-Pokémon database records.
- Fast Move lookup prefers an explicit form name over a shared Pokédex ID.
  A unique player roster/configuration can refine a bare player badge for its
  reference lookup; it cannot set the mirror opponent's form. Form evidence is
  included in derived move-reference support. Missing explicit-form knowledge
  does not fall back to another form's move pool.
- The player HUD type lookup and Replay's player type display use the retained
  player form. Archive roster reconstruction keeps forms through bare OCR and
  clears stale observations when a scan is restarted.
- The inferred lead retains GO's bare battle label while citing the full roster
  identity. This prevents a later bare badge from looking like a new switch.

## Validation

- Focused checks cover tied forms, shiny/gender duplicates, actual bundled
  Raichu reference descriptors, real Game Master types/moves, repository
  conversion, archive round trips and old-record compatibility, mirror-side
  isolation, and rescan behavior.
- An older test required a form missing from reference knowledge to borrow the
  shared number's move pool. It was replaced with exact-form lookup and explicit
  rejection tests; this is the failure mode being fixed.
- Final full unit suite: **1,004 tests, zero failures/errors/skips**.
- Final debug APK build passed. Log: `/tmp/overdex-form-final.log`.
- Installed with `adb install -r` on the Moondrop, serial
  `MDPH00124112500414`, after confirming no active Droidball service. Android
  reported success and package update time October 21, 2026, 13:05:28. Opened
  MainActivity; no AndroidRuntime error appeared in the immediate launch check.
  This was an update/launch check, not an instrumentation run or a live scan.
- Matching a reference to its own catalog verifies wiring/coverage, not
  recognition accuracy on a phone. The first real-phone result is below.

## Live Team Select result

The user's October 21 Moondrop screenshot (status-bar time 13:25) shows the
correct proposed roster: **Raichu (Alolan), Raichu, Obstagoon**, in that order.
The first card is shiny Alolan Raichu; GO labels both Raichu cards simply
"Raichu." The user reported **"it was instant"**, then confirmed **"moves are
also correct."** This validates the displayed forms and move lists for this
specific real-phone roster. Response time was user-observed, not measured in
milliseconds; the screenshot alone does not identify which witness supplied
each accepted name.

Source: [user's Team Select screenshot](/tmp/codex-clipboard-c095deb1-8c42-4eaa-b6e1-5b16bd39cd4c.png).
The subsequent slot-swap/rescan result is recorded below. Other devices and
live battle form retention remain separate acceptance checks.

After the screenshot fix, the user supplied two visible editor captures at
13:34 and 13:35. [Slot 1, Alolan Raichu](/tmp/codex-clipboard-630f206b-9e30-4535-ac6c-8b700e1c1a6b.png)
shows Spark, Volt Switch and Thunder Shock, followed by the charged-move section
with Psychic, Thunder Punch and Wild Charge. [Slot 2, ordinary Raichu](/tmp/codex-clipboard-cb1bc3b3-dda0-4a45-b3a2-f689f94e3baf.png)
shows Spark, Thunder Shock, Volt Switch and Charm. Only the top of the ordinary
form's charged list is visible, so the screenshot is not a full-list audit.
This confirms that the editor retains the distinct form references when changing
the selected slot. Both screenshots show unselected moves and `0/3 ready`; they
do not establish the owned Pokémon's actual moves or team application.

The next [Team Select capture, 13:40](/tmp/codex-clipboard-2de657df-7bfe-4a2e-8398-4a072cb40be7.png)
shows the requested swap: ordinary Raichu is now first, shiny Alolan Raichu
second, and Obstagoon remains third. The proposed roster matches that order.
The [editor capture, 13:41](/tmp/codex-clipboard-f715879e-a375-4e42-83c4-ef776d261db7.png)
retains that order and shows ordinary Raichu's Fast Move list, including Charm,
in slot 1. This passes the slot-swap/rescan acceptance case and demonstrates
that the new first slot did not retain the previous Alolan move list. The new
second slot's move list is not expanded in these captures. Team application,
frame-publication timing and live battle retention are not established by them.

The subsequent Team & Moves screenshot was a black rectangle because opening
the editor enabled Android's `FLAG_SECURE`. The screenshot fix removes that
flag from the field editor; `fieldEditorBlocksCapture`, the replay-cache reset,
and the 250 ms closing grace period still keep editor text out of visual battle
observation. See [screenshot verification](2026-10-21-team-editor-screenshots.md).

## Moondrop acceptance steps

The user has both ordinary and Alolan Raichu available.

1. After installing the update, open GO's Team Select screen with ordinary
   Raichu in the leftmost slot. Keep the two other slots unchanged throughout.
2. Use **SCAN TEAM SELECT** in the pre-battle overlay. If the team editor is
   open, **RESCAN** performs the same action. Leave the team screen still until
   the roster is proposed. Note whether the first slot says **Raichu**.
3. Replace only the lead with Alolan Raichu. Request another scan before starting
   a battle. The first slot should say **Raichu (Alolan)**.
4. Confirm/open **TEAM / MOVES**. For Alolan Raichu the current bundled reference
   should offer Spark, Volt Switch, and Thunder Shock, with no Charm. This is a
   list of legal candidates; it does not establish which move this owned Pokémon
   actually has. Do not use a TM for this test.
5. Report the displayed label for each scan and any failure to finish or any
   normal/form label that changes after it first appears. Leave a failed result
   available for inspection before manually correcting it.

A battle is optional after these two scans. A later battle can check Electric
and Psychic type icons for Alolan Raichu and the actual configured Fast Move.
Form-specific sprite artwork still uses the existing species-number provider;
judge this first test by the roster label, type data and move list, not artwork.

## Remaining boundaries

- Validate margins on held-out, real card captures, including nicknames, shiny
  variants, partial crops, and another device. Unknown/occluded form remains a
  manual confirmation case.
- Opponent form resolution from badge/type/cry evidence, fully form-aware owned
  cards and sprites, and all delayed identity revisions remain broader backlog
  work. This slice does not claim those are complete.
- No calibration coordinate, source artwork, or raw historical observation was
  changed. No new audio/image reference files were bundled.
