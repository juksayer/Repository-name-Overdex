# Directory density refinement — November 5, 2026

The trainer chose 26-pixel Phosphor spacing and Instrument mono in the directory
study, noting that the denser presentation fits more rows while remaining
legible. This small follow-up applies those preferences to the Android tree.

## Change

- Directory headings keep 16 sp letters and 20 sp line height. Minimum height
  decreases from 30 dp to 26 dp, and vertical padding decreases from 5 dp to
  3 dp. Wrapping and accessibility font scaling can increase height naturally.
- Reducing padding as well as the minimum matters: the old 20 sp line plus
  10 dp padding would otherwise keep the heading at least 30 dp tall. The HTML
  study had that same effective minimum despite its nominal 26-pixel setting.
- Bundle the selected Instrument mono typeface, Share Tech Mono, for directory
  labels and field details. Other modules retain their existing typography.
  Full-row branch drawing and selection scrolling still use measured geometry.
- Retain the compact shell, keyboard, file/folder/binder icons, and accepted
  Right/Left/A/B navigation.

## Font provenance

Unmodified `ShareTechMono-Regular.ttf` comes from the
[Google Fonts repository](https://github.com/google/fonts/tree/main/ofl/sharetechmono).
The app packages its original SIL Open Font License in
`assets/licenses/share_tech_mono/OFL.txt` alongside the local font resource.

Font SHA-256:
`9ceab1f87414829af259c0f537573ae03ef7dd3147c0b27a36a1a0beb6732677`.
Font metadata identifies Share Tech Mono, Regular.

## Verification

- Debug and instrumentation APK assembly passed.
- All 1,741 JVM tests passed across 255 suites, with no failures, errors or skips.
- API 34 emulator instrumentation: `OK (4 tests)` in 44.36 seconds. Covers
  directional folder navigation and cleanup, blocked CRT touch, wrapped-row
  scrolling at large font scale, and owned-Pokémon entry/save/restoration.
- During later visual review, the emulator again displayed a System UI ANR;
  dismissed that system dialog. It did not fail the four completed checks.
  The older intermittent swipe-gesture timeouts documented in the
  [directory verification](2026-11-05-phosphor-directory.md) were not addressed
  or rerun in this presentation-only refinement.
- Source/document whitespace checks pass. The verbatim upstream OFL file retains
  its original trailing space on line 21 and is excluded from that format check.

## Moondrop installation

- Rechecked that no Overdex service or MediaProjection was active before
  installation. Installed successfully without opening another screen over the
  trainer's current activity. Physical readability/comfort review remains with
  the trainer; automated UI checks ran only on the isolated emulator.
- Installed and built APK hashes match:
  `7c955180a0bf192108f6d51e4071d5d91529711f7991cb3335939b9adc0cc50d`.
- Compared 49 protected files: none added or removed; only the Firestore local
  cache changed. Owned-card, preferences, calibration, registration and replay
  files retained their contents. Match archive inventories match exactly.

Private installation and check evidence:
`/home/sean/overdex-device-backups/2026-11-05-directory-density/`.
