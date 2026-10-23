# Raichu match review and Droidball notification controls

October 21, 2026. Canonical checkout: `/home/sean/AndroidStudioProjects/Overdex`,
branch `odx-recovery`. Existing work is preserved; no commit or push was requested.

## Recording reviewed

Moondrop archive `2026-10-21_13-49-25_b020a891-34dd-4a53-b33c-468db958d329.odxmatch.zip`
was copied to `/home/sean/overdex-device-backups/2026-10-21-raichu-review/`.
The phone and desktop SHA-256 agree:
`7d527257750b09d04454ae9e82fb3b8a01b698e7e45ee716a535ffae2c69c66e`.
Archive inspection verified all 314 referenced artifacts and 2,122 articles.
The original recording remains on the phone. No archived testimony was edited.

[Selected articles and counts](evidence/2026-10-21-raichu/review.json) retain
article IDs, source times, confidence, and provenance. Times below are relative
to the recorded GO, after about 162 seconds of pre-battle menus.

- Team Select correctly distinguished slot 1 shiny Alolan Raichu, slot 2 ordinary
  Raichu, and slot 3 Obstagoon. The final configuration was Alolan Raichu with
  Spark / Trailblaze / Wild Charge; ordinary Raichu with Volt Switch / Brick
  Break / Thunder; Obstagoon with Counter / Night Slash / Hyper Beam.
  The user later noted that moves may have been rearranged during observation.
  Multiple pre-GO configuration articles agree with that possibility; the last
  configuration, not the initial menu selection, is the comparison above.
- Battle OCR supplied the bare name `Raichu`. The old lookup selected the exact
  ordinary-name configuration, producing Volt Switch before GO despite both
  forms being on the roster. At +20.843 seconds cadence changed that estimate
  to Thunder Shock. Neither establishes the configured Alolan Spark correctly.
- The update prevents a bare, ambiguous form label from silently selecting the
  ordinary teammate's configured move. It retains both legal candidate pools
  and withholds that configuration claim until the active form is resolved.
  A unique or explicitly named form still selects its own configuration. This
  is a containment fix, not complete active-slot identification: resolving the
  scanned lead and same-name switches consistently across HUD, inference, and
  replay remains work. The previous archive is not retroactively corrected.
- Opponent identities were Combusken (-1.642 s), Magcargo (+30.616 s), and
  Flareon (+57.841 s). Recorded Fast Move uses: player Raichu 21; opponent
  Combusken 4, Magcargo 2, Flareon 1. These are detector counts, not a human
  ground-truth count or proof that all attacks were observed.
- Recorded charge spending: player Trailblaze at +17.78, +41.98, +54.98;
  player Wild Charge at +71.01; opponent Flame Charge at +24.67 seconds.
- Only Combusken's faint had explicit faint testimony (+29.06 s). Opponent
  ball counts decreased from three to two, but no later one/zero count was
  accepted. That prevents the final-member HP gate from being sufficient here.
- Audio is present: ten preserved clips, including stereo Pokémon GO playback.
  Victory music was matched from +78.085 s, and the outcome crop at +83.38 s
  clearly reads `YOU WIN!`. The recorded MatchEnded is WIN; music corroboration
  agrees. Outcome OCR publication was about 2.03 seconds after its source frame.
  The compact archive does not establish the first instant the result became
  visible, because no earlier outcome image was retained.
- The accepted player `GHOST` type samples before GO actually contain part of
  the exit button. An opponent sample during Magcargo contains part of its
  name/card and Poké Balls, not the intended type icons. Both crop placement
  and rejection of non-icons need attention. Calibration was not rewritten.

Auditable images:
[result text](evidence/2026-10-21-raichu/MatchOutcomeCrop-0.png),
[player type crop](evidence/2026-10-21-raichu/PlayerActiveTypeIconsCrop-0.png),
[opponent type crop](evidence/2026-10-21-raichu/OpponentActiveTypeIconsCrop-0.png).

## Notification behavior

- A stopped session leaves a quiet, dismissible closed-eye Droidball
  notification with **Start** and **Overdex**.
- Start opens existing team pre-flight. Launch uses Android's ordinary screen
  sharing consent; stopped projection tokens are never reused.
- An open battle session offers **Stop**, **Next match**, and **Overdex**.
  Closed eyes while Overdex is foregrounded or waiting do not change Stop into
  Start. Eye state and capture lifecycle are separate.
- Stop uses the existing save/stop path. Next match saves the current ledger,
  closes its observers, and prepares a fresh match using the still-live capture
  session. If no result was observed, the previous record stays incomplete;
  the action does not fabricate a winner or result.
- Notification actions target an explicit non-exported receiver and carry their
  original session and match IDs. Old controls cannot stop a newer session or
  advance a newer match; queued Next requests are rechecked by the match owner.
- Registration offers Stop and Overdex, without Next match. The idle notification
  does not keep a foreground service, recorder, or observer alive.

