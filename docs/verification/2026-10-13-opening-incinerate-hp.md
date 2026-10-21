# Opening Incinerate HP merge — October 13, 2026

## Recording and reproduced failure

Reviewed the Moondrop's new internal archive without changing its contents:
`2026-10-13_20-39-54_1c2ee1d9-4811-4774-b668-2f62fac4876a.odxmatch.zip`.
The ZIP is 16,187,694 bytes with 4,997 Articles; the manifest count agrees. Its
monotonic span is 172.767 seconds.

Sneasel's bar has three separately settled opening losses. Times below are
seconds after the archived MatchStarted:

| Damage confirmation | Before HP | After HP | Player move |
| --- | --- | --- | --- |
| 3.092 | 98.94% | 73.54% | Incinerate |
| 4.723 | 73.54% | 47.34% | Incinerate |
| 8.081 | 47.34% | 22.22% | Incinerate |

The first lower readings precede their confirmations: 2.588, 4.510, and 7.164
seconds. This is a difference between a measurement and its settled-damage
confirmation, separate from capture-to-publication latency.

The old replay cadence normalizer merged the first two confirmed uses because
they were only 1.631 seconds apart, inside its 2.5-second Incinerate period.
HP alignment then combined both losses at the first presentation impact.
A failing regression reproduced **47.34% HP after the first animation**, with
only two opening Incinerate animations. No charged move was needed to cause it.

## Change

Replay now carries each use's linked, valid recipient damage testimonies into
the cadence scheduler. A separate settled loss that continues the previous HP
level remains a separate cycle even when confirmation timestamps are close.
Its animation is placed at the move's reference cadence. Duplicate reports and
the short draining tail of a single hit still coalesce. Combatant and charge
boundaries continue to bound the presentation.

The corrected impacts are at 3.092, 5.592, and 8.092 seconds. Their displayed HP
is approximately 73.54%, 47.34%, and 21.81% (the last includes nearby lower bar
measurements). Each orange chunk represents that hit's own loss. Raw Articles,
their times, and the archive are unchanged; this change is replay-only.

## Validation

- The actual opening fixture failed before the change and passes afterward.
  Original IDs and payloads are preserved, with GO as the fixture's clock origin.
- Regression checks cover three separate hits at 2.5-second intervals, distinct
  damage chunks, duplicate/tail rejection, reverse scrub, and unchanged archive
  serialization. Existing cadence, charge-boundary, and replacement tests pass.
- All **710 JVM tests passed** on the standalone full-suite run. The first run,
  concurrent with APK compilation, had one 4-second timeout in the unrelated
  `ResultMusicWitnessTest`; the complete retry passed without changing that test.
- Debug and instrumentation APKs built successfully.
- Moondrop rendering test passed for the recorded opening: the visible Sneasel
  bar reads 73%, 47%, and 21% at successive Incinerate impacts (UI truncates to
  integer percent). The Fire projectile remains visible at each impact; reverse
  scrub restores the first HP state. First/second-impact screenshots inspected.
- Installed APK SHA-256:
  `87e19a1c600c0f436a3baa6b0fe7fc1b31871f633675fb02f1ef86239391a616`.
  MediaProjection was inactive before installation. All 11 calibration/HUD
  file hashes were unchanged, and the reviewed archive remains on the phone.

Local evidence and logs: `/tmp/overdex-opening-hp-20261013/`.

## Validation boundary

This corrects a reproduced replay grouping error. It does not establish perfect
live Fast Move counting, remove measurement noise, or change energy tracking.
The existing 20:39 recording is sufficient to verify this specific correction;
a new battle is unnecessary for that check.
