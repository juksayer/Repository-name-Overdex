# Directory registration and recorded charged moves — 2026-10-14

## Installed result

Checkout: `/home/sean/AndroidStudioProjects/Overdex`, branch `odx-recovery`.
Device: Moondrop `MD_PH_001`, serial `MDPH00124112500414`, Android 14.

The debug APK and instrumentation APK were installed with `adb install -r` after confirming no MediaProjection session was active. No uninstall or app-data reset was performed.

App APK SHA-256: `90bb3254d058c71e11a18806aebfce88dd5dc38e7094cfb9c87927447ea15c6c`.
Test APK SHA-256: `bffc2623a9bdbcc4354b6ba4a54d644dad86ff6a44674e2c9d8e551113441d03`.

## Owned Pokémon in the Directory Tree

`BATTLE → Add Pokémon` expands the registration fields within the existing tree. The LCD provides species browsing, text entry, and move details. A edits or selects, B finishes the current field, and START saves when browsing the registration branch. Save Card and Cancel Card are also explicit tree entries. Species is required; other fields may remain unknown.

The draft retains one individual ID through retries. A completed save uses the existing owned-Pokémon storage and shared team memberships. Repeated save presses cannot create duplicate cards, and a failed save preserves the draft.

Physical-device validation:

- `DirectoryOwnedPokemonDeviceTest`: **2 passed**. Production editor and instrument buttons, isolated in-memory Room storage, complete field entry, one saved card, failure/retry, draft restoration, and cancellation.
- Production `MainActivity` walkthrough: opened the tree branch, chose Pikachu, entered a nickname and CP, selected moves, changed flags/size/team fields, then cancelled. The real tree and LCD keyboard worked. This walkthrough deliberately did not leave a test card in the user's collection.
- The initial instrumentation failure was a test-fixture problem: static paging data omitted source load states, leaving the editor in its loading state. The harness now supplies completed load states. No production loading gate was weakened to pass the test.

Evidence: `/tmp/overdex-review-20261014/directory-device-test-fixed.log`, `directory-phone.log`, and `directory-phone.png`.

## Seven new recordings

All seven archives opened with matching manifest/timeline article counts. Every retained artifact hash passed verification: **7,360 artifacts total**. Originals remain on the phone; local copies and hashes are in `/tmp/overdex-review-20261014/`.

| Recording on October 13 | Match ID prefix | Articles | Verified artifacts | Recorded result |
| --- | --- | ---: | ---: | --- |
| 23:20:50 | `7a0e738e` | 2,876 | 719 | WIN |
| 23:27:05 | `c4c63422` | 5,194 | 1,368 | WIN |
| 23:33:54 | `2c55ae22` | 4,816 | 1,131 | WIN |
| 23:41:40 | `8dc0ed62` | 2,579 | 787 | WIN |
| 23:44:31 | `67b6fef8` | 3,676 | 1,161 | WIN |
| 23:49:45 | `f293ba49` | 3,094 | 919 | WIN |
| 23:54:16 | `b68a5ff2` | 5,114 | 1,275 | LOSS |

The archives contain the opponent sequences Sneasel/Sealeo/Vaporeon, Combusken/Magcargo/Flareon, and Plusle/Electrode/Jolteon. Stored identities establish archive coverage, not when the live HUD displayed them.

An audit sampled each replay every 100 ms using the bundled move reference and the repository's base-species aliases. It found no simultaneous completed charged-move animations in these seven projections. Eight episodes across three recordings remained untyped; their readings did not justify inventing a move. Output: `replay-projection.txt`. The temporary audit source is retained beside that output, outside the repository.

## Weather Ball name resolution

Pokémon GO announced `Abomasnow used Weather Ball!`; the reference names the legal move `Weather Ball (Ice)`. Previously the missing parenthetical qualifier prevented a whole-name match.

`MoveTextAliases` now recognizes the explicit Weather Ball display-name alias. Live inference and old-archive replay require a unique match within the performer's legal movepool. An ambiguous Ice/Fire candidate list still resolves to no move. This is not generic parenthesis stripping or a global move-type guess. Raw text and its observation time are preserved; inferred articles identify the alias basis. Duplicate readings do not spend energy twice.

The permanent regression fixture `app/src/test/resources/replay/abomasnow-weather-ball-20261013.json` contains 373 relevant articles from the 23:49:45 archive. IDs and payloads are retained; only the monotonic origin is shifted to the recorded GO. Regression checks recover Weather Ball (Ice), Ice Punch, Energy Ball, and Earth Power once each and leave the archive unchanged.

## Energy Ball cut off by a switch

The same recording announces Energy Ball at 48.994 seconds, then replaces Abomasnow with Camerupt about 1.259 seconds later. The decorative replay action lasted 1.8 seconds and did not throw until 78% of that duration, so the recorded switch removed the performer before its projectile appeared.

Replay now shortens that visual action to finish before the next recorded combatant appearance. The shortened action has no post-impact hold across that boundary. The projection records `PRESENTATION_FIT_BEFORE_SPECIES_CHANGE`; raw announcements, species-entry times, and HP measurements are not rewritten. A recipient-replacement regression also checks that the old move is absent at the new recipient's exact entry time.

`ReplayChargeTypeDeviceTest`: **3 passed** on the Moondrop. Checks cover a synthetic QTE sequence and two recorded fixtures using the phone's actual Pokémon repository. They verify the type icon below the performer, projectile flight, a single icon, and reverse scrubbing. The new Energy Ball flight screenshot was inspected visually: `abomasnow-weather-ball-20261013-2-1100000000.png` in the local evidence directory.

## Final checks and remaining limits

- `:app:testDebugUnitTest`: **723 passed**, zero failures, errors, or skipped tests.
- `:app:assembleDebug` and `:app:assembleDebugAndroidTest`: passed after the final production changes.
- Physical-device checks: 2 registration tests and 3 replay tests passed. Registration tests used the same unchanged registration implementation; replay tests used the final installed APK.
- All **11 calibration preference/profile files** have identical SHA-256 values before and after the installs and phone walkthrough. Snapshot records: `/tmp/overdex-review-20261013-summary/install/directory-20261014-before.json` and `directory-20261014-complete.json`.
- No changes were committed or pushed by this verification task.

The 23:41:40 recording has an unresolved match-start problem: the accepted GO is 95.227 seconds after recording began, despite timer-clearance support at 32.721 seconds and charged-move evidence before that GO. This task does not repair or silently replace that boundary. It needs a separate start-detector investigation.

These checks validate registration behavior, archive integrity, and replay rendering of the tested evidence. They do not establish complete live Fast Move counts, timely HUD delivery, or perfect charged-move recognition. Garbled and ambiguous readings remain unresolved.
