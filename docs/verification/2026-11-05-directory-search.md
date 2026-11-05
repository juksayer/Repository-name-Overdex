# Search sessions end at the directory — November 5, 2026

The trainer reported that reopening the Pokédex could silently retain a previous
search/type restriction, producing only one type or an empty binder. Returning
to the directory should end transient searches across modules.

## Change and scope

- `PokedexViewModel.resetSearchSession()` clears both the query and the complete
  `SearchRequest`, including a type-only restriction with no visible query text.
  The ViewModel is process-owned, so list disposal alone did not clear this state.
- `DirectorySearchResetEffect` runs on entry to `main_menu`. MainActivity clears
  the pending Pokédex keyboard-open flag at the same boundary. Ordinary
  detail-to-binder returns retain the current search; detail type/move/region
  actions can still intentionally filter that existing binder visit.
- Closing the directory's Add Pokémon picker, or leaving its branch, clears the
  species query, region, choice and hold state. The saveable card draft retains
  its specimen identity, species, nickname, CP, moves, flags and memberships.
- The other inspected searches belong to module compositions or navigation-entry
  ViewModels and are discarded when those entries are popped: owned binders,
  binder editing, registration, current-team selection and Battle Records.
  Saved binder rules, selected binder context and per-card Records entry context
  are durable/intended views, not transient searches to erase.
- CRT display filters, calibration profiles, teams, cards, recordings and
  communications drafts are unaffected. A recent-search list remains a possible
  later UI choice; this change does not persist search history.

## Verification

- All 1,741 JVM tests passed across 255 suites with no failures, errors or skips.
- Debug and instrumentation APK assembly passed.
- API 34 emulator instrumentation: `OK (11 tests)` in 93.384 seconds. Includes
  the real ViewModel reset under a NavHost, preservation on detail return,
  clearing query/type and pending search-opening state at the directory, and a
  type-only query reset. It also covers directory navigation, hold acceleration,
  keyboard/navigation geometry and owned-card save/restoration.
- The new directory-card regression applies both a query and regional filter,
  backs out to BATTLE, reopens with blank query/All regions/first choice, and
  saves to verify that the original draft fields survived.
- `git diff --check` passed.

## Phones

- Confirmed neither device had an active Overdex service or MediaProjection,
  then installed the same APK on Moondrop and Pixel 4. Both installed hashes
  match the checked build:
  `57c1dfd34976c3f8e7ccfbee6a536df769a135bf2f7f881646c5e5420f06a90f`.
- Moondrop manual check: directory → OVERDEX → Bulbasaur detail → Grass type →
  binder search. Confirmed `SEARCH: GRASS`. Closed the keyboard and returned to
  the directory, reopened OVERDEX and search, and confirmed `SEARCH: _`.
  Charmander (#004) was present again, demonstrating the type restriction also
  cleared. Returned to the directory afterwards. No card was created or saved.
- Pixel 4 received the same checked APK; no physical Pixel search or GO-return
  workflow was performed. Automated checks ran only on the isolated emulator.
- Protected before/after file inventories: Moondrop 49/49, Pixel 4 22/22, no
  additions or removals. Pixel's protected files are byte-identical. Moondrop
  changed only Firestore cache, Firebase heartbeat and authentication token-cache
  files; non-token user state is identical. Owned-card storage, binder/team
  preferences, calibration, registration and replay data retained their contents.
  Both match-archive inventories match exactly.

## Navigation audit and remaining acceptance boundary

The [screen audit](../audits/2026-11-05-screen-navigation.md) covers all 35 public
top-level Screen composables and their actual entry paths. Its menu findings are
documented, not silently removed or enabled by this search fix.

The [GO return audit](../audits/2026-11-05-go-return-paths.md) found matching
summary-routing logic in the previously installed APKs despite their different
builds. The memory-only summary handoff does not survive a full process restart.
The precise cause of the reported Pixel return remains unproven. Aligning the
builds removes one comparison variable; it does not claim that a fresh GO return
was reproduced or that process-death summary navigation is fixed.

Private backups, instrumentation, screenshots, UI hierarchies and hash checks:
`/home/sean/overdex-device-backups/2026-11-05-directory-search/`.
