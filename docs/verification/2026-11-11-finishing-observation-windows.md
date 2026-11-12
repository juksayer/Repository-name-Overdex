# Step 2: finishing-hit observation windows

This slice follows the source-time faint-attribution repair. It changes capture
and source-supported Fast Move reconstruction, not swap advice, HUD text styling,
or charged-move readiness presentation.

## Implemented behavior

- Actual recipient pixels have their own lightweight frame stream. A missing HP
  measurement no longer necessarily prevents a completed visual transient from
  being observed. Visual-only samples never update the HP hub's last reading or
  publish an HP fraction.
- A measured return to two normal battlefield bars opens one fixed three-second
  recipient watch. Charge cues, real charged-camera measurements, source clocks,
  dimensions, identities and lifecycle boundaries govern eligibility. The watch
  samples 24 by 24 real pixels per side; it does not repeat historical pixels.
- The transient detector retains the original peak time and requires an observed
  recession. Reordered frames, long sampling gaps and relocated sampling grids
  cannot complete an old peak. Ending a match does not flush a pending peak as an
  attack.
- A completed recipient transient can support one Fast Move when paired with a
  strict accepted reference sound whose legal active-species pools independently
  resolve its attacking side. Copied request/cue sides are insufficient. Live and
  Replay use the same pairing policy, source-time appearance/charge guards and
  source references. HP evidence and paired evidence must not duplicate a hit.
- The replacement message has its own adjustable box, a three-second capture
  assignment and one pending OCR crop. Exact known phrases or two source-distinct
  near-complete readings produce a prompt observation. Raw OCR and original crop
  clocks remain intact. Success wakes player resource, inactive-card and decision
  timer readers; it does not assign a faint or change a team count.
- Two separate adjustable terminal visual territories preserve bounded real
  sequences. Their neutral summaries describe captured frames, not a recognized
  shrink, recall or faint. Exact-purpose raw crops also survive compact export
  when observation stops before a summary can reference them.

See [terminal capture review](2026-11-11-terminal-visual-capture.md) for attention
limits, actual-video positives and living-recall/camera/Shadow-aura negatives.

## Recorded regression

The original Moondrop archive is match
`8c55c9c3-e7c2-40e7-80de-bd7cd529a6f6`. Its SHA-256 remains
`3d9acb465204b5d981a2df21bea56373fdf1de104c28abd44c30292ab24b55d4`.
The original source entries and clocks are never rewritten. A supplemental
fixture retains two exact original audio Articles alongside the earlier
attribution fixture; its provenance is recorded beside it.

Visual impacts around archive +70 and +88 seconds have nearby sound candidates,
but the competing Incinerate fits prevent strict independent Rock Throw audio
attribution. Replay already supports these attacks from independent HP changes.
The recorded regression must add no extra attacks from these ambiguous sounds.
Tests separately retain correct
Turtonator, Camerupt and opponent Gourgeist faints, the conscious Carbink and its
supported attacks, and the unresolved final player Gourgeist faint.

## Remaining acceptance boundary

An immediately absent recipient may never provide two positive normal bars.
Neutral terminal capture covers that inspection case; it does not bypass the
Fast Move camera guard. Generic color change or shrinking cannot safely declare
a faint: this recording also shows a living Carbink shrinking during recall.
Automatic shrink/disappearance classification and the proposed corroborated
terminal-faint fallback remain unimplemented pending reliable visual evidence.

There is no independent final recipient impact in this archive to pair with its
late Mud Shot sound. This slice must leave that missing final attack and player
faint unresolved. The old video is human validation evidence, not a synthetic
replacement for absent archived witnesses.

Real-device validation is still required for capture coverage, raw PNG cost,
archive size, and recognition during a new low-HP/charged-return ending. Tests
and an emulator cannot establish live recognition accuracy. The implementation
was verified before installation; the subsequent requested phone update is
recorded below.

## Validation

- Native screenshot recognition on the disposable `Overdex_API34_Check`
  emulator passed **2 tests**. The default Moondrop crop returned exactly
  `Switch in a new Pokémon?` with native word geometry. Repeated shield-choice
  and ordinary-battle crops were rejected; the shield text was actually read,
  so that negative check did not pass merely because OCR returned nothing.
- The emulator could not download its optional Google OCR module. Native checks
  therefore used the repository's established test-only bundled-model approach,
  via an external Gradle init script. No repository dependency was changed.
  This verifies the real screenshot/crop/native-reader path, not optional-model
  delivery or live phone performance.
- The original archive SHA-256 still matches the value above, and both recorded
  fixture subsets were checked against the original Articles field for field.
- The final full unit suite passed **2,563 tests**, with zero failures, errors or
  skipped tests. This includes 22 live recipient/audio cases, seven Replay
  pairing cases, five recorded-source cases and three archive-retention cases.
  The full original archive was supplied through `OVERDEX_FAINT_REPLAY_ARCHIVE`.
- The normal debug APK build passed without the emulator-only init script.
  Its ZIP contains no bundled OCR native library. APK SHA-256:
  `c208f616a5e0d5cea5288582bcf0827e209ec07722bb0d95622b6512692e5b83`.
- Independent review covered later audio onset, pair-first/late-HP duplication
  and recipient replacement boundaries. A failing regression fixture was
  corrected to provide an explicit independent faint; its unsupported default
  count-based conclusion had correctly been rejected. Safety assertions and
  production behavior were preserved. The final full suite above includes the
  corrected fixture.
- `git diff --check` passed. The disposable emulator was stopped after its native
  checks. No physical phone was changed during implementation verification.

Native test output: `/tmp/overdex-step2-native.log`; full-suite/build output:
`/tmp/overdex-step2-validation.log`.

## Moondrop installation — November 11, 2026

After the trainer requested the audio/switch rule and installation before the
next match test, the normal verified APK above was installed with data retained
on `MD_PH_001`, serial `MDPH00124112500414`, physical resolution 1080 by 2400.
No Droidball service or MediaProjection session was active before replacement.
The installed `base.apk` hash exactly matches the verified normal APK.

A 418,285,056-byte pre-install backup preserved 429 files across match archives,
battle records, calibration profiles, the summary inbox, preferences and
databases. Backup and manifests are in
`/home/sean/overdex-device-backups/2026-11-11-finishing-observation-windows-210524/`.
After launch, all 429 files were still present; the only changed hash was the
Firestore local cache database. All match archives, battle records, calibration
profiles, inbox files and preferences matched exactly, with no added or missing
files in the checked paths.

The app cold-launched successfully to its home screen, stayed the resumed app,
and had no fatal markers for the new process. The screenshot and launch check
are in the backup directory. This is installation/startup verification only;
the next live match still needs to establish finishing-hit capture coverage,
correct attribution and processing cost. The related source-time rules are
[AUDIO-01 through AUDIO-03](../reference/battle-rulebook.md#audio-and-visual-timing--november-11-2026).
