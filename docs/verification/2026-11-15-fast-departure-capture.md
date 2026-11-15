# Bounded Fast Move departure capture

The reviewed November 15 12:12 Moondrop match records a complete-bar color
observation at GO+137.376 s, with 11 damage-color columns and 21 unknown columns.
It does not retain the subsequent Vaporeon departure frames. The video and
trainer review place that departure about two seconds later; they do not make
the absent match frames available to the live witness.

Two capture gaps were found. A frame-local missing-bar callback requested a
departure window without an Article ID, but the visual gate rejected all such
requests. It now accepts that scheduling hint without inventing a source Article;
the actual subsequently preserved crops are the evidence. Also, the terminal
particle witness previously received assignments only for charged-move phases.
It now receives the existing bounded low-HP, Fast Move, and departure windows,
while retaining the charged phase's priority.

The gate can also request a three-second window from a source-located complete
bar with a small visible live/damage-color span. Uncertain columns and surviving
slivers are not classified as empty. This is a sampling request, not a damage
amount, departure, faint, or KO link. The original color Article is cited; PNGs
and numeric samples retain their own capture clocks and availability. Repeated
frames cannot extend the fixed window or bypass its cooldown. Existing source,
dimension, lifecycle and appearance-boundary checks remain in force.

The saved Vaporeon bar, using its recorded publication delay as an approximate
delivery schedule, opens a window covering the reviewed departure. This is a
scheduling regression, not proof of live frame delivery. Extracted pictures from
that video and a separately recorded living Swampert switch exercise the actual
numeric particle reader in a bounded buffer. VLC's extracted pictures were
sampled with simulated test clocks; they are not inserted into either archive
and do not establish their original frame timestamps. On these sampled pictures,
the existing collapse-sequence reader produced one Vaporeon visual candidate and
none for the living-switch excerpt. This is encouraging but does not establish
live performance or validate candidate-to-faint attribution across other effects.
Further tests cover
nonlethal or unknown color, malformed geometry, wrong source, switch boundaries,
charge windows, unavailable frames and original evidence references.

Neither capturing a visual sequence nor a visual candidate alone proves faint
or assigns Incinerate. Distinguishing a true departure from a living recall and
combining an independently supported faint with the hit remain separate work.
The existing video-reviewed correction remains unchanged. A real-device match
is required to measure actual delivery, frame continuity, processing cost and
whether the window captures the critical sequence.

Derived images and test logs are under the 1 TB drive's
`2026-11-15-moondrop-1212-review/departure-study` directory; the source recordings
and match archives were not modified.

The full debug unit suite passed with 3,117 tests and 11 skipped; the debug APK
also built successfully. The later video-candidate assertion passed separately.
The APK was installed and launched on Moondrop `MDPH00124112500414`; the reviewed
on-device archive retained its original SHA-256 and all four trainer corrections
remained present. A live capture, visual Replay and device-cost trial is pending.
