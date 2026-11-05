# Match History opens the selected match's summary — November 5, 2026

The user approved consolidating the empty legacy History entry with the existing
saved-match browser. The intended flow is BATTLE → Match History → selected
Match Summary → Timeline / Replay. Automatic post-battle summaries remain direct.

## Implemented behavior

- The directory's Match History action opens the existing internal archive
  browser. The redundant Match → Match Summary shortcut is removed; Replay
  Editor and the contextual latest-summary/automatic-return paths remain.
- History shows recorded dates, results, and independently observed teams.
  Missing results remain explicitly incomplete; unknown dates remain unknown.
  Sorting uses recorded match time, never filename, export time or file mtime.
- Readable completed/recovered copies are grouped by Match ID, retaining the
  richest article count with a deterministic tie-break. This is presentation
  only: no archive is changed or deleted. Unreadable files remain separate and
  visible with an opening error. Active checkpoints are excluded until normal
  publication or interrupted-match recovery.
- Selected URI and Match ID are saved with the navigation entry. New matches
  arriving, richer duplicate packages and returning from Summary preserve the
  selected match where it remains available. Up/Down keeps selection visible;
  A/Open Summary and B/Back work through the existing instrument and LCD.
- A history selection navigates to a distinct URI-bound summary. It overrides
  any current live source and loads only that selected archive. Failure cannot
  silently substitute the newest or current match. Inspection receives the same
  archive and URI; Back returns to that summary, then the History selection.
- Open Match History returns to an existing list on the stack instead of adding
  another copy. The legacy `battle_history` route redirects for restored older
  stacks. The existing `timeline_viewer` destination identity is preserved so
  latest/automatic-summary stacks still restore after an update.
- The legacy History repository and screen source remain retained but are no
  longer the production entry. No new match-storage system is introduced.

## Scope and preservation

History reads the existing available internal archives with references-only
loading on I/O; it does not decode images or audio to list matches. Existing
retention and Downloads exports are unchanged. Battle Records remains the
cross-match event-analysis view, including its retained lifetime indexes; those
indexes are not misrepresented as currently replayable archives.

The pending automatic summary handoff remains memory-only across full process
death, as documented in the [return audit](../audits/2026-11-05-go-return-paths.md).
This feature binds deliberately selected saved summaries; it does not implement
durable automatic summary navigation.

## Verification

- Full JVM suite: 1,746 tests in 256 suites; zero failures, errors or skips.
- Debug APK and instrumentation APK assembly passed.
- Five new pure tests cover recorded chronology, selection refresh, honest
  incomplete/unreadable labels, active checkpoint exclusion, and duplicate
  packages/richer-source remapping.
- Twelve distinct API 34 native checks passed: the six settings/automatic-return
  checks in the initial run, all four summary controls/refresh checks in the
  navigation rerun, and `OK (2 tests)` for the final paired History run in
  14.24 seconds. Initial failures exposed stale test action counts and shared
  LCD/test FileProvider state. Tests now give each destination its own display
  state and prime FileProvider from the real context before isolating fixtures.
  No failed assertion was removed or timeout extended to claim acceptance.
- The new native tests use real isolated ZIPs and production history, summary,
  timeline and replay screens behind GlassShield. They contradict filename and
  mtime chronology, open an older LOSS against a competing live WIN, retain
  archive/URI and replay identity, return to the same History selection, handle
  a new match arriving, avoid duplicate History navigation, and reject a missing
  selected URI without substituting live/latest data.
- `git diff --check` and local documentation links passed.

## Phones and data checks

- Confirmed no active Droidball service or MediaProjection before each install.
  Both Moondrop and Pixel 4 received the checked debug APK. Installed SHA-256s
  match the build: `09f15889e039916c033555065e7f2b252f7fafe238f128b0b8d1ad5c2b62fe26`.
- Moondrop normal controls: Down to battle, A to expand, Down to match history,
  A to open. The list showed the November 5 9:19 Garchomp WIN first and the 9:16
  Turtonator/Camerupt/Gourgeist incomplete match second. Selected the older
  entry and confirmed its 9:16 summary, observed moves and missing-result label.
  Inspection opened archive `45b6ccca-68f8-4bad-8f4e-cffa83d6d06d`; replay
  preparation completed and transport became available. A separate, paced
  summary-to-History Back check confirmed the 9:16 entry remained selected.
- The new tests use a small synthetic directory trigger; this Moondrop check
  verifies the actual directory/MainActivity command wiring. No new battle was
  needed or recorded. Pixel received the same APK but its physical navigation
  was not separately exercised. Native instrumentation ran only on the emulator.
- Protected inventories retained 49/49 files on Moondrop and 22/22 on Pixel, with
  no additions or removals. Pixel's protected files are byte-identical. Moondrop
  changed only Firestore cache, Firebase heartbeat/token cache and Android data
  transport runtime state; non-token user state is identical. Cards, teams, binder preferences, calibration,
  registration and replay data retained their contents. Both match archive
  inventories (names, byte counts and mtimes) are unchanged.

Private checks, backups and screenshots are kept outside the repository:
`/home/sean/overdex-device-backups/2026-11-05-match-history/`.
