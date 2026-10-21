# Missing-letter species recognition — 2026-10-19

## Behavior

The shared species resolver now recognizes an unambiguous catalogue name when
one or two letters are missing from the complete observed label. At least five
letters and three quarters of the canonical name must remain, in their original
order. Adjacent `CP` numbers can be excluded from a team-badge label.

Validated examples include `karmory`, `s armory`, `Skarmry`, and `Skarmor` resolving
to Skarmory; `Seale` resolving to Sealeo; and `Mewto` resolving to Mewtwo rather
than Mew. Exact catalogue readings retain priority. Competing legal names stay
unresolved: `Porygo`, `Snease`, `Nidora`, and `Nidorin` do not force an identity or
fall through to a permissive prefix guess. A missing-letter reading does not
invent a Shadow/form descriptor.

Live badge recognition and persisted badge/announcement witnesses use the same
resolution details. The original OCR text and the repair basis are retained on
the identity testimony and archive payload. A one-letter repair has confidence
0.82; a two-letter repair has confidence 0.76. Neither is represented as exact OCR.

## Validation

- 924 JVM tests passed, with zero failures, errors, or skipped tests.
- Debug app and Android-test APK builds passed.
- Five selected instrumentation tests passed on the Moondrop MD-PH-001,
  Android 14: MissingSpeciesLetterDeviceTest (1), SpeciesAdmissionDeviceTest (1),
  and LiveSpeciesRecoveryDeviceTest (3).
- The new device test sends synthetic `karmory` and `s armory` badge pixels
  through native ML Kit OCR, the live pipelines, custody, and HUD presentation.
  Both sides resolve to Skarmory; the opponent's move list is populated. Raw OCR,
  source timestamps, crop references, repair basis, and archive round trips are
  asserted.
- Catalogue ambiguity tests use all 1,752 bundled species/form names and base
  aliases, rather than a one-species vocabulary.
- `git diff --check` passed.

These checks validate the repaired recognition path, not live-battle accuracy
under motion, poor contrast, or an obscuring HUD. A new live battle remains the
acceptance check.

Installed APK SHA-256:
`cd718f1e22f4f424a8fe5025a834a95399c1821c857748386bbc9d4983d6a31d`.

## Device-test cleanup incident and recovery

The connected Gradle runner uninstalled the app after testing. This removed
private match files and recent settings. Android restored an older version-9
database containing 24 owned cards, but that is not proof that newer card details
survived. The incident was disclosed to the user immediately after discovery.

AGP 9.3.3 defaults `android.injected.androidTest.leaveApksInstalledAfterRun` to
false. Its DeviceProviderInstrumentTestTask maps that setting to keepInstalledApks;
TestSuiteTestTask inverts it for `android-test.uninstall-after-tests`. The project
now sets it to true. BUILDING.md requires a verified backup of all private app
data before physical-device tests and recommends explicit install-with-replace
and targeted instrumentation instead of relying on runner cleanup behavior.

Recovery performed:

- Reinstalled the normal debug APK.
- Restored all nine named calibration profiles and the current legacy battle
  calibration byte-for-byte from the pre-test backup.
- Restored the active profile selection from the October 19 diagnostic output:
  `663b6764-049e-45d0-9589-211cb1f23198` (MOONDROP MD-PH-002 1080x2400).
  The device itself identifies as MD-PH-001; the profile name is user-owned.
- Recreated the legacy Team Select fallback from that active profile's exact
  saved regions. The named profile remains authoritative.
- Restored the five saved teams and selected team from the complete preferences
  printed during the October 19 investigation.
- Restored the five October 19 archives copied before the test, plus 164 other
  distinct matches from surviving Downloads/ODXMatches exports. For duplicate
  exported match IDs, retained the copy with the most articles, then artifacts,
  then bytes. Every restored archive was checked against its source SHA-256.
  All 206 public export files remain untouched.
- Restored HUD geometry from the latest available explicit preference snapshot
  (October 15); a more recent manual position is not guaranteed.
- Restored only audio, notifications, and overlay permission grants confirmed
  in the earlier device snapshot. Screen-capture consent is requested normally.

An attempted backup of the nonexistent `match_calibration_profiles.xml` contained
an error message, not XML. It was excluded from the valid calibration count and
its accidentally restored copy removed after verifying the exact contents. The
actual preference is `match_calibration_profile_selection.xml`, recovered above.

The 24 restored owned cards have unique original IDs, and their species, CP,
nickname, shadow, and shiny values match the earlier October 6 inventory.
The app reopened successfully, ran its existing 9-to-10-to-11 migrations, and
retained all 24 cards with every recovered original field unchanged. SQLite
integrity checks passed before and after migration. The active calibration and
current-team preferences remained byte-for-byte unchanged after startup, with
no fatal exception in the app process. The normal installed APK hash matches the
tested build. Four older `active-` archives were automatically finalized by the
app's checkpoint recovery; the library still contains 169 archives.

All 169 match hashes were also verified inside the separate 5,799,629,312-byte
recovery tar. Its preference XML files parse successfully. Database backups are
retained separately both before and after the schema migration.

The physical navigation buttons reach Match Summary, Recorded Matches, and the
newest archive (`d504d15f-614b-4697-9f6c-a4d1d960a142`). The archive viewer displays
2,424 articles and the original Abomasnow/Camerupt team testimony. No new match
was started during recovery. The app is left at that archive viewer.

Custom binder definitions, newer owned-card metadata, registration
alignment, replay preferences, and unexported forensic evidence were not found
in a usable current backup. Do not claim complete recovery of those items.

Recovery backups and manifests are outside Git at
`/home/sean/overdex-device-backups/2026-10-19-moondrop-recovery/`.
