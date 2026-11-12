# Verified empty HP and completed Replay

Historical Replay-only checkpoint. Live activation was subsequently implemented
in the [November 12 integration](2026-11-12-live-verified-hp-faints.md); the
validation and installed APK below describe this earlier build.

The ordinary terminal HP reader can measure an empty outline even when the
native badge's final Poké Ball count disappears too quickly. Live faint
publication still depends on team-count history. This change adds a narrow,
retractable **completed-Replay** path; it does not change live faint publication.

## Evidence and behavior

`VerifiedHpFaintResolver` orders one side's independent evidence by capture time.
A trusted, confident side-located species badge owns the interval. A normal
battlefield RED measurement at no more than 25% HP can support one subsequent
EMPTY measurement within the fixed three-second terminal window. Both readings
must include source dimensions, confidence of at least 0.80, and compatible
outline geometry. The geometry bounds match the terminal watch, including its
small vertical displacement allowance. A numeric zero alone is insufficient.

Switch/entry and ambiguous-identity boundaries clear ownership and the red
anchor. Charge-phase boundaries clear the anchor; the same known Pokémon can
acquire a fresh battlefield red bar afterward. A centered charged-impact bar
cannot supply this path's red-to-empty proof. Multiple readers of one source
frame do not provide multiple votes; contradictory custody IDs are barriers.
Screen-ignore/restart boundaries discard the previous attempt's candidates.

Later positive HP with independent, consistent ownership retracts the candidate.
A fresh independent badge must re-identify that actor after the empty reading;
the old active name cannot silently claim an unidentified replacement's HP.
Replay also excludes the contradicted empty values, repeated empties, and their
co-timed zero damage ticks from displayed HP. Otherwise a Pokémon could return
conscious while the old minimum-HP rule still held its bar at zero. Independent
border pulses and attacks are retained. Correction references identify the
original measurements and the later survival proof.

`ReplayVerifiedHpFaintProjection` creates a presentation-only faint with basis
`VERIFIED_RED_TO_EMPTY_HP`; the KO marker, fall and HP use the empty image's
capture clock. It does not create a Poké Ball count, a move, expected damage, or a
result. A count-confirmed/explicit KO already present for that Pokémon suppresses
an extra HP-derived marker. Original archived articles, timestamps and
serialization remain unchanged and available through `timedArticles`.

## Recorded pixels and limits

- Tinkaton: retained red pixels at video 59.005411111 s and empty outline at
  60.504888889 s from `Screen_recording_20261111_161531.mp4`. These pre-existing
  test crops were derived from 816×1808 video by bilinear scaling to 672×1493,
  as documented in their provenance file. The real pixel reader's outputs are
  adapted into test articles. They are **not** measurements recovered from the
  old live archive. The same pixel geometry is additionally assigned to the
  player side to test symmetric plumbing; it is not a second recorded player KO.
- Living Carbink recall: native 816×1808 source pixels from
  `Screen_recording_20261111_124455.mp4` at 181.297733–181.654900 s remain positive
  (~17%) or unreadable as the Pokémon shrinks/disappears. They create no KO and
  do not drain Replay HP to zero.
- Original 16:15 archive: 122 HP measurements, two RED classifications and no
  EMPTY classification. This change cannot restore the missing live Tinkaton
  measurement merely because the separate video contains it.
- Original 21:13 archive: 638 HP measurements, 86 RED classifications and one
  EMPTY classification belonging to the earlier Annihilape sequence. There is
  no final Cramorant empty measurement. Its fade/shrink after Rock Tomb remains
  the separate terminal-visual recognition problem.

Live activation is deliberately deferred: `PlayerActiveIdentityInference`,
entry-side attribution and other live consumers can close activity/slots when
they receive a faint. A new live source needs coherent retraction across those
consumers before this rule should publish live faint articles.

## Validation

Focused resolver, Replay, count-projection and recorded pixel tests: **29 tests
in five suites passed**, with no failures, errors or skips. Coverage includes
delayed/reordered observations, repeated counts and zeros, ordinary switches,
charge boundaries, ambiguous identities, conflicting IDs, missing dimensions,
stale anchors, geometry changes, later survival, capture-clock ownership, archive
immutability, and retaining unknown team counts/results.

Full unit suite: **2,796 tests across 368 suites**, no failures, errors or skips.
The debug APK build passed in the same run. `git diff --check` passed.

Build SHA-256: `49f73f1283f3a942fc5f3e7c0d4cc42a21c83b7ff697a2632f54ee4fe7572a98`.
Logs, build validation and Pixel installation/retention evidence are retained at
`/home/sean/overdex-device-backups/2026-11-11-pixel-verified-hp-replay-233150/`.

Installed on Pixel 4 `99261FFAZ0017J` at 23:37 local time. The installed package's
SHA-256 matches the tested APK. Cold launch reached the normal ODX-Fi home screen,
with process 18325 alive and no new crash-buffer output. Droidball was stopped
before installation and was not started for this check. The Moondrop was not used.

Before/after scoped backups retained all 111 protected files, including 32 match
archives. 110 files are byte-identical. The sole changed file is Firebase's cache
database: only `target_globals.last_remote_snapshot_version_seconds/nanos`
changed; every other logical table is identical. Match archives, settings,
calibration and other protected databases are unchanged. The loose artifact
store remains on the phone and was excluded from these scoped backups; no
instrumentation, data clearing or restore operation was performed.

No new live battle has validated this path. Unit tests use actual retained pixels
plus explicitly constructed source-history cases; they do not establish current
device capture coverage or recognition accuracy.
