# Automatic Droidball stop on lock and screen-off — October 27, 2026

## Behavior

An active Droidball session ends when Android reports both noninteractive power
state and a showing keyguard. The service checks at startup and after screen
broadcasts. If keyguard state arrives after screen-off, it rechecks every 500 ms
while noninteractive. Awake state cancels that recheck. No wake lock is acquired.
Swipe-only locks count; a device configured without a keyguard does not satisfy
the lock condition. Ambient/dozing display is noninteractive. Android may defer
a delayed recheck during deep sleep.

Projection revocation also uses the complete graceful session-stop path. Screen
broadcasts and projection callbacks share a one-shot controller. Teardown closes
that controller and unregisters the receiver, so a queued callback cannot stop a
later session. Unlocking/waking never automatically restarts capture.

The stop path ends observers and session ownership, clears active state, releases
the service's projection/audio/overlay resources, and leaves the idle notification
with Start and Overdex actions. It submits an honest `DROIDBALL_LIFECYCLE` raw
record for the automatic reason. It does not invent a battle result or an explicit
user Stop action.

Before taking the archive snapshot, automatic shutdown awaits accepted testimony
and witness availability on both Match publication lanes, including inline
derivations. Capture cleanup does not wait for disk export. A 30-second drain
timeout follows the existing archive-failure path and retains the field ledger
and existing checkpoint instead of publishing an incomplete new snapshot. An
already-saved result refreshes the same archive with final accepted evidence;
it does not add another Match or summary handoff.
After successful export, the ended Match's private worker is released. Its
Timeline remains available to summary and ledger readers. Failed exports retain
that worker and the existing recovery evidence.

Platform semantics were checked against official Android documentation:
[PowerManager.isInteractive](https://developer.android.com/reference/android/os/PowerManager#isInteractive()),
[KeyguardManager.isKeyguardLocked](https://developer.android.com/reference/android/app/KeyguardManager#isKeyguardLocked()),
[screen-off broadcasts](https://developer.android.com/reference/android/content/Intent#ACTION_SCREEN_OFF),
and [projection automatic stopping](https://developer.android.com/media/grow/media-projection#status_bar_chip_and_auto_stop).

## Automated verification

- 13 focused tests passed: nine stop-policy/order/idempotence regressions and
  four publication-drain regressions.
- Full unit suite: **1,205 tests, 201 suites**, no failures, errors, or skipped
  tests. Debug APK build succeeded.
- Drain tests preserve a burst of 128 raw records, automatic-stop reason, offline
  availability, timestamps, confidence, and evidence references; they also wait
  for suspended interpretation and preserve derived lineage.
- `git diff --check` passed.

## Physical Pixel 4 check

Device `99261FFAZ0017J`, Android 13. Started normal Droidball capture through the
app's pre-flight screen and Android's screen-sharing consent, then returned to
the launcher. This was a lifecycle test, not a Pokemon GO battle.

1. Confirmed service and MediaProjection active. Frame-delivery logs confirm
   source frames were published before locking.
2. Sent the ordinary sleep key. Service and projection were absent **2.276 seconds
   after the command**. Lifecycle log records `SCREEN_OFF_AND_LOCKED` once.
3. `onDestroy` completed. Final frame-delivery summary has zero subscribers;
   Android reports no active recording configuration/audio policy.
4. A completed archive containing an intentionally incomplete battle record was
   saved once: `2026-10-27_12-23-02_cbe3f828-f25d-41cb-9ee4-8070ca16bcf6.odxmatch.zip`.
   Manifest and Timeline both contain **199 Articles**, including one automatic
   stop reason. There is no MatchStarted, MatchEnded, or StoppedByUser testimony.
   The archive inspector passed; no media artifacts were declared for this
   launcher-only test.
5. Woke the phone, then repeated sleep/wake. Droidball remained stopped and no
   second archive appeared. Notification 197 is idle and exposes only Start and
   Overdex, with no ongoing-service flag.
6. Calibration-profile selection, battle calibration, Team Select calibration,
   and HUD-layout preference hashes remain unchanged after the test.

This physical check used APK SHA-256
`79eedd4a1a67a7638db227106fe360b7f4510302c06cb1302bcc663724284b7e`.
The subsequent change only releases the Match worker after successful export.
The full 1,205-test suite and debug build passed again after that addition.

Final APK SHA-256
`9deac9262b95f3c2a229dfd501b72f804593efd6d10138c082fba3e737eb7255`
was installed on both the Pixel 4 and Moondrop `MDPH00124112500414`, with the
installed APK hashes verified. Both devices' four calibration/HUD preference
hashes are unchanged. The Pixel was locked after the earlier lifecycle test;
the final worker-release addition was not exercised in another physical session.
Moondrop's own lock transition was not exercised in this check.

Build/install receipts, device-state dumps, the incomplete test archive, and
preference hashes are retained outside Git under
`/home/sean/overdex-device-backups/2026-10-27-lock-stop-review/device-validation/`.
The two genuine new Moondrop matches are reviewed separately in
[the roster review](2026-10-27-moondrop-roster-review.md).
