# Pokémon GO return-path audit — November 5, 2026

## Scope and conclusion

This was a read-only investigation of why Pixel 4 and MOONDROP can return from
Pokémon GO to different Overdex screens. It inspected current source, installed
APK metadata and relevant bytecode, Android task/process state, and the capture
breadcrumb. It did not launch, tap, stop, install, or otherwise change either
phone. No production source was changed and no builds or tests were run for this
audit. This document is the only audit output added to the repository.

The summary-return implementation is the same in the relevant portions of both
installed APKs. A completed or stopped match queues a one-time summary return in
the application-owned ViewModel. That survives Activity recreation within the
process, but not full process death. Ordinary returns without a pending summary
retain the existing navigation destination. Cold process recovery preserves
recordings and presents an interruption notice; it does not recreate the summary
handoff.

At inspection, Pixel had no Overdex process, while MOONDROP had a running process.
This is a material state difference consistent with the documented cold-restart
boundary. It does **not** establish that a process death caused the user's earlier
reported return behavior. No fresh GO return was performed or reproduced.

Current return behavior is unchanged. The companion
[search-reset verification](../verification/2026-11-05-directory-search.md)
records the subsequent validation and installation on both phones.

## Implemented return matrix

| Entry or event | Preconditions | Destination or effect | Current owner |
| --- | --- | --- | --- |
| Recents, launcher, or notification body / **Overdex** | Existing process; no pending summary | Existing navigation stack remains. `onStart` returns Droidball presentation to the shell; `onResume` sets visibility and notification state but performs no navigation. | `MainActivity.onStart`, `onResume`, `PokedexApp` |
| Return after a match result | Current match emits `MatchEnded`; process survives | The current match is retained and offered to the summary handoff. When MainActivity is RESUMED, navigate to `timeline_viewer` (Match Summary), pop up to `main_menu` without removing it, and acknowledge the handoff once. | `PokedexViewModel.retainAndArchiveMatch`, `MatchSummaryReturnEffect` |
| Return after explicit Stop | Non-registration match exists; process survives | Stop retains the match and offers the same one-time summary handoff, including an honestly incomplete record when no decisive result was observed. Stop does not itself launch Overdex above GO. | `PokedexViewModel.stopObservation` |
| Capture stops or service signals stopped | Current match exists; process survives | Retain the current record and offer summary through the same path. Automatic-stop reasons remain independent testimony; return navigation does not invent a result. | `PokedexViewModel` service signal collector and stop path |
| A further match starts before return | Prior summary is still pending | Clear the pending navigation request. A late result from the released match may be archived but cannot replace the current match or offer its summary. | `PokedexViewModel.startFreshMatch`, `retainAndArchiveMatch` |
| Activity recreation with process alive | Application-owned ViewModel still exists | Match ownership and pending summary survive. The navigation framework can restore its own saved stack; the pending summary, if any, is consumed only at RESUMED. | `OverdexApplication.viewModelStore`, `MainActivity.pokedexViewModel` |
| Full process restart | Previous process ended | New ViewModel and empty in-memory handoff. Navigation starts at `main_menu` unless Android restores saved navigation state. Startup recovers prior checkpoints and publishes an interruption notice, without offering summary navigation. Saved Match Summary remains manually available. | `OverdexApplication.onCreate`, `PokedexApp` |
| Notification **Start** | Capture service is stopped | Open/reuse MainActivity with `DROIDBALL_START_ACTION`; consume the action and navigate to `droidball_readiness`. | `DroidballNotification`, `MainActivity.consumeDroidballNotificationIntent` |
| Notification **Next match** | Session/match IDs match the active capture | Save the current record and start a fresh match using the existing projection. Starting the new match clears the pending summary. | `DroidballNotificationReceiver`, `PokedexViewModel` |
| Registration overlay **Review** | Guided registration mode | Open/reuse MainActivity with `open_guided_registration=true`; navigate to `guided_registration`. This is an intentional separate return path. | `DroidballService` registration overlay, `MainActivity` |

Readiness launch removes the readiness route before starting observation.
Launching from team setup returns the navigation stack to `main_menu` first.
Consequently, a normal return during an ongoing match need not have the same
destination as a return after a completed or stopped match.

No return-screen preference was found in the inspected return path. The
persisted `droidball_runtime` value is only a process-death breadcrumb; it is not a
route or a second runtime state machine. Its consumed `false` value cannot rule
out an earlier interruption, because process startup consumes and clears an
active breadcrumb.

