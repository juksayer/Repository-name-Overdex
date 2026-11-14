# Smaller Fast Move markers in Match Replay

November 14, 2026.

Fast Moves now appear in the existing Replay event strip and previous/next event
navigation. One marker is created from each resolved Fast Move animation, after
witness fusion, camera-pulse qualification and HP baseline handling. Raw HP,
border and audio observations cannot independently multiply these markers.

Markers seek to the resolved impact time. The original observation timestamp and
supporting article IDs remain attached separately. A known move gets its name;
unresolved moves say “Fast move · name unknown” and do not acquire an invented
type, identity, damage amount or energy value.

Fast Move dots use a one-dp radius, half the existing two-dp charged-move radius.
They are gray; charged moves retain orange. Player markers remain above opponent
markers. Small dots are drawn first so coincident larger event markers remain
visible. Dense events remain individually reachable using the existing controls.
No archive or live-recognition rule changes are required for this feature.

## Verification

- Focused unit checks: 13 passed, zero failures. These include pulse/canonical-use
  deduplication, reversed archived input order, unchanged archive serialization,
  preserved source clocks, simultaneous-event navigation, and paired-recording
  marker counts (Pixel 10, Moondrop 15).
- Android test APK built successfully. Device tests verify the smaller marker is
  visible and occupies less than half the charged marker's illuminated pixel area,
  and preserve the existing compact-LCD navigation checks.
- Full unit suite: 3,013 passed, six optional checks skipped, zero failures or
  errors. Debug build succeeded.
- Moondrop physical display/navigation tests: `OK (3 tests)`, including the
  illuminated-pixel size comparison. The first Pixel attempt could not find a
  Compose hierarchy while its secure lock screen was showing; it is not counted
  as passing device validation. Both logs are retained.
- Installed on Pixel 4 and Moondrop with app data retained. Pulled installed APKs
  match the tested build SHA-256:
  `660e646d514ef1ab5370232eeb330ed242cad6aad613bae008d008c680a753a7`.
  Installation and launch do not substitute for a new live-match trial.

External logs and checkpoint audit:
`/mnt/overdex-backup/archives/overdex-device-backups/2026-11-14-commit-and-markers/`.

The earlier integrated battle work is checkpointed separately as `1b58a35e`.
GitHub push authentication was unavailable during the initial publishing attempt;
that failure does not affect the local commits or source evidence.
