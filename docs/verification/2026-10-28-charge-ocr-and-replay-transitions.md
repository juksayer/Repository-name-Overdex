# Charged-move OCR and replay transitions — October 28, 2026

The user requested legal-movepool recovery of `FouPlay` and earlier charge
preparation based on disappearing charge controls and the exit icon.

## Legal move recovery

`FouPlay` already passed the single-character repair. The recorded Candela text
was `Scrafty used FouPay!`, missing both lowercase Ls. A shared legal-move text
resolver now serves live inference and replay recovery. It permits at most two
missing internal characters while retaining both ends, original character order,
at least six visible characters, and at least three quarters of the legal name.
Exact names take precedence; competing repairs remain unresolved.

Species/form legality and performer attribution are still required. In Replay,
a named active Pokemon with an illegal move cannot be reinterpreted as a
nickname to assign that move to the other side. Mirror and ambiguous readings
remain unresolved without additional evidence.

Live inference retains raw text, source timing, custody links and the reason for
repair alongside a derived spend. Replay can reconstruct the Foul Play name and
Dark icon from an old raw reading without creating an archived energy spend or
changing the recording. Repeated readings do not duplicate the attack.

## Earlier replay preparation

`ReplayChargeTransitions` consumes existing charge-control rim measurements.
It requires two stable positive frames, repeated loss in both controls,
continuous source availability, unchanged source/crop geometry and a nearby
explicit `Get Ready` or `Attack Incoming` cue. A single control is accepted only
when the current Pokemon is explicitly configured with one charged move.

The preparation can start at the first sampled loss. The replay action retains
the last-visible/first-unavailable interval, confirming frame, original phase
cue, geometry and source Article IDs. This is a bounded presentation estimate,
not a newly witnessed exact phase timestamp. The original cue still anchors the
announcement-association window, so starting preparation earlier does not split
one charged move into two episodes.

Unknown measurements alone, source disablement, coverage gaps, geometry changes,
switches (including resolved forms or team slots with unchanged badge text),
faints, competing cues, and contradictory recorded attacks must not
establish an earlier boundary. Accepted preparation intervals also suppress
reconstructed Fast Move noise during that charge preparation. The Timeline and
archived Articles remain unchanged.

No exit-icon detector or new calibration region is included in this change.
That independent cue still needs a calibrated region and measured visibility
before it can contribute to live or replay phase attribution. This change adds
no image reader or live capture load.

## New Moondrop recording

Copied read-only from the phone and verified:

- Archive: `2026-10-28_09-59-04_c0f775ec-947e-473b-9bda-57850a5989fa.odxmatch.zip`.
- SHA-256: `01d70c67c1bc199845f9e409181648026a11e1c731a0e073b06eeacf864874a7`.
- 3,925 Articles and 481 artifacts verified. Device original unchanged.
- Desktop review: `/home/sean/overdex-device-backups/2026-10-28-moondrop-charge-transition/transition-review.md`.

The recording has 250 charge-fill measurements: 239 `UNKNOWN_RIM` and 11
`UNKNOWN_SEPARATOR`. The few rim candidates are unstable; no valid fill was
found. All 60 charge-label readings are blank. Sampled retained label crops show
ground or attack effects rather than move names. There is no retained controls
or exit crop, and the only full frame is Team Select. This does not establish
the exact crop displacement or prove that the controls were absent.

The recorded-measurement regression rejects every proposed earlier transition
in this match. The existing `Trainer Charge-Move Controls` and left/right
charged-move-name boxes need live verification; no saved calibration was changed.

Useful explicit cues remain: Attack Incoming precedes one Grass Knot by 3.163 s;
Get Ready precedes two Iron Heads by 6.368 s and 8.035 s. The recording contains
two Grass Knots and four Iron Heads. WIN text and win music agree, but the final
Jolteon faint was not directly recorded. Times in the detailed review are
relative to an inferred MatchStarted; GO was not observed.

VS-triggered audio analysis is confirmed before countdown 2. VS publication took
7.508 s after the source frame, a remaining latency issue. The first retained
accepted audio sample is not the time analysis first ran.

## Validation and remaining acceptance

Focused checks cover legal OCR recovery, unchanged archives, duplicate/side
safeguards, bounded transition provenance, and the actual Moondrop measurements.
Independent review found and verified the fix for resolved form/slot switches
that keep the same raw species text.

- Final full suite: **1,271 tests across 210 suites**, zero failures/errors/skips.
- Debug APK build and `git diff --check` passed.
- APK SHA-256: `855ba64a3c4cc5a1d8633c483de13e0de1a424029ead5cc187c22fcc607587b4`.
- Verification log: `/home/sean/overdex-device-backups/2026-10-28-moondrop-charge-transition/verification.log`.
- Moondrop update installed successfully after confirming no Overdex service
  was running. Six calibration, HUD-layout and current-team preference files
  have identical SHA-256 hashes before and after installation.

A new live transition test has not been performed. No active session or saved
calibration was interrupted or changed. The Pixel was not updated in this task.
