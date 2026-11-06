# Screen navigation and reachability audit — November 5, 2026

This is a source audit of the working tree in
`/home/sean/AndroidStudioProjects/Overdex`, branch `odx-recovery`, based on
`5c4e3aa5` plus the existing local changes. It covers all 35 public top-level
`*Screen` composables under `ui/screens`, their actual callers, Directory commands,
the NavHost, CRT/LCD input ownership, and relevant secondary screens. Source line
numbers describe the working tree at this review and can move with later edits.

No menu behavior was changed by this audit. No build, test, emulator, or physical
device check was performed for it. The findings below establish source wiring,
not observed device failures or a claim that any of these issues is fixed.
Search reset work and the GO return workflow are separate from this scan.

The companion [GO return-path audit](2026-11-05-go-return-paths.md) compares both
installed builds and process state. It finds matching summary-routing logic,
while documenting the memory-only handoff's full-process-restart boundary.
The build mismatch is real but is not a demonstrated cause of the reported
return-screen difference. The [search-reset verification](../verification/2026-11-05-directory-search.md)
records the implemented search fix and subsequent checks/installations.

## Classifications and input boundary

- **Reachable:** a current Directory, screen, intent, or archive action leads to it.
- **Stranded:** a reachable surface contains an action or content without the
  necessary normal touch/instrument/remote path.
- **Dormant:** code or a route exists, but no current production entry was found.
- **Intentional tooling/device behavior:** build guards, previews, or explicit
  device filters explain the difference; this alone is not a navigation defect.
- **Legacy-active:** still reachable, while current product direction places the
  responsibility elsewhere. This does not authorize its removal or revival.

