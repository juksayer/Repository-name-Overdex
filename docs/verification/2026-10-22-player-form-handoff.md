# Player form and active-slot handoff

October 22, 2026. Canonical checkout `/home/sean/AndroidStudioProjects/Overdex`,
branch `odx-recovery`. Existing unrelated work is preserved. No commit or push.

## Behavior

`PlayerActiveIdentityInference` binds each player appearance to the observed
Team Select lead, a unique compatible roster slot, or repeated independent
charge-button label OCR. A bare `Raichu` badge alone cannot select between two
Raichu forms. Repeated badges retain the appearance; a subsequent entry can open
an unresolved appearance until independent evidence selects its slot.

The latest Team Select sprite form survives subsequent bare name OCR. The lead
rule requires an actual scan, not just a saved slot-one configuration. Later
button reads use their source-frame time and the configuration at that time.
Duplicate delivery and cached text do not supply a second vote. A delayed badge
cannot restore the departed active battler. Historical label evidence can refine
its earlier interval without changing the currently active battler.

Match appends a separate `PlayerActiveIdentityResolved` Article with its basis,
appearance start, slot and supporting Article IDs. Original badge/cry/entry
Articles remain unchanged. This derived identity feeds the existing use, Fast
Move, energy, charge-announcement and player-information comparisons. No new
camera/audio/OCR reader is added. A bare charged announcement uses the active
form's legal pool after side attribution; opponent mirrors never borrow the
player's roster.

Player faint tracking keeps full form names distinct. The observed faint closes
its appearance, and a lingering badge does not revive the other same-name form.
Known returning forms retain their own move identity. Energy from delayed uses
is matched to the resolved appearance at the original hit time.

Replay uses the same appearance resolver for old recordings and reads new
identity Articles directly. Its Fast Move and charged-button lookups no longer
merge regional forms under a shared base name. Charged button icons, recorded
energy ownership, HP appearances and faint transitions follow that identity.
An unresolved mixed-form appearance does not inherit a prior form's configured
move or button loadout. This is read-only reconstruction, not an archive rewrite.

## Recorded regression

Source: Moondrop `2026-10-21_13-49-25_b020a891-34dd-4a53-b33c-468db958d329.odxmatch.zip`
under `/home/sean/overdex-device-backups/2026-10-21-raichu-review/`.
Original SHA-256: `7d527257750b09d04454ae9e82fb3b8a01b698e7e45ee716a535ffae2c69c66e`.

The unit fixture `app/src/test/resources/battle/2026-10-21-raichu-identity.json`
selects 107 original Articles (scan, configuration, labels, active species,
start, uses, spending and faint evidence), preserving their values, IDs and times.
It omits unrelated Articles/media; predecessor references can therefore refer
outside this focused fixture. The full original archive remains untouched.

The fixture now presents Alolan Raichu throughout the player's observed battle
and Spark for its supported Fast Move actions. The final pre-GO configuration is
used. Assertions verify that constructing and scrubbing Replay does not change
any source Article. Historical energy quantities remain recorded quantities;
this change corrects actor binding rather than independently recounting all old
attacks or replacing old energy calculations.

## Validation

- **1,041 JVM tests passed**, zero failures, errors or skips across 184 classes.
- Normal debug and Android-test APK builds succeeded. No emulator-only OCR
  dependency was added to the build.
- **One Android 14 emulator integration test passed** using the real Custody,
  Match dispatcher, bundled Pokémon reference data and move interpreters. It
  covers scanned Alolan lead → repeated bare badge → Trailblaze announcement →
  Obstagoon → ambiguous Raichu → repeated Brick Break labels → ordinary Raichu
  and Volt Switch → delayed old badge → Brick Break announcement.
- Focused tests also cover both lead orders, configured-only ambiguity, returning
  forms, independent energy balances, delayed uses, separate charged buttons,
  fainted forms, mirror-side isolation and archive round trips. The existing
  idle-match test now compares the base species for its side-attribution
  assertion; its recorded form suffix is preserved by the new reconstruction.
- Logs and final APK metadata: `/home/sean/overdex-device-backups/2026-10-22-player-form-handoff/`.

## Remaining acceptance boundary

No physical-phone installation or new live battle was performed for this slice.
The user requested emulator validation. A real match must still establish prompt
recognition through switches with both Raichu forms, and correct per-form move
and energy behavior under capture latency. This does not complete opponent-form
recognition, type-crop calibration, form-specific sprite artwork, or identity
among multiple indistinguishable owned copies of the same form. A same-name
switch with no entry/form evidence remains unresolved rather than guessed.
