# Isolated HP crop capture throughput — November 15

## Scope and source

A test-only Android shell process on the Moondrop MD_PH_001
(`MDPH00124112500414`) copied the full calibrated player and opponent ordinary
HP search territories from recorded pixels into a bounded 16-item queue. It did
not start Droidball, replace the installed app, run Pokémon GO or execute the
real HP locator, event pipeline or archive writer.

The source was the trainer's recording
`Moondrop-2026-11-15_12-12-38-mtIRSv.mp4`, SHA-256
`9255d72b94851681276b047bb7f704ee90ca14aa3c8916e1e8e4efd122afd53f`.
Twenty-four frames sampled from ordinary footage and the reviewed Sealeo and
Vaporeon finishing sequences were decoded and resized from the recording to
672×1493 before the trial. Their video PTS values are retained in the manifest.
This is not an original-size or independently annotated full-speed sequence;
the synthetic feed cycles those stills at fixed rates. The original is unchanged.

The two rectangles are outward-rounded from the saved Moondrop calibration:
player `(35,366,207,654)` and opponent `(312,329,264,505)`, in x/y/width/height
at 672×1493. Each queue item carries sequence, a synthetic receipt clock, the
source fixture's original video PTS and every pixel in both rectangles. The
consumer compares both arrays exactly with precomputed source pixels, checks each
fixture's original video PTS and increasing synthetic sequence order, and counts
queue-full events. Cycling fixtures does not preserve real video-time ordering. This
preserves the copied territories only: pixels outside them, other witnesses,
overlay/occlusion metadata and charge-camera bars outside these rectangles
were not exercised. A complete rectangle does not prove a complete visible bar.

## Results

| Synthetic feed | Duration | Consumer | Offered | Verified | Queue-full | Mismatch/order error | Max queue | Mean / max copy |
| --- | ---: | --- | ---: | ---: | ---: | ---: | ---: | --- |
| 15 fps | 10 s | Array comparison | 150 | 150 | 0 | 0 | 1 | 2.671 / 9.677 ms |
| 20 fps | 10 s | Array comparison | 200 | 200 | 0 | 0 | 1 | 2.839 / 10.520 ms |
| 20 fps | 60 s | Array comparison | 1,200 | 1,200 | 0 | 0 | 1 | 2.737 / 14.586 ms |
| 20 fps | 6 s | Comparison plus simulated 150 ms delay per frame | 120 | 56 | 64 | 0 | 16 | 2.516 / 7.290 ms |

The 60-second run used 5.495 seconds of process CPU time and observed a
25.3→51.0 MiB Java heap range; native allocation was 93.3 MiB at start and end,
including 24 predecoded full-frame fixtures. Maximum measured source-schedule
lateness was 16.030 ms and maximum copy-to-verified completion was 18.836 ms.
Battery temperature was 33.2°C before the trials, 33.4°C at a later check and
33.6°C after the priority trial; the phone was USB-powered. These figures describe this isolated process and
short thermal interval, not complete battery, peak native-memory or live-game
impact. The overloaded delay is simulated, not a measurement of the locator.

The test proves that this crop-copy prototype kept its synthetic 20 fps feed
for one minute, and demonstrates explicit queue overflow under sustained slower
consumption. It does **not** prove that the real locator can process every crop,
that pixels outside the rectangles are recoverable, or that full live capture
will keep up alongside Pokémon GO and other readers. A finite queue cannot
absorb indefinitely slower recognition. Before any runtime change, benchmark
the actual locator and its whole-bar/tiny-HP behavior on timestamped recorded
sequences and a real concurrent phone workload; bound memory and measure spill,
latency, overload and gaps. Never silently claim a missing frame was processed.

The actual ordinary locator can also recover across the **full width** between
the vertical bounds of the two territories. Therefore the two-rectangle trial
does not retain its complete recovery search space. A second 60-second run
copied the outward-rounded full-width recovery band `(0,329,672,692)`: 1,200 of
1,200 items verified, no queue overflow, no pixel/PTS mismatch or reordering,
maximum queue depth one, and 2.913 ms mean / 14.006 ms maximum copy time. Process
CPU time was 7.821 seconds; sampled Java heap rose from 43.2 to 69.9 MiB, with
93.3 MiB native allocation at both endpoints. It retains more of the ordinary
locator's search space, but still does not preserve the rest of the source frame
or prove charged-camera coverage and live detector throughput.

For a separate isolated detector measurement, the currently installed app's
`ActiveHpBarMeasurer.measureAll` ran on each of the 24 full-width bands, after
four warm-ups, eight times per image. Decoding and pixel copying were outside
that timing. Across 192 calls, elapsed median was 29.95 ms, maximum 63.17 ms,
and 10 calls exceeded 50 ms. Thread-CPU median was 29.64 ms. The values are
per-call observations on these fixtures, not full-locator or live performance;
they do not assess correct HP classification or tiny-survivor safety.

Finally, a synthetic 30-second feed at 20 fps copied the full-width band into
the 16-item queue and ran the installed app's actual pixel detector on every
dequeued item. All 600 offered items were processed with no queue overflow,
pixel/PTS mismatch or change from the detector's precomputed output on the
same fixture. Mean detector time was 31.097 ms, maximum 75.158 ms, and 18
calls exceeded 50 ms; maximum copy-to-completion latency was 78.147 ms and
maximum queue depth was one. The process consumed 22.610 seconds of CPU time;
native allocation at the end was 93.3 MiB, including the 24 predecoded source
fixtures. A later battery check showed 33.8°C while USB-powered.

This tests the actual pixel detector but not the complete live locator,
assignment, downstream witnesses, archive path, capture source, competing
Pokémon GO workload, sustained device thermal behavior, or correct HP readings.
Its cyclic stills are not a full-speed source sequence. No runtime code was
changed. The high isolated CPU cost and the omitted work rule out concluding
that this path is ready for concurrent live use without further measurement.

## Trainer-proposed pressure control

A second isolated trial modeled one shared consumer with an optional 80 ms
sleep after each HP crop. The optional work stands in for competing work; it is
not an actual Overdex reader and sleeping does not simulate CPU contention.
Both cases offered 160 recording-derived crops over eight seconds at 20 fps.
The adaptive case paused optional work at a queue depth of eight and resumed it
at two. These values are test inputs, not production thresholds.

| Policy | Verified HP crops | Queue-full | Optional executions / suppressed | Maximum queue | Maximum receipt-to-verified latency |
| --- | ---: | ---: | --- | ---: | ---: |
| No throttling | 114 | 46 | 114 / 0 | 16 | 1,323.8 ms |
| Pause optional work under pressure | 160 | 0 | 100 / 60 | 9 | 428.5 ms |

Both cases reported zero pixel/PTS mismatches for verified crops. This confirms
the policy can help under the model's assumptions; it does not establish that
real Overdex readers share this queue or that reducing their rates will recover
enough CPU. Nor can optional-work throttling rescue a consumer whose own sustained
cost exceeds the arrival interval, as the separate 150 ms HP-consumer trial
demonstrates. A production experiment should measure actual pressure and resource
contention, defer only new optional work with bounded hysteresis, preserve already
accepted evidence, and keep essential HP, identity, charge, audio, terminal and
result evidence independently available.

The original recording and archives were not altered. Fixture manifest, source,
DEX and raw results are on the 1 TB drive under
`/mnt/overdex-backup/archives/overdex-device-backups/2026-11-15-hp-crop-throughput/`.