## Installed-build and process snapshot

Snapshot date: November 5, 2026. Update times below are the device package
manager's reported values; their timezone was not independently established.
This snapshot predates the search-reset installation in this task. Both phones
were subsequently aligned to the checked APK; see the
[installation and search verification](../verification/2026-11-05-directory-search.md).

| Property | Pixel 4 | MOONDROP |
| --- | --- | --- |
| Serial | `99261FFAZ0017J` | `MDPH00124112500414` |
| Reported model | `Pixel_4` | `MD_PH_001` |
| Android release | 13 | 14 |
| Overdex package | `com.example.overdex` | `com.example.overdex` |
| Advertised version | `versionCode=1`, `versionName=1.0` | `versionCode=1`, `versionName=1.0` |
| Last package update | `2026-11-04 18:43:50` | `2026-11-05 15:15:12` |
| Package flags relevant to provenance | Debuggable, test-only | Debuggable |
| APK SHA-256 | `044648f6557fcd2089b90429e904eef871e5bcd9ac65f9e279316069ee8344c8` | `543621106610458065e2fb789501db0244da2f7e0ae5769b47829c9a84f8a09e` |
| Overdex process | Absent (`pidof` returned no PID) | Running, PID 4995 |
| MainActivity task | One existing task, not visible | One existing task, foreground/resumed |
| Droidball capture service | Not running | Not running |
| `capture_was_active` | `false` | `false` |

MOONDROP's installed APK hash matched the local
`app/build/outputs/apk/debug/app-debug.apk` at audit time. Pixel's installed APK
was different. The identical version metadata therefore does not establish
identical builds.

However, binary mismatch is **not a proven cause** of the return discrepancy.
Read-only decompilation and comparison established:

- MainActivity's lifecycle, intent handling, application-owned ViewModel, and GO
  launch methods matched after removing debug-line metadata.
- PokedexViewModel's relevant summary, retain, stop, and fresh-match methods
  matched. Differing methods were its constructor, B/Left/Right handling, and
  terminal swipe-word loading.
- The DEX containing `MatchSummaryHandoff` and `MatchSummaryReturnEffect` was
  byte-for-byte identical.
- The Compose summary callback had the same behavior in both APKs:
  `markBooted` → `timeline_viewer`, with `popUpTo("main_menu")`,
  `inclusive=false`, and `launchSingleTop=true`. Synthetic lambda names differed.
- Pixel's installed manifest and current source both used `singleTask` for
  MainActivity. Each phone had one Overdex task; no duplicate-shell explanation
  was demonstrated.

These checks establish code and state facts, not the historical sequence of the
user's reported return. Android release, memory pressure, whether a result/Stop
was recorded, whether the user started another match, and the chosen entry
action can produce different session conditions; none was isolated in a fresh
reproduction during this audit.

## Prior decision and persistence boundary

The [October 25 verification](../verification/2026-10-25-match-return-and-pixel-badge.md)
records an earlier hidden Filter Settings defect: the shell's START handler
opened settings while invoking Stop. That work removed the hidden settings
overlay and introduced the once-only RESUMED summary handoff. Both audited APKs
contain that later handoff implementation.

That report explicitly states that the handoff survives Activity recreation
through the application-owned ViewModel but is not persisted across full process
death, with saved Match Summary available manually after a cold restart. The
current implementation still has this boundary:

1. `MatchSummaryHandoff` stores pending source and deduplication state only in
   memory.
2. Retaining the current match offers that source before asynchronous archive
   export finishes.
3. `MatchSummaryReturnEffect` consumes it only at RESUMED and acknowledges it
   after navigation, preventing subsequent returns from stealing deliberate
   navigation.
4. Startup recovery preserves previous checkpoints and produces a notice; it
   does not reconstruct the pending handoff.

Persisting only a pending match ID would be insufficient with the present
summary screen. `MatchSummaryRoute` snapshots the in-memory source when one is
available; otherwise it loads the newest saved archive. It does not resolve a
requested match ID. A restored ID could therefore trigger navigation before its
archive or recovered checkpoint is ready and show a different record through
the newest-archive fallback. Simply restoring the ID also needs durable
acknowledgment and supersession rules to avoid reopening an old summary on later
launches.

## Future persistence and validation slice

