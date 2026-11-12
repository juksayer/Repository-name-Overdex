# Bounded player appearance reader

The final charged-KO path does not require a recorded empty HP outline. This
change adds an independent visual measurement for the player-side endings that
the particle/flash reader missed. A measured color contraction is only a
candidate: a living switch also passes it.

## Production behavior

`TerminalParticleWitness` uses its existing shared charge assignment and frame
collector. During an incoming player-recipient phase it also measures three
independently adjustable regions: Player Appearance, Appearance Color Reference,
and Appearance Background. Their normalized full-display coordinates persist
through preferences, named profiles and working-checkpoint restore. Older
profiles acquire only the new defaults; existing adjustments remain unchanged.

The reader resizes to at most 336 pixels wide and samples at most 20 Hz. It keeps
at most 21 frozen hue profiles for two seconds, with a cap of one million stored
support pixels. Source gaps above 250 ms, coordinate changes, obscuration and
unstable/featureless background interrupt continuity. A brightening guard retires
an appearance hidden by the reviewed bright attack effect. It does not identify
the sprite or prove all possible occlusions absent.

Numeric measurements retain the actual source clock, publication identity,
source/raster geometry, measured regions, background statistics, frozen baseline
clock and color bins, support areas and brightened fraction. They travel through
the existing bounded batches (600 frame limit, batches of at most 20); no new
continuous PNG stream or OCR worker is added. Particle and appearance visibility
remain independent. An invalid particle box cannot disable a readable appearance
measurement. No inferred HP, count, move or faint is emitted by this reader.

`TerminalAppearanceSequences` requires a measured baseline, contraction and
clearance of the same support. Missing frames/supports are barriers; duplicates
and reordered delivery retain their original source order. The shared
`TerminalChargedFaintResolver` accepts this additional sequence only for a player
recipient with independent identities, last-Pokémon count, named opposing
charged hit and matching loss. Switches, continued play, positive HP, source
conflicts and later identified survival defeat or retract the conclusion.

Live tracking and Replay use the same attribution and source clear clock. Replay
may show the inferred depleted endpoint after the corroborated KO, but it keeps
the last observed HP reading and does not invent intermediate damage or a
measured empty bar. Original archive entries and timestamps remain unchanged.

## Recorded regression evidence

The compiled production reader and sequence interpreter processed all five
recordings at the same 20 Hz ceiling: 12,241 source frames. The host supplies a
336×747 OpenCV-resized raster, matching the preceding development study. Source
videos are actually 816×1808; Android resampling is checked separately rather
than assuming that the host transformation validates it.

| Recording | Frames | Appearance candidate clear times |
| --- | ---: | --- |
| 190345 | 3,137 | 170.687067 s; 358.772700 s |
| 124455 | 2,803 | 110.247922 s — living Gourgeist switch |
| 161531 | 2,819 | None |
| 211335 | 1,752 | None; bright Quagsire attack rejected |
| 112107 | 1,730 | None |

Test-only copies combine these separately labeled video measurements with the
original 19:03 archives using approximate GO alignment, also tested at ±1 second.
The second match resolves final player Camerupt at GO+147.7097 s with the original
last HP measurement above 70% intact. Live, Replay, reversed/shuffled deliveries
and late batch envelopes agree. The first match's final Gourgeist remains
unresolved because the archive lacks its fully named final opposing charged hit.
The original archives without new appearance measurements remain unresolved.

The unit corpus includes all numeric readings, not only successful candidates.
Additional checks cover visibility changes, missing baselines, invalid geometry,
stream changes, persistence and archive round trips. Existing opponent terminal
particle/flash recognition is preserved.

## Validation boundary

Full unit suite: **2,871 tests across 379 suites, zero failures/errors/skips**.
Debug application and instrumentation APKs built successfully. An existing
terminal-HP handoff test exposed a startup race in its fixed ten-millisecond
sleep; it now waits for the hub's actual subscriber before sending its bounded
samples. Production HP interpretation is unchanged.

Pixel 4 (`99261FFAZ0017J`) instrumentation: **OK (3 tests)**. This exercises the
incoming appearance path, outgoing particle path, real capture-clock/overlay
handling, Custody/archive round trips, and the standalone recorded-image reader.
The device test uses 62 original decoded PNG frames across the
two charged endings, the living recall and the Quagsire washout. These fixtures
are transferred to a dedicated app-cache directory and are not shipped in the
application. Run `TerminalAppearanceReaderDeviceTest` with the instrumentation
argument `appearanceFixtureDirectory` pointing to that directory.

Android resizes the original 816×1808 images to 336×744 and resolves the normalized
regions in that raster. Its three candidate clear clocks match the host scan:
170.687066666 s, 358.772700000 s and the living recall at 110.247922222 s. Quagsire
still supplies no candidate. The incoming witness test additionally verifies
the default regions at the capture-like 672×1493 geometry (336×747 raster).

| Pixel recorded interval | Frames | Median reader time | 95th-percentile reader time |
| --- | ---: | ---: | ---: |
| Gourgeist charged ending | 18 | 20.09 ms | 49.68 ms |
| Camerupt charged ending | 7 | 20.14 ms | 20.98 ms |
| Living Gourgeist recall | 26 | 15.67 ms | 17.41 ms |
| Quagsire bright effect | 11 | 13.07 ms | 17.14 ms |

These small isolated samples include Android scaling and measurement, exclude
PNG decoding, and are not a concurrent live-capture load benchmark. The reader
runs only inside its existing incoming-charge assignment, with the 20 Hz ceiling.

Installed application SHA-256:
`6a140ee61a03b9ab22b91cecf315d97810363dd81f616d998003c98202694867`.
Installed-file hash matches the built APK; launch reports `Status: ok`.
Of 102 pre/post hashes, all 32 match archives, 51 battle-record files, calibration
profiles/reference and other protected settings remain unchanged (100 unchanged
in total). Two Firebase runtime preference files changed during startup; no
protected file was removed or added. Droidball/MediaProjection were inactive
before installation. The test did not start capture or a live game battle.

Fresh live-battle recognition is still unverified. The 124455 final Fast Move KO
remains missed; gray bodies, other scenery, species, lighting, camera behavior
and device calibration are not established by these development examples.

Scripts, source hashes, class hashes, full numeric scans, PNG hashes and device
proof are retained in
[/home/sean/overdex-device-backups/2026-11-12-player-appearance-reader](/home/sean/overdex-device-backups/2026-11-12-player-appearance-reader).

## Subsequent recognition change

The [partial-charge finish change](2026-11-12-partial-charge-finish.md) removes
the fully named move as a prerequisite when a separate, qualified used-move
announcement establishes the attack's performer and direction. It resolves
the first 19:03 Gourgeist finish in the video-enriched regression; the original
archive alone remains unresolved. The test/build results above describe the
earlier appearance-reader build, not this subsequent change.
