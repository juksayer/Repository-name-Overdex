# Paired Fire Fang review — October 25, 2026

## Sources and test conditions

The user confirmed that only Mawile attacked. The other team stayed idle. The
test was interrupted, so there is no authoritative hand-count or assumption of
continuous attacks. No screen-recording video was made for this first test.
The user subsequently supplied a separate repeat video and clarified the shapes:
an expanding tooth ring near the player and two closing jaws near the opponent.
The repeat-video inspection is documented separately below.

Both archives were copied without altering or deleting the phone originals to
`/home/sean/overdex-device-backups/2026-10-25-fire-fang-paired/`. The desktop SHA-256
matched the phone copy, and every declared artifact verified.

| | Pixel 4 | Moondrop MD_PH_001 |
| --- | --- | --- |
| Device | `99261FFAZ0017J` | `MDPH00124112500414` |
| Archive | `pixel.odxmatch.zip` | `moondrop.odxmatch.zip` |
| Match ID | `793cd89e-6e37-4da9-bfac-1159218b2d4f` | `b7344c07-2c4e-43f5-9a88-4a09c7dc099f` |
| Filename time | `2026-10-25_15-18-29` | `2026-10-25_15-18-29` |
| SHA-256 | `b3eb53c3a409bc9f05ad511350d0db1b35f051fab1c035de35788aede56d0e06` | `101f88e7e54c276ab9fb2fc6c8c692e4c693343bb3a8c7219fc9c8433b3381e2` |
| Articles / verified artifacts | 5,624 / 889 | 6,201 / 467 |
| Mawile's side | PLAYER | OPPONENT |
| Result recorded | WIN | LOSS |

These phones had received the preceding `0036fd4` build. No install or phone
navigation was performed during the user's repeat test.

## Attack attribution and counting

| Recorded measurement | Pixel | Moondrop |
| --- | ---: | ---: |
| Mawile Fast Move uses | 94 | 104 |
| Idle-team Fast Move uses | **0** | **0** |
| Uses including HP-loss evidence | 84 | 94 |
| Border-only uses | 10 | 10 |
| Raw visual-artifact events near Mawile | 12 | 128 |
| Raw visual-artifact events near recipient | 47 | 103 |
| Recipient HP measurements | 642 | 1,402 |
| Median recipient HP measurement interval | 299 ms | 124 ms |

The independent impact requirement successfully stopped attacker-side effects
from becoming attacks by the idle side in this test. The visual sampler still
labels its territory from the adjacent HP bar; a `damagedSide` field on raw
visual testimony does not independently prove damage or attack ownership.

The 94/104 totals are **not validated true counts**. The net difference is in the
HP-backed group, and the Pixel has substantially sparser recipient measurements.
Some larger Pixel loss steps correspond to multiple smaller Moondrop steps.
This is evidence of missed/merged measurements, not permission to manufacture
the omitted attack events. Equal border-only totals do not mean they are the same
ten events: their timestamps differ. There are also suspected loss/flash pairs
split into separate uses before Fire Fang's duration became known. Both missed
events and duplicates remain possible. The original Articles remain unchanged.

| Recipient appearance | Pixel uses | Moondrop uses |
| --- | ---: | ---: |
| Aegislash | 35 | 40 |
| Flygon | 43 | 48 |
| Galvantula | 16 | 16 |

For example, Pixel HP tick `7bbf5aec-04bf-4061-b816-8d2f7a44e14e` records
`0.98936 → 0.94681` once, while Moondrop ticks
`5dd2e247-3082-43d2-9e60-15290e9cf3f2` and
`0b02670b-6796-4794-bad6-639a849f46e5` record
`0.98942 → 0.96825 → 0.94180`. Five larger Pixel Aegislash drops, each about
4.255%, account for a very similar total loss to forty smaller Moondrop drops.

Flygon also has Pixel sample gaps longer than the HP detector's 1.25-second
reacquisition limit. It deliberately does not invent an attack count for a loss
that happened while the baseline was unavailable. The cause of the sparse
Pixel measurements has not yet been established.

On Moondrop, damage tick `22871240-284c-4c45-88c7-fc3c438c9e63` is followed
674.8 ms later by border source `180f6d11-3944-46e3-96a7-215a757c23cf`, with
intervening HP samples at the same plateau. Both became separate uses before
the delayed Fire Fang identity was published. That is a duplicate suspect,
not sufficient proof to remove the second use without video.

