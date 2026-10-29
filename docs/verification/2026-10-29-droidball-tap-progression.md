# Droidball tap progression — October 29, 2026

The Directory Tree's docked Droidball now responds progressively during each visit:

| Tap | Response |
| --- | --- |
| 1–2 | Original roughly 15-degree spring tilt, two blinks, return upright. |
| 3 | Stay upright and blink twice. |
| 4 | Stay upright and blink once. |
| 5 | Close eyes immediately, settle at 15 degrees, and ignore further taps. |

Tap count has no inactivity timeout. A tap during an unfinished response begins
the next response. Leaving the owning route, changing interaction eligibility or
starting a journey clears the local count and pose. The fifth response ends its
frame loop after settling; sleeping does not block any other instrument controls
or stop/start observation. The field body's existing controls remain separate.

`DroidballAcknowledgementMotion` provides the finite poses and durations.
`DroidballAcknowledgingBody` owns the local count, prevents queued sixth clicks,
and yields to travel. Its custom face/tilt feedback replaces the default clickable
indication, which otherwise briefly darkened the whole square on the Moondrop.
No animation-setting, journey-audio or capture-state changes are involved.

## Verification

- Full unit suite: **1,417 tests / 225 suites**, zero failures/errors/skips. Six
  focused motion tests cover the progression, blink boundaries, neutral endpoints
  and the persistent terminal pose.
- Debug app and Android-test APK builds passed.
- Installed on idle **Pixel 4** (`99261FFAZ0017J`) and **Moondrop**
  (`MDPH00124112500414`) after editable-state backups.
- Both phones returned **OK (2 tests)** from the actual `ODXFiShell` fixture.
  Captured pixels verified tilt, double and single blinks, upright third/fourth
  responses and exact rest. A sixth physical tap left the sleeping pose unchanged;
  A still invoked its callback; a presentation-only departure/reset restored the
  click action and resting pose. The existing cancellation test also passed.
- The blink check waits for the first rendered eyelid frame instead of assuming
  the same frame-start delay on both devices. Failure diagnostics preserve the
  resting/third-tap images and report changed-pixel bounds.
- This isolated test Activity can draw over the lock screen. It did not unlock
  either phone, open trainer screens, launch GO or start capture. Presentation
  journey state resets after each test. System animation settings were unchanged;
  no separate live check with animations disabled is claimed.
- All **6 Pixel / 16 Moondrop** calibration/profile, HUD-layout and current-team
  files retained their saved hashes. Neither phone had an active Overdex service
  after the checks.

Installed app APK SHA-256:
`e22df6c971d2b72f0c5e6449a630be49cbb635c583b8a13ae9017ddc27a4f289`.

Editable-state snapshots, build/device logs and hash manifests:

- `/home/sean/overdex-device-backups/2026-10-29-droidball-tap-progression-pixel/`
- `/home/sean/overdex-device-backups/2026-10-29-droidball-tap-progression-moondrop/`
