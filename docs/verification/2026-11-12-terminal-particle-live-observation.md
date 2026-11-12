# Live terminal particle observations

Follow-up: the [final charged-collapse inference](2026-11-12-terminal-charged-ko.md)
now consumes optional flash geometry together with independent charge, identity,
count and result evidence. The implementation-only status below records the
earlier observation checkpoint; particle batches by themselves remain neutral.

Status: the numeric particle reader is registered in the Droidball observation
composition. Charged-phase scheduling, typed custody publication and archive
serialization are implemented. This does **not** yet classify collapse, recall,
sprite ownership, a KO or an HP loss. A fresh live-battle acceptance check and
the sequence-to-KO inference remain outstanding.

## Runtime path

`PokedexViewModel` registers `TerminalParticleWitness` immediately after the
existing `TerminalVisualSequenceWitness`. They share the latter's
`TerminalVisualCaptureGate`, which receives accepted early charge cues and
source-timed canonical articles. The particle witness reads an independent
multicast feed on the Default dispatcher; it does not encode PNGs or wait for
the sparse-image writer. One-shot/channel inputs do not acquire an additional
consumer. Only one receiving territory is assigned at a time.

`particleAssignment()` is independent of the PNG cadence, attempt limit and
summary drainage. Get Ready and Attack Incoming allow preparatory measurement
for the requested recipient before delayed move-used recognition. The absolute
budget ends no later than 20 seconds from phase preparation, 8.5 seconds from
an accepted player-QTE grade, or 6 seconds from a witnessed Used source time,
whichever applicable deadline is earliest. Repeated cues do not renew the
budget. Either combatant's newer appearance, a dimension change, lifecycle
boundary or a new opposing phase retires the previous assignment. A low-HP or
departure-only image window does not open this continuous charge reader.

The requested recipient is **context**, not a claim that this Pokémon is
already visible: QTE, shields and attacker effects can still occupy that
territory. Every observation remains neutral for later source-time reconciliation.

## Measurements and availability

The witness uses the existing adjustable player/opponent terminal visual crops,
rejects its own overlay and retained-badge pixels, and reads actual Bitmaps
synchronously without retaining them. The underlying
[pixel primitive](2026-11-12-terminal-particle-reader.md) retains its source-gap,
geometry, transparency, clutter and allocation guards.

`TerminalParticleObservationBuffer` stores numeric observations only, capped at
600 readings per phase. Batches contain at most 20 samples and normally publish
within one second; an independent 100 ms task flushes due partial batches and
closes an expired assignment even when frames stop. Each sample keeps its own
source and wall clock, preceding source clock, source dimensions, crop bounds,
publication stream/sequence, status, measurement cost and pixel features.
Obscured, stale and invalid frames interrupt pixel continuity instead of
asserting absent particles. A changed publication stream resets the baseline;
older publications cannot replace its watermark. Window closure is not proof
that every frame up to its scheduled deadline was observed.

A late Used context flushes the previous batch before adding new references;
it does not rewrite earlier samples. Typed observations survive compact exports
and JSON round trips. A final batch arriving after the first result snapshot
requests the existing completed-match archive refresh. This closes a race where
the most relevant final readings could otherwise remain only in memory.

## Verification

The full unit suite passed: **2,840 tests across 373 suites**, no failures,
errors or skips. The debug APK and Android instrumentation APK built successfully.
The 18 added unit tests cover scheduling and bounded work, original Cramorant
cue clocks, delayed/reordered context, both receiving sides, sparse-PNG quota
independence, appearance/lifecycle retirement, occlusion, publication stream
changes, partial flushes, real recorded pixels through batching/serialization,
and actual witness-to-Custody lifecycle publication without frames.

The recorded-image tests retain positive shield/QTE/attacker counterexamples.
No production consumer translates a particle batch into a faint, damage tick,
move use or Replay HP change. These tests establish observation behavior;
they are not live KO accuracy evidence.

The connected Pixel 4 check uses a bundled saved QTE image, controlled source
frames and the real Android witness/custody/archive path. It starts no game,
screen projection or Droidball service and does not write a saved match. Its
result and APK hashes are retained under
[/home/sean/overdex-device-backups/2026-11-12-particle-live-integration](/home/sean/overdex-device-backups/2026-11-12-particle-live-integration).

The Pixel 4 instrumentation run passed **`OK (1 test)`** in 1.359 seconds.
It verified baseline/measurement/occlusion/rebaseline behavior, preserved all six
individual source clocks through archive serialization, and published no faint,
HP measurement or attack. The current debug build is installed on Pixel 4
`99261FFAZ0017J`; its installed APK hash matches
`0edc50ea9725d06ad43135456d3416120475d33b7dcf274744bce98bf762e92c`.
The app launched successfully. All 102 checked existing archive, battle-record,
calibration and preference files have unchanged hashes, and Droidball remained
stopped. The Moondrop was not operated. These are controlled-device results,
not a new live-battle test or a measured concurrent-capture CPU budget.

## Remaining acceptance

Use these source-timed measurements with an independently owned charged hit,
qualified collapse/recall ordering and result/resource evidence to resolve the
KO, while retaining concession and living-recall alternatives. Confirm the
same resolved event reaches live HUD, Replay and plausible inferred HP depletion
without fabricating an observed empty outline or a missing attack. Verify
capture cost and actual finishes in a fresh recorded battle before declaring
the broader KO/HP objective complete.