Cross-phone entry observations disagree by roughly 0.516 seconds for Flygon and
0.123 seconds for Galvantula in capture wall time. This supports pairing the
phases, not a frame-exact constant clock offset. Pixel's Match Start is inferred
from countdown/combat support; Moondrop directly witnessed GO. Never align only
those start events and hide their different evidence quality.

## Idle reaction motion incorrectly identified moves

The Pixel produced these identities despite recording no attack use by those
Pokémon. Times below are relative to that archive's own `MatchStarted`, not a
claim of exact cross-phone synchronization.

| Time | Idle Pokémon | Identified move | Article ID |
| --- | --- | --- | --- |
| +3.876 s | Aegislash | Psycho Cut | `a7675045-da59-4089-abac-6c8de37c2b60` |
| +12.051 s | Aegislash | Air Slash | `3ab9d0e5-d4e4-4761-8d62-e130cfd6d167` |
| +142.094 s | Flygon | Dragon Tail | `82db76df-e1db-4da2-8b65-36562641d3ce` |
| +155.878 s | Flygon | Sand Attack | `971b0166-c68f-4aba-85fe-89853d134cf7` |

All four cite `BAR_MOTION_CADENCE_AND_REFERENCE_KNOWLEDGE`. Motion-only evidence
can pass the cadence resolver, although visual-only evidence is already barred
from doing so. Being hit can move a Pokémon and its HP bar, so this is not a
reliable standalone identity source. The Moondrop's configured Psycho Cut and
Dragon Tail identities are legitimate reference knowledge; its additional
motion-based support for idle Flygon is not evidence of an attack.

The bounded correction is to require local, independent impact support before
bar motion may establish, narrow, change, or strengthen a move identity. A hit
long ago in the appearance must not license subsequent idle reaction motion.
Raw motion testimony and configured move knowledge must remain intact.

## Identity timing and audio coverage

Both phones eventually named Fire Fang from effectiveness evidence, not sound.
The Pixel's first published Fire Fang used source time +97.599 s and appeared
2.672 s later. A subsequently published article carries source time +61.060 s
but arrived 51.705 s after that source: it must not be reported as live recognition
at +61 s. The Moondrop's first Fire Fang article used source time +99.907 s and
was published 10.327 s later. Prompt move identification remains unresolved.

The Pixel had an unrelated saved team (Swampert, Quagsire, Obstagoon), although
Team Select and battle observations correctly supplied Mawile. It therefore
could not use a configured Mawile Fast Move. The Moondrop's configured third
slot was Mawile, whereas battle observations identified Galvantula there.
Do not use either stale configured roster to override the observed combatants.

Both phones recorded 48 kHz, two-channel playback input and actual signal.
The archives contain 84 sound measurements on Pixel and 30 on Moondrop. The
current 72-reference `fast_move_sounds/manifest.json` has **no Fire Fang entry**;
the supplied named move-audio collection also lacks a Fire Fang filename. This
is a named-reference gap, not proof that another asset cannot share its sound.
Fire Fang never appears in either archive's ranked audio candidates. No audio
measurement independently identified Fire Fang or its attacker.

One Moondrop sound candidate passed the spectral separation check as Volt
Switch, but did not become a Fast Move identity or use. A high matcher score is
not a correctness percentage; the controlled attacker context contradicts that
candidate. Validate any newly labeled Fire Fang samples against separate
recordings, including idle/background segments, before allowing them to select
a move or alter energy.

## Repeat video and separate archives

The repeat is **not** the first battle. It includes a later switch to Dragonair
and later Galvantula attacks; do not apply the first battle's "only Mawile
attacked" constraint to its entire duration.

- Video: `/home/sean/Desktop/Screen_recording_20261025_153449.mp4`.
  SHA-256 `3e689ada22b44ada8cc80608b2eb34cfa382c1efa7f02c39ee8cec0cc98bef95`.
  Duration 179.390456 seconds, H.264, 816 × 1712. It has a video and a data stream,
  **no audio track**; the separate Overdex archives do retain audio evidence.
- Pixel repeat: `da358e08-3c86-4c88-9e86-b40b3a5b7e2b`,
  `pixel-repeat.odxmatch.zip`, 4,815 Articles / 862 verified artifacts,
  SHA-256 `6f66bd405ca5f4c3c22e2934e15dc4ba8448ca1cf30837e35c1ac5318667c3fa`.
