# Calibration screenshot preview and Select cycling — November 9, 2026

Implemented and verified in source and on the API 34 emulator at a Pixel-sized
1080 × 2280 display, density 440. The updated APK is ready; the physical Pixel 4
was no longer visible over USB when installation was checked.

## User behavior

- In Draggy Boxes, **hold A → CHOOSE SCREENSHOT** opens a visual chooser with a
  full-image Fit preview, lazy thumbnail list, filename, source dimensions and
  device/source labels. Previous/Next browse without committing. **USE SCREENSHOT**
  confirms the displayed candidate; Cancel/back retains the prior reference.
- **Hold Select** advances one picture and wraps at the end. It cycles the
  selected folder's images that match the active library filter when any exist;
  otherwise it cycles the visible captured/bundled references. Hold A continues
  to open the library and its folder/filter controls.
- Preview and thumbnail requests decode asynchronously at bounded display
  sizes. An unreadable candidate clears the old preview and disables Use.
  Unknown-dimension and other-screen-shape images remain available for review,
  with warnings and the existing main-screen editing protections.
- Browsing does not change the chosen crop, move/size mode, active device profile,
  saved boxes or restore points. The chooser commits by source ID, not a possibly
  obsolete list position.

## Verification

| Check | Result |
| --- | --- |
| Focused screenshot-library and viewport checks | Passed |
| Full debug unit suite | 2,305 tests; zero failures, errors or skips |
| Debug app and Android test APK builds | Successful |
| API 34 native UI checks | `OK (7 tests)`; failure markers checked |
| Pixel-sized visual inspection | Preview, thumbnail rows and footer visible; Fit preserves the whole portrait image |
| Diff whitespace check | Passed |
| Physical device update | Not performed; Pixel not visible over USB |

Native tests decode real red portrait/blue square PNG files and a corrupt image.
They check actual preview pixels, delayed selection until Use, Cancel, replacement
of a failed preview, and review warnings. The integrated screen test cycles the
entire visible library and wraps, starts from a foreign read-only reference,
preserves crop/mode/profile data and verifies that the library opens the chooser.
Existing device-profile and restore-point tests also pass. No instrumentation ran
on a personal phone. Enlarged system fonts/landscape are not claimed as verified.

The first Android-test compilation exposed an unsupported explicit import for a
Compose member assertion. That test-only import was removed before the successful
final build and native run. Production code and assertions were not weakened.

Private logs, tested source snapshots, the native chooser image, an immutable APK
pair and the validation manifest are retained under
`/home/sean/overdex-device-backups/2026-11-09-calibration-preview/`.

App APK SHA-256:
`3a881cbd5215c4bf78c3eff235ffc58dc77c12fcb984cc49b44e4c2d0d5cfb8b`.
No commit or push was made.

## Supplied departure examples

The [separate screenshot review](2026-11-09-departure-screenshot-review.md)
records the shrinking/disappearance examples, independent badge/HP timing, the
held-ball negative example and Shadow-aura occlusion. It preserves the original
desktop screenshots and the recording's native 816 × 1808 geometry. This UI work
adds no automatic hand, recall or faint classification; labeled continuous
recording samples remain the acceptance boundary for that experiment.
