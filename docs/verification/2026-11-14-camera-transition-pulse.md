# Camera-transition border pulse — November 14, 2026

## Result and scope

Completed Replay now qualifies a border pulse against a conservative, source-timed
camera excursion. This removes the Pixel +49.659284 s false Fast Move in the paired
November 13 Camerupt exchange. It does not alter the live witnesses, archive entries,
source timestamps, accepted testimony, charged-move ownership, or HP measurements.

The suspect stretch changes from five animations to four. Three independently
measured HP losses remain at +43.260093675, +45.987806759, and +48.344304182 s.
The +44.317454197 s HP-confirmation duplicate remains a separate unresolved issue.
No finishing hit, KO, move name, or move duration is invented by this correction.

## Recorded basis

Existing opposite-side recordings:

- Pixel: `/home/sean/Desktop/Screen_recording_20261113_183448.mp4`.
- Moondrop: `/home/sean/Videos/Overdex/Moondrop-2026-11-13_18-34-50-ivjKu8.mp4`.

The previous frame review located three actual Pixel video impacts at 85.842,
88.410, and 90.859 s, followed by Attack Incoming camera movement. Video and archive
clocks are separate; the approximate 42.5 s offset is not exact synchronization.
The original review is retained in `2026-11-14-hp-confirmation-duplication.md`.

At the rejected pulse the complete PLAYER bar moves from approximately
`[42,746,234,764]` to `[72,799,266,816]`, continues to `[149,857,342,874]`, and
returns to `[42,747,234,764]`. Its width, height and HP plateau remain consistent.
A subsequent captured charged-move announcement corroborates the sequence.
The archive lacks an accepted Attack Incoming cue here; the implementation does
not inject the cue visible only in the recording.

Rejected border basis: `f666daf2-9a01-4901-8232-a0e34201337c`.
Archived derived use retained but not animated: `4e02b3d2-8d07-4f8d-a32c-06a7e5790637`.
The Replay model retains the qualification's original evidence IDs for inspection.

## Qualification and preservation

Require three fresh stable normal-bar samples; same-frame pulse and accepted bar;
substantial horizontal AND vertical displacement; a continuing displacement within
500 ms; compatible complete geometry and return within three seconds; and a
charged-move announcement within ten seconds. Both appearances must remain the
same. Faint, result, capture restart and ignore boundaries veto qualification.
Conflicting same-frame readings and source-dimension changes also veto it.

A new HP plateau vetoes qualification. At most one interior column of numerical
jitter is allowed. This is not proof that subpixel damage did not occur, nor a
threshold for treating tiny HP as empty. The whole-bar readers and tiny-HP rules
are unchanged. These conservative numeric limits are tested against this recorded
case and controls, not validated universally across devices or animations.

Only the `HP_BORDER_PULSE` basis is removed. A canonical use with independent HP
loss or matched recipient visual/audio support remains an attack with its source
references. A disqualified pulse without such support does not become a Replay
attack; its original Article and archived derived use are unchanged. Missing raw
pulse provenance is not guessed. This is not an interval-wide attack blackout.

## Whole-match comparison

| Saved match | Fast Move uses before → after | Scheduled animations before → after |
| --- | --- | --- |
| Pixel | 12 → 11 | 12 → 11 |
| Moondrop | 16 → 16 | 14 → 14 |

Only the identified Pixel camera use disappears. Every surviving attack retains
its exact start, impact, source time, and evidence. Charged-move impact times,
identities and evidence are unchanged. Pixel's derived exchange end and estimated
charge preparation start move earlier by 1.314979870 s because the false last
attack no longer constrains those presentation bounds. These derived bounds were
reported separately rather than hidden in an exact-output comparison.

All 1,839 Pixel and 3,143 Moondrop original articles match both the bundled fixtures
and read-only review inputs. The original ZIPs were opened read-only and hashed.

## Validation and artifacts

`ReplayCameraTransitionPulseTest` covers the recorded case and reversed delivery,
missing/contradictory geometry, missing phase/return, stale samples, dimensions,
appearance and faint boundaries, stationary blue/subpixel-hit controls, vertical
bob, real HP loss during movement, mixed HP/audio support, and missing provenance.
The original Sealeo finishing-hit regression and recipient/audio tests are included.

Results and comparison scripts live on the 1 TB drive:
`/mnt/overdex-backup/archives/overdex-device-backups/2026-11-14-camera-transition-pulse/`.
See `whole-match-comparison.json`, `archive-integrity.json`, `final-focused.log`,
and `full-validation.log`. The historical overlap study is updated to expect four
current animations while retaining its counterexamples to unsafe broad deduplication.

This is saved-Replay validation. Live camera-pulse publication is unchanged;
physical-device Replay acceptance remains distinct from build/install success.

Final validation: 3,008 unit tests total, 3,002 passed, six optional diagnostics
skipped, zero failures/errors; debug APK build passed. Installed with `install -r`
and launched on Pixel 4 (`99261FFAZ0017J`) and Moondrop (`MDPH00124112500414`), after
confirming no active projection or Droidball service. Pulled installed APKs match
SHA-256 `fcbea4bb980e75411ffd12f1dfe75892f6f45b01f5db577628869099be2c9857` on both.
Prior APKs were backed up on the 1 TB drive. No app data clear, archive rewrite,
commit or push was performed. Installation and launch are verified; user review
of the Pixel saved match around 0:42–0:51 remains the physical Replay check.
