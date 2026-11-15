# Automatic finishing recovery: reviewed Moondrop match

The November 15 12:12 Moondrop recording and its independently preserved match
archive contain the trainer-reviewed Sealeo and Vaporeon Incinerate KOs. This
slice recovers supported events in completed Replay without altering the original
archive, video, testimony or the separately saved trainer corrections. Live
recognition and live HUD behavior are unchanged.

For Sealeo, two portions of the mixed Incinerate sound support one occurrence.
The player HP-bar motion is compatible with the configured Incinerate, the
opponent's localized Sealeo cry has a repeat-departure candidate, and a separate
team-count loss has already identified Sealeo as fainted. With appearance,
phase, ownership and competing-attack checks satisfied, Replay recovers the
player's attack and associates it with that independently confirmed faint. Its
landing is shown at the cry as a **presentation estimate**; that time is not a
measured HP tick. Source clocks and references for each observation remain
available.

For Vaporeon, mixed Incinerate audio, a recipient visual effect and a compatible
complete-bar color change support a player's hit. The archive also retains a
partial Vaporeon-like cry: two separate reference portions align to the same
estimated onset and pass the tested competing-reference margin. Replay exposes
this as a possible cry, with an unknown purpose. The retained bar contains
ambiguous columns and the saved witness images do not cover the actual collapse;
the audio, result and prior count alone do not resolve a faint or its cause.
The separately video-reviewed correction remains available. The original video
shows the departure, but reviewing it externally does not make the missing
archived image an automatically retained witness.

The matching and presentation tests cover source-time ordering, competing
attacks and sounds, mirror ownership, charge and appearance boundaries, living
switches, nonlethal damage, unknown bar pixels, tiny surviving HP, duplicate
trainer and automatic actions, and reverse seeking. A separate saved match
contains an opponent Swampert that switched and subsequently returned alive;
its switch is not credited as a KO. The independently recorded video controls
and HP fixtures supplement synthetic boundary tests. These are saved-match and
automated tests, not evidence of live-device performance or universal audio
recognition accuracy.

The 1 TB drive retains the diagnostic test logs and inputs in
`/mnt/overdex-backup/archives/overdex-device-backups/`. Source checksums:

* Reviewed archive: `927c311c86d9ee8d834007db3acb22730b37c62a0040995be40e7a6b4fbc28fe`.
* Source video: `9255d72b94851681276b047bb7f704ee90ca14aa3c8916e1e8e4efd122afd53f`.
* Separate living-switch archive: `e3620d1e8c954537806e4ac1760f4bfc313c4fe4a70bbc0f46b1483baa555aaf`.

The saved team's Gourgeist has Incinerate in the bundled game data. Its move
configuration is treated as legitimate context; configuration alone never
creates an attack.

The full debug unit suite passed with 3,111 tests and 11 skipped, and the debug
build succeeded. The build was installed and launched on Moondrop
`MDPH00124112500414`; the original on-device archive hash and all four existing
reviewed correction entries were verified unchanged. Live battle and visual
device Replay validation have not been performed for this slice.
