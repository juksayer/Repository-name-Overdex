# Pixel 14:47 Replay gaps — November 12, 2026

Match `41ed0b78-fc1c-413f-b05f-651270378eb9`. Original archive and processing
report copied to the 1 TB drive at `2026-11-12-pixel-1447/`; device copies retained.
Trainer reports numerous long idle periods in Replay. No accompanying video was
provided in this turn, so real attack counts and exact quiet intervals are not
independently established.

## Recorded evidence

17 canonical Fast Move uses: player Gourgeist 3, Camerupt 1, Turtonator 0;
opponent Clodsire 8, Tinkaton 3, Mimikyu 2. Turtonator is identified with Incinerate,
but move identity alone does not establish individual uses. Replay also has
fallback reconstruction and filtering, so these counts are not claimed to be the
exact number of displayed animations without running the full presentation.

Opening opponent HP measurements fall from .9894 to .8723/.8677, then .7460.
The first opening loss is absent as an opponent-side damage tick and no player
Turtonator canonical Fast Move is recorded. Recorded audio includes accepted
Incinerate candidates, but current inference requires a qualifying impact with
side/source-time alignment; accepted audio similarity alone is not one counted hit.

`RecordedPixel1447HpDeliveryTest` feeds the first eight archived opponent HP
measurements, original geometry, confidence and source times into the unchanged
ActiveHpBarDamageTickDetector. It passes: the detector emits a >10% opening loss
at the second sample's original source time. Thus the stored values can support
a tick if delivered directly. This does not replay live eligibility gates,
resets, unarchived frame-hub samples or collector scheduling. Those are the next
boundaries to inspect, rather than loosening the damage detector or inventing
Replay attacks. Fixture retains original article IDs and payloads.

Only two opponent-bar PRESENT border pulses were recorded, versus eleven on the
player bar; this is consistent with much weaker visual impact support for player
attacks. Missing pulse records do not prove no attack happened. Samples later
contain large gaps/camera relocation, for which the existing detector deliberately
reacquires rather than assigning all missing damage to one move.

## Processing

2,825 accepted testimony records and 2,825 completed. HP pending peak 8,
oldest tracked pending age 1.459 seconds; all completed by export. Raw publication
39.395 seconds cumulative elapsed versus 7.164 seconds CPU, still the largest
serial stage. The report contains the old aggregate raw_publication timer;
this match does not exercise the newly prepared four-part publication instrument.
The installed APK remains the cache build, not the publication-breakdown build.

No MatchStarted article is present; MatchRecordStarted is present. Any offsets
used during this inspection were relative to the earliest preserved monotonic
source clock, not asserted Replay timestamps. Archive records WIN; its correctness
was not independently reviewed in this investigation.

## Next narrow investigation

Track opponent HP frame delivery, `fastMoveEvidenceAllowed`, and reset reasons
around the opening loss. Compare accepted HP measurements to what the damage-tick
collector actually receives. Continue the separate publication timing trial;
faster delivery alone is not yet shown to restore missing impact events.

Validation: one focused recorded-data detector test passed. No production code,
recognition rule, archive content or installed build changed in this review.
