# Match return, settings, and Pixel retained badge — October 25, 2026

## Reported behavior and source evidence

The user reported extra retained opponent Poké Balls on the Pixel, and the
Moondrop returning from Pokémon GO to Filter Settings with an Open Researcher
Mode link. They requested Match Summary on return and consistent settings.

Both phones were idle when inspected. Source archives were copied without
modification to `/home/sean/overdex-device-backups/2026-10-25-badge-summary/`;
phone and desktop hashes agree:

| Device | Match | Articles / verified artifacts | Archive SHA-256 |
| --- | --- | --- | --- |
| Pixel 4 `99261FFAZ0017J` | `7a6fdf81-8feb-44d5-b418-b35055957753` | 1,695 / 260 | `f1635708a4f43246d1361350c10433dcc062de7155c3e84a8b5d19377be17e09` |
| Moondrop `MDPH00124112500414` | `7d55236d-0a15-46cc-b24e-1b005666f8f7` | 2,555 / 283 | `ae94cc5746b0c41cd855b787b51c3b8ad399e653182f71995d18f08c54528145` |

The Pixel was last updated October 24 at 09:27:32, APK
`961d5a0a980ce8d1ee4e41b58a69b043d6952f909e42dc03f1bb52e7851addf0`.
It predates the retained-image invalidation and curved-join fixes. The current
Moondrop APK was `649dfb9108abf78a26e5e2965c59181a46879de4f0ea4a6e38f490b0c507e5d8`.

Pixel resource testimony records 3 balls at 69.218 seconds, 2 at 77.470, and 1
at 90.503, relative to record start. The cited native crops were extracted and
visually checked: they contain the corresponding bright/dark balls. WIN was
recorded at 115.215 seconds. No final zero-ball measurement is claimed.

This is consistent with the already diagnosed stale-screenshot defect in the
older installed build, not a failure to measure those two count decreases. No
additional badge algorithm change is needed before testing the existing fix on
this phone. The new archive has repeated Magikarp appearances and only one
faint Article despite two ball decreases; duplicate-species combatant/faint
identity remains a separate follow-up, not silently repaired in this record.

The Moondrop records opponent 3→2→1 resources and WIN. Its archive and current
screen were inspected for return navigation; this review does not claim a new
full battle-accuracy assessment.

## Navigation cause and change

ODXFiShell's START handler set `showSettings = true` whenever observation was
SERVICE_ACTIVE, then invoked the screen's intended stop action. Stopping a
session therefore opened a hidden Filter Settings overlay. Match-end did not
have its own return destination.

- START/STOP now invokes only the screen's intended callback.
- Removed the hidden Filter Settings and Researcher overlays, obsolete Konami
  tracking, and the uncalled hardcoded Settings screen. Actual filter values
  and settings data remain intact.
- Intueor / Signal Observatory remains the explicit filter/settings destination,
  using the shared shell and physical-button navigation. Its summary entry now
  says Match Summary.
- A completed or stopped match queues its summary in the application-owned
  ViewModel. Main navigation consumes that request only while RESUMED, once.
  There is no Activity launch over Pokémon GO.
- Acknowledgment prevents repeated returns from stealing deliberate navigation.
  Starting another match clears pending navigation. A late prior-match result
  may still be archived but cannot replace the current summary or cancel the
  current checkpoint writer.

This handoff survives Activity recreation through the application-owned
ViewModel, but is not persisted across full process death. The saved-match
summary remains manually available after a cold process restart.

## Validation and install

Final results are recorded below after build, emulator checks, and device update.

- Full unit suite: **1,116 tests**, zero failures/errors/skips.
- Debug app and instrumentation APK builds succeeded.
- Disposable API 34 emulator: **OK (6 tests)**. Three new checks cover STOP and
  ordinary/Konami button sequences not opening hidden settings, a result held
  until RESUMED with one-time consumption, and explicit settings controlled by
  the shell buttons. Three existing recorded/pixel badge checks cover 3→2→1,
  stale queued frames, curved join, and exclusion of HUD pixels.
- The emulator initially displayed a System UI not-responding dialog before
  tests; it was inspected and dismissed. Tests then passed without changing
  production code to work around the system dialog.
- APK SHA-256: `cfc2c7683e75c8c06d72227c4dbc051c35b821ec2bca344c126eb914b7b2d5c6`.
- Settings, databases, calibration and local replay metadata were backed up on
  both phones. The Pixel has no replay-compilation/excerpt/source directories;
  the backup and hash inventory use only existing directories.
- No tests were run inside the user's phone data. Phone updates use `install -r`;
  no uninstall or data clearing is part of this task.

Fresh Pokémon GO acceptance remains: stop/finish a match, return to Overdex,
expect that match's summary, then navigate to settings and leave/return without
being redirected again. On Pixel, a hidden native badge after a faint must not
restore extra bright balls. The emulator checks do not establish a fresh Pixel
battle result.

Installation completed successfully in place on both idle phones. All 44 hashed
Pixel files and 293 hashed Moondrop files in the selected match/calibration/replay
folders are unchanged after installation. Both apps cold-launched without a fatal
exception. No battle session was running or interrupted.

Moondrop physical-button check: Directory Tree → three Down presses → A opens
Signal Observatory, showing actual filters ON, scanline 1.00 and curvature 0.10.
Down → A opens Match Summary for October 25 at 2:45 PM, with YOU WIN and duration
1:59. UI XML and screenshots are retained in the backup's `moondrop/` directory.
This is a real-phone settings/summary navigation check, not a claim that a new
post-update Pokémon GO battle was performed. Automatic lifecycle handoff is
covered by the emulator test above.
