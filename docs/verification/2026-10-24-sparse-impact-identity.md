# Sparse impacts do not establish a slower Fast Move

## Recorded failure

In Pixel match `8952313d-385b-4cc0-ae33-1259faf1827f`, three player uses at
approximately 155.448, 156.900 and 158.360 archive seconds supplied two HP-damage
intervals with a median of 1.455777957 seconds. The old inference identified
Dragonair's move as Iron Tail with confidence 0.97051865. The user confirmed
Dragon Breath and continuous attacking.

The previous safeguard protected an already established faster move from a
slower multiple of its duration. It did not protect an unidentified combatant.
One visible damage update every three Dragon Breath cycles could therefore
eliminate Dragon Breath and select Iron Tail. Repeating that sparse pattern did
not make it proof of a slower move.

## Change

Impact cadence votes now keep a faster legal move eligible when the elapsed gap
matches a whole-number multiple of its duration within the existing timing
tolerance. This applies to HP-damage uses, border pulses and their derived
cadences, which remain one evidence family.

Compatibility is separate from positive cycle evidence. These possible missed
hits cannot establish an identity, contribute another independent cadence sample,
or award energy. Direct intervals, independent motion, uniquely resolving
effectiveness or accepted reference audio can still resolve the candidate set.
Unvalidated recipient visual effects cannot certify an identity. No additional
attack is inserted into the timeline, and no original measurement is rewritten.

## Validation

The regression fixture contains 132 unchanged selected Articles from archive
seconds 129–160 plus the preceding opponent identity, in original publication
order. Its provenance file records the archive hash, source range and fixture
hash. As the live Match does, the test uses the resolved player appearance and
does not feed historical move identities or energy conclusions back as evidence.

- Before: the recorded regression fails by identifying Iron Tail.
- After: Dragon Breath and Iron Tail remain eligible; neither receives exact
  player energy from this ambiguous segment. Serialized source Articles remain
  unchanged.
- Repeated damage and duplicate pulse/use views do not become independent proof.
- Independent motion can resolve a genuinely slower move; an alias-only gap
  cannot provide a missing direct sample for a faster one.
- Two accepted distinct reference sounds resolve a cadence ambiguity, with both
  audio Articles cited. This checks inference behavior, not live matcher accuracy.
- Existing Sealeo, idle Camerupt, paired Greninja, configured-move, switch and
  charge-boundary regressions continue to pass.

The full suite passes **1,078 tests** with no failures, errors or skips. The debug
APK builds. This fix is included in the subsequent
[combined device update](2026-10-24-low-confidence-fast-audio.md) on both phones.
The Pixel initially refused installation for insufficient storage, then succeeded
after the user freed space.

## Remaining boundary

This prevents the demonstrated wrong identity; it does not recover an exact count
of unseen Dragon Breath uses. Sparse candidates may stay unresolved longer until
another witness supplies distinguishing evidence. Separate best-play estimates
remain estimates. The saved historical match is unchanged.

The same late-start recording had audio but no Fast Move audio analysis: the
listener required a countdown or MatchStarted. The subsequent
[late-start audio fix](2026-10-24-late-start-fast-audio.md) recovers listening from
a witnessed in-progress charged phase and documents the retained-clip comparison.
A fresh live battle remains a separate acceptance check.
