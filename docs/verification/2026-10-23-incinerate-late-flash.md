# Incinerate's late border flash was counted twice

## Finding

The [settled-fill review](2026-10-23-controlled-settled-fill-review.md) identified
an HP-loss/fill-supported use at archive 231.598 seconds and a border-only use at
232.691 seconds in Moondrop match `0330b719-8c57-45bc-9f41-247bf016a452`.
Both generated 20 energy. Their exact source-time separation is 1.093956307 s.

Review of `Screen_recording_20261023_175949.mp4`, sampled every quarter second
from video 143–148 s, supports one Incinerate in this pair. Turtonator attacks,
Swampert loses HP, and the later flash occurs while Turtonator is idle. The next
visible attack starts around 147.5 s. The approximate archive/video offset is
86.75 s, independently established in the earlier review.

The intervening archived HP measurements corroborate this: 41 of 42 measurements
from archive 232.3–236.3 s remain near 32.8–33.0%. One transient reading jumps
upward to 45.0%; it does not supply another accepted damage tick. Do not describe
every individual measurement as stable or discard that outlier.

The cause is a fixed fusion ceiling: known moves could join different witnesses
only within 1.05 s. The delayed flash missed that limit by about 44 ms and became
another use, even though the damage-based use was still pending when it arrived.

## Change

`FastMoveUseInference` retains the existing short-move allowance, but permits
longer trusted durations up to half a cycle: 1.25 s for five-turn Incinerate.
Unknown durations and an energy-preference assumption alone do not get this
extension. Separate HP losses and separate pulse onsets still create separate
uses even if lag compresses their timestamps. This is not a minimum-attack-gap
filter and does not invent attacks from a known duration.

All underlying measurements remain recorded. The fused use cites both the loss
and flash and retains the loss's source time. The independent best-play forecast
is unchanged. The longest fusion hold grows by 200 ms for this known five-turn
move; no new OCR, frame, audio or image-retention work was added.

## Count and energy checks

The regression fixture contains 455 unchanged Articles in original arrival
order: four context Articles plus selected numeric measurements/conclusions
from archive 226–245.1 s. Context preserves the resolved player appearance,
opponent identity, configured Incinerate identity and preceding charge phase.
The test does not feed old use/energy conclusions back into the new inference.

- Before the fix: the regression fails with six inferred uses instead of five.
- After the fix: five original HP-loss cycles produce five uses and five energy
  awards, totalling 100 generated energy instead of 120 in this segment.
- Both suspicious source Articles now support one use; its timestamp and attacker
  are preserved. Fixture serialization remains unchanged after inference.
- Independently recomputing the existing player ledger without the duplicate
  award leaves the earlier 80-energy checkpoint unchanged. At video 154.052 s,
  the result is 90 energy instead of the old capped 100, matching the independently
  measured two-button range of about 89.6–90.5. This arithmetic is offline review,
  not an automatic visual correction or a modification of the archived ledger.

Source IDs, hashes, sampled-frame hashes and numerical results are in the
[review data](charge-fill-height/0330b719-late-flash-review.json). Frames and
contact sheet remain in the desktop backup's `incinerate-pair/` directory.

## Validation and remaining boundary

Focused checks pass for the recorded Incinerate segment, previous paired Camerupt
and Greninja recordings, one-turn Water Gun, unknown/assumed identities,
compressed distinct hits, charge pauses, switch/faint boundaries and energy
deduplication. The full suite passes **1,070 tests**, and the debug APK builds.

APK SHA256: `b1879ea11b6115df050d35fe2a0353f049730f49391b23cdacadb6b8182f8c2f`.

Installed successfully as an in-place update on the connected Pixel 4 after
confirming it had no active Droidball service. Package inspection confirms the
update. The Moondrop was not connected, so it has not received this build.
Installation is not a live-battle acceptance check.

This prevents the demonstrated duplicate in new inference. Existing archives
and their historical Replay events were not rewritten. It does not prove all
attacks are counted correctly or resolve every connection-related gap. Live
acceptance requires a subsequent battle on the updated build.
