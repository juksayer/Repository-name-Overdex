# Confirmed roster versus equipped moves — October 27, 2026

## Problem and resulting behavior

The two Moondrop recordings reviewed in
[the roster review](2026-10-27-moondrop-roster-review.md) confirmed Aegislash,
Alolan Sandslash, and Dragalge, while configuration testimony still described
Aegislash, Flygon, and Mawile. The user subsequently reported Powder Snow,
Acid, and continuous attacking. That clarification is external ground truth;
the original recordings are unchanged.

Confirming a scanned roster now persists an unfinished-moves marker and removes
the old loadout from the active selection. This survives application/store
recreation. Named teams and owned cards remain intact. A completed **Use This
Team** save removes the marker and publishes the explicitly selected moves.
The app does not silently borrow moves from another owned copy of the same
species. An unreadable pending marker also fails closed rather than restoring
old equipment.

The field panel distinguishes **ROSTER CONFIRMED · MOVES NEEDED** from **TEAM &
MOVES READY**. The editor labels choices as selected and explains that **Use This
Team** saves them. Closing/reopening the same scan preserves partial choices
within the process. An explicit rescan discards them, even if the species names
are identical; a fresh scan takes priority over an older pending roster.
Observation-only battle startup remains available when moves are unfinished.

Accepted `PlayerTeamRosterConfirmation` is also a source-time invalidation
boundary for live inference, Replay, and owned-card history. Configuration at or
before that boundary remains raw testimony but cannot supply moves, form/copy
disambiguation, or owned-card attribution for the newly confirmed roster. A
delayed older configuration cannot undo the boundary. Rejected confirmations do
not invalidate anything. Later explicit configuration restores its saved slots.

Replay retains the existing prebattle setup boundary at MatchStart. It filters
stale configuration-derived move, energy, and form conclusions only in its
presentation; the archive's articles are not rewritten. Legacy recordings
without an accepted roster confirmation retain their previous behavior.

## Why independent observation did not resolve Powder Snow and Acid

The bundled legal moves and audio references exist. This review uses the local
reference data; it does not claim a fresh external game-balance verification.

- Alolan Sandslash's Metal Claw, Powder Snow, and Shadow Claw all have a
  one-second duration. Powder Snow and Shadow Claw also tie at eight energy.
  Cadence and that energy tie cannot choose Powder Snow.
- Dragalge's early HP-backed uses in the newer recording occurred at +0.922,
  +1.999, and +2.983 seconds. Acid fits that rhythm, but Water Gun remains viable
  if alternate impacts were missed. Removing the missed-impact safeguard would
  revive the previous Water Gun/Powder Snow confusion.
- Strict audio matches did identify Acid at +69.419 in the first recording and
  +126.247 in the newer one, and Powder Snow at +67.193 in the newer recording.
  There were never two accepted, nonoverlapping matches to either move within
  one qualifying window. A charged sequence also cleared the Powder Snow window.
- Other highly ranked clips failed the spectral separation checks. A later
  SUPER EFFECTIVE phrase followed Aqua Tail and was correctly excluded from
  Fast Move identification.

No audio threshold or cadence harmonic protection was weakened in this change.
An eventual focused improvement can test one strict, uniquely attributed audio
match plus independent cadence or discriminating effectiveness, with appearance,
charge-phase, mirror-match, and overlapping-clip safeguards.

Detailed source times, Article IDs, scores, and unchanged archive provenance:
`/home/sean/overdex-device-backups/2026-10-27-lock-stop-review/powder-acid-diagnostic.md`
and `powder-acid-diagnostic.json` in the same directory.

## Validation

- 147 focused unit tests passed after repairing invalid short-roster test
  fixtures. The replay adapter also now tolerates short/oversized archived
  confirmations without crashing or restoring stale equipment.
- Full unit suite: **1,229 tests**, zero failures, errors, or skips. App and
  Android-test APK builds succeeded. `git diff --check` passed.
- Final APK SHA-256:
  `d749d5ec4d11af9ee61b3cf67a43d788a87dd313e3c74b8f0e5e7234f4b247ef`.
  Installed and hash-verified on Pixel 4 `99261FFAZ0017J` (Android 13).
- Pixel instrumentation: **OK (6 tests)**, comprising isolated binder/team-store
  persistence and field-editor UI checks. These cover pending-roster reload,
  same-species invalidation, named-team restoration, move choices across all
  three slots, explicit application, and rescan availability. Test preferences
  are isolated and removed afterward.
- Calibration profile, battle crops, Team Select crops, HUD layout, and selected
  team preference hashes are unchanged after installation and device tests.
- Moondrop was disconnected during installation. Its update and the real
  Pokemon GO battle check remain pending. Device UI tests establish the setup
  controls and persistence, not live recognition or exact battle counts.

Build logs, test output, installed-APK receipts, and preference hashes are under
`/home/sean/overdex-device-backups/2026-10-27-roster-readiness/`.

## Next live acceptance check

On the updated phone, scan the intended three Pokemon, confirm the roster,
choose Powder Snow for Alolan Sandslash and Acid for Dragalge plus their actual
Charged Moves, finish all three members, and tap **Use This Team**. Verify the
panel reports **TEAM & MOVES READY**. Closing midway should preserve choices on
reopen; rescan should start empty move choices. Starting without finishing must
not reactivate the previous team's moves.

For an independent witness test, use SFX-only on the observing phone, leave the
other side idle, and give each attacker 8–10 uninterrupted seconds before any
Charged Move. An MP4 supplies an independent timing/count reference. Continuous
tapping alone is not an exact count of completed attacks.
