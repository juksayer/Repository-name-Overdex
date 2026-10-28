# Halloween Cup review and exact-frame pairing — October 28, 2026

## Preserved recordings and declared loadout

Three newer Moondrop records were copied with matching phone/desktop SHA-256
hashes and verified without changing the originals:

| Record | Articles | Artifacts | Result |
| --- | ---: | ---: | --- |
| `16-37-32_dff261b7` | 3,374 | 391 | LOSS |
| `16-41-09_96446061` | 193 | 0 | Short nonbattle attempt; no MatchStarted/result |
| `16-44-18_fcc29d4c` | 2,814 | 388 | WIN |

The latest record is the primary review. The user declares Shadow Golbat with
Wing Attack, Shadow Greninja with Water Shuriken, and Shadow Victreebel with
Razor Leaf. Attack pattern remains unspecified. User declarations are preserved
separately from archived observations. The Team Select frame visibly confirms
all three shadow icons and the roster scan correctly identifies the species.
The durable roster/active identity payloads do not encode that shadow status.

Backups, source crops, hashes, and separate identity/audio/count reports are at
`/home/sean/overdex-device-backups/2026-10-28-halloween-cup/`.

## What this recording establishes

- The game screen says **Mega Halloween Cup: Great League Edition**, with a
  1,500 CP limit. Overdex incorrectly records MASTER_LEAGUE from its badge pattern.
- Player-active testimony identifies Golbat, then Victreebel. Player Greninja
  remains roster-only in this record; opponent Greninja appears at the lead and
  returns later. Shared species alone is not evidence of a side-assignment error.
- There are **41 recorded Fast Move uses: 19 player and 22 opponent**. Player
  counts are Golbat 15 and Victreebel 4. They are not independently validated
  true attack totals. Nine uses cite the new HP plateau association evidence.
- Wing Attack is correctly identified. Victreebel's Acid conclusion is explicitly
  an energy-preference assumption, and is wrong against declared Razor Leaf.
  A leading Razor Leaf audio comparison narrowly misses strict acceptance.
  No exact energy is granted from the Acid assumption.
- Audio is available: 48 non-silent retained WAV clips and 31 sound measurements.
  Repeated Fairy Wind comparisons did not produce Rapidash identity because
  strict samples exceeded the current corroboration interval or charge resets.
- Six named charge episodes read correctly through the transparent HUD:
  Poison Fang twice, Psychic twice, Hydro Cannon twice. Both Psychic spends are
  missing. The durable bare Rapidash identity excludes Psychic in the current
  reference catalogue, whereas Galarian Rapidash permits it; the HUD already
  shows Galarian candidates. Form propagation differs between consumers.
- Another player charge sequence has retained EXCELLENT and BLOCKED crops but
  no used-move banner/name. The sampled crop gap is 2.829 seconds. This is a
  missed sequence, not proof of an OCR error on a retained move-name frame.
- Golbat has 104 recorded generated energy and 80 named charged spending. Before
  each Poison Fang, recorded generation trails spending. The ledger is incomplete;
  missing attacks must not be invented to make the arithmetic balance.
- All 194 calibrated fill-height samples are unknown and all 28 charge-label
  reads are blank. The inspected label crop is ground/circle edge. An inspected
  active-type crop contains Golbat text/Poke Balls but is classified Ghost.
  These territories do not provide trustworthy move/type corroboration here.

## Concrete priority defect and narrow correction

Rapidash has two counted uses at the same source time, **+65.395704 seconds**,
same side, and same appearance. Damage `07c39ac8...` and border onset
`43b38003...` are from the same frame. Extended plateau matching takes that loss
for a later onset at +66.301 seconds, leaving the original same-frame onset to
count independently. The damage was published after 0.792 seconds, before both
derived uses (1.962 and 2.688 seconds); this is an association-priority defect,
not a beyond-grace damage delivery case.

The resolver now matches complementary exact-frame impacts first. A competing
same-frame impact also blocks an extended plateau match. A new regression proves
the later onset cannot steal the directly matched loss or create two uses at
the loss timestamp. The later onset remains independent evidence; this change
does not certify whether it was a separate real attack. It must not be described
as removing every extra attack or lowering the whole-match count.

The original seven-plus-seven Charm regressions and adjacent/idle/late-boundary
controls remain in the full suite. Raw observations and recorded source clocks
are preserved; original archives are not rewritten.

## Remaining Fast Move phase concern

A pulse-only opponent Greninja use at +111.432 seconds is 1.490 seconds after
Hydro Cannon's announcement. Player HP drops from about 85.6% to 32.8%, with its
bar relocated to the charge-animation territory. This strongly suggests charged
damage was counted as a Fast Move. Without full video it remains a supported
phase-attribution concern. The fixed charge-impact tail and incomplete earlier
phase cues need separate repair; the exact-frame change does not fix this.

## Validation

- Full unit suite: **1,304 tests / 213 suites**, zero failures/errors/skips.
- Debug APK build and `git diff --check` passed.
- Installed on the idle Moondrop (`MDPH00124112500414`); all **17** saved
  calibration/profile, HUD-layout, and current-team file hashes are unchanged.
  The Pixel was not updated. Installation and comparisons are in the backup folder.
- APK SHA-256:
  `322a8f9e51022dda37c7c9fbfa5bf87728cb87e59fc77cee97372575f9b8eb92`.

No new battle was played during this review. The supplied match validates the
earlier build's behavior and supplies the new regression; it cannot validate the
subsequent exact-frame correction on a physical live battle.
