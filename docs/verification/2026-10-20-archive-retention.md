# October 20: Replay only exports and recent-match retention

This follows the paired self-match review and the user's request to retain
recorded readings without requiring the original images/audio, remove backed-up
older phone archives, and keep about 50 recent matches.

## Implemented behavior

Match Summary now offers **EXPORT REPLAY ONLY** on the LCD. It retains every
Article in the selected archive, including original OCR text, measurements,
confidence, timestamps, predecessor links, and source-artifact references.
It excludes image and audio bytes. It does not rerun OCR on unread images,
repair old observations, or claim that omitted media was verified.

The package uses format version 3 and declares `mediaIncluded: false` in both
manifest and timeline. Normal media-bearing exports remain format version 2.
Legacy archives still require their declared media during complete verification.
Re-exporting a media-free archive cannot silently claim that its media returned.
Archived species-image recovery is skipped when source media is omitted.

**OPEN EXPORTED MATCH** restores access to the existing Android file picker.
Both export and open-file actions are reachable using the shell's left/right
buttons and A. Downloads exports remain separate from internal auto-saves.

Internal completed recordings are trimmed to the newest 50 distinct Match IDs
at launch and after saving. Recorded timestamps determine age; exporting an old
match again does not make it new. Duplicate files for a Match ID share one slot.
Active checkpoints, invalid files, and explicitly protected Match IDs are kept.
Lifetime battle statistics must be preserved before an old archive is removed.
Automatic saves still use the compact, cited-evidence format.

The limit applies to internal completed archives. It does not delete explicit
Downloads exports or perform garbage collection on the shared loose-artifact
store. Keeping a raw-media sample remains useful when recognition is wrong:
OCR text alone cannot recover a glyph or sound that was never recognized.

## Verified Moondrop cleanup

Device: Moondrop `MDPH00124112500414`.

The original 187 archives were copied to the PC and SHA-256 checked before any
deletion. After the earlier explicitly requested deletion of the 20 largest
archives, 167 remained. This update retired another **117 archives**, freeing
**1,700,874,927 bytes** and retaining exactly the newest **50**.

- All 50 retained archive hashes match their pre-update values.
- Statistics exist for all 117 newly retired Match IDs, with preserved article
  counts.
- All 12 checked calibration-related files are unchanged.
- Originals of the retired files remain in the verified PC backup.
- The Pixel was disconnected; this update was installed only on the Moondrop.

Backup and verification records:

`/home/sean/overdex-device-backups/2026-10-20-paired-review/moondrop/`

Key records are `retention-cleanup-result.json`,
`before-retention-update/settings-and-records.tar`,
`retained-50-hashes.txt`, `retention-final-device-check.json`, and
`retention-install-final.json`. The archive and calibration hashes were checked
again after installing the final build.

## Actual device export

The shell buttons opened BATTLE → Match → Match Summary. Two right-button
presses selected **EXPORT REPLAY ONLY**, and A created:

`Download/Odxmatches/2026-10-20_11-07-59-387-replay-only.odxmatch.zip`

Match ID: `c12270c6-6aa9-449d-8a4e-a0667fa52936`.

| Measurement | Original compact | Replay only |
| --- | ---: | ---: |
| Bytes | 19,720,723 | 382,766 |
| Articles | 4,628 | 4,628 |
| Media included | Yes | No |

Every decoded Article matches the original exactly. The new ZIP contains only
`manifest.json` and `timeline.json`; its manifest retains 527 source references.
The archive inspector passes, explicitly reporting zero source-media bytes
verified because they were intentionally omitted.

Export SHA-256:
`7de76616e28ab5f9f29042669115036ee2495a867886814286c814aa8c0b91d7`.

The exported file was then reopened through **OPEN EXPORTED MATCH** and the
Android file picker. The archive viewer showed the matching Match ID and all
4,628 Articles. **OPEN REPLAY** loaded successfully; dragging the scrubber and
playing the recording rendered both combatants, HP bars, team indicators,
energy, and charged-move icons from the recorded data without source media.
The phone was left with playback paused. No new app crash was present in the
crash buffer. Device screenshots and
`replay-only-export-verification.json` are preserved in the backup directory.
This checks media-free playback; it does not certify the old recording's
species-form, type, move, or energy interpretation.

## Local validation

Full suite: **951 tests, zero failures/errors/skips**. Debug APK build passed.
The added regression cases cover media omission, unchanged Articles, serializer
agreement, malformed packages, re-export retention, newest-50 ordering,
duplicate Match IDs, protected/invalid files, and failed statistics preservation.
`git diff --check` and the updated archive inspector also pass.

Final installed APK SHA-256:
`dd956eb04ec83b7214a652ddf9a64bb1c478ee3bace061190182e6b2c06a4c35`.

## Type-recognition follow-up

This update does not change type matching. The existing correctly framed Pixel
crops show Electric/Psychic for Alolan Raichu and Water/Dark for Greninja, despite
wrong matcher outputs. Those samples are sufficient to work on the matcher.
Useful additional user samples are original-resolution screenshots labelled with
the exact species/form, expected types, and phone model. QTE type-icon examples
should also name the charged move and remain separate from badge examples.
