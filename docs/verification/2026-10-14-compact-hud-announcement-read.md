# Compact HUD and announcement reading — 2026-10-14

Historical verification: the timed opening described below was removed on
October 15 after live use showed that it arrived too late. See
[the permanent-clearance review](2026-10-15-afternoon-matches-and-clear-hud.md).

## Scope and platform

Checkout: `/home/sean/AndroidStudioProjects/Overdex`, branch `odx-recovery`.
Target: Moondrop `MD_PH_001`, serial `MDPH00124112500414`, Android 14.

The user's `Screenshot_20261014_090311.png` shows a whole-screen MediaProjection consent dialog with Cancel and Start now, without an app selector. `MainActivity` already requests the ordinary screen-capture intent. Android's single-app sharing is not available through the displayed consent UI on this phone. `FLAG_SECURE` is unsuitable for reading behind the HUD: it prevents capture of a secure surface rather than supplying the game pixels it covers.

## Presentation changes

- Removed POSSIBLE FAST and POSSIBLE CHARGED headings.
- Fast Move text is teal; Charged Move text is violet. Existing confirmation checkmarks and red effectiveness warnings remain.
- Move labels align to the right. A small four-direction arrow shares the last charged-move row on the left and opens the existing position editor. If no charged moves are present, the control remains available below the cards.
- Reduced vertical padding and spacing; sprite cells are 48 dp high instead of 52 dp. Sprites remain 38 dp. Outside horizontal padding and the complementary badge corners remain intact.
- No gradient was added. A gradient may be evaluated visually later; it cannot guarantee recovery of game text beneath our letters.

## Bounded read opening

`PersistedAnnouncementWitness` can request another source frame when accepted announcement text contains a recent entry/charge cue and the capture-time HUD footprint overlaps the crop. Requests older than three seconds and repeat requests less than two seconds apart are rejected. Registration, the foreground instrument, position editing, and the field team editor do not open a read area.

`DroidballService` maps the calibrated source rectangle to the overlay's actual screen position. It does not move saved calibration coordinates or subtract an assumed notification-bar height. `HudCaptureWindow` temporarily makes only the intersecting strip transparent, including its background and text, for 450 ms. The remainder of the HUD stays drawn. Restoration is bounded independently of OCR completion.

The service waits for the transparent layer to draw. It then returns a capture-time interval with composition/restoration margins. The witness explicitly selects an announcement crop acquired in that interval, even if custody publishes it after the HUD returns. This prevents ordinary latest-frame conflation from skipping the useful crop. A late or absent crop does not create testimony; the retry waits at most 1.5 seconds for delivery.

The original covered reading remains on the Timeline. The retry reads verified source pixels, emits normal raw OCR evidence with the new crop's references and capture time, and does not fabricate missing characters or overwrite earlier testimony. Read-window timing selects candidates; it is not a new claim of perfect OCR or complete visibility.

## Four recordings reviewed

Local copies, hashes, inspection logs, and OCR review data are retained in `/tmp/overdex-review-20261014-morning/`. Originals remain in the phone's internal archive directory.

| October 14 recording | Match prefix | Articles | Verified artifacts | Recorded result |
| --- | --- | ---: | ---: | --- |
| 03:17:24 | `04b09cb5` | 3,388 | 1,134 | No final-result article |
| 03:23:00 | `1f4dafad` | 2,996 | 906 | WIN |
| 08:19:28 | `f06708cb` | 3,204 | 844 | WIN |
| 08:31:33 | `870cab89` | 3,027 | 781 | WIN |

All four manifests match their timelines, and all **3,665** retained artifacts pass hash verification. Stored opponent identities cover Combusken/Magcargo/Flareon, Plusle/Electrode/Jolteon, and Sneasel/Sealeo/Vaporeon. Coverage does not establish timely live HUD delivery.

Retained accepted text includes `Abomasnow used Ertet0% Fuper poner` and `Abomasnow used WeatErpout!\nPOSI`. These show contamination from HUD labels and controls, not merely an inability to read GO's font. Other readings, including `Camerupt used Rock Tomb!`, are complete. Raw evidence remains unchanged.

Of 70 sampled OCR readings containing common announcement cues, 67 were published within three seconds of capture. Per-recording median capture-to-publication delays were 1.15–1.61 seconds. These include custody and OCR delay; they are not frame rates. The 03:17 recording lacks a final-result article; this change does not repair that separate boundary issue.

## Validation and limits

- Final full unit suite: **726 passed**, no failures, errors, or skipped tests.
- `:app:assembleDebug` and `:app:assembleDebugAndroidTest`: passed after the final production changes.
- `HudAnnouncementReadDeviceTest`: **1 passed on the Moondrop**, using the final installed app and test APKs.
- The read opening was drawn 17 ms after its request; the screenshot was acquired between 68 and 158 ms. The restoration draw occurred at 467 ms, on the next frame after the 450 ms timer.
- More than 98% of the sampled pixels in the exposed strip matched the unobstructed background exactly. The covered comparison differed from the baseline, HUD pixels above the opening stayed unchanged, and OCR recognized `Plusle` from the exposed `Go, Plusle!` announcement. Restoration and opening/closing the position editor via the arrow also passed.
- The first device attempts did not pass. The final implementation clears the completed HUD layer, and the test waits for its actual draw rather than assuming a redraw happened. Early assertions also had to inspect unmerged text/sprite semantics because the whole panel is clickable. No pixel-match or visibility threshold was weakened.
- All **11** calibration preference/profile files have identical SHA-256 values before and after installation. Snapshots: `/tmp/overdex-review-20261013-summary/install/hud-read-20261014-before.json` and `hud-read-20261014-after.json`.
- Installation used `adb install -r`, with no active MediaProjection session. No uninstall, app-data reset, commit, or push was performed.

App APK SHA-256: `6daa2a46d69149e4a20ac460b36f4fc97e7e6194f5c0e8ce1415c322189b85d1`.
Test APK SHA-256: `d477ba1a61afd9d342caf64043cf4a77d7a711dc2cfdc2a28dc8ea3bee66bfa5`.
Logs: `final-build.log` and `hud-device.log` in the evidence directory. Inspected screenshots are in its `screenshots/` directory.

The device test places retained GO text behind a real floating HUD, compares the revealed region to its unobstructed pixels, checks unchanged HUD pixels above the opening, reads the exposed announcement, and checks restoration. It also checks the compact controls and preserved sprite size.

A new live Pokémon GO match remains the acceptance boundary for the complete capture-to-OCR retry, announcement dwell time, and visual comfort. A fully unreadable first cue cannot request this retry; short announcements may depart before the OCR result arrives. Damage can corroborate candidate moves, but HP-percentage loss alone is not a unique move identifier.

Platform references: [Android app screen sharing](https://developer.android.com/about/versions/14/features/app-screen-sharing), [MediaProjection](https://developer.android.com/media/grow/media-projection), and [FLAG_SECURE capture behavior](https://developer.android.com/security/fraud-prevention/activities).
