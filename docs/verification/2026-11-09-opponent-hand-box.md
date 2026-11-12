# Opponent hand sampling box — November 9, 2026

**Implemented, verified and installed on the Pixel 4.** Moondrop installation and
actual arm-event reliability remain untested. The user supplied an approximate
Moondrop location and will position the box. See the
[experiment and user workflow](../plans/2026-11-09-opponent-hand-box.md).

## Behavior

- Added **Opponent Hand / Poké Ball** to Draggy Boxes, guessed at X 1045, Y 600,
  W 25, H 80 in full 1080 × 2400 display coordinates. Existing adjusted boxes
  and profile restore points are preserved. On the connected 1080 × 2280 Pixel,
  this normalizes to X 1045, Y 570, W 25, H 76.
- Before battle, the Droidball field menu's **OPPONENT BOX** opens a thin live
  guide with drag, move/size, precise arrows, view-only touch passthrough, and
  independent saving. Selecting the experiment also supplies a battle HUD
  reopen control for the rest of the observation service session.
- **SAMPLE 10s** uses the current unsaved draft. Both guide and control panel
  hide, and source frames must be at least 150 ms after the request. Raw capture
  is limited to 100 ms cadence, 100 images and ten seconds per request. It is
  manual only and can run before GO. Match/session termination stops it.
- Exact source dimensions, crop geometry, pixels, and source clocks survive
  custody and normal compact saving, independently of recognition. This adds no
  arm/send-out/recall/faint classification and does not change result music.

## Verification

| Check | Result |
| --- | --- |
| Focused persistence, sampler and archive tests | 51 passed; zero failures, errors or skips |
| Full debug unit suite | 2,305 passed; zero failures, errors or skips |
| Debug app and Android test APK builds | Successful |
| API 34 emulator native checks | `OK (2 tests)`; failure markers checked |
| Pixel install | Update install succeeded; no uninstall/data clear |
| Pixel launch | Cold launch `Status: ok`, process present |
| Pixel preservation | All 21 backed-up settings/database/profile files identical after update; none missing |
| Diff whitespace check | Passed |

The native capture test uses deterministic pixels to verify tiny-default and
moved-draft crops, original pixels/source clocks, cached-frame rejection,
100 ms cadence, sample cap, custody and compact retention. The native window
test verifies physical placement without an inset shift, transparent interior,
precise move/resize controls, unsaved draft sampling, hiding both windows,
restoring controls after sampling, and saving the exact latest draft. These
checks establish capture plumbing and UI behavior, not real arm recognition.

An initial UI run was dimmed by an emulator **System UI isn't responding**
dialog. The retained screenshot showed uniform dimming across the whole activity.
After dismissing the unrelated dialog, both native checks passed against the same
production build; no transparency assertion was weakened. Earlier focused-test
setup errors (a missing enum import and comparing against zero-valued legacy
defaults instead of the manager's loaded defaults) were corrected before the
passing checks. No production calibration defect was masked by those corrections.

Private logs, test-result manifests, exact tested Kotlin source snapshots,
screenshots, immutable APK copies and pre/post Pixel backups are retained under
`/home/sean/overdex-device-backups/2026-11-09-opponent-hand-box/`.

Installed app APK SHA-256:
`bdbcc700a818f38b8666b548b90f3c0d01e797c20b404b41521367f8d8f8f1a4`.
The update also contains the preceding HP-search and event-driven reader work
that had not yet been installed on this phone. No instrumentation ran on the
physical Pixel, and no commit or push was made.

Next acceptance: user placement and labeled real send-out/recall samples, plus
ordinary play and camera-transition negatives. Measure whether the gesture is
inside the box and consistently visible before proposing an automatic witness.