If consistent automatic summary navigation across full process death is chosen
as a separate change, its minimum complete scope is:

1. Persist one pending match identity with an explicit consumption/acknowledgment
   policy. Define when an incomplete stopped record becomes eligible, without
   inventing a battle result.
2. Resolve that exact match against a successfully published or recovered
   archive. Wait for startup recovery/publication rather than selecting the
   newest unrelated archive. Handle an unavailable or unreadable requested match
   explicitly.
3. Bind the summary route and content to the selected identity after process
   recreation. Navigation alone must not discard that binding.
4. Preserve one-time consumption, clearing on a deliberate new match, rejection
   of late prior-match results, and existing session/match checks on notification
   controls.
5. Define the competing-intent priority for restored pending summary,
   notification Start, guided-registration Review, and deliberately restored
   navigation. Do not introduce a broad global navigation reset.

Focused verification should cover:

- Finished and explicitly stopped matches remain pending while GO is foreground,
  then open the correct summary once on return.
- Navigating to settings after consumption, leaving, and returning does not
  redirect again.
- Starting another match clears the prior request; a delayed old result cannot
  reopen it or replace the current summary.
- Activity recreation with a surviving process retains the pending match.
- Controlled process death before archive publication and after publication
  resolves the same requested match after recovery, without substituting the
  newest archive or claiming capture is still running.
- Repeated cold launches after acknowledgment do not reopen the old summary.
- Missing/unreadable archives and incomplete checkpoint tails produce an honest
  available-state or recovery result.
- Notification Start and registration Review reach their intended screens under
  the chosen priority rules, with no stale notification able to control a new
  session.

Physical acceptance on both Pixel 4 and MOONDROP should record the same installed
APK hash, starting screen, service state, exact entry/taps, whether the process
survived, pending/acknowledgment state where diagnostic support exists, and the
displayed match identity. A fresh finished/stopped GO return remains necessary
to verify that user workflow. No move-recognition change is implied by this
navigation slice.

## Current source evidence

- [AndroidManifest.xml](../../app/src/main/AndroidManifest.xml): MainActivity
  `singleTask`, launcher entry, non-exported capture service and control receiver.
- [MainActivity.kt](../../app/src/main/java/com/example/overdex/MainActivity.kt):
  application-owned ViewModel; `launchPokemonGo`; `onStart`, `onResume`,
  `onNewIntent`; `consumeDroidballNotificationIntent`; `PokedexApp` summary effect,
  navigation host, readiness/team-setup launch routes, and `timeline_viewer`.
- [PokedexViewModel.kt](../../app/src/main/java/com/example/overdex/ui/PokedexViewModel.kt):
  summary state; `startFreshMatch`; `MatchEnded` collection; service stop and
  next-match collection; `retainAndArchiveMatch`; `archiveCompletedMatch`;
  `stopObservation`.
- [MatchSummaryHandoff.kt](../../app/src/main/java/com/example/overdex/ui/navigation/MatchSummaryHandoff.kt)
  and [MatchSummaryReturnEffect.kt](../../app/src/main/java/com/example/overdex/ui/navigation/MatchSummaryReturnEffect.kt):
  in-process pending state, deduplication, clearing, and RESUMED-only consumption.
- [DroidballNotification.kt](../../app/src/main/java/com/example/overdex/battle/observation/DroidballNotification.kt):
  plain activity open, Start action, and session/match-bound broadcast controls.
- [DroidballService.kt](../../app/src/main/java/com/example/overdex/battle/observation/DroidballService.kt):
  registration Review activity intent and session capture lifecycle.
- [DroidballRuntimeMarker.kt](../../app/src/main/java/com/example/overdex/battle/observation/DroidballRuntimeMarker.kt)
  and [OverdexApplication.kt](../../app/src/main/java/com/example/overdex/OverdexApplication.kt):
  interruption breadcrumb and startup recovery notice.
- [ArchiveDirectoryManager.kt](../../app/src/main/java/com/example/overdex/battle/archive/ArchiveDirectoryManager.kt):
  durable publication, interrupted checkpoint/journal recovery, and archive listing.
- [MatchSummaryScreen.kt](../../app/src/main/java/com/example/overdex/ui/screens/observatory/MatchSummaryScreen.kt):
  `MatchSummaryRoute` uses a live source or newest saved archive, rather than a
  persisted requested-match identity.
