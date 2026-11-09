# Charged-impact HP, announcement coverage, and late Rocket results

The November 9 Moondrop Rocket archive records the battle but no `MatchEnded`
before user Stop. The result itself is visible in retained source pixels. The
changes below repair acquisition/admission boundaries and preserve the distinction
between captured measurements, inferred battle events, and presentation.

## Original recording

Match `08fb4471-18ff-455f-8cd5-af57d7bd6a11` contains 2,849 Articles and 430
declared artifacts. The copied ZIP and all declared artifacts verify. The phone
ran APK SHA-256 `ede136390e57c0efc096e66cf8b3ca8dff19ed63bf88b6e6c8b154156a8eb3be`.
The complete 11,993,429-byte ZIP hashes to
`9462609e8667d4db9dc3fa4b104b9bf2647e7d7010f3585fca993938a4e682f1`.
Copy provenance is also retained in private `copy-provenance.json`.

A loose full frame shows `YOU WIN` wholly inside the saved outcome box
(100,627)–(568,888) on the 672 × 1493 source. Its file time is approximately
1794233061 seconds. Nearby sparse frames show landscape and then rewards.
These file times establish ordering approximately, not exact capture timestamps.
The normal fast result window expired approximately ten seconds before that
sample. A subsequent fallback request waited 30,938 ms for normal OCR admission
and selected newer blank scenery. Compact retention cannot establish whether
the two-second probe sampled the brief result in the first place.

The original ZIP, 96 content-hash-verified loose frames, structured findings,
native test fixtures, and pre-update private-data backup are retained under
`/home/sean/overdex-device-backups/2026-11-09-battle-capture-fixes/`.

## Announcement pixels and presentation

The factory announcement strip now covers normalized Y .18–.28, above the
independent QTE grade field. Older profiles migrate only when their rectangle
exactly equals the known former factory value. Custom coordinates and current
profiles remain authoritative. In particular, the Pixel's observed wrong custom
Y .316 strip is not silently overwritten; the absent Pixel needs a deliberate
profile correction while retaining its original profile/checkpoint.

Dedicated announcement/QTE OCR excludes small words within the captured HUD
footprint regardless of text color. Original OCR and per-word filtering decisions
remain available. Full-size game words beneath transparent presentation remain
eligible. The generic species/OCR filtering contract is unchanged.

An independent presentation observer briefly hides the HUD's lower move/status
lines during source-timed Get Ready, incoming attack, accepted QTE, and move-use
intervals. The roster stays visible. Expiry, new cues, and Stop are serialized so
an old timer cannot reveal labels during a newer charge. This observer emits no
battle testimony and does not depend on a successful HP lock.

## Charged-camera HP

The red-bar watch remains useful during ordinary battle. A charged move can
instead take green HP directly to empty while the camera moves the bar to the
center. A recent, same-appearance normal bar supplies size; independent Get Ready
or Attack Incoming testimony supplies recipient ownership. Neither missing pixels
nor central location alone establishes a side or a zero.

The new preparation window performs a bounded central search at 10 Hz. Once an
actual outline is acquired, its small rectangle is checked on delivered frames
for at most three seconds. A single clear empty outline is sufficient. Appearance,
dimensions, Ignore, result, and Stop invalidate the appropriate ownership state.
The normal locator cannot invalidate a phase-owned acquisition merely because it
relocates concurrently.

Raw HP retains original source clocks, source dimensions, geometry, explicit
`CHARGED_IMPACT_<SIDE>_HP_BAR_FRAME_WITNESS` provenance, and phase-cue Article IDs.
Camera samples and the first ordinary return reset the live Fast Move detectors.
Persisted vertical cadence and HP-effectiveness OCR also exclude these samples.
Inference, energy timing, and replay honor the exact charged-camera source times
without adding a future suppression tail that would erase an immediate genuine
post-charge Fast Move.

