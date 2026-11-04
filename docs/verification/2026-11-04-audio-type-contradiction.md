# Keep legal audio candidates when type recognition contradicts identity

## Recorded failure

The Moondrop match recorded November 4 at 07:15 contains Golbat, Gengar, and
Drapion against Krabby, Dewpider, and Walrein. The user confirmed continuous
attacking in a Team GO Rocket battle. The copied archive contains 4,036 Articles;
all 535 declared artifacts passed hash and size verification. Its SHA-256 is
`0c3f5d02857d5a9ad7b9ffe5fd0c492503307aca04c91ac2b1ffc5b14171f65a`.

All 72 retained player type readings are incorrect: Water for Golbat/Gengar and
Electric for Drapion. The images contain the correct type icons inside correctly
positioned crops. Reproducing the current icon matcher on those source images
reproduces every accepted type list. The 12 named profiles and three actual
calibration preference files are byte-identical to the November 3 backup.

FastMoveAudioCandidateContext applied type filtering before the configured-player
move check. A type set compatible with none of the already resolved species/form
references removed that side's whole legal pool. Consequently, 44 of the 45
retained audio measurements omitted player candidates, and the requirement for
both sides' reference pools prevented normal reference acceptance.

## Change

A compatible type set continues to narrow the established reference family.
When no reference in that complete family has the witnessed types, audio search
keeps its existing identity-derived references and applies the existing legal
configured-player move checks. The conflicting type Article remains cited.

The fallback never reloads a broader species family, searches the whole sound
library, admits an illegal configured move, or recreates a missing active
identity. Incomplete and unknown form references remain unavailable. Compatible
regional-form narrowing, mirror opponents, roster invalidation, source-time
boundaries, and asynchronous snapshot invalidation retain their existing rules.

## Verification

- Focused context suite: 25 tests pass. Regression cases cover the recorded
  Golbat/Water, Gengar/Water, and Drapion/Electric contradictions, opponent form
  alternatives, unknown and switched identities, stale/illegal configuration,
  explicit Alolan identity, incomplete references, and crossing audio windows.
- A private JVM check replays the actual archive's context events in publication
  order and compares the previous implementation with the patched implementation
  at all 45 retained audio windows. Player candidates are available in 42 windows
  instead of one; complete two-side context likewise rises from one to 42. One
  window remains invalid across a context boundary, and two other windows still
  lack a player pool. All retained old legal side pools are unchanged.
- That archive check uses the current Game Master and actual production context
  classes. It does not re-match PCM, manufacture accepted sounds or attack counts,
  rewrite the archive, or demonstrate live recognition accuracy.
- Final full JVM suite: 1,652 tests across 246 suites, no failures, errors, or
  skips. Debug APK build passes. Private checker sources are outside the checkout
  and excluded from that final suite and APK.
- Installed the tested APK on idle Moondrop `MDPH00124112500414` and launched
  Overdex. Installed SHA-256 matches the build:
  `bf9b0dc4dbb41a33f57fd33184cd6df206f90dc6babde49addc295780259cc23`.
  Before/after backups contain 336 files each; all 314 compared user-data and
  calibration files are unchanged. All four Room tables have identical logical
  contents, including 29 owned Pokémon. The reviewed phone archive is unchanged.
  No instrumentation runs on the user's phone. App launch is not a new battle
  acceptance check.

Private archive checks, logs, and the pre-install data backup are under
`/home/sean/overdex-device-backups/2026-11-04-audio-type-contradiction-fix/`.

## Remaining boundaries

This fixes candidate exclusion, not type-icon classification. The icon matcher
still needs scale-aware badge extraction, consistent candidate scoring, and
honest handling of unavailable color evidence. The separate missing charged-move
episode, incompatible Fast Move timing pairs, and incomplete Rocket result
recording remain review findings. A new recorded battle is required to assess
live sound acceptance and resulting event coverage after this change.
