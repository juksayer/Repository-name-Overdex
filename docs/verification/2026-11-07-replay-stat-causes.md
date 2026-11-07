# Named causes for Replay stat effects

November 7, 2026. The Sierra review raised a suspected Poison Fang recipient
inversion. The preserved source images instead confirm Pancham's Crunch lowering
Golbat's defense. Replay's side attribution is correct for that effect, but the
unnamed stat badge did not make its cause clear.

## Evidence and scope

The [Sierra recording](2026-11-07-sierra-hp-review.md) contains three independent
defense-down sound cues. Only the middle cue has a named charged-move attribution:
the source announcement says `Pancham used Crunch!`, and a subsequent source crop
shows `DEFENSE FELL!` above Golbat's HP bar and back sprite. Its archived
`StatChangeObserved` correctly names Crunch and the player recipient.

The other two sounds have no resolved charged-move announcement or affected-side
Article. They may correspond to the reported Poison Fang uses, but the recording
does not establish that attribution. They remain unknown; configured moves alone
cannot turn those sounds into a recorded Poison Fang effect.

## Resulting Replay behavior

- An attributed stat effect keeps its existing recipient, arrow, source time,
  duration, and combatant-appearance guard. A smaller second badge line now names
  its recorded causal move, such as `Crunch` below `DEF ↓` on Golbat.
- When a recorded player Poison Fang attribution is present, its badge is on the
  opponent recipient and names `Poison Fang`.
- Event details retain the causal move as well as the side and stat direction.
- A sound without a resolved side stays in the separate `SIDE UNCONFIRMED`
  presentation, with no badge attached to either Pokémon and no invented move.
- Live inference, original testimony, archive contents, and the ODX-Fi shell are
  unchanged. Replay is showing existing recorded information more clearly.

## Regressions and validation

Added inference and model cases for opponent Crunch affecting the player,
Poison Fang affecting the other side for either performer, the Sierra cue
sequence's two unknown effects, and a named player Poison Fang on Pancham.

The rendered regression drives the actual Replay model and battlefield through
Crunch, Poison Fang, and an unknown cue. It checks a single stat arrow with its
causal move on the correct left/right recipient, and no combatant badge for the
unassigned sound. It uses synthetic Articles and existing local sprites, rather
than private battle files.

The final full suite passes **1,883 unit tests**, with zero failures, errors, or
skips. Debug app and Android-test APK builds pass. On the API 34 emulator,
`ReplayStatChangeDeviceTest` and `HpObservationDiagnosticsDeviceTest` together
return **OK (4 tests)**, including the new rendered cause/recipient check.

Installed on the Moondrop with Droidball stopped, without data clear, uninstall,
or phone instrumentation. The installed APK matches the tested build:
`89e5ec8a34333320cea53fcbdac06c9bda16ebd05b5d4b02bc3c2cb1a886f8a5`.
The [Sierra diagnostic review](2026-11-07-sierra-hp-review.md) records the protected
user data and installation comparison. The missing Poison Fang announcements
and final-hit capture remain separate recognition work.
