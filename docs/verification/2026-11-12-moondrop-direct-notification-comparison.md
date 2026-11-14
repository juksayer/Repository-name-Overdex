# Moondrop direct-notification trial comparison

Both source archives and diagnostic JSON reports are preserved on the 1 TB drive
in `2026-11-12-moondrop-1637` and `2026-11-12-moondrop-1720` beneath
`/mnt/overdex-backup/archives/overdex-device-backups/`. The newer folder also
contains `stage-comparison.csv` with per-payload calls, elapsed and CPU times.

Latest Match: cc4826df-3dd3-446d-a8d0-e44512fe830f, archived LOSS. Its 3895 articles
and all 465 media artifacts verified successfully. Prior Match was a WIN.
These are different battles, not controlled repetitions. Source Match Start to
Match End spans were approximately 80.27 s before and 160.98 s after; diagnostic
counters cover the whole observation session, not only those battle intervals.

| Measurement | Before | Direct notification build |
| --- | ---: | ---: |
| Accepted/completed testimony | 2484/2484 | 5105/5105 |
| Temporary article-first subscriptions | 210 | 0 |
| Deliveries to those temporary subscriptions | 331546 | 0 |
| All measured article deliveries | 584015 | 479300 |
| Peak simultaneous article subscriptions | 93 | 88 |
| Raw broadcast elapsed total | 28.664 s | 56.555 s |
| Raw broadcast CPU total | 6.568 s | 12.734 s |
| Raw broadcast elapsed per testimony | 11.540 ms | 11.078 ms |
| Direct receipt notification elapsed total | absent | 1.398 s |
| Direct receipt elapsed per testimony | absent | 0.274 ms |
| Peak tracked pending HP observations | 35 | 85 |
| Maximum tracked oldest HP age | 3.969 s | 7.853 s |

Broadcast alone is about 4% lower per testimony, but including the new separately
measured receipt notification gives 11.352 ms versus 11.540 ms, about 1.6% lower.
This is not persuasive evidence of a meaningful latency improvement across two
different matches. Broadcast cost within common payload classes also remains
similar: crop 11.600 -> 11.181 ms, HP 11.744 -> 11.641 ms, raw text 11.165 ->
10.544 ms per call. Total listener deliveries fell despite twice the accepted
observations, confirming removal of the repeated history scans.

Other after-build totals: cadence 28.833 s (5.648 ms/testimony), Fast Move 20.861 s
(4.086 ms), energy 20.178 s (3.953 ms), charge announcement 14.136 s (2.769 ms),
faint reconciliation 11.064 s (2.167 ms). All testimony completed; larger HP
backlog shows completion alone is not adequate live responsiveness.

Conclusion: the unnecessary replay-delivery mechanism is fixed on the phone,
but the main broadcast bottleneck remains. Most long-lived listeners still
receive almost all Articles and the simultaneous count fell by only five.
Sampled callback elapsed times include waiting; they cannot identify publisher
lock time or CPU cost. Broadcast elapsed minus CPU is not proof of any particular
lock, scheduler, GC or OCR explanation.

Recommended next isolated experiment: route the already identified pre-battle
content readers through their relevant inputs while preserving phase-controller
signals and capture-time eligibility of late evidence. Measure simultaneous
subscriptions and broadcast latency afterward. Avoid broad routing changes or
claiming a solved bottleneck from this trial. Replay default-start navigation
was not visually validated by these processing reports.
