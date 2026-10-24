# Fast Move audio after a late observation start

## Recorded failure

Pixel archive `8952313d-385b-4cc0-ae33-1259faf1827f` began after the countdown.
Its SHA256 is `4c0478c55720663074eb8170b3b8fc65c07c4d86dfeb8e22f763fe68f7271d3c`.
The recording contains neither countdown nor MatchStarted, but it does contain
charged phases, 21 AudioCaptured Articles and 20 stat-change sound checks. There
are no FastMoveSoundMeasured Articles and no fast-move-audio-analysis lane.

The Fast Move listener required a countdown or MatchStarted to open. Reference
identity separately required MatchStarted. As a result, captured audio could not
help resolve the Dragon Breath / Iron Tail ambiguity described in the
[sparse-impact regression](2026-10-24-sparse-impact-identity.md).

## Change

AttackIncoming, GetReadyWitnessed or ChargeMoveUsedAnnounced now opens Fast Move
listening at that witness's time if countdown and MatchStarted were missed.
Audio custody cites the opening witness. These events establish that a battle is
already underway; they do not manufacture MatchStarted or its timestamp.

Reference identity also accepts this witnessed in-progress boundary. Countdown
alone still cannot identify a pre-GO Fast Move. Existing charged-phase exclusion
windows remain in force, and capture still closes on result, five faints or manual
stop. Restart and ignore behavior is unchanged. Listening parameters, matcher
thresholds, side attribution, overlapping-window deduplication and the requirement
for two distinct accepted reference matches are unchanged.

The sparse-impact fix and this fix preserve original observations. Neither adds
unseen attacks or awards energy from elapsed time or weak sound matches.

## Retained-audio comparison

AudioCaptured `52f42d83-10d0-4a4b-8cad-fa5dfb1d605d` retained a 13-second,
8 kHz mono stat-change clip spanning approximately archive seconds 146.169–159.169,
including the ambiguous Dragonair impacts. Its artifact SHA256 is
`3ad78e02b5796f42c1103b94fded95689d28f701528572eb91c2e67d743af152`.

The existing reference matcher was run against all bundled Fast Move references
using its normal 1.2-second analysis windows at 200 ms steps. Among 60 windows:

- Dragon Breath ranked above Iron Tail in all 60.
- Dragon Breath ranked first in 26; Ember in 15, Take Down in 18, Bug Bite in one.
- No window met the full reference-acceptance criteria. Maximum Dragon Breath
  similarity was 0.93889636; a high similarity alone does not establish identity.

This is encouraging comparative evidence, not a verified identification. The
clip was retained by a different audio witness and is not a replacement for a
fresh live recording with the Fast Move listener enabled. Thresholds were not
relaxed to make this example pass. The WAV, source Article, analysis results and
temporary diagnostic source are retained under the local device backup's
`2026-10-24-pixel-review/audio-review/` directory; they are not bundled in the app.

## Validation

The new gate and reference-identity regressions fail before the fix and pass
afterward. They cover all three late-opening cues, no fabricated start, rejection
before the boundary and during QTE, two later accepted sounds establishing a
legal identity, and no reopening after result or manual stop. The live audio
witness test also exercises PCM capture through custody with GetReadyWitnessed
as its only opening event and verifies the evidence reference.

The full suite passes **1,082 tests**, with no failures, errors or skips, and the
debug APK builds. APK SHA256:
`f0ef90ea1deb9faac08435734da68e54cbcd20e0b2f9d4f650f8b39a77dbc002`.

Both phones had no active Overdex service. The Pixel rejected its in-place update
with `INSTALL_FAILED_INSUFFICIENT_STORAGE`; no data was deleted. This fix is now
included in the [low-confidence display update](2026-10-24-low-confidence-fast-audio.md)
installed on both phones. The Pixel retry succeeded after the user freed space;
that report records installation and configuration-preservation checks.

## Remaining acceptance

A new live battle must confirm that late-start recordings now contain Fast Move
sound measurements and that any accepted identity belongs to the correct side.
The older archive stays unchanged. Audio cannot recover exact missed attack counts
from this inconclusive clip. Stale configured teams, the centered single charge
button and broader real-battle attack counting remain separate work.