- Moondrop repeat: `756c9985-ea6a-4c6b-b96f-00451a0706a6`,
  `moondrop-repeat.odxmatch.zip`, 5,789 Articles / 523 verified artifacts,
  SHA-256 `ab86446d45364d3cf0b3fc771d0132c445a4093318d9bd13df307c4e652d7b5c`.

The video shows the Pixel perspective. Near 90.364 s, two white toothed jaws are
visible around Aegislash; at 90.514 s they overlap a bright orange impact/fire
effect. Near 91.013 s, a pale blue-white radial ring expands around Mawile.
Purple shadow aura surrounds Mawile throughout the sequence. These distinct
appearances are visible; their uniqueness to Fire Fang and reliable detector
thresholds are **not** established. Do not classify the blue-white ring as an
Electric/Ice move or count fire lingering after contact as another attack.

Archived announcement crops were compared against decoded video frames at three
separate moments. Best matches yield record-relative-minus-video offsets:

| Archive crop time | Video PTS | Offset |
| ---: | ---: | ---: |
| 95.666955 s | 19.673444 s | 75.993511 s |
| 204.597851 s | 128.643189 s | 75.954662 s |
| 220.544479 s | 144.595389 s | 75.949090 s |

The diagnostic uses **75.97 ± 0.10 seconds** for this Pixel pair only. Its
84–96-second video slice has 223 actual decoded frames and 18 original recorded
events. The saved HTML/JSON are outside Git at
`/home/sean/.codex/visualizations/2026/09/13/01a09cc7-b682-79f0-ad52-76698424dade/fast-move-visual-review/fire-fang.html`.
The sparse archived HP geometry and diagnostic gap/reset rules produce zero
candidate bursts here despite visible effects. This does not establish zero
attacks or zero live observations. The full video previews remain useful for
labeling the separate visual phases.

The in-app browser rejected reopening its failed review tab under its URL
security policy. No workaround was attempted. The self-contained local HTML is
available directly; successful generation is not a browser rendering check.

## Implemented correction

`FastMoveCadenceInference` now requires two distinct, locally aligned impacts
attributed to the same attacker before bar motion can influence its move
identity or narrow its candidate list. The impact interval must agree with the
motion interval: endpoint tolerance cannot turn one-second attacks into a
half-second move. Separate motion samples cannot reuse one impact pair as
independent support.

Late impact testimony rechecks retained motion without changing the original
Articles. Conclusions retain their supporting predecessor IDs and do not take a
source time earlier than the motion/impact inputs needed for that conclusion.
Explicit entry, switch, faint, and charged-sequence boundaries keep older hits
out. Initial unnamed hits can still be recovered when the first readable species
name arrives later; the recognition timestamp is not treated as proof of entry.
Configured moves remain reference knowledge and do not create attacks or energy.

The recorded regression fixture contains 626 selected, unmodified Articles from
the first Pixel archive in their original publication order. Every selected
Article was compared with its source. The fixture retains the old false
identifications for audit but excludes those conclusions when deriving new
identities. Its SHA-256 is
`ee91b446e9bbcb81b533c5eaf89c1b6ccbc30fafffab7be1e21241e4611f58f7`.

The regression removes all four idle Aegislash/Flygon identifications, retains
Mawile's Fire Fang identification, and awards no observed-use energy to the idle
team. An older October 15 Sealeo fixture now stays unresolved: its previous
Water Gun confirmation depended on short motion intervals that were not
supported by its own impacts. This is an intentional reduction in unsupported
certainty; the original recorded attacks remain intact.

The focused checks passed 115 tests, including 18 motion-ownership cases.
After independent review and the late-recognition correction, the complete
debug unit suite passed **1,135 tests across 196 suites**, with zero failures,
errors, or skips. `:app:assembleDebug` succeeded and `git diff --check` passed.
The built APK SHA-256 is
`1ab3cc41f6fd7a1eebdd6932236c92b2eedd2e5da6b8cae124792eb211a4e34b`.
The update has not been installed during the active Droidball sessions on either
phone. Live post-install behavior remains unverified.

## Next acceptance boundary

Use the repeat's video to label attacker effects, recipient teeth, idle
frames, and actual impacts separately. Align it using independently observable
events and preserve uncertainty. Keep visual shape/color, audio, HP loss, and
cadence as independent measurements. Do not infer ownership from particle
proximity, or treat the diagnostic centroid arrow as tracked particle motion.

## Follow-up: small HP steps and sampling gaps