The CRT's input boundary is deliberate. The shell applies `glassShield()` at
[ODXFiShell.kt:590](../../app/src/main/java/com/example/overdex/ui/ODXFi/ODXFiShell.kt#L590).
[PokedexComponents.kt:66–71](../../app/src/main/java/com/example/overdex/ui/components/PokedexComponents.kt#L66)
consumes CRT pointer changes in the initial event pass. Module content is placed
inside that area at
[ODXFiShell.kt:646](../../app/src/main/java/com/example/overdex/ui/ODXFi/ODXFiShell.kt#L646).
An ordinary CRT button or scroll gesture therefore needs a corresponding LCD or
instrument action. Removing GlassShield is not the proposed remedy.

The LCD supports child controls: when custom `lcdContent` is supplied,
[InstrumentLCD:184](../../app/src/main/java/com/example/overdex/ui/components/PokedexComponents.kt#L184)
does not install the shell's generic drag/tap recognizers. The existing LCD buttons,
lists, sliders, and replay controls are not automatically blocked by GlassShield.

## Concrete findings

### P2 — QR result Cancel cannot resume scanning

**Classification:** reachable screen, stranded action.

After a successful scan, `TrainerFoundPreview` supplies A = link trainer and
B = `onBack`, which leaves the scanner:
[QrScannerScreen.kt:194–203](../../app/src/main/java/com/example/overdex/ui/screens/QrScannerScreen.kt#L194).
The displayed Cancel button is the only caller of `onDone` at
[lines 252–255](../../app/src/main/java/com/example/overdex/ui/screens/QrScannerScreen.kt#L252).
That button is inside the shielded CRT. `onDone` would clear `scannedIdentity` and
resume the camera, as wired at
[line 85](../../app/src/main/java/com/example/overdex/ui/screens/QrScannerScreen.kt#L85).

The user can link with A or leave with B, but cannot activate Cancel/scan again
through the normal controls. A later focused fix could map B to `onDone` on the
result preview and expose Link/Scan Again on its LCD. That change was not made.

### P2 — Remote/gamepad cannot open Match Calibration image menus

**Classification:** reachable on touch instrument buttons; stranded with remote
and gamepad key input.

The screenshot library opens only through Hold A, and the screenshot picker
through Hold Select:
[MatchCalibrationScreen.kt:554–560](../../app/src/main/java/com/example/overdex/ui/screens/MatchCalibrationScreen.kt#L554).
The image menu includes screenshot folder selection and All/Shape/Device Folder
filters at
[lines 788–807](../../app/src/main/java/com/example/overdex/ui/screens/MatchCalibrationScreen.kt#L788).
These long callbacks are connected by the Match Calibration route in
[MainActivity.kt:1625–1627](../../app/src/main/java/com/example/overdex/MainActivity.kt#L1625).

The shell's remote key handler invokes only first-press `onA` for
D-pad Center/Enter/gamepad A at
[ODXFiShell.kt:485–490](../../app/src/main/java/com/example/overdex/ui/ODXFi/ODXFiShell.kt#L485),
and first-press `onSelect` for Menu/gamepad Select at
[lines 518–521](../../app/src/main/java/com/example/overdex/ui/ODXFi/ODXFiShell.kt#L518).
Neither branch dispatches `onALong` or `onSelectLong` for held/repeated key events.
The visible touch buttons do have long callbacks at lines 721 and 744.

Device Calibrations itself opens through Start and is reachable. The narrower
gap is Images. A later fix could support remote long actions or offer Images
through an ordinary menu/LCD action. No remote device was exercised in this audit.

### P2 — Synthetic partner action is not restricted to debug builds

**Classification:** reachable developer action in the ordinary Profile menu.

`TrainerProfileScreen` adds `LINK_DEBUG` whenever no partner exists at
[TrainerProfileScreen.kt:67–74](../../app/src/main/java/com/example/overdex/ui/screens/TrainerProfileScreen.kt#L67),
invokes `linkDebugPartner()` at
[lines 95–98](../../app/src/main/java/com/example/overdex/ui/screens/TrainerProfileScreen.kt#L95),
and renders the row at
[lines 261–265](../../app/src/main/java/com/example/overdex/ui/screens/TrainerProfileScreen.kt#L261).
There is no `BuildConfig.DEBUG` guard on these paths.
[PartnerRepository.kt:97–108](../../app/src/main/java/com/example/overdex/data/PartnerRepository.kt#L97)
persists a synthetic `debug-partner` named `Atlas Test`.

This differs from the consistently debug-gated Accessibility Probe. A later
focused change could gate both the row and activation. It was not changed here.

### P2 — BATTLE → History is a permanent empty legacy entry

**Classification:** reachable stub; hypothetical list actions also stranded.

The Directory exposes History at
[PokedexViewModel.kt:355](../../app/src/main/java/com/example/overdex/ui/PokedexViewModel.kt#L355).
Its repository always returns `emptyList()`:
[BattleHistoryRepository.kt:7](../../app/src/main/java/com/example/overdex/data/BattleHistoryRepository.kt#L7).
Consequently the current screen always shows “No Battles Yet,” irrespective of
the canonical saved-match archive.

There is a second, currently masked gap: the route supplies B/Start/Select, but
no row selection, Up/Down, or A handler at
[MainActivity.kt:719–740](../../app/src/main/java/com/example/overdex/MainActivity.kt#L719).
Rows would depend on their CRT `.clickable` at
[BattleHistoryScreen.kt:100–104](../../app/src/main/java/com/example/overdex/ui/screens/BattleHistoryScreen.kt#L100).
Adding only A would not repair the empty repository.

Records and Match Summary have separate canonical archive-backed paths. A later
product choice could route this redundant entry to the existing saved-match
directory or deliberately retire its navigation. This audit changes neither the
menu nor retained source.

**Subsequent resolution, November 5:** the user approved consolidating this flow.
BATTLE → Match History now opens the existing archive browser, and each selected
match opens its own summary before inspection/replay. The old History route
redirects for restored navigation; the unused repository/screen source is retained.
See [Match History verification](../verification/2026-11-05-match-history.md).

### P2 — Legacy Shared Timeline has unreachable overflow content

**Classification:** legacy-active surface with stranded content; not a proposal
to revive Trainer Timeline.

A linked partner's Profile still exposes the `shared_timeline` route through
`VIEW_TIMELINE` at
[TrainerProfileScreen.kt:73–74](../../app/src/main/java/com/example/overdex/ui/screens/TrainerProfileScreen.kt#L73)
and [line 100](../../app/src/main/java/com/example/overdex/ui/screens/TrainerProfileScreen.kt#L100).
The screen passes only B to the shell at
[SharedTimelineScreen.kt:41–43](../../app/src/main/java/com/example/overdex/ui/screens/SharedTimelineScreen.kt#L41).
Its populated `LazyColumn` at
[lines 55–69](../../app/src/main/java/com/example/overdex/ui/screens/SharedTimelineScreen.kt#L55)
has no remembered state or instrument/LCD scrolling. CRT swipes are blocked, so
entries extending beyond the visible viewport lack a normal scrolling path.

The recorded product direction is to migrate useful sharing duties into Trainer
Comms and then retire the old surface, not revive a separate Trainer Timeline:
[current feature status](../reference/current-feature-status.md#L139) and
[Trainer Comms plan](../plans/trainer-comms-sharing.md#L16).
Disposition belongs to that migration. No feature revival, deletion, or scrolling
implementation is authorized by this finding alone.

### P3 — QR scanning offers no camera-unavailable explanation

**Classification:** device capability gap; source finding, not a tested TV failure.

Camera hardware is optional in
[AndroidManifest.xml:21](../../app/src/main/AndroidManifest.xml#L21).
Profile still offers Scan Trainer QR without checking for a back camera.
[QrScannerScreen.kt:175–185](../../app/src/main/java/com/example/overdex/ui/screens/QrScannerScreen.kt#L175)
requests `DEFAULT_BACK_CAMERA` and silently catches binding failure. A camera-less
device, or one lacking a usable back camera, can enter this route without a
specific explanation. A later fix could surface availability/binding errors or
explain the unavailable action. No camera or permission workflow was run here.

## Actual entry paths

The Directory is built in
[PokedexViewModel.kt:315–359](../../app/src/main/java/com/example/overdex/ui/PokedexViewModel.kt#L315),
with dynamic binder entries updated at lines 365–372. MainActivity consumes the
commands at
[MainActivity.kt:533–563](../../app/src/main/java/com/example/overdex/MainActivity.kt#L533).

- **Launch Droidball** → readiness → team setup or team library.
- **OVERDEX** → species binder → Pokémon detail → evolution detail or search.
- **BATTLE** → Add Pokémon tree fields, Current Team, BINDERS, Match Summary,
  Replay Editor, Records, and the legacy History entry.
- **Owned binder** → specimen detail → Edit/Records/Delete; `+` opens guided
  registration → manual wizard or screenshot registration calibration.
- **INTUEOR** → display settings, Match Calibration, Match Summary, and the
  Accessibility Probe in debug builds.
- **PROFILE** → QR identity/scanner, linked partner's legacy Shared Timeline,
  and Trainer Comms regardless of partner linkage.
- **Match Summary** → saved-match directory or import → archive viewer → replay
  or editor. Battle Records can also enter replay directly.
- **Additional entry points:** notification start goes to readiness;
  `open_guided_registration` activity intents go to guided registration; opening
  an archive selects the archive viewer. These are explicit actions/effects in
  MainActivity, not hidden gestures.

No production `navigate(...)` target without a corresponding registered NavHost
destination was found. A route's registration is not, by itself, evidence that
the user can enter it; the dormant registrations below illustrate the difference.

## Coverage inventory: all 35 public top-level Screen composables

“Main” below means `app/src/main/java/com/example/overdex/MainActivity.kt`.
The declaration links identify the exact screen source. The caller column lists
production callers, excluding comments and Studio preview invocations.

| Screen declaration | Actual route / caller | Reachability |
| --- | --- | --- |
| [AccessibilityProbeScreen:43](../../app/src/main/java/com/example/overdex/ui/screens/AccessibilityProbeScreen.kt#L43) | `accessibility_probe`; Main:1283 | Debug only, via INTUEOR → Accessibility Probe. |
| [BattleHistoryScreen:40](../../app/src/main/java/com/example/overdex/ui/screens/BattleHistoryScreen.kt#L40) | `battle_history`; Main:734 | Reachable Directory stub; see finding. |
| [BattlePreviewScreen:20](../../app/src/main/java/com/example/overdex/ui/screens/BattlePreviewScreen.kt#L20) | `battle_preview`; Main:1678 | Dormant runtime entry; intentional HUD workshop has four Studio previews. |
| [BattleRecordsScreen:33](../../app/src/main/java/com/example/overdex/ui/screens/BattleRecordsScreen.kt#L33) | `battle_records?ownedId={ownedId}`; Main:716 | Directory Records or an owned card's Records; LCD/instrument filters and replay. |
| [BattleTeamLibraryScreen:25](../../app/src/main/java/com/example/overdex/ui/screens/BattleTeamLibraryScreen.kt#L25) | `team_library`; Main:1219 | Current Team, readiness, or team setup. |
| [BattleTimelineScreen:28](../../app/src/main/java/com/example/overdex/ui/screens/BattleTimelineScreen.kt#L28) | `battle_log`; Main:771 | Dormant route: no incoming navigation found. |
| [BinderLibraryScreen:29](../../app/src/main/java/com/example/overdex/ui/screens/BinderLibraryScreen.kt#L29) | `binders`; Main:1171 | BATTLE → BINDERS → Add or Edit Binders. |
| [CalibrationScreen:35](../../app/src/main/java/com/example/overdex/ui/screens/CalibrationScreen.kt#L35) | `calibration`; Main:838 | Dormant older calibration command; active Match Calibration is separate. |
| [ChatScreen:67](../../app/src/main/java/com/example/overdex/ui/screens/ChatScreen.kt#L67) | No production caller or route. | Dormant predecessor; TrainerCommsScreen is active. |
| [CurrentBattleTeamScreen:47](../../app/src/main/java/com/example/overdex/ui/screens/CurrentBattleTeamScreen.kt#L47) | `current_battle_team`, `droidball_team_setup`; Main:1210,1242 | Directory Current Team and readiness/team library setup. |
| [DroidballReadinessScreen:31](../../app/src/main/java/com/example/overdex/ui/screens/DroidballReadinessScreen.kt#L31) | `droidball_readiness`; Main:1228 | Directory Launch Droidball, applicable Start, notification request. |
| [EditSpecimenScreen:32](../../app/src/main/java/com/example/overdex/ui/screens/EditSpecimenScreen.kt#L32) | `specimens/edit/{id}`; Main:1162 | Owned detail Edit or team-library card edit; delegates to wizard. |
| [GuidedRegistrationScreen:24](../../app/src/main/java/com/example/overdex/ui/screens/GuidedRegistrationScreen.kt#L24) | `guided_registration`; Main:1178 | Owned binder `+` or registration activity intent; LCD/instrument actions. |
| [MainMenuScreen:27](../../app/src/main/java/com/example/overdex/ui/screens/MainMenuScreen.kt#L27) | `main_menu`; Main:698 | NavHost start destination; boot then Directory. |
| [MatchArchiveDirectoryScreen:35](../../app/src/main/java/com/example/overdex/ui/screens/observatory/MatchArchiveDirectoryScreen.kt#L35) | `match_archive_directory`; Main:1415 | Match Summary → Matches or editor → archives. |
| [MatchArchiveViewerScreen:59](../../app/src/main/java/com/example/overdex/ui/screens/observatory/MatchArchiveViewerScreen.kt#L59) | `match_archive_viewer`; Main:1491 | Opened/imported/selected archive; contains replay and inspection modes. |
| [MatchCalibrationScreen:78](../../app/src/main/java/com/example/overdex/ui/screens/MatchCalibrationScreen.kt#L78) | `match_calibration`; Main:1641 | INTUEOR → Match Calibration; image-menu remote gap above. |
| [MatchReplayScreen:114](../../app/src/main/java/com/example/overdex/ui/screens/observatory/MatchReplayScreen.kt#L114) | No standalone route; BattleRecordsScreen:131, MatchArchiveViewerScreen:149, ReplayEditorScreen:139 | Reachable nested scene; LCD transport supplied to containing shell. |
| [MatchSummaryScreen:134](../../app/src/main/java/com/example/overdex/ui/screens/observatory/MatchSummaryScreen.kt#L134) | `timeline_viewer` → MatchSummaryRoute → call at MatchSummaryScreen.kt:121 | Directory Match Summary, INTUEOR, and explicit summary-return effect. |
| [ModuleScreen:21](../../app/src/main/java/com/example/overdex/ui/screens/ModuleScreen.kt#L21) | `module/{title}/{status}/{description}`; Main:794 | Only incoming callback is a History row; currently masked by empty repository/stranded row. |
| [MyCollectionScreen:97](../../app/src/main/java/com/example/overdex/ui/screens/MyCollectionScreen.kt#L97) | `specimens/collection?binder={binder}`; Main:1113 | Directory BINDERS or binder manager; controls supplied through enclosing shell. |
| [OwnedPokemonDetailScreen:42](../../app/src/main/java/com/example/overdex/ui/screens/OwnedPokemonDetailScreen.kt#L42) | No production caller or route. | Dormant predecessor; SpecimenDetailScreen is active. |
| [PokedexListScreen:49](../../app/src/main/java/com/example/overdex/ui/screens/PokedexListScreen.kt#L49) | `list`; Main:950 | Directory OVERDEX or detail search; enclosing shell connects binder/search controls. |
| [PokemonDetailScreen:55](../../app/src/main/java/com/example/overdex/ui/screens/PokemonDetailScreen.kt#L55) | `detail/{id}?binder={binder}`; Main:1009 | Species binder selection and evolution navigation; instrument focus selects content. |
| [QrIdentityScreen:27](../../app/src/main/java/com/example/overdex/ui/screens/QrIdentityScreen.kt#L27) | `qr_identity`; Main:883 | Profile → Show My QR; observation content plus B return. |
| [QrScannerScreen:36](../../app/src/main/java/com/example/overdex/ui/screens/QrScannerScreen.kt#L36) | `qr_scanner`; Main:890 | Unlinked Profile → Scan Trainer QR; result Cancel gap above. |
| [RegistrationCalibrationScreen:44](../../app/src/main/java/com/example/overdex/ui/screens/RegistrationCalibrationScreen.kt#L44) | `registration_calibration`; Main:1203 | Guided registration → Screenshots / Draggy Boxes; actionable LCD, instrument adjustment. |
| [ReplayEditorScreen:42](../../app/src/main/java/com/example/overdex/ui/screens/observatory/ReplayEditorScreen.kt#L42) | `replay_editor`; Main:1572 | Directory Match → Replay Editor or archive viewer; nested playback and LCD editing. |
| [ReviewKitScreen:11](../../app/src/main/java/com/example/overdex/ui/screens/ReviewKitScreen.kt#L11) | No production caller or route. | Dormant placeholder; analysis/save/clear buttons are no-ops. |
| [SharedTimelineScreen:30](../../app/src/main/java/com/example/overdex/ui/screens/SharedTimelineScreen.kt#L30) | `shared_timeline`; Main:876 | Linked Profile → View; legacy-active, stranded overflow content. |
| [SignalObservatoryScreen:41](../../app/src/main/java/com/example/overdex/ui/screens/SignalObservatoryScreen.kt#L41) | `signal_observatory`; Main:1324 | Directory INTUEOR; instrument focus operates launch/settings entries. |
| [SpecimenDetailScreen:32](../../app/src/main/java/com/example/overdex/ui/screens/SpecimenDetailScreen.kt#L32) | `specimens/detail/{id}`; Main:1146 | Owned binder or newly registered card; delegates to shared card detail controls. |
| [SpecimensScreen:27](../../app/src/main/java/com/example/overdex/ui/screens/SpecimensScreen.kt#L27) | No production caller or route. | Dormant older collection entry surface. |
| [TrainerCommsScreen:42](../../app/src/main/java/com/example/overdex/ui/screens/TrainerCommsScreen.kt#L42) | `private_chat`; Main:868 | Profile → Trainer Comms; independent of legacy partner linkage. |
| [TrainerProfileScreen:44](../../app/src/main/java/com/example/overdex/ui/screens/TrainerProfileScreen.kt#L44) | `trainer_profile`; Main:850 | Directory PROFILE; instrument navigation, native editing dialogs, synthetic-partner gap above. |

The four entirely uncalled public screens are `ChatScreen`,
`OwnedPokemonDetailScreen`, `ReviewKitScreen`, and `SpecimensScreen`. This is an
inventory of dormant source, not a deletion list.

## Secondary screens and wiring checked

- [AddOwnedPokemonWizard:44](../../app/src/main/java/com/example/overdex/ui/screens/AddOwnedPokemonWizard.kt#L44)
  is registered as `add_pokemon_wizard`, entered from guided registration's
  Enter By Hand, and reused by EditSpecimenScreen. It is outside the `*Screen`
  count because its public name ends in `Wizard`.
- [OwnedPokemonCardDetailScreen:58](../../app/src/main/java/com/example/overdex/ui/screens/SpecimenDetailScreen.kt#L58)
  is an internal shared implementation called by SpecimenDetailScreen. Records,
  Edit, Delete, and delete confirmation have LCD/instrument paths at lines 108–150;
  their presence in CRT content does not make those actions stranded.
- [TrainerFoundPreview:194](../../app/src/main/java/com/example/overdex/ui/screens/QrScannerScreen.kt#L194)
  is the scan-result branch; Link has A, while Cancel/rescan has the gap above.
- [MatchSummaryRoute:36](../../app/src/main/java/com/example/overdex/ui/screens/observatory/MatchSummaryScreen.kt#L36)
  loads the source and calls MatchSummaryScreen at line 121. It is the active
  `timeline_viewer` route; the older `battle_log` registration is separate.
- [DirectoryOwnedPokemonEditor](../../app/src/main/java/com/example/overdex/ui/screens/DirectoryOwnedPokemonEditor.kt)
  is embedded in the main Directory rather than a registered screen. Its tree
  fields, LCD, and keyboard handlers are wired by the `main_menu` route.
- [DirectoryCropPreviewLcd](../../app/src/main/java/com/example/overdex/ui/components/DirectoryCropPreviewLcd.kt)
  and [DirectoryCropEditor](../../app/src/main/java/com/example/overdex/ui/components/DirectoryCropEditor.kt)
  are the contextual main-menu LCD path, not an additional hidden NavHost route.
- [MatchReplayScreen](../../app/src/main/java/com/example/overdex/ui/screens/observatory/MatchReplayScreen.kt)
  exports LCD controls through its callers. Archive viewer, editor, and Records
  do not require touching the CRT to use replay transport.
- [HandheldNavigation](../../app/src/main/java/com/example/overdex/ui/components/HandheldNavigation.kt),
  [ODXFiShell](../../app/src/main/java/com/example/overdex/ui/ODXFi/ODXFiShell.kt),
  and [InstrumentLCD](../../app/src/main/java/com/example/overdex/ui/components/PokedexComponents.kt#L157)
  were checked for the input ownership behind the above classifications.

## Dormant commands, hidden controls, and intentional differences

- `battle_log` is registered at
  [MainActivity.kt:743](../../app/src/main/java/com/example/overdex/MainActivity.kt#L743)
  but has no incoming navigation. `OpenBattleLogs` now opens `timeline_viewer`.
- `calibration` and `battle_preview` have command handlers at
  [MainActivity.kt:551](../../app/src/main/java/com/example/overdex/MainActivity.kt#L551)
  and line 559, but no producer of `OpenCalibration` or `OpenBattlePreview` was
  found. BattlePreviewScreen explicitly identifies itself as a HUD workshop and
  has four Studio previews; that is a reason to preserve tooling, not add a user
  menu automatically.
- `OpenTournament` and `OpenFilterSettings` are declarations only at
  [InstrumentCommand.kt:31–32](../../app/src/main/java/com/example/overdex/model/navigation/InstrumentCommand.kt#L31).
  Neither has an emitter or handler. They are not evidence of a broken current
  menu, because no current action emits them.
  **Tournament follow-up, November 5:** `OpenTournament` now has a Battle →
  Tournaments producer and a `tournaments` route. The private lobby/bracket is
  implemented; see the [acceptance record](../verification/2026-11-05-tournaments.md).
  The original finding remains applicable to `OpenFilterSettings`.
- The shell accepts `onLaunchProbe`, `onLaunchObservatory`,
  `onLaunchMatchCalibration`, and `onFilterSettingsChange` at
  [ODXFiShell.kt:294–301](../../app/src/main/java/com/example/overdex/ui/ODXFi/ODXFiShell.kt#L294),
  but never invokes those parameters. Passing them into a shell does not create
  a hidden launch gesture. The active launches/settings belong to the separately
  wired SignalObservatoryScreen. Removing unused API parameters is not required
  by this audit.
- **Accessibility Probe is intentionally debug only.** The launcher guard is at
  [MainActivity.kt:483](../../app/src/main/java/com/example/overdex/MainActivity.kt#L483),
  route registration is guarded at line 1254, and INTUEOR filters the item in
  [SignalObservatoryScreen.kt:69](../../app/src/main/java/com/example/overdex/ui/screens/SignalObservatoryScreen.kt#L69).
  This is not a missing release route.
- **Calibration device filters are explicit, not lost menus.** Match Calibration
  defaults the screenshot library to This Screen at
  [MatchCalibrationScreen.kt:157](../../app/src/main/java/com/example/overdex/ui/screens/MatchCalibrationScreen.kt#L157).
  Hold A exposes All/Shape/Device Folder filters. Start opens Device Calibrations;
  Show Other Devices is conditional on profiles from another device at
  [lines 626–632](../../app/src/main/java/com/example/overdex/ui/screens/MatchCalibrationScreen.kt#L626).
  A new/mismatched device can automatically receive Save Profile at lines 526–534.
  The remote long-action limitation is recorded separately above.
- **TV input behavior is explicit.** The shell detects television UI mode at
  [ODXFiShell.kt:347–355](../../app/src/main/java/com/example/overdex/ui/ODXFi/ODXFiShell.kt#L347)
  for its BackHandler. The D-pad/gamepad key mapping is installed on the shell
  generally, not only on a named device model. No separate model-specific
  production menu was found outside the calibration filters.

Potential follow-ups above are audit recommendations, not work completed in this
slice. A recent-search list would be a possible later UI option; it is not a
requirement for the separate Directory search-reset change.
