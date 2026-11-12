# Moondrop Replay interruption — November 11, 2026

The retained Android records identify an external process termination at
**16:18:49**, while Overdex was in the foreground. Google Play services was
being updated. This explains the app disappearing without an Overdex exception;
it does not establish that Replay's move/HP projection is correct.

## Evidence

Read-only evidence was collected from Moondrop `MDPH00124112500414` into
`/tmp/overdex-moondrop-1615-l1uca3ql/`. Times below are the device's displayed
log timestamps on November 11, 2026.

| Time | Android record |
| --- | --- |
| 16:18:48.928 | ActivityManager force-stops `com.google.android.gms` for `installPackageLI`. |
| 16:18:48.930 | PackageManager records an update of system package `com.google.android.gms`, retaining its data and replacing its code path. |
| 16:18:49.262 | ActivityManager kills foreground Overdex process `20694` with the explicit reason `stop com.google.android.gms due to installPackageLI`. |
| 16:18:49.263 | ApplicationExitInfo records reason `16 (PACKAGE UPDATED)`, with the same description. |
| 16:18:49.272 | ActivityTaskManager removes Overdex MainActivity because the process died with no saved state. |

Sources: `logcat-all.txt`, lines 12085–12086 and 12133–12137, and
`app-exits.txt`, newest entry. The crash buffer contains no Overdex exception;
the retained DropBox index contains no `data_app_crash` or `data_app_anr` entry.
The older 12:38:55 excessive-CPU exit is a separate event and is not evidence
that CPU usage caused this interruption.

## Scope and remaining work

- No production crash fix was made: these records do not support attributing
  this exit to a Replay exception, out-of-memory error, or ANR.
- Replay sound ownership and sprite loading were inspected. No crash cause
  was established from that inspection; it is not a performance certification.
- Replay's cursor uses in-memory Compose state and the opened archive is held
  by the Activity. Restoring an interrupted Replay after process replacement
  would require a separate durable resume feature. Ordinary saved-state APIs
  alone cannot guarantee recovery when Android explicitly reports no saved state.
- Move/HP timing and KO attribution remain separate investigations against the
  saved match and gameplay recording. They are not explained away by this exit.
- The device was not restarted, reinstalled, force-stopped, or navigated during
  this investigation. No Android tests were required for this diagnosis-only
  document.
