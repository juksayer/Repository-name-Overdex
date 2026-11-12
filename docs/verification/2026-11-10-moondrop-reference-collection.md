# Reviewed Moondrop calibration collection

The supplied `calibration screenshots` folder now contains 67 readable PNGs, all
1080 × 2400, totaling 92,332,853 bytes. SHA-256 identifies 66 distinct images:
`YouWin.png` and `YouWinAndCheckmark.png` are exact duplicates. All source files
remain unchanged.

## Review and prepared collection

The collection now has 31 non-QTE images and 36 QTE images. The initial 30
non-QTE images and all 36 QTE images were visually reviewed; the later
`supereffectivetext.png` addition supplies opponent-HP effectiveness placement.
A bounded static landmark comparison of the initial 30 non-QTE images found
the visible ordinary battlefield exit glyph at the same vertical offset in all
15 applicable screenshots. The new image is not included in that measurement.
Hidden or phase-replaced
controls were not treated as layout failures. Ordinary badge bands are consistent
with the current Moondrop layout reviewed against direct capture on November 9.
Uniform dimensions alone were not used as proof of compatible UI geometry.

A separate reviewed desktop collection contains:

- **Moondrop calibration:** 21 byte-identical PNG copies for initial box placement.
- **QTE bubble references:** all 36 byte-identical QTE PNGs, with visually checked
  type labels and notes for overlap, fading, particles and source-edge clipping.
- **Review evidence/Supporting scenes:** extra menu, transition, dialog and
  validation stills, outside the normal calibration folder. The exact duplicate
  points to the retained win image in the gallery.
- A searchable `review.html`, editable `inventory.csv`, detailed review JSONs,
  and `collection-manifest.json` with source hashes, scene coverage and roles.

The source folder and reviewed desktop collection are separate. Select the
`Moondrop calibration` child folder when using **CHOOSE SCREENSHOT FOLDER**.
The original PNGs are the preferred sources for later color and recognition
experiments; bundled JPEG copies are for preview and geometry placement.

## Core references

| Source PNG | Main purpose |
| --- | --- |
| Announcement | Charged announcement and high player-recipient HP |
| ChargeMoveReadyWhiteBorder | Ready control rims, labels, badges and inactive cards |
| ChooseYourParty | All three selected cards/names, league and Use This Party |
| CountdownGlyph3 | Numeric opening countdown and entry context |
| CountdownGlyphGO | Complete wide GO and opening transition |
| GBLrecord | Battle/Party tabs and surrounding post-match menu |
| GOOD_EFFORT | Two-line loss result |
| HPbarCenter2 | Low centered player HP during Aqua Tail impact |
| HPbarCenter3 | Different centered player HP/color state in the same context |
| HPbarCenter4 | Higher centered player HP during Thunderbolt |
| OpponentFaintHPbar | Complete empty opponent outline; one centered charged control |
| PlayerLowHPposition | Lower vertical player-bar position, with green HP |
| ProtectShieldTimer | Shield choice and readable nonzero decision timer |
| SwitchInaNewPokemon | Forced replacement dialog and timer |
| TeamInfoExitHpInactiveChargeMoves | Clean ordinary battlefield baseline |
| VSscreen | Actual centered VS disc |
| YouWin | Win result and continue checkmark |
| FairyQTE2 | Varying QTE target sizes |
| PsychicQTE2 | Broad QTE bubble envelope |
| WaterQTE2 | Wide horizontal QTE bubble envelope |
| supereffectivetext | SUPER EFFECTIVE text placement above the opponent HP bar |

These names omit `.png` only for table readability. Camera motion is intentional
in HP examples: HP regions remain search territories. A complete empty outline
and particles do not independently establish a faint cause or final KO. The
`PlayerLowHPposition` name describes vertical position; its HP is green, around
60%. Damage flashes and trails remain separate from literal remaining fill.

The added `supereffectivetext.png` fills the opponent-side placement part of S07:
the effectiveness text is above the opponent HP bar, rather than below a charged
button. This still does not establish the source attack, whether it was a Fast
Move or charged move, its cause, or its timing. Matching geometry is useful
without promoting the screenshot into attack testimony. S06's corresponding
text placement above the player's HP bar remains unrepresented.

## QTE coverage and remaining targets

All 18 filename labels agree with the visible QTE glyphs:

| Type | Screenshots | Type | Screenshots | Type | Screenshots |
| --- | ---: | --- | ---: | --- | ---: |
| Bug | 2 | Dark | 3 | Dragon | 2 |
| Electric | 2 | Fairy | 3 | Fighting | 2 |
| Fire | 3 | Flying | 2 | Ghost | 1 |
| Grass | 1 | Ground | 2 | Ice | 1 |
| Normal | 2 | Poison | 4 | Psychic | 2 |
| Rock | 1 | Steel | 1 | Water | 2 |

