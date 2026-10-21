# October 20 paired self-match review

## Source and user confirmation

Both devices were connected: Moondrop `MDPH00124112500414` and Pixel 4
`99261FFAZ0017J`. Five previously unreviewed archives were copied, compared
against device SHA-256 hashes, CRC-checked, and verified with the archive
inspector including every declared artifact. All passed. Originals remain on
the phones.

Times are America/Chicago. The user confirmed that the sides took turns
attacking in the newest self-match. Exact attack counts were not supplied.
The user also confirmed that the Raichu was Electric/Psychic and ultimately
corrected its Fast Move to **Spark**, not Volt Switch.

| Device | Local start | Match ID | Articles |
| --- | --- | --- | ---: |
| Moondrop | 09:37:07 | `7f0cd358-8731-4fa8-9b9f-d3db2d0ea088` | 4,792 |
| Pixel 4 | 09:37:08 | `d2194b61-e37f-46ff-bfdf-51728d0f51bb` | 5,792 |
| Moondrop | 09:40:22 | `b053c3d0-fe84-4ca2-9f33-44d14c9fe78f` | 116 |
| Moondrop | 09:42:14 | `c12270c6-6aa9-449d-8a4e-a0667fa52936` | 4,628 |
| Pixel 4 | 09:42:13 | `1ce39878-043f-4b4b-8db2-b860588ba9d9` | 6,390 |

The 09:40 file is a startup interlude, not another battle. The other four files
are two paired battles. Full copy metadata and hashes are preserved in
`/home/sean/overdex-device-backups/2026-10-20-paired-review/copied-archives.json`.

The devices were running different builds:

- Moondrop: `2da6246347dbb0705f04b27137fd07bc3e6a7d2d097f42f9b8a43bdb924e7ae9`
- Pixel 4: `ce4de468dd136bda951e0d70635bd85b0a52907c118977dd7d1e769cd1cf8b16`

No identical-build assumption is made in this comparison. Source times are
monotonic within each recording; their absolute values are not comparable
between devices. Rendering and publication differences also prevent treating
matching start timestamps as exact cross-device impact alignment.

## Newest battle

Both archives have the expected rosters and complementary results: Moondrop
LOSS, Pixel WIN. Recorded Fast Move use totals, normalized to the same actors:

| Actor | Moondrop view | Pixel view |
| --- | ---: | ---: |
| Moondrop's Raichu | 9 | 8 |
| Pixel's Greninja | 12 | 11 |
| Moondrop's Obstagoon | 8 | 8 |
| Pixel's Obstagoon | 0 | 0 |
| Pixel's Lucario | 6 | 7 |
| Moondrop's Hydreigon | 0 | 0 |

These are archive counts, not certified actual counts. Similar totals conceal
different misses and duplicates. In particular, both phones can observe the
same misleading visual transition.

Both newest archives have Poké Ball counts descending 3 → 2 → 1, without the
unsupported zero counts seen in the earlier HUD-contaminated recordings.
Faint timing remains imperfect. Raichu and the Pixel's Obstagoon are marked
fainted substantially later on the Moondrop. The final Hydreigon KO still lacks
a corresponding explicit faint observation even though the result is present.

## Fast Move defect and correction

In the Moondrop's first Greninja attack stretch, four persistent HP losses
precede four orange flashes. The source-monotonic damage-to-flash delays are
approximately 511, 758, 426, and **1,002 ms**. Seven uses were emitted from those
four pairs. The mixed sequence then falsely supported Feint Attack at roughly
919 ms, even though successive damage steps are about 1.36–1.61 seconds apart.

`FastMoveCadenceInference` now compares like evidence with like:
damage-to-damage, and pulse-to-pulse. Interleaved damage/flash fragments cannot
create a fictitious faster cadence. Both streams remain one impact evidence
family, so their shared observation cannot count as independent confirmation.
Charge boundaries reset the separate interval anchors.

`FastMoveUseInference` accepts a duration established by consistent repeated
impact timing. The measured phase allowance is 70% of that duration, bounded
between 450 ms and 1,050 ms. Distinct damage steps or distinct pulse onsets
still cannot merge merely because they are nearby. Duration never creates an
attack during an idle period.

Bubble and Water Shuriken both occupy three turns in the local catalogue.
Consequently, the Water Shuriken name selected from this cadence alone remains
labelled as a higher-energy tie-break assumption. The duration can nevertheless
support fusion. This change does not promote that name preference into exact
energy evidence.

The unchanged source fixture and its provenance are under
`app/src/test/resources/battle/2026-10-20-greninja-impact-phase.*`.
Tests distinguish the following:

- Re-evaluating the recorded split uses no longer identifies Feint Attack.
- With the measured duration available, all four raw loss/flash pairs produce
  four uses, citing both originals and retaining the damage timestamp.
- With live feedback and no knowledge from the other phone, this opening
  produces six uses instead of seven. Two early duplicates were already
  published before duration resolved. The test does not erase those articles.
- Two hits observed by both damage and flash streams remain one cadence
  interval per stream, not two independent votes.

This is a bounded improvement, not complete attack counting or a rewrite of
existing recordings.

## Raichu form and type-icon diagnosis