The clear bundled Pixel Magcargo empty-bar screenshot is a positive real-pixel
fixture. The private Earth Power frame `30ed911c…` is a negative boundary: its
outline is visible, but an attack beam and orange/yellow shoulders obscure the
interior. That frame remains unknown. Human knowledge of the knockout does not
authorize weakening the empty-bar verifier.

## Result selection

Sparse result references are retained in source order, at most 16 references and
eight seconds, and receive urgent native OCR admission even when the inexpensive
visual text heuristic is weak. A later blank or reward frame cannot replace a
retained earlier result merely because OCR is occupied.

Ordinary sparse checks remain two seconds apart. A final or exhausted team with
missing battlefield HP after a charged-move announcement can receive a bounded
500 ms late tail, ending at the earlier of charge +45 seconds or last battlefield
HP +60 seconds. Charged-camera HP can renew urgent/red attention but does not
masquerade as normal battlefield reappearance. A true normal return cancels the
tail. The existing four-per-second urgent window is unchanged.

Only actual native OCR followed by accepted WIN/EFFORT testimony establishes
`MatchEnded`. Scheduling cues do not create a winner, a finishing move, zero HP,
or a faint. Archived originals are not rewritten.

## Validation boundary

The final production source passed all 2,219 unit tests with no failures, errors,
or skips. The debug application and Android test APKs built successfully. The
application APK SHA-256 is
`1b00ff6e147431872dd08c35d53161b3cb838038f692bb0950ffc2008c3fe11b`.
The final Android test APK SHA-256 is
`7a636dfee5a3c20c74ad12190adc9a9a822c0b71e94ed238541a8b2af8b76440`.

Twenty-five distinct Android tests passed across disposable emulators:

- Thirteen HP checks cover the red-bar and charged-camera readers, one-frame
  empty measurements, a stalled normal locator, source/cue retention through
  Custody, Timeline and archive serialization, detector resets, and real Pixel
  screenshots at native and delivered sizes. Missing/obscured bars remain unknown.
- Four announcement checks recognize entry, Sludge, Hydro Cannon, and a private
  Rock Tomb frame containing the HUD. Full-size game text survives while small
  overlapping HUD labels are excluded.
- Four OCR filtering checks preserve the original readings, crop references,
  filtered output, and source timestamps through the live witness.
- Three result checks use actual native OCR of saved concession/Rocket pixels,
  including a weak result probe competing with newer rewards/routine OCR, and
  verify the source-timed accepted result chain.
- One rendered HUD check verifies that the native window shrinks/restores during
  charge protection while the roster's bounds and origin remain fixed.

The OCR and HP checks ran on API 37. The rendered HUD check ran on API 34 because
the current Espresso framework's InputManager reflection is incompatible with
API 37. An API 34 result attempt could not load its optional native OCR module;
the three actual OCR result checks passed on API 37. Initial test-only failures
were corrected without changing production behavior: recorded pixels can read
`swampbert` instead of `swampert`, and the existing live OCR test needed recent
monotonic source times rather than timestamps one/two seconds after boot. The
final announcement/OCR run reports `OK (8 tests)`; the HUD run reports
`OK (1 test)`. Raw OCR reports, logs, and the deduplicated 25-test inventory remain
in private `native-validation.json` and its referenced evidence files.

The tested application was installed on the Moondrop after confirming capture
was stopped and verifying a pre-update backup. All 412 backed-up private files
were byte-identical immediately after replacement and before first launch; the
loose artifact store was excluded from this private-file inventory. The installed
base APK hash matches the tested application above. Startup resumed MainActivity
without a fatal runtime entry, and capture remained stopped. The custom Moondrop
announcement rectangle also remained exactly unchanged after startup. Backup,
installation, startup, and calibration provenance are retained in the private
`pre-install/moondrop/` directory. No instrumentation ran on the phone.

The Pixel was not connected and was not updated. Its custom announcement profile
still needs the deliberate correction described above. Synthetic pipeline tests,
real retained-pixel tests, native OCR, rendered HUD checks, and phone startup prove
different things. Live finishing-hit capture and result latency still require a
new recording on the updated Moondrop; the original Rocket archive stays intact.
