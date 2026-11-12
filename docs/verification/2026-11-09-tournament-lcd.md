# Tournament progression, LCD menus and trackpad — November 9, 2026

The user reported that both accounts agreed on the first tournament battle but
could not find a way to reach the second battle. B also failed to return to the
desktop. Tournament actions were difficult to read within the LCD. The user also
requested the whole LCD as the desktop trackpad and resuming an existing drag
when the same finger returns after leaving its bounds.

This change preserves explicit host confirmation and server-authoritative
tournament records. Validation uses disposable repositories and fixtures; the
user's real tournament rooms, reports and match history must not be changed for
testing. No tournament backend or security-rule deployment is part of this work.

## Causes found in the existing code

- Reciprocal reports do not themselves advance a battle. The host must finalize
  their agreement. That action appeared after seven player actions, while the
  LCD displayed narrow two-column labels at 11sp. Selecting or updating an action
  list also returned selection to READY. The necessary forward step was difficult
  to discover and the screen did not prominently distinguish an agreed result
  from a result still waiting for the opponent.
- READY creates a new preparation without a result. Offering READY after a
  result had already been reported allowed a repeated readiness action to erase
  that player's current report, removing the agreement required to advance.
- Automatic pairing selection observed the event ID and phase, but not the
  player's active series ID. A new round can begin while the event remains
  ACTIVE; selection could remain on the old completed pairing and hide controls
  for the player's new pairing.
- Tournament Back returned immediately while any screen operation was busy.
  Leaving the room also passed through the same busy operation wrapper, so a
  delayed acknowledgement could prevent harmless navigation.
- The shared instrument shell latched a diagnostic route string on composition
  and compared it when dispatching B. NavHost may compose a destination before
  that global diagnostic route updates. An active shell could therefore retain
  the previous route and reject B. A route string also cannot distinguish two
  separate navigation entries with the same route.
- The tournament did not request edge-to-edge LCD content. General LCD padding,
  room status and notices further reduced the action viewport. The desktop
  trackpad additionally reserved a header strip for context controls, and its
  gesture handler permanently cancelled a contact when any sample left the pad.

These are source-level findings consistent with the reported symptoms; they do
not claim that every one of these paths occurred in the user's recorded match.

## Resulting behavior

### Tournament progression

- Once both players submit reciprocal reports, the host's first LCD action is
  **NEXT BATTLE**. It records the agreed result through the existing host
  transaction and exposes a fresh battle with fresh readiness. A last required
  final win instead offers **FINISH TOURNAMENT**. A series that advances to
  another round offers **CONFIRM RESULT · ADVANCE**; an agreed draw offers
  **RECORD DRAW · REPLAY**.
- The guest sees a disabled **WAITING FOR HOST** first action and an explanation
  naming the host. The host sees an explicit instruction to confirm the agreed
  result. One-sided reports and conflicting reports retain distinct messages.
- READY and NEED A MOMENT are hidden after that player has reported a win, loss
  or draw. Result correction/report-problem controls remain available; a current
  report is not silently replaced by a fresh readiness record.
- When the player's active series changes, the room follows that new pairing.
  Finishing a battle cannot count it again: the existing transaction validates
  the expected battle key and current reports.
- B cancels the screen's waiting operation and navigates immediately. From a
  room, the first B returns to the tournament list and the next B returns to the
  desktop. Cancelling the UI wait does not promise to roll back a server request
  that may already have committed; reopening observes the server's room again.
  Room deselection begins before another selection can be queued, and repository
  selection continues to serialize listener retirement and replacement.

### Navigation ownership

The real `NavBackStackEntry` and its live RESUMED lifecycle own shell Back input.
Outgoing, non-resumed and disposed shells reject queued Back callbacks, including
duplicate-route entries. Standalone previews and tests retain the fallback route
guard. Global route diagnostics remain diagnostic and no longer establish input
ownership for a real navigation destination.

### LCD menus and trackpad

- Tournament actions occupy the available LCD surface in one full-width column,
  using 14sp labels. The selected action scrolls into view. Pairing details,
  notices and connection explanations appear in the CRT so they do not crowd
  the action list. The physical shell dimensions remain unchanged.
- Desktop trackpad mode uses the entire LCD content surface, including the old
  header area. Select returns to the context controls. An accessibility action
  also exposes **Show LCD context**.
