# OCR correction review and Moondrop announcement calibration

Date: 2026-10-20. Canonical checkout: `/home/sean/AndroidStudioProjects/Overdex`.

## User correction file

Reviewed `/home/sean/Downloads/overdex-ocr-corrections-000001.json`.

- Review set: `75e163a69dfb9aed6ce3`.
- 48 corrections: 39 marked visible, 9 reconstructed using context.
- SHA-256: `c29ec228f9201945f8a1fe3c786e68db7d31d1f20316dd00a074202d584017fa`.
- Source identities, original text, crop article references, image hashes, and coordinates match the existing 48-case review desk. Embedded source PNG hashes were verified.
- An unchanged copy is retained in `/home/sean/overdex-device-backups/2026-10-20-ocr-corrections/`.
- Original timelines and OCR articles remain unchanged. The corrections are review labels, not newly observed testimony or automatic recognition aliases.

The Moondrop announcement images repeatedly cut off the tops of letters. Examples include `et Keady:`, `Kaicnu usea raiiDlaze`, and partial Night Slash/Cross Chop announcements. The user also identified clipping while reviewing them. The full retained battlefield reference confirms the announcement box intersecting the top of “Get Ready!”.

The review also contains clean readings, faded text, captured HUD energy text, and a player-species crop that contains an exit/backpack icon rather than a name. Do not treat all 48 entries as equivalent OCR failures or train every supplied transcript as visible text. In particular, context-only reconstructions of fragments such as `DDSta` or `uoc` are not sufficient evidence for a global Cross Chop substitution.

The Pixel announcement crop is a different region and is not changed by this adjustment.

## Calibration adjustment

Device: Moondrop `MDPH00124112500414`, model `MOONDROP MD-PH-001`, physical display 1080 × 2400.

The archived Moondrop announcement region was `[50, 475, 622, 531]` on a 672 × 1493 captured frame (572 × 56 crop). Its normalized top was `0.31841594`, equivalent to approximately 764 physical display pixels.

During the review the user explained that they had already adjusted calibration before finishing their crop review. The subsequently loaded profile included those adjustments: announcement top `0.31341594`, height `0.04750837`, plus recent changes to team badges, Poké Balls, and type icons. Those changes were retained. They must not be diagnosed as spontaneous movement or reverted to the earlier backup.

Created and activated a separate profile through the phone's normal **Save calibration as** dialog:

- Name: `Moondrop - announcement Oct20`
- ID: `ca1fedf6-a6c4-4e72-8e93-ea87cdd67537`
- Model: `MOONDROP MD-PH-001`
- Display: 1080 × 2400

Starting from the loaded adjustment, moved the announcement top upward by three fine steps and increased its height by the same amount. This raises the top by 36 physical display pixels, keeping both horizontal edges and the bottom edge unchanged.

Final normalized region:

```json
{
  "x": 0.07440476,
  "y": 0.29841596,
  "width": 0.85119045,
  "height": 0.06250837
}
```

Approximate physical display equivalent: x 80, y 716, width 919, height 150. The calibration LCD shows x 50, y 445, width 572, height 93 on the 672 × 1493 reference image. The full “Get Ready!” lettering is inside the box with space above it.

Validation:

- Compared the profile immediately before and after the final adjustment: only `battleRegions.announcement` changed.
- All other battle regions and all Team Select regions were identical.
- All nine other profile files were byte-for-byte unchanged during the final adjustment.
- Exited calibration, re-entered it, selected Announcement again, and confirmed the same coordinates and active profile.
- No status/navigation-bar subtraction, full-profile reset, default migration, or live resolver change was introduced.

Backups and phone screenshots:

`/home/sean/overdex-device-backups/2026-10-20-ocr-corrections/`

- `calibration-before.tar`: initial snapshot before the install/navigation.
- `calibration-before-final-adjustment.tar`: includes the user's newer settings and the new profile before the final upward expansion.
- `calibration-after.tar`: final persisted profile and settings.
- `announcement-before.png`, `announcement-adjusted.png`, `announcement-reopened.png`.
- `before/files/calibration_reference/latest_battle.png`: original 672 × 1493 battlefield reference.

This verifies framing and persistence on the saved reference. A new live recording is still required to measure recognition improvement and coverage across different announcements.

## New match check and article-layout installation

There is no newer completed Moondrop recording. The newest remains:

`2026-10-20_09-42-14_c12270c6-6aa9-449d-8a4e-a0667fa52936.odxmatch.zip`

It is the already-reviewed 09:42 match, contains 4,628 articles, and was saved at 09:45. The 09:40 file is the earlier short session.

Installed the already-built article readability/confidence update with `adb install -r`, preserving application data. APK SHA-256:

`e69441ece65c4f59ba94a798c602df1d414983287722e68b178123b139b7f256`

Opened **Battle → Match → Match Summary → Open recorded matches → 09:42 archive** using the shell buttons. Confirmed on the physical phone that long source names and **Confidence: Not scored** occupy separate full-width lines instead of squeezing into a vertical strip. The archive loaded successfully. Evidence: `archive-article-layout.png` and `archive-article-layout.xml` in the backup directory.

No production source changed in this calibration follow-up, so the existing 951-test/build result documented in `2026-10-20-confidence-and-ocr-review.md` was reused, not rerun. This follow-up completes that document's pending Moondrop installation and article-list visual check.
