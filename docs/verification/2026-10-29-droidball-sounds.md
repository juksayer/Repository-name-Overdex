# Droidball roll and right-edge audio — October 29, 2026

The user's requested Sonic rolling sample accompanies the visible rolling legs
of the existing journey. The supplied skid accompanies the arrival's braking;
the scan starts no new effect. Finishing the visible return to the right edge
plays the supplied Sonic pinball bumper. A late frame starts only
the currently active movement, without replaying expired cues. Each journey owns
its cues, preventing duplicate sounds across navigation composition or resizing.
An early Team Select/panel opening can still skip decorative motion immediately.

One application-context SoundPool uses two streams, media volume, modest per-effect
gain, no audio-focus request and `ALLOW_CAPTURE_BY_NONE`. Its async load callback
cannot restart a canceled roll. Unloaded impacts are dropped rather than played
late. All three supplied Sonic WAVs remain intact with source hashes; packaged
mono 22,050 Hz copies keep their speed and pitch. Runtime audio totals 134,168 bytes.
`tools/build_droidball_sounds.py` regenerates all three packaged files.

The shared drawing owner supplies sound timing. Phase changes, removed renderers,
hidden field windows and a stopped shell Activity cancel loops. A resumed docking
body can rearm the same interval. Capture and Stop do not wait for sound effects.

## Verification

- Full unit suite: **1,411 tests / 224 suites**, no failures, errors or skips.
  Seventeen new cue tests cover both observer orders, repeated/stale frames,
  scan silence, interrupted/skipped arrivals, impact deduplication, hidden-owner
  cancellation, visible resume, and witnessed/expired/deduplicated braking.
  Debug app and Android-test APKs build.
- Wave files decode as mono PCM16 at 22,050 Hz: roll 2.019456 seconds, skid
  0.467891 seconds, impact 0.552018 seconds. Source hashes match the supplied originals.
- The generated `docs/verification/evidence/droidball-sounds-20261029/roll-skid-bumper-preview.wav`
  is an illustrative roll/skid/contact preview at runtime gains. It is
  not a recording from the phone or a full journey soundtrack.

## Pixel backend check

The Moondrop was disconnected. Pixel 4 (`99261FFAZ0017J`) had no active Droidball
session. Its editable state was saved before updating the app. An isolated
instrumentation check exercised roll/stop/impact/cancel without opening GO,
starting capture, navigating trainer screens or changing media volume.

The initial public active-player query did not find SoundPool. Android's
`PlaybackActivityMonitor` ignores SoundPool state events, so its public active
list cannot establish playback. The corrected test temporarily adopts the shell's
audio-routing inspection permission and checks the registered player's real
attributes; it drops that identity afterward. The app gains no permission.
Result: **OK (1 test)**, `USAGE_MEDIA`, `CONTENT_TYPE_SONIFICATION`,
`ALLOW_CAPTURE_BY_NONE`. This verifies Android's registered playback settings;
speaker audibility and subjective timing still need a full journey listening check.

Snapshots, hash verification and logs:
`/home/sean/overdex-device-backups/2026-10-29-droidball-sounds-pixel/`.
No active service remained after the check. This initial Pixel check preceded
the user's supplied skid and bumper; the later Moondrop check below covers those.

## Supplied assets and Moondrop launch check

The trainer then supplied `sonic_spin.wav`, `skid.wav` and
`sonic_pinball_bumper.wav`. These replace the earlier roll candidate and generated
impact; all three originals are retained unchanged in the source folder. Skid
starts at the first witnessed braking frame (39% of arrival). A missed/expired
braking interval does not produce a delayed skid.

The final app was installed on both Pixel 4 and Moondrop
(`MDPH00124112500414`), with neither running capture at installation. The isolated
Android settings test also passed on the Moondrop: **OK (1 test)**. A real launch
from Overdex through screen-share approval and into GO produced this order in the
live native-playback log:

- Departure roll accepted, followed by stop.
- Arrival roll accepted, followed by stop and one skid accepted.
- Rightward snap roll accepted, followed by stop and one edge impact accepted.

The three named playback calls all returned nonzero Android stream IDs. This
checks timing and native playback acceptance; subjective loudness remains a
listening judgment. The check returned to Overdex and stopped the session.
No battle was played; the navigation checks created prebattle test records.
Calibration/profile, HUD-layout and current-team hashes remained unchanged on
both phones (16 critical files on Moondrop, six on Pixel). The final service query
on Moondrop reports `(nothing)`. Pixel remains idle.

Final APK SHA-256:
`20c2ec3af17ee37dae96619e1cde36bb82b409ba8ad93d750081b3924ba72f9a`.