- A drag belongs to its original contact. Leaving the LCD pauses pointer
  movement; outside deltas are not forwarded. Returning with that same contact
  establishes a new relative anchor and subsequent movement resumes, without a
  catch-up jump. An excursion cannot turn into a tap on release. The original
  contact remains owned through the excursion so crossing other shell controls
  cannot activate them. Batched historical pointer samples follow the same
  boundary handling.

## Validation

| Check | Result |
| --- | --- |
| Focused tournament/desktop/controller checks | 58 passed; zero failures, errors or skips |
| Final complete debug unit suite | 2,305 passed; zero failures, errors or skips |
| Debug app and Android-test builds | Successful |
| Native UI coverage | 33 distinct cases passed across the initial run and the clean navigation rerun |
| Pixel 4 and Moondrop update | Both update installs succeeded; no uninstall or data clear; cold launches reported `Status: ok` |
| Physical tournament screens | Existing reports visible; Moondrop host has enabled NEXT BATTLE first, Pixel guest shows WAITING FOR HOST |
| Physical B navigation | Room → list → desktop verified on both phones |
| Physical LCD inspection | Larger full-width tournament labels fit on Moondrop; Select toggles a full-surface trackpad and returns to context |
| Calibration preservation | All 4 Pixel and 15 Moondrop backed-up calibration files identical after installation |
| Whitespace check | Passed |

Passed regression coverage includes:

- `TournamentProgressionDeviceTest`: production room and shell with two
  disposable account repositories sharing an in-memory backend; three agreed
  battles through the visible forward action, guest waiting, fresh readiness,
  final summary/reopening, Back during a pending readiness write, and automatic
  selection of the next round's pairing. No production Firebase accounts or
  rooms are used by these tests.
- `TournamentActionsDeviceTest`: readable action navigation with increased font
  scale, disabled/busy actions and replacement lists.
- `ShellNavigationOwnershipDeviceTest`: actual NavHost entries with a deliberately
  stale diagnostic route; outgoing and disposed callbacks; duplicate routes;
  standalone fallback behavior.
- `DesktopTrackpadDeviceTest`: full LCD geometry, boundary pauses, same-contact
  re-entry, multiple excursions, release behavior and preservation of ordinary
  context controls.
- Focused tournament/desktop unit checks, the complete debug unit suite, both APK
  builds and a whitespace check after the final source changes.

Native checks ran only on the disposable API 34 emulator at 1080 × 2280,
density 440. Thirty cases passed in the initial run. Two new navigation cases
initially polled before advancing the Compose clock through the NavHost
transition. Their helper now settles composition/layout and advances that clock;
the lifecycle and stale-input assertions remain unchanged. The final navigation
class passed `OK (3 tests)`. An intermediate rerun was interrupted by an
overlapping instrumentation start; the final class run was clean. No production
assertion or recognition threshold was relaxed.

The first complete unit run, concurrent with the emulator under observed severe
host memory/swap pressure, had one existing audio-witness timeout. After closing
the emulator, the unchanged complete suite passed all 2,305 tests in the final
serial run. Initial compilation also caught missing test-tag imports; these were
corrected before successful builds and device checks.

Before installation, neither phone had an active Droidball service. The settings,
database and calibration backups contained 24 Pixel and 39 Moondrop files; none
were missing afterward. Calibration files and preferences remained identical.
Only the ordinary Firestore local cache database changed during startup/sync.
No instrumentation ran on either personal phone. The real tournament was opened
for inspection only: both accounts' existing reciprocal reports and the host's
zero-finalized-battle count were retained. No host result confirmation, readiness
write or tournament edit was performed on those accounts.

Private logs, physical screenshots, source snapshots, validation manifests and
immutable build outputs belong under
`/home/sean/overdex-device-backups/2026-11-09-tournament-lcd/`.

Installed app APK SHA-256:
`ce237265a89dc9c9884c2eedec6901331290bf15a986cbaddd8dff3b83887872`.
No commit or push was made. The user's next real host confirmation and bilateral
battle remain separate acceptance steps; the disposable fixture's completed
tournament is not a claim that those real records were advanced.

## Recent match availability

Two recent Pixel archives and three recent Moondrop archives were copied and ZIP
verified before updates. The Moondrop 17:11:23 archive contains an accepted Match
Start, retained YOU WIN text and a WIN Match End; result publication trails the
result frame timestamp by about 3.56 seconds. The other four contain session or
prebattle diagnostics without an accepted Match Start/result. Those attempts do
not prove that no battle occurred, and do not independently explain tournament
progression. Exact inspection findings are retained privately in
`new-matches/inspection-summary.json`; source archives remain on both phones.