## League and summary graphics

The supplied `Images/Combat/pogo_{great,ultra,master}_league.png` references
are verified. The numbered achievement directory also contains bronze/silver/
gold versions: `Badge_52_{1,2,3}_01.png` (one stripe),
`Badge_53_{1,2,3}_01.png` (two stripes), and
`Badge_54_{1,2,3}_01.png` (three stripes). `Badge_14_{1,2,3}_01.png` is the glove
medal family. `CombatRank00` through `CombatRank03` are a different medal family.

[Medal candidates](evidence/2026-10-21-raichu/result-medal-candidates.png) and
[league/rank references](evidence/2026-10-21-raichu/league-and-rank-references.png)
show these exact supplied assets. Source artwork is retained in Downloads.

No new graphic-driven result recognizer is enabled. The latest archive retains
Team Select full frames and result text, but not a full medal-summary frame.
Before enabling a trigger, compare the summary's actual layout and scale with
these candidates and negative Team Select/profile/battle samples. A matching
league icon alone must not terminate the match; it is present before battle.
The appropriate first use is a bounded result-OCR wake-up cue after battle
context, retaining the matched crop. The result phrase/music remain independent
evidence; a medal cannot by itself determine win versus loss.

## Validation

- Full JVM suite after the navigation follow-up: **1,019 passed**, zero failures/errors/skips.
- Debug app and instrumentation APKs built successfully.
- New tests cover lifecycle controls, registration restrictions, stale session/
  match targets, and the mixed-form configured-move regression.
- On the physical Moondrop (MDPH00124112500414): idle Start opened pre-flight;
  launch requested Android sharing consent; Next saved an incomplete recording
  and opened a new one without another consent prompt; Stop saved and ended the
  service, restoring Start; Overdex opened MainActivity. The two temporary
  recordings contain no fabricated MatchStarted or MatchEnded.
- The original 50 archives were restored after retention displaced two during
  the notification test. Restored file hashes match their desktop backups, and
  all 19 preference-file hashes (including teams, calibration and HUD placement)
  match before/after. See [notification validation](evidence/2026-10-21-raichu/notification-validation.json).
- No instrumentation suite was run on the user's data-bearing phone. The
  instrumentation APK was compiled, with the retained-badge test updated to
  provide the now-required battle context explicitly.

## Overworld badge and recovery follow-up

The user reported that starting Droidball before reaching the battle menus could
retain scenery as the opponent badge, leave the HUD overlapping it, and require
relaunching Overdex. Inspection found three contributing paths:

- The service treated two coarse color matches as battle entry and cached one
  rectangle, without any independent battle-surface evidence. The old detector
  also accepted a sampled non-battle menu region as a player badge.
- A manual HUD request immediately switched to the anchored layout. Until a
  badge appeared, that window was transparent and its recovery controls hidden.
- Presentation deliberately ignored late prebattle phase updates; explicit
  Ignore/new-match navigation could therefore preserve an incorrectly open HUD.

Native badge detection/cache now require the session's independently established
battle surface. The cheap detector also requires name-like ink above the resource
colors. A retained image belongs to one match and observation attempt; leaving
battle, restarting, rescanning or advancing cannot carry its image forward. A
rectangle intersecting our HUD cannot become the cached original. The existing
exclusion of our retained pixels from witnesses remains in place.

Before a native badge is seen, an early HUD request keeps the regular navigation
controls visible. Ignore and explicit navigation reset the presentation, while
genuinely late phase updates still cannot close an established live HUD. The Team
Select scanner stays eligible after an early manual HUD request, until battle
evidence arrives. A fresh Team Select acceptance can restore its confirmation
panel in the same session. Saved crop/HUD coordinates are not changed.

The first live navigation check found a second false-acceptance path: at 14:44:18
the three-card/color alternatives accepted the overworld as Team Select, then
the one-shot readers guessed Feraligatr, Sceptile (Mega), and Poliwhirl from
unrelated pixels. That test recording is preserved on the desktop under
`2026-10-21-badge-navigation-check/` (match `71b66961-5c3c-4924-abf1-ec00f7d96412`).
Its false readings were not silently removed or used to change the saved team.

The final update removes color-only party acceptance. It requires Use This Party
wording with supporting badge/card/league evidence, or agreeing league and CP
restriction text plus two cards. League classification waits for accepted Team
Select. Ignore resets the scan witnesses and tentative roster, and a lone type
icon guess no longer opens battle presentation. The repeated live check covered
60 seconds in the overworld with zero Team Select, league, roster, active-species,
MatchStarted or MatchEnded articles, and no retained scenery rectangle.

The final build is installed on the Moondrop. The subsequent positive navigation
check was paused when the phone changed to a Pokémon catch screen; the user was
asked whether they were operating it. No further UI actions were taken while
that answer was pending. Entering actual Team Select and the native-badge/HUD
handoff during a battle remain to be checked on this final build. In particular,
the conservative HUD-overlap rejection may prevent refreshing a cached badge
where the two curved windows' bounding rectangles intersect; that geometry has
not yet been exercised in a live battle.