Multiple bubbles in one frame and multiple nearby screenshots are not independent
validation. Charge/match provenance is not established, so no random training/test
split was assigned. Keep complete charge sequences or matches in one partition
when their provenance is known. The existing stationary badge matcher is not a
moving-QTE classifier; folder selection does not train one.

The following remain missing or limited:

- Clear NICE, GREAT and EXCELLENT reveal text. None of the 36 QTE images shows
  the grade; bubble coverage must not be relabeled as grade coverage.
- Effectiveness text above the player's HP bar. The added opponent-side image
  supplies S07 placement only; independently established Fast Move context and
  timing remain necessary on either side. Charged `BLOCKED` and matchup text
  below charged buttons are different targets.
- Immediately-after-voluntary-switch shade/dial on the small stacked inactive
  cards. Forced replacement cards are not that surface.
- Normal-field opponent open-hand/ball release and recall sequence. Opening
  countdown close-ups and entry toasts provide only partial support.
- A clean centered **opponent** recipient during charged impact. Identified
  centered recipients in this collection are player Pokémon.
- A confirmed Shadow-obscured entrance/departure sequence.

## Bundled library correction

The previous loader enumerated all 60 image files in `battle_samples`, using the
catalog only for optional labels. This admitted 28 uncatalogued historical files
even though only 32 Pixel images were approved. Shape filtering could not retire
an old layout with the same aspect ratio.

The loader now admits a bundled image only when its filename appears in the
validated eligible catalog map. A missing/invalid catalog admits no bundled
images. Latest-device captures and selected user libraries retain their existing
paths; saved crop coordinates and profiles are not migrated by this change.

The current approved catalog contains **21 Moondrop plus 32 Pixel references**,
or 53 eligible references. Its 55 total records also retain the two excluded
Pixel gallery examples. The 21
new native-size JPEGs use quality 94 and 4:4:4 sampling, with no resizing, cropping
or system-bar subtraction, totaling 10,812,721 bytes. Every entry records its
source PNG and bundled asset hashes.

All 60 pre-existing asset files are byte-identical to the pre-change baseline.
The 28 unapproved files are retained for history and recognition fixtures but
excluded from the calibration picker and cycling. Three have previously proven
field-layout incompatibility; the other 25 are retired as unapproved, without
claiming every individual image has been proven incompatible.

## Verification

The final 67-source-image, 21-reference package passed **2,344 unit tests across
309 suites**, with zero failures, errors or skips; both debug APKs built
successfully. **Nine native tests passed on a disposable API 34 emulator**. They
cover the actual approved-catalog loader, absence of all 28 historical references
through the available filters, 21 native-size Moondrop references, retention of
32 Pixel references and own-device/custom-folder paths, full long-Select cycling,
image preview/commit behavior, foreign-image read-only handling, and saved-profile
preservation during browsing and calibration edits. The preceding 20-reference
iteration also passed its focused 16-test run before the additional screenshot.

The final app APK SHA-256 is
`e90dfcbde779100486dd45287da4afcfddd5a6966a75180e3ce608af94777e3e`;
the final test APK SHA-256 is
`aa660b00efff3f436350b5b5c14859e6c36b1220fb30da8752212ea45505b34e`.
The local review page was rendered during the first iteration. The final static
review confirms 67 gallery cards, 21 core entries, all 36 QTE records, and all
205 local image/document links resolving. All original 66 source hashes, the
additional supplied source hash, all 60 pre-existing bundled assets, and the
original Pixel catalog entry objects were verified unchanged. All new core and
QTE PNG copies match their source hashes.

The emulator and temporary local preview server were closed after verification.
After the user reconnected the Moondrop (`MDPH00124112500414`, MD-PH-001),
read-only preflight confirmed no Droidball service. The update was installed
with package replacement, and the installed APK SHA-256 matches the tested
artifact above. Before replacement, 427 saved-data files were backed up in a
406,161,408-byte archive, independently rehashed against the stopped app's files.
The previous installed APK was retained for rollback. The backup includes
profiles, preferences, databases including WAL files, registration, battle
records, match archives, replay files, and `no_backup` personal notes. Loose
capture artifacts in `files/artifacts` and generated caches are excluded from
this desktop backup and remain on the phone; they were not separately rehashed.

All 427 backed-up files matched immediately after installation. After a successful
cold launch, all 401 checked calibration, registration, record, archive, replay,
team-selection and personal-note files still matched. Only Firestore local state
and the profile-installation marker changed among the pre-existing files during
startup. The app process remained running, no new crash-buffer entries appeared,
and Droidball remained stopped. No physical-phone instrumentation was used.
This proves installation, startup and saved-file preservation; live battle
recognition and manual on-phone calibration acceptance remain unverified.

Private inventories, landmark review, independent asset-preservation checks,
generation script and validation
logs are retained under
`/home/sean/overdex-device-backups/2026-11-10-moondrop-reference-collection/`.

This review establishes a consistent starter reference set and explicit coverage
gaps. It does not assert that every crop is calibrated, that QTE recognition is
implemented, or that a still image establishes an attack's timing or cause.
