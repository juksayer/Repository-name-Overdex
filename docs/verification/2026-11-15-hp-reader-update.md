# Complete HP bar color evidence continuity

The live complete-bar track now submits a new `LIVE_COLOR` testimony when a
visible orange damage-colored trail appears or disappears while live-colored
pixels remain. It retains the same source frame, side, appearance token,
geometry, color counts and confidence. The existing status stays compatible
with prior archives and Replay. A changed color trail is evidence of appearance,
not proof of damage amount, a distinct attack, empty HP, a faint or KO credit.
Repeated unchanged frames do not create new testimony.

When an actively sampled complete bar cannot be read because its outline is
unavailable or the region is obscured, the normal-field and charged-impact paths
interrupt any pending orange-only persistence run. They retain the last valid
geometry, apply the existing source-time/generation guards, and submit no
fabricated measurement. A delayed valid result cannot cause a later source-time
sample to bridge a previously observed unreadable interval. Source frames not
delivered to this reader remain a known coverage limitation; this does not
claim that every game-rendered or captured frame is received or that unordered
late availability can retroactively revise already published testimony.

The focused checks include the recorded Sealeo nonlethal frame with a surviving
sliver alongside orange; actual complete-bar sampling from the recovered Sealeo
frames; injected bright and dim tiny survivors; whole-bar and clipped-geometry
checks; source-time reorder and unknown gaps; appearance resets; and the
recorded living Carbink recall. The new changes do not resolve the ambiguous
background in Sealeo's KO frame, publish an inferred zero, or attach Incinerate
to the faint. The original source recording and archived match retain their
previous SHA-256 values. Test and build logs are on the backup drive in
`2026-11-15-hp-reader-update/`.
