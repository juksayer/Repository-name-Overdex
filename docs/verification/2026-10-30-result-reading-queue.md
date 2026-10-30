# Result reading queue — October 30, 2026

## Recorded failure

The Rocket match `9425d5d1-ced0-4c77-be94-b51cd4265956` captured a decisive
YOU WIN crop at source +211.287 seconds and published that crop at +211.543.
Its OCR reading was not published until +279.455, after the user stopped the
session at +279.270. The typed MatchEnded followed at +279.463.

The source crop was already present. The outcome reader used an unbounded deque,
alternating newest and oldest. The winning frame stayed between those choices:

| OCR admission | Selected source frame | Observation |
| --- | --- | --- |
| +210.206 s | +209.636 s | Blank; 19 pending |
| +211.643 s | +199.953 s | Old blank; 22 pending |
| +212.988 s | +212.588 s | New blank; 24 pending |
| +213.739 s | +200.452 s | Old irrelevant frame |
| +214.634 s | +214.008 s | New irrelevant text |

At final selection, the win crop was 67.429 seconds old and 83 other crops were
pending. That is not one 67-second shared OCR wait: the final request waited
1.280 seconds for admission; native OCR took 0.619 seconds. About 66.149 seconds
elapsed from capture before the final request was queued. Across 96 outcome
diagnostics, median shared-reader wait was 619.5 ms and maximum was 4,178 ms;
median crop age at admission was 6,747 ms. Queue selection was the main issue.

The compact recording retains only 14 outcome crop Articles, alongside all 96
latency diagnostics. It does not contain the source pixels/arrival time of every
blank crop. Tests can reproduce this scheduling failure shape, but cannot claim
an exact replay of every arrival or measured native OCR performance.

## Change

The capture gate, four-per-second sampling, phrase resolver, and result authority
remain the same. Outcome OCR was already urgent; no new global priority was
needed.

`OutcomeEvidenceQueue` keeps at most 12 recent artifact references spanning up to
three seconds. It reads them oldest-source-first, so a newer summary or blank
cannot jump over a brief result. Delayed, expired and overflow references move
to source-ordered recovery. Future-dated references do not receive live priority.
These limits bound recent work, not OCR completion time.

The text witness has one urgent recent worker and one normal-priority recovery
worker. Each has at most one backend request outstanding. Both select the actual
crop after the shared OCR scheduler admits their turn; recent age is rechecked
then. Recovery cannot hold a fresh result behind an entire old backlog. The
shared scheduler still serializes native reads and provides normal-lane turns
under sustained urgent work.

The workers queue only immutable crop references. No source crop Article is
deleted or edited. Raw and treated readings retain their crop citation,
perceivedAt and monotonic source time. On the first decisive phrase, the witness
closes its queue and cancels both workers and its crop collector. Typed result
witnesses independently consume the recorded text. Stop cancels queued work;
native bitmap lifetime remains protected until any active read completes.

Diagnostics now include RECENT/RECOVERY, priority and separate pending counts.
`queueMs` remains this backend request's wait; `captureToAdmissionMs` is the
selected crop's age. Since selection happens after admission, those values refer
to different intervals and should not generally be subtracted to infer backlog.

## Validation

- Focused result queue, witness, capture gate, phrase resolver, OCR scheduler and
  result-music corroboration checks passed: 42 tests, zero failures/errors/skips.
- Twelve queue tests cover the 83-reference backlog, a brief result followed by
  summary, four-per-second arrivals with slower reads, bounded recent work,
  exact-once recovery, source order/expiry, future times, close and invalid limits.
- Five reader integration tests use controlled admission and a real shared OCR
  scheduler with fake text recognition. They verify urgent WIN ahead of recovery,
  source provenance through raw text/typed phrase/MatchEnded, selection after
  admission, failed-read survival, late-native cancellation and complete shutdown
  after a decisive result. They do not measure ML Kit or live phone performance.
- Full `:app:testDebugUnitTest` passed: 1,516 tests, zero failures/errors/skips.
  `:app:assembleDebug` passed. Read-only review found no blocking scheduling or
  cancellation issue.
- Installed with data preserved on idle Moondrop `MDPH00124112500414`
  (MD-PH-001). No Droidball service was active before replacement. Installed
  and built APK SHA-256 match:
  `f1fd5e6eb3a70de969bb7c198b140888fc8a94eeb39154934a6646e3568a47d3`.

Live acceptance remains pending. On the Moondrop, start Droidball, enter Team
Select and complete a normal battle. Let YOU WIN or GOOD EFFORT appear before
stopping. Compare source capture, crop publication, admission, OCR publication
and MatchEnded times in the new archive. The expected result is prompt selection
of the recent result without traversing an old backlog. A result appearing only
after Stop, or a recent result aging into recovery under load, still needs review.

The three-second window is not a completion guarantee: sustained shared-reader
overload can expire a result into recovery. The new lane metrics make that
remaining limitation observable.

Private source recording:
`/home/sean/overdex-device-backups/2026-10-30-new-match-and-homework/2026-10-30_10-59-27_9425d5d1-ced0-4c77-be94-b51cd4265956.odxmatch.zip`.
Installation metadata:
`/home/sean/overdex-device-backups/2026-10-30-result-reading-queue/verification-and-install.json`.

## Subsequent Moondrop recordings

Two normal battle recordings made with the queue change contain decisive results
from the RECENT urgent lane. Their raw source timing and result publication are:

| Match | Result source | Raw OCR delay | Typed MatchEnded delay | Pending at read |
| --- | --- | --- | --- | --- |
| `d6b41423-8147-4ad3-bcbf-79b8015e8d7a` | +163.031 s, GOOD EFFORT | about 2.95 s | 2.989 s, LOSS | 13 |
| `b252d3b6-fc02-4a48-8ae3-f138bbdce188` | +209.175 s, YOU WIN | 2.540 s | 2.603 s, WIN | 16 |

The first final request waited 487 ms for shared OCR; its selected crop was
2,380 ms old at admission, and native OCR took 473 ms. The second waited 242 ms;
its crop was 2,138 ms old, with 337 ms of native OCR. Neither result traversed the
remaining recovery backlog before publication. Both archives also contain
RECOVERY reads, showing the background lane made progress.

This is positive real-device evidence for result attention under these two
matches' load. It is not a universal latency guarantee or proof of complete Fast
Move counts. Both recordings captured GO but none of 3, 2 or 1; the reported
lingering setup menu needs a separate presentation correction.

Desktop copies and all 884 declared artifacts were verified without modifying
the phone recordings. Evidence and review details are under
`/home/sean/overdex-device-backups/2026-10-30-party-menu-and-calibration/`.