The completed first navigation test was copied to the desktop and verified
byte-for-byte before its phone archive and matching history index were removed.
The original archive displaced by retention was restored and hash-verified; all
50 original archive names are present again. The current session
`7db890aa-f345-42ec-8a88-59a8079faf3b` remains active and was left untouched.
Sixteen of 19 preference files are identical to the pre-navigation backup,
including teams, calibration and HUD settings. Only Firebase heartbeat/auth
runtime files and Droidball's running-session state changed. The desktop
`2026-10-21-badge-navigation-check/cleanup-validation.json` records the check.

### Resumed navigation check, 15:17–15:20 CDT

After the user authorized continuing, the same running session was navigated
from a Pokémon detail page to the overworld, Scan Team Select, the main menu,
Battle, Candela, Great League, and the actual party screen. No app or service
relaunch was needed to navigate. The panel correctly stayed Seeking Team Select
through the non-party screens, but had **not accepted the real party screen**
when the phone disconnected from USB. This is not a completed positive check.

The journal was copied before disconnection: 43,411 checksum-verified Articles,
through 15:20:17 CDT, with no MatchStarted or TeamSelectPartyWitnessed. It remains
under the desktop backup directory as
`resumed-navigation-before-disconnect.odxjournal`. The device recording remains
active; it has not been stopped or removed.

Mapping the journal's actual aperture coordinates onto the contemporaneous
1080×2400 screenshot explains the missing party confirmation:

| Aperture | Full-screen bounds (left, top, right, bottom) | Visible problem |
| --- | --- | --- |
| Use This Party | 368, 2056, 710, 2128 | First and last letters are cut off; visible fragment is approximately `SE THIS PART`. |
| League text | 236, 829, 839, 879 | Upper portions of the glyphs are outside the crop; the expanded controls also overlap the right side. |
| Restrictions | 252, 929, 821, 976 | Upper portions of the glyphs are outside the crop. |

The coordinates agree with the pre-test saved calibration; this check found no
new install-time movement or screen-scaling discrepancy. Restoring just the
Use This Party aperture to its existing default (245, 1960, 835, 2140) encloses
the full label in the screenshot. This correction has **not** been applied:
the phone is disconnected. Preserve the current profile, correct that box
through the app's calibration flow, and repeat the positive Team Select and
Ignore/Rescan check after reconnection. No source changes or new installation
were made during this resumed check.


### Reconnected positive check, 15:34–16:00 CDT

After backing up calibration, only Use This Party's horizontal position and
width were changed through Match Calibration. Its bounds on the 1080×2400 phone
are now approximately (286, 2056, 804, 2128). All other regions, including its
vertical geometry, are unchanged. The active profile remains `Moondrop -
announcement Oct20`; the save also refreshed legacy published-frame metadata to
672×1493. The proposed default rectangle above was not applied.

Notification Next created match `d494bf27-5570-4029-aebc-f8c05c6342b6` at
15:49:58; the old journal remained briefly during export. Stop saved normally.
A new Match was needed because its snapshot witness loads calibration at creation.

Fresh session `99fd2f35-d4ec-4c92-9f3f-b4a0b2fda39c` passed the phone checks:

- Scan displayed the correct confirmation: Alolan Raichu, ordinary Raichu,
  Obstagoon, in that order. Great League was also witnessed.
- No / Rescan returned to Seeking. Ignore cleared the tentative confirmation and
  stayed idle. Scan then restored the correct team within the same session,
  without restarting Overdex or the service.
- No team confirmation was submitted and no battle was started. Stop saved and
  ended capture, leaving GO at Team Select. After testing, the original current
  team/moves were restored because pre-flight had selected a saved team; the
  saved-team collection was unchanged. Overdex was restarted after the recovery
  check only to reload that restored preference.

**Latency remains open:** Party testimony was published 27.012 and 29.943 seconds
AFTER its source frames. Prompt frame timestamps do not mean prompt recognition.
Sprite readings followed their later frames by roughly 1.6–2.1 seconds. The delay
is upstream of the completed roster; its precise cause remains unisolated. The
native badge/HUD handoff in battle also remains unverified on this build.

All three navigation-only recordings (`7db890aa`, `d494bf27`, `99fd2f35`) were
backed up with matching phone/desktop SHA-256, valid ZIPs and verified artifact
hashes. None contains MatchStarted/MatchEnded. Only those test archives and their
history indexes were removed. Three originals displaced by retention were
restored and hash-verified; all original 50 names are present. Backups and
confirmation/Ignore/rescan screenshots remain in the desktop
`2026-10-21-badge-navigation-check` directory. No code change or new build was
needed; the previously tested 1,019-test build remains installed.
