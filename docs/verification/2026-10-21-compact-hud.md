# Compact battle HUD and implementation priorities

October 21, 2026, canonical checkout on `odx-recovery`.

## Observable result

- The live battle HUD no longer prints species names under opponent sprites.
  The native GO badge supplies the active name; accessible sprite descriptions
  retain identity and faint state.
- Each opponent card is 44 dp high instead of 48 dp. The image area is 42 dp
  instead of 38 dp, constrained horizontally by the equal-width three-card row.
- A cached Coil transformation removes only fully transparent outer pixels from
  the displayed sprite, then fits its proportions into the card. Original PNGs
  remain unchanged. For example, Sneasel occupies only 53 × 48 pixels of its
  96 × 96 source; sizing the whole file left much of the card unused.
- Local asset resolution/image requests are remembered while species identity
  remains unchanged. Trimming runs through Coil's image pipeline/cache rather
  than on every presentation update.
- Existing outer padding, GO badge seam/shape, faint marks, move labels/checks,
  estimated-energy notation and deliberate long-press dragging are retained.
  Unknown or unavailable sprites have a question-mark fallback.
- The [battle priorities](../plans/battle-priorities.md) are now the active ordered
  backlog, linked from the architecture map and feature inventory. Species/form/
  side correctness and measured Fast Move/energy reliability precede full 3D
  recognition, broad rankings and unrelated cosmetic work.

## Checks completed

- Full unit suite: **990 tests, zero failures/errors/skips**.
- `:app:assembleDebug` and `:app:assembleDebugAndroidTest`: successful.
- Installed the final app on the Moondrop MD_PH_001, Android API 34, density 480.
  There was no active Droidball capture session before replacement. Installation
  used `adb install -r`; app data was not cleared or uninstalled.
- Existing `HudAnnouncementReadDeviceTest` passed on the phone after the final
  image-trim change. It checks badge-gated visibility, absent species/utility
  text, accessible sprite sizing, right-aligned compact moves, estimated-energy
  text, no accidental swipe movement, and long-press dragging from both the move
  list and sprite. Its expected sprite measurement was updated for the new layout.
- Visually inspected the Android-captured final screenshot. Sneasel fills the
  card, its proportions are preserved, and the two unknown slots and move list
  remain readable. The screenshot uses a controlled plain backdrop, not a live
  Pokémon GO battle.
- Launched the normal Overdex MainActivity after the device test.

## Data preservation

A complete private-app archive was copied before instrumentation and fully read
back while hashing its files. After the first test, all **240 protected files**
(preferences, databases, calibrations, battle records and replay projects/sources)
matched their original hashes. All **33,176 files under `files/`** remained present
with unchanged lengths.

After the final installation, test and normal app launch, calibration/team/owned
Pokémon/replay records still matched. Changes were confined to Firebase heartbeat,
authentication and Firestore cache files. No original app files were missing or
resized. This metadata check is not a fresh content hash of every large image or
match archive; the protected-record comparisons do use content hashes.

Verification artifacts are ignored under `build/verification/2026-10-21-hud/`:
build/device logs, initial/final preservation reports, original file hashes,
protected-state snapshots and phone screenshots. The temporary 3.5 GiB full
private-data safety copy was removed only after the preservation checks passed,
returning the space to the nearly full computer. The small protected-state
snapshot and original file hashes remain. No match data on the phone was deleted.

## Remaining live boundary

No new battle was played during this task. Confirm the compact panel's readability
against the native team badge, the fainted-card appearance and actual OCR results
in the next match. The unchanged geometry tests and controlled overlay check do
not establish live badge alignment, faster recognition, or Fast Move accuracy.