Closer inspection distinguishes two causes that must not be combined into a
single "missed frames" diagnosis. All five large Aegislash losses on the Pixel
have retained intermediate three-pixel HP plateaus. The existing 3.5-pixel edge
uncertainty floor rejects these smaller losses even when several samples show
them, and then absorbs them into a later larger loss.

Before tick `7bbf5aec-04bf-4061-b816-8d2f7a44e14e`, the opponent bar stays at
horizontal bounds `[336, 528]` (inner width 188 pixels) while its vertical position
bobs. Its recorded fill changes from `0.9893617` to `0.9734042`, a three-pixel
loss. Three samples retain that intermediate plateau across roughly 770 ms;
the old detector waits until `0.9468085` and records the combined loss. No
interpolation or expected attack schedule is needed to recover the already
measured intermediate level. Any correction must still reject outline jitter
and undamaged Pokémon in the idle recordings.

The Flygon sampling gap is a different limitation. Between the Pixel's opponent
HP readings at 105.850 and 107.610 seconds, its shared HP reader produces
player-only measurements at 106.867 and 107.209. The HP worker therefore ran on
those frames but could not publish an opponent measurement. Those exact source
pixels were not retained, so this does not establish a specific locator defect.
Other readers also observed frames inside the gap, and neither archive records
a battle-time global capture outage over the current 750 ms threshold.

Over the stable Flygon interval (99–168 seconds), Pixel has 196 source timestamps
with at least one HP measurement: 65 with both bars and 131 with just one. The
Moondrop has 490, all with both bars. Successful HP-bearing frames alone cannot
distinguish subscriber drops from inspections that found neither bar. A later
bounded diagnostic should record source sequence gaps, reader processing time,
and per-side successful/unsuccessful inspection counts without retaining a new
stream of screenshots. Increasing capture rate or widening calibration is not
justified by these counts alone.

The detector correction keeps the ordinary 3.5-pixel uncertainty floor and adds
a geometry-qualified path for a persistent three-pixel-sized loss (a 2.5-pixel
rounding boundary). It requires unchanged horizontal end caps relative to the
accepted baseline and at least three lower-plateau samples spanning 350 ms.
Vertical bobbing is allowed. Rebounds out of that qualifying plateau, changed
horizontal bounds, a long sample gap, relocation, and reset clear the candidate.
A deeper loss cannot retroactively validate a small plateau that failed these
checks. The source timestamp remains the first qualifying lower reading.
After entry settles, effectively unchanged HP (less than half a source pixel)
may refresh the reference end caps without changing the HP baseline. This avoids
permanently rejecting later losses because an initial end cap was one pixel
away from its settled position. It does not permit the baseline HP to ratchet.

The five positive excerpts and the undamaged Mawile sequences preserve 1,766
original HP measurements, including Article IDs. Every copied field and the
publication ordering were verified against the source archives. All five
positive excerpts produce two distinct measured losses, while all 274 Pixel and
1,437 Moondrop undamaged Mawile samples produce zero damage ticks. The earlier
Abomasnow noise and geometry-free measurement safeguards remain covered.
The complete 281-measurement Aegislash appearance, including its initial entry
geometry, was then verified against the source and replayed through the detector.
It produces 40 measured losses versus 35 originally archived ticks, retaining
all five intermediate losses at their original source times. The focused suite
passes 27 tests (14 existing and 13 new).
These checks establish the measured HP-step correction; they do not equate
either original 94/104 attack total with a validated true count. Source archives
are unchanged, and the suspected 674.8 ms flash/loss duplication remains open.

The separate repeat video provides one count-consistent control. In video
84–96 seconds, an idle lead-in is followed by five distinct closing-jaw/fire
episodes, then idle again. Their approximate jaw-onset brackets are
87.508–87.643, 88.295–88.384, 89.258–89.375, 90.145–90.263, and 91.160–91.278
seconds. The matching repeat archive records five Mawile uses at video-relative
87.571, 88.967, 89.421, 90.841, and 91.581 seconds. This supports that short
segment's count, not frame-exact timing. HP afterimages and bar movement make
individual loss boundaries less clearly readable than the jaw sequences.

Final validation for the small-loss correction: `:app:testDebugUnitTest` and
`:app:assembleDebug` succeeded. All **1,148 tests across 197 suites** passed,
with zero failures, errors, or skips. Independent review and
`git diff --check` passed. APK SHA-256:
`2946d0e1bfa4911c246c01be2547bd1f13610c0ec853359086da4faaa12ec348`.
This is recorded-evidence validation; the new capture behavior still needs a
post-install battle before live accuracy can be claimed.
