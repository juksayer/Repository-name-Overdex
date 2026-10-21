# Finishing charge, KO pose, and coordinate-strip review — October 13, 2026

## Recordings

Inspected both new Moondrop archives without modifying the device originals:

- `2026-10-13_20-01-56_a49bdc77-367a-44f5-b654-8a967d8a135c.odxmatch.zip`: 3,166 Articles, 112.87 seconds. GO is 36.755 seconds into the recording.
- `2026-10-13_19-43-41_2e9d4224-0e63-456c-a7dc-7f59fbc67b4c.odxmatch.zip`: 2,679 Articles, 115.99 seconds. GO is 28.293 seconds into the recording.

The newer recording accepts Dragon Pulse at GO +17.329 seconds and Rock Tomb at +42.611 seconds. Earth Power is readable on the right button at +48.304 seconds, but no accepted contact/announcement establishes that it was the final move used. At +57.386 seconds the preserved announcement crop contains the lower portion of GREAT and HUD position text. Accepted OCR is empty. A win is observed at +65.669 seconds and corroborated by result music.

The 35 published announcement readings have median capture-to-publication delay 2,218 ms and maximum 10,624 ms. These are total path delays, not isolated ML Kit execution measurements. The archive may omit unreferenced crops, so its sparse image list is not itself proof of the live capture frequency.

Both active type crops overlap Android's Pointer location strip: the developer text is visibly superimposed on the glyphs. The matcher nevertheless published inconsistent types. This is occlusion, not a reason to shift saved calibration coordinates.

Local evidence, build/test logs, and screenshots: `/tmp/overdex-final-charge-20261013/`.

## Changes

- Type crop capture is unavailable while Pointer location is enabled and its full-width strip intersects that calibrated type region. The coordinate OCR crop covers only the left three cells; its narrower width is not used as the obstruction width. The setting is refreshed during a session. Calibration and Android's setting are not rewritten.
- Announcement OCR has a transient priority between urgent identity/result work and routine labels. Bounded scheduling allows all lanes to progress. One warm recognizer remains shared.
- A completed, witnessed charge can finish its presentation after a result arrives. Result observations cannot authorize a new move or complete an unresolved QTE.
- When a faint/result falls within an incoming charge's presentation, the recipient's pose waits for that projectile's impact. The original faint/result timestamp is retained separately. Confirmed-faint HP reaches zero at impact; a result alone still does not invent zero HP or a KO cause.
- KO presentation staggers and fades colour for 360 ms, holds upright for 1 second, then falls over 420 ms. No eye crosses. Pause/reverse scrub sample the same deterministic poses.
- Ending padding permits the finishing projectile and fall to complete even when the last archived Article is the result.

## Validation

- All 707 JVM tests passed (zero failures/errors/skips); debug and instrumentation APK builds succeeded.
- Boundary tests cover a named Earth Power followed immediately by faint/result, HP/impact ordering, original archive preservation, reverse scrubbing, post-result rejection, and the three OCR scheduling lanes.
- A trimmed fixture from the new recording retains original IDs/payloads with GO as the clock origin. It checks that recorded Rock Tomb remains usable and an Earth Power button label alone is not silently turned into a performed move.
- Moondrop device OCR test reads the preserved Rock Tomb crop through the transient lane. The rendering test uses an explicitly synthetic accepted-charge boundary fixture, not fabricated repair of the new recording.
- Both device checks passed. The first render-test run expected a numerical HP accessibility label despite the fixture having no HP measurement; its expectation was corrected to the existing honest `Full HP assumed on first entry` label. The rerun passed. Impact, gray upright hold, and fallen-sprite screenshots were visually inspected.
- Installed debug APK SHA-256: `432be322bf5e3f0331b69abb72fa4583feeefb555163d0822342333837e42285`. Installation occurred with MediaProjection inactive. All 11 calibration/HUD files matched their pre-install SHA-256 hashes. The final read of Android's Pointer location setting returned `0` (off), having been `1` during the archive review; no tool command or code change in this work wrote that setting. Overdex was relaunched normally after the tests.

## Remaining live boundary

This build does not recover the unobserved Earth Power from the old archive. A new live battle is needed to measure announcement latency and final-move capture under load. Turning off **Developer options → Pointer location** removes the physical obstruction; ADB's privileged system-log view is not a normal standalone input source for an installed Android app.