The Moondrop's recorded team configuration says ordinary `Raichu`, dex 26,
with **Volt Switch**, Trailblaze and Surf. The user corrected the actual Fast
Move to **Spark**. In the earlier pair, the Pixel's repeated audio-reference
matches support Spark; the newest Pixel interpretation wrongly reaches Charm.

All active-species articles say `Raichu` without a form descriptor. The
repository/replay lookup consequently selects ordinary Electric-only Raichu.
Its catalogue includes Charm. Alolan Raichu is Electric/Psychic and its
catalogue does not include Charm. Explicit form retention and form-aware
interpretation are still needed; replacing every Raichu globally would be wrong.

The Pixel's source crops are correctly placed:

- [Opponent crop: Electric and Psychic](evidence/2026-10-20-paired/pixel-first-opponent-types.png)
  — artifact `fe6be7ac4286c434bf230a6171e65fce74ccb81bd20686b64808c17876963995`;
  source article `d7c996e1-a3b0-4af6-aecf-70f2f0674029`.
- [Player crop: Water and Dark](evidence/2026-10-20-paired/pixel-first-player-types.png)
  — artifact `5f927b7d3298be88960e1bf798e6208a2078f9fc092d66ea2b5521456d48edc3`.

The installed Pixel reader emitted Fairy for Raichu and Water/Electric for
Greninja from these crops. The source-code matcher compares a fixed square
around the glyph against a reference at a different effective glyph scale.
Inspection reproduces those wrong shape rankings. Tight glyph normalization
improves Electric/Psychic but is not yet reliable for Dark; that experiment is
not shipped as a general fix. These images do not justify moving calibration
boxes. Neither the type matcher nor form assignment is claimed fixed here.

## Remaining charged-move problems

The Pixel records Raichu's Trailblaze and Greninja's Night Slash energy-spend
events, while the Moondrop misses those explicit spend events. Some Moondrop
announcement text remains damaged, such as `Greninja used NIgn Slasn:`.

The Pixel also reads clean Obstagoon Cross Chop announcements while both
combatants are Obstagoon. Current actor resolution can reject them as ambiguous.
Attack Incoming could provide additional opponent-side support, but this
review does not change that rule. A clear move name must not be assigned to
the wrong side to improve a replay's appearance.

## Validation

Full local unit suite: **940 tests, zero failures/errors/skips**.
Debug APK build passed.

Built APK SHA-256:
`3c4d8f68572d441215829118347d304dccd8251c1e4c454e4dcabb9bdcc606aa`.

The controlled recordings and regression fixture establish the described
failure and local correction. A new battle on the updated build remains the
live acceptance check.

## Device deployment and requested cleanup

The verified APK above was installed with data-preserving updates on both
phones. Before installing, all internal archives, settings, databases, and
calibration profiles were copied to:

`/home/sean/overdex-device-backups/2026-10-20-paired-review/`

Backup files were compared with device SHA-256 inventories. Post-install
inventories matched: 187 Moondrop archives and seven Pixel archives, with
settings and calibrations unchanged. The Moondrop's active Raichu Fast Move
was then changed from Volt Switch to Spark following the user's correction;
other team fields, saved snapshots, and historical Articles were preserved.
Both pre-edit and post-edit preference files are included in the backup.

Both app launches were issued. The Moondrop remained running without a new
app crash in the crash buffer. The Pixel remained at its secure lock screen,
so its post-update UI behavior was not validated.

At the user's explicit request, the 20 largest completed match ZIPs were
subsequently removed from the Moondrop. Each was verified against the desktop
backup before deletion. Exactly **3,098,565,164 bytes** were removed, leaving
**167** archives. All remaining file hashes matched the pre-update inventory.
The five newly reviewed archives were not removed. Pixel files were not deleted.
The complete removal list and checksums are in:

`/home/sean/overdex-device-backups/2026-10-20-paired-review/moondrop/removed-20-largest.json`

## Media retention assessment

The user clarified that images and audio need not remain once their information
is faithfully represented in the record. Current compact archives still retain
cited media, so compact is not a media-free mode.

For the four complete paired recordings, compressing the original, unmodified
`timeline.json` alone produced:

| Device / battle | Original archive bytes | Timeline ZIP bytes |
| --- | ---: | ---: |
| Moondrop / 09:37 | 18,654,538 | 375,019 |
| Moondrop / 09:42 | 19,720,723 | 356,454 |
| Pixel / 09:37 | 30,814,603 | 486,336 |
| Pixel / 09:42 | 32,933,056 | 523,195 |

These are size measurements, not converted app archives or proof of recognition
completeness. The current reader requires the manifest to declare every cited
artifact, and complete verification requires the declared media to be present.
Simply stripping ZIP entries would break that contract.

A future media-free mode should retain all Articles, timestamps, measurements,
OCR text, confidence, source hashes, and evidence links; declare that the source
media is omitted; and preserve replay-only reconstructions separately from the
original observations. In particular, archived species-crop recognition can
currently supply replay identities, so these additions need to be recorded
before deleting their source images. Unresolved episodes still benefit from
retained media during recognition work.

No automatic retention behavior or archive schema was changed in this review,
and no media was stripped from the 167 remaining Moondrop archives.
