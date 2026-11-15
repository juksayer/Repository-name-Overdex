# Short priority window for possible finishing exchanges

In the reviewed 14:50 Moondrop archive, Vaporeon's complete-bar color testimony
at GO+91.88 s did not reach the Timeline until roughly 8.3 seconds after its
source frame. The normal terminal visual gate had a three-second source-time
deadline, so reacting to that later publication could not preserve the ending.
The archive's original uncertain pixels do not prove zero HP or a faint.
For context, cumulative diagnostics for 193 ordinary opponent HP publications
reported mean source-to-submission time about 299 ms and mean subsequent
submission-to-Timeline time about 3.5 s. These are distinct measurements over
the match, not the latency of this particular complete-bar sample, and some
source-to-submission samples fell in longer latency buckets.

After a complete-bar measurement is accepted into Custody, the HP reader now
also supplies that same source-timed measurement directly to the terminal
capture scheduler. An eligible small visible damage-color span opens a bounded
visual window when the source frame is at most 500 ms old. It does not wait for
Timeline publication and does not change the accepted testimony or its original
clock. The direct window is marked `FRAME_COLOR_HINT`; because its Article ID
does not yet exist, it makes no fabricated reference. Actual captured crops
are cited, and the independent bar Article remains in the normal ledger. If
the frame is stale, the normal archival path still preserves the measurement.

That fresh cue, or an independent frame-local missing-bar scheduling hint, can
start a fixed three-second priority window. New routine charge-button label and
button-effectiveness OCR captures and sparse forensic HP PNG captures pause;
queued OCR inputs may finish. Source HP measurements, Fast Move and cry audio,
species, balls, shields, announcements, results, terminal pixels and the
opponent hand sample remain available. A repeated hint cannot extend the
window, and it ends on its own even after a living switch or nonlethal hit.
The one-second cooldown keeps repeated low-HP readings from continuously
withholding the optional captures. It is an attention budget, not a claim that
every possible ending is covered. Other applications are not terminated.

The source archive regression reads Vaporeon's unchanged original bar Article
and demonstrates that a simulated prompt 300 ms reader delivery can open this
window before the original eight-second publication, while a stale cue cannot.
This proves scheduling behavior, not the actual Moondrop delivery time or that
the obscured departure pixels were captured. Separate controls cover repeated,
stale and future hints, full bars and tiny surviving/unknown color, automatic
expiry, original evidence, existing living switches and backwards Replay.
Review of the actual recording and source evidence remains in
[the Moondrop match review](2026-11-15-moondrop-1447-1450-review.md).

The change is deliberately limited to capture scheduling. It neither changes
the decision that Vaporeon fainted nor credits Incinerate without the missing
independent evidence. The full live workflow, actual delivery latency,
occlusion, frame continuity and CPU benefit require a new device trial.

Focused tests passed (70 tests, none skipped). The full debug unit suite passed
3,125 tests with 11 skipped, and the debug APK built. It was installed and
launched on Moondrop `MDPH00124112500414`; the original on-device 14:50 archive
still has SHA-256
`f0609c30d6e5970ee6cc4561774701264522359224f202d90150ac90199d2258`.
The source video and backup archive were only read. No fresh battle was run
with this build during this verification.
