# Delayed entry species-reading coverage

The second match in `Screen_recording_20261111_190345.mp4` supplied a redundant
species-reading case during the final charged sequence. A `Go, Stunfisk!` image
captured at Match +131.581772 seconds was processed at +143.134599 seconds.
Both sides already had accepted native badge images captured later: Camerupt at
+138.008671 and Stunfisk at +138.763102. Despite those confirmations, the entry
opened windows 51/52 and reread the same names.

The opponent reread occupied about 941 ms and the player reread 494 ms of the
shared OCR turn while final Mud Bomb text waited. Those measured durations
identify avoidable work, not a guaranteed 1.435-second reduction in end-to-end
delay. Other readers, publication, and the separately installed no-shields
handoff also affect the next match's workload. Removing these jobs does not
prove recovery of an unrecorded HP value or faint.

## Change

`SpeciesCheckCoordinator` retains the latest successfully accepted native badge
proof independently for each side: explicit source capture time, crop reference,
species and accepting window. A delayed ENTRY request strictly older than that
capture produces `COVERED`, citing the original cue and badge crop. It does not
open a window, clear identity, retire another assignment, or modify testimony.
The diagnostic uses the accepting window's ID and the covered cue's source time;
it is not a new identity or appearance boundary.

Equal/newer ENTRY cues and all other reasons keep their existing behavior.
Implicit completion-time defaults, legacy two-read confirmations, unknown reads,
future clocks and the other side's badge do not establish coverage. Restart and
stop clear the proof; stopped coordinators reject late acceptance. If the ENTRY
request arrives before badge acceptance, existing work may still run: cancellation
of that race is outside this slice.

Both normal and unchanged-species branches of `LiveOverlaySpeciesPipeline`
already pass the original source frame clock to acceptance. Its registration in
`PokedexViewModel` and its pre-native-OCR `needsReading` check were verified.
Production consumers of species-check diagnostics gate appearance changes on
`OPENED`, so `COVERED` does not create a switch, reset HP, clear move identity or
generate a faint.

## Verification

- 34 focused tests passed: 33 coordinator cases and one recorded sequence test.
- Full unit suite: **2,721 tests across 360 suites; zero failures, errors or skips**.
- Debug APK build succeeded. SHA-256:
  `ad3e6a49449468f31ab6390ef56467aee2b5fb2afde427c7c0618d5437ae3b5c`.
- `git diff --check` passed.
- No phone installation performed for this slice at verification time; the
  Moondrop's active observation session was left running. Live acceptance remains
  pending. The later 21:13 match was recorded on the preceding build.

The literal 39-Article regression excerpt and transitive dependencies are in
[`2026-11-11-delayed-entry-species`](../../app/src/test/resources/battle/recorded-evidence/2026-11-11-delayed-entry-species/README.md).
The fixture hash, source archive hash, exact source clocks, Article IDs and queue
measurements are retained there. The regression preserves recorded expiry before
late badge recognition, checks both covered sides, and verifies that the genuine
later final charge still opens its own checks. It creates no missing KO.

Test logs were retained at `/tmp/overdex-species-covered-focused.log` and
`/tmp/overdex-species-covered-full.log`; the parsed full-suite/APK summary is
`/home/sean/overdex-device-backups/2026-11-11-moondrop-1903-review/species-covered-checks.json`.
