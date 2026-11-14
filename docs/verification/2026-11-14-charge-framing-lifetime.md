# Replay charge framing lifetime — November 14

## Change

Previously, sprite framing eased back during the final 750 ms of CHARGING and
was absent during STRIKING. The throw therefore occurred after the player and
opponent had already returned to their ordinary sizes.

Replay now has a separate, cursor-derived framing interval. Supported preparation
eases in over 750 ms, retains the existing player +20% / opponent −15% scale and
horizontal offsets through the scheduled impact, then eases back over 750 ms.
The visible sprite tops remain the anchors. The existing apex release, projectile
timing, HP measurements, move identities and faint associations are unchanged.
Framing after impact is not an extended charge action or an additional attack.

Existing preparation cues are reused: Get Ready, incoming/shield-choice cues and
accepted QTE grades. Validated charge-control disappearance can refine the start
using its existing corroboration requirements. Bare move-use text does not invent
an earlier preparation. The decision timer remains independent support; this
change does not turn an arbitrary timer into a charge or shield-use conclusion.

Open preparation still expires at its existing bounded end and cannot produce a
strike. New preparation, either Pokémon's replacement, observation restart and
Match boundaries prevent old framing carrying into a different episode. Pausing
and reverse seeking use the cursor, not accumulated animation state.

## Shield crop audit

Replay recognizes `Use a Protect Shield?` and `No Protect Shields remaining!`.
There is **no dedicated shield-choice calibration box** in the current crop
registry. Replay accepts these phrases from its approved announcement streams or
retained outcome text. The live validated outcome-crop reuse path specifically
handles the complete no-shields prompt and validates its original word geometry.
The decision timer, charge controls and forced replacement message have separate
calibrated territories; the forced replacement message is not a shield prompt.

A dedicated adjustable shield-choice crop is a useful separate follow-up to make
capture dependable. It is not added by this presentation-only change.

## Ending audio audit

The newest Moondrop archive `bd8af3b0-8ce5-4a2b-9917-19eb11fe91c8` **does retain
audio near the unresolved ending**. Six 1.2-second mono 8 kHz WAV windows begin
at Match-relative 141.745, 143.344, 144.348, 152.146, 153.733 and 155.333 seconds.
Their samples have nonzero signal. These are selected windows, not continuous
coverage. Signal presence does not identify Seed Bomb or a Dragonair faint cry.

Unchanged copies and an index retaining Article IDs, timestamps, duration, hashes
and RMS are in:

`/mnt/overdex-backup/archives/overdex-device-backups/2026-11-14-new-moondrop-review/charge-focus/`

The stream capture implementation assigns the Article clock to window start;
end time is start plus its preserved duration. The user's tentative Seed Bomb
recollection remains separate from recognition. No source archive was modified.

## Validation

Focused tests cover growth, holding through impact, post-impact return, Get Ready
and shield-choice cues, open-sequence expiry, replacements/results/restarts,
source preservation and reverse seeking. The rendered-art device test fixture
also checks that STRIKING retains the focused layout dimensions.

The three preserved Moondrop archives were reprocessed through the actual Replay
model. Comparing every previous exported field, including all sampled scenes,
shows **no change except the new `chargeFocus` field**. Earth Power at 1:35.995,
Aqua Tail at 1:52.222 and Shadow Ball at 2:07.838 retain framing at impact. The
comparison result is in `charge-focus/comparison.json` on the backup drive.

Full unit suite and debug build passed: **3,033 tests, zero failures/errors,
six skipped opt-in tests**. The external archive diagnostic ran in this suite
with its fixture directory explicitly enabled. The debug APK and Android device-test
compilation passed. Device tests were not executed this turn.

The Moondrop is not currently attached; only the Pixel is visible. Installation
on the Moondrop and visual acceptance remain pending. The unit/model checks are
not proof of the physical device's rendering or live recognition accuracy.
