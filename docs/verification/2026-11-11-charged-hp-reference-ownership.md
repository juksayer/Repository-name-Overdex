# Charged HP references survive delayed identity delivery

## Problem and behavior

The [21:13 Moondrop match](2026-11-11-moondrop-2113-review.md) recorded a final
player QTE with `NO_REFERENCE`, although a captured normal opponent HP sample
was only 4,936,390,384 ns older. The original archive does not preserve the
precise ordering of live HP seeding and the species subscriber's reset.

The old unconditional reset reproduces one possible failure: seed that normal
HP, deliver the earlier Cramorant identity, then prepare the final QTE. The
species reset erased the newer geometry. Delivering identity first succeeded.
The regression failed with the original reset behavior before the fix.

`ChargedImpactHpWatchPolicy.appearanceBoundary` now advances a same-side source
boundary and prunes only references captured at or before it. A charge window
survives only when both its reference and preparation are strictly newer. Its
token, acquired outline, consumed-empty state, impact time and deadline remain
unchanged. Retained reference freshness still uses the original capture time.

Live identity handlers use this operation; explicit valid appearance starts are
preferred, otherwise the accepted species image's source time is the boundary.
Current faint, unresolved switch/departure, stop, ignore, enable/disable and
screen-size resets retain their hard-reset behavior.

There was a second delivery ordering: an older identity arriving during the
ordinary locator's scan changed the terminal generation, preventing even newer
normal geometry from reaching charged HP. The live caller now snapshots a
separate per-side charged-reference epoch before scanning. Successful measured
geometry may seed charged HP only if that epoch and the exact current dimensions
still match, and its source time passes the appearance boundary. Hard resets
advance the epoch even when their supplied timestamp is older; resizing away
and back cannot admit an old scan. Admission never changes dimensions.

The terminal-generation guard for ordinary HP publication remains intact. This
change does not publish extra HP samples or bypass source ownership in the
ordinary detector. The existing locator supplies actual visible measurements;
no additional pixel search, OCR, frame queue or image retention is added.

## Recorded evidence and regressions

The fixture at
`app/src/test/resources/battle/recorded-evidence/2026-11-11-cramorant-hp-reference/`
contains eleven unchanged original Articles with closed evidence dependencies
and hashes. It preserves the actual failed `NO_REFERENCE` diagnostic. Both safe
delivery orders now prepare using the recorded 0.7619048 reference. A reference
from before Cramorant's identity is rejected in both orders. Preparing does not
supply an impact anchor, a new HP fraction or a faint.

`ChargedHpReferenceOwnershipTest` also covers equal/earlier/newer references,
unchanged acquired windows and deadlines, consumed empty state, stale reference
age, a window based on predecessor geometry, older duplicate identities, hard
resets, scans in flight, side independence, centered pixels and A-to-B-to-A
dimension changes. Existing charged and terminal policy regressions are retained.

Before the implementation, the new general tests reported 3 failures out of 7;
the recorded test reported 1 failure out of 3, specifically newer HP seeded
before the delayed identity. Logs:

- `/tmp/overdex-hp-reference-before.log`
- `/tmp/overdex-hp-reference-recorded-before.log`

After implementation:

- Focused policy/recorded/early-preparation/terminal checks: 67 tests passed.
- Full unit suite: 2,737 tests in 362 suites, zero failures/errors/skips.
- Debug APK build: successful. Full log `/tmp/overdex-hp-reference-full.log`.
- Independent code review found no blocking defects in this slice.
- `git diff --check`: passed.

## Moondrop update

Installed with data-preserving replacement at **2026-11-11 21:58:56 -0600** on
`MDPH00124112500414`, after checking that DroidballService was absent. Overdex
launched successfully; its main activity was resumed and the screen was saved
for visual verification. This build includes the previously tested delayed-ENTRY
species coverage change as well as this HP-reference fix.

Built and installed APK SHA-256:
`e230f26b4be94594e9c4072d016e11ccfb8df8884602179a8151fe179cd54881`.

Before installation, the scoped records/settings backup included 455 files;
every tar member was readable. A fresh device inventory matched 454 files; the
only change was the live Firestore cache database, whose two copies both passed
SQLite quick_check with the same table counts. Backup, inventories, build summary
and installation screenshot are retained under:

`/home/sean/overdex-device-backups/2026-11-11-moondrop-hp-reference-update-215308/`.

Post-install comparison found all 455 original files still present and no added
files in those roots. All 51 match archives, 338 battle-record files, 14 calibration
profiles, the calibration reference, 21 preferences, and the owned-Pokémon/team
database with WAL/SHM were byte-identical. Only the Firestore cache differed;
pre/post copies passed quick_check and had identical table counts. See the saved
`post-install-verification.json`. The app opened to its ODX-Fi home screen.

## Acceptance boundary

This is a tested timing fix, not proof of the historical cause of the Cramorant
failure. The archive is unchanged and still lacks the final charged HP and
Cramorant faint. The user's reported Rock Tomb KO and manually reviewed
shrink/bar-disappearance/extended-hand sequence remain separate evidence.

The next recorded Moondrop battle should confirm that preparation retains a
fresh source-time reference, begins charged HP scans, and captures supported
damage or depletion. A missing bar, orange trail or hand pose alone must not
become numeric zero or a faint. Live KO accuracy remains unvalidated.
