# October 11: replay HP and pre-battle team changes

Device: Moondrop MD_PH_001, serial MDPH00124112500414, 1080 × 2400.
Checkout: /home/sean/AndroidStudioProjects/Overdex, odx-recovery working tree.
Final installed debug APK SHA-256: `9d92ef9bc2c335a0fb48aa8a1851ebadae75f22e06c05ada0cad0166fbed1ac3`.

## Recorded failures

Reviewed internal archives 2026-10-11_10-32-11 and 10-36-08. Both contain player Raichu and opponent Sneasel, Sealeo and Vaporeon. The 10-34-25 record is a short session without a battle. The 10-32 recording has a GO boundary; the 10-36 recording does not. Neither contains a witnessed result.

The 10-32 archive includes low HP readings during the countdown, before GO. Its replay minimum-health projection retained those readings. The 10-36 player measurements are short, thick shapes (134 × 26 and 106 × 26), unlike the real opponent bars (about 193 × 17). Missing measurements also previously drew an empty bar.

Replay now starts a first appearance at full health as a presentation assumption, retains prior HP when that species returns, ignores pre-GO measurements when a GO boundary exists, and rejects implausibly thick shapes before choosing a geometry anchor. Raw articles remain unchanged. Sparse or unreliable live HP capture remains a separate issue; this change does not synthesize damage.

## Team workflow

Pre-flight lists ready named teams and owned-card teams directly in the LCD; the existing full team library remains available. This phone currently has a complete current lineup but no named library entries.

Before battle, Droidball exposes TEAM / MOVES and SCAN TEAM SELECT even if a current team exists or the HUD was opened. Rescanning restarts Team Select witnesses and clears tentative roster presentation. The field editor can load saved teams, correct species, and choose one Fast Move plus one or two Charged Moves per slot. Drafts only replace the current lineup when applied. Scans do not infer owned-card IDs or moves. Explicit saved selections retain those IDs. Configured team revisions go to the Timeline; replay and Fast Move inference use the replacement configuration.

The secure field editor pauses frame publication while open. Closing restores the previous window position and waits briefly for the compositor before resuming frames. The CRT GlassShield and calibration are unchanged.

## Validation

- Full unit suite: 501 passed, no failures/errors/skips.
- Debug app and instrumentation APK builds passed.
- Two physical-device Compose tests passed: scanned three-slot move selection and application; saved lineup selection followed by Rescan.
- Normal pre-flight and saved-team library opened on the Moondrop.
- Started a menu-only Droidball session in GO. Opened TEAM / MOVES and verified the existing Raichu / Hydreigon / Obstagoon lineup and moves.
- Navigated to Blanche → Great League → Team Select. Did not press USE THIS PARTY or begin a battle.
- Two explicit scans of that screen produced three correct player roster slots each, through visual and OCR witnesses. Source frames were observed about 8.2 seconds after the first scan request and 5.05 seconds after the second.
- Opened TEAM / MOVES after the second scan: all three species were present, all moves unselected, and 0/3 ready. Closed without altering the saved team.
- Returned to Overdex: STOP remained available. Stopped the test session normally.
- Calibration selection, legacy calibration, Team Select calibration, HUD layout, and current-team preference files compared byte-for-byte unchanged against backups.

Evidence and logs are retained locally under /tmp/overdex-review-20261011. The menu-only test is saved as `2026-10-11_11-42-24.odxmatch.zip`. Its Match ID is e3c582f9-80a9-4fe4-b403-fbb7b4e1a150; it is not a played battle. A fresh complete battle is still required to validate live HP capture quality and the resulting replay visually.
