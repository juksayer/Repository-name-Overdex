# Session control and QTE sprite focus — October 28, 2026

## Changes

- Exit Icon editing stays in CRT Match Calibration, immediately after QTE Type
  Icons. The temporary HUD menu and live/prebattle Exit guide are removed.
  Saved regions, named profiles, and screenshot selection are unchanged.
- Modules that do not override the shell's Start action inherit the current
  Droidball session control. Previously, modules including Match Summary and
  Signal Observatory could display STOP while the default action did nothing.
  Explicit module actions, such as calibration saving, retain ownership.
- Stop cancels the old per-match control listener so queued session signals
  cannot reopen its HUD. Archive work retains its independent scope. The shell
  displays STOPPING during service teardown and START after the service reports
  STOPPED; the independent runtime collector remains active.
- During player QTE preparation, the player's sprite grows to 120% of its normal
  size and the opponent shrinks to 85%. Replay-clock easing takes 250 ms at both
  ends and restores normal size by charge release. Pauses and reverse scrubs
  reproduce the same pose. Fallen sprites, opponent charge preparations, HP
  bars, type badges, and base layout sizes retain their existing presentation.
  Archived testimony and event times are unchanged.

## Inactive-card evidence boundary

Inactive-card disappearance is useful potential corroboration, but there is no
production present/absent/unknown signal yet. Existing fingerprints can hash bare
background, and the HP reader can find false bars in field markings. Earlier
Moondrop evidence includes such crops outside QTE, so a missing HP measurement
must not be treated as a witnessed disappearance. A later structural visibility
reader should establish prior presence and combine its transition with explicit
phase evidence. No new disappearance detector is enabled in this update.

## Verification

- Full unit suite: **1,275 tests / 211 suites**, zero failures, errors, or skips.
  New replay tests cover QTE phase limits, size restoration, and reverse scrubs.
- Debug app and Android device-test APKs built successfully. Three new Compose
  regressions cover inherited Stop/Start, contextual action ownership, and
  contextual labels during an active/stopping session. These compiled but were
  not executed: the API 34 emulator failed during startup with exit 139 before
  its app installation completed.
- Independent review caught and corrected a contextual-button label mismatch
  and a generic-module callback that blocked inheritance. Review also confirmed
  that canceling the per-match listener leaves runtime reconciliation and archive
  work alive.
- Installed on the idle Moondrop (`MDPH00124112500414`), then launched Overdex.
  The Directory Tree displayed START. All **17** saved calibration/profile,
  HUD-layout, and current-team files matched their pre-install hashes. The Pixel
  was not updated.
- The exact reported stale-Stop symptom was not reproduced before installation.
  A physical capture-session Stop → Start → new-session cycle and the visual QTE
  sizing remain user acceptance checks; app launch and unit success do not prove
  those live behaviors.
- APK SHA-256:
  `7a26e8bd6577085b1b1ebec4a3fe8704272a0846fe513449d8e317241176babd`.
- `git diff --check` passed. Source hashes were unchanged across the final build.

Evidence and build/install logs are stored outside the repository at
`/home/sean/overdex-device-backups/2026-10-28-stop-qte-focus/`.

The newest Moondrop recording, `7ba07a0b-2208-42e1-80e2-b2b1214c92c3`, was copied
without modifying the phone's archive. It contains an unresolved `Twiste!`
announcement and a preceding Get Ready cue; name-repair changes remain outside
this UI/lifecycle update.
