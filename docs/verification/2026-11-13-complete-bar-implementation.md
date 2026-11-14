# Complete-bar tracking and appearance resets — November 13, 2026

Implementation of the focused follow-up to the [recording study](2026-11-13-hp-tracking-relative-fade.md). Focused and full checks passed; the exact debug APK was installed on the Pixel 4. Live match acceptance remains pending.

The normal locator supplies a side-owned geometry prior. A bounded local reader requires paired rails and both end caps in one source frame before recording a complete-bar profile. It counts live and orange damage-colored interior columns separately. An orange-only bar retains geometry without supplying a measured HP amount. The existing strict HP interior reader still owns true red/green/yellow/empty measurements; the new color record cannot turn orange, disappearance or an incomplete rectangle into zero HP.

Charged-impact acquisition uses the existing recipient-owned phase and same-frame shared ordinary/impact territory. It can now acquire and follow complete orange geometry before a valid HP amount becomes available. Geometry acceptance does not consume the empty-bar opportunity or extend the phase deadline. Disjoint boxes, competing complete outlines and absent caps remain unavailable.

Complete-bar state is bounded to one record per side. No frames are retained by this track. Color status changes create separate `CompleteHpBarObserved` testimony with source coordinates, capture time, known appearance boundary, live/damage column counts and brightness measurements. Repeated unchanged statuses do not create per-frame testimony. Sustained orange requires three source-timed samples spanning at least 100 ms with gaps no larger than 150 ms. It is a candidate color observation, not a faint or finishing-move inference. No production faint consumer is connected to it in this slice.

Appearance resets clear geometry and pending color, including repeated visits by the same species. Source-time cutoffs and generation checks reject older or in-flight pixels after the reset. The ordinary geometry locator also retains its newest-frame watermark across a reset so a delayed frame cannot rewind ownership. Hard lifecycle and frame-size changes invalidate pending color tracks.

Fading remains optional support. This change does not lower outline brightness thresholds, require a fade, invent an empty outline, or add a parallax/background-removal classifier. Absolute rail/background brightness measurements are preserved at status transitions for inspection; they are not a validated fade detector. Existing archives are not modified.

## Regression inputs

Native-pixel crops under `app/src/test/resources/battle/hp/2026-11-13-complete-bar/` retain lossless source resolution, absolute crop offsets, source timestamps and source-image hashes in `provenance.json`. They include Sealeo at 41.299011 and 42.905467 seconds (nonlethal surviving slivers) and Cramorant at 178.553633 seconds (orange remainder). Source videos/retained full frames remain on the 1 TB drive. These selected frames check classification and geometry, not live end-to-end KO accuracy.

Controlled tests cover a single live source column alongside a large damage trail, missing caps, disappearance, sustained color without fading, reorder/gaps, same-appearance generation races, resize/disable, same-species reentry and charged geometry followed by a later empty opportunity. The existing Mismagius recorded empty-outline regression is retained. Archive tests verify the new record's content, time and evidence references survive round trip.

## Remaining acceptance boundary

The next Pixel match must demonstrate that the complete geometry/color observations reach the archive at the right source time and stay attached to the correct appearance. Mobile reader cost and real battle recognition remain device-trial questions. This build alone does not establish that final-hit attribution is solved.

## Completed verification

- Focused suite: 146 tests passed, none skipped. The first run exposed overly broad rejection of dark orange antialiasing in the native Cramorant fixture; the corrected color-only profile passed the rerun without changing HP amount thresholds.
- Full unit suite: 2,976 tests total; 2,972 passed, 4 skipped, zero failures/errors. Skipped cases: CryContinuousRecordingTest, CryWaveformRecordedTest, MatchProcessingArchiveProfileTest and ArticleBroadcastFanoutProfileTest (see the saved full-summary.json for exact method names).
- `:app:assembleDebug` succeeded. `git diff --check` passed. No commit was created.
- Pixel 4 `99261FFAZ0017J`: no running Overdex service before update; replacement install succeeded and the launcher started. All 48 prior entries under internal match_archives remained present immediately after installation. This is an archive-presence check, not a claim of complete database/UI validation.
- Installed APK SHA-256 exactly matches the built APK: `9752e619f8ad1a8da7a8af535838933cbc50059dd8b56b6bea10b15dcf56335a`. Version remains `1.0` / code `1`; use the fingerprint for this development build. Base commit `ce6717709dc62ae7badfd9fafcde2b229d486dde`, with uncommitted workspace changes.

Logs, test summaries, APK, before/after archive inventory and install proof are retained under `/mnt/overdex-backup/archives/overdex-device-backups/2026-11-13-complete-bar-implementation/`. Source recordings and old archives were not modified. Complete-bar reader CPU and elapsed time join the existing bounded terminal-watch diagnostics; device performance is not inferred from desktop timings.
