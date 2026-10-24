# Preserve unresolved opponent forms — October 24, 2026

## Problem and resulting behavior

A bare opponent badge such as `Stunfisk` previously selected its ordinary
reference. That made SUPER EFFECTIVE evidence against Galarian Stunfisk support
Mawile's Ice Fang and reject its actual Fire Fang. The reference choice was more
specific than the species evidence justified.

Bare observed opponent names now retain all catalogue references with that base
name. Explicit form names remain exact. Defensive typing alternatives remain
separate, and effectiveness eliminates a move only when every possible typing
contradicts it. Unknown/missing reference typing cannot positively support an
identification. Configured/resolved player identities remain exact and cannot
supply evidence for the opponent's form.

A shared per-Match cache supplies these alternatives to cadence, exact Fast Move
energy, HUD Fast/Charged move pools, and the opponent forecast. This avoids a
correct regional candidate being silently removed by an ordinary-only consumer.
Identity does not create attacks or energy. Existing source Article IDs/times
remain attached to inferred results; historical archives are unchanged.

## Checks

- 12 new regressions include actual catalogue Mawile/Stunfisk move pools,
  ordinary versus Galarian defensive typing, later form resolution, no player
  roster lineage borrowed by opponents, Raichu form alternatives, battle-equivalent
  cosmetic/shadow persistence, incomplete references, shared cache, and regional-only
  Metal Claw through identification, HUD, forecast, and exact energy.
- Focused tests: 129 passed, zero failures/errors.
- Full `:app:testDebugUnitTest`: 1,113 passed, zero failures/errors/skips.
- `:app:assembleDebug`: successful. No app source changes after this build.
- `git diff --check`: passed.

Key expected regression results:

| Evidence | Allowed result |
| --- | --- |
| Bare Stunfisk + SUPER EFFECTIVE | Fire Fang and Ice Fang both remain possible. |
| Stunfisk (Galarian) + SUPER EFFECTIVE | Fire Fang may be identified with species/text predecessor IDs. |
| Configured ordinary player Stunfisk | Exact ordinary defensive typing. |
| Bare Raichu | Compatible forms remain possible until separate evidence resolves them. |

## Moondrop installation

- Device: MD_PH_001 / `MDPH00124112500414`.
- Droidball had no active service before replacement.
- APK SHA-256: `649dfb9108abf78a26e5e2965c59181a46879de4f0ea4a6e38f490b0c507e5d8`.
- APK bytes: 220,901,439.
- In-place `install -r` succeeded; no uninstall/data clearing.
- Settings/database/calibration/replay backup retained at
  `/home/sean/overdex-device-backups/2026-10-24-form-ambiguity/settings-before.tar`
  (5,322,240 bytes).
- All 292 hashed files in match archives, calibration profiles/reference,
  battle records, replay compilations/excerpts/sources were unchanged immediately
  after install. Hash manifests are in the same backup directory.
- Cold launch succeeded; UI dump contains IDLE and Launch Droidball. No fatal
  exception in the launch log. Screenshot/UI dump retained in backup directory.

## Remaining acceptance boundaries

This preserves form uncertainty; it does not visually recognize the opponent's
form. Default sprites and replay form/type presentation remain separate. Possible
Charged Move lists use the compatible-form union, but charged-announcement name
resolution still has its own exact/base lookup and needs a separate follow-up for
regional-only charged moves. No league-specific form exclusion was added.

Unit checks and an idle phone launch do not establish live recognition accuracy.
Next review should include Mawile with Fire Fang and an unresolved regional
opponent, with known move/form labels and original independent evidence retained.
