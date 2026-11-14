# Pixel cry trial and reported Incinerate stutter — November 13, 2026

## Preserved inputs

Pixel 4 `99261FFAZ0017J`; four post-install archives copied read-only to
`/mnt/overdex-backup/archives/overdex-device-backups/2026-11-13-pixel-cry-stutter-review/`.
Device/copy SHA-256 checks and archive artifact verification passed. Source archives
remain untouched. The directory contains source hashes, processing diagnostics,
inspection reports, extracted timelines, and `summarize_review.py` for reproducible
event tables and processing-stage totals. No production code, build, or device
installation changed during this review.

Latest match: `2026-11-13_07-36-34_013d701e-714b-427e-a1d4-5d2b196f84f9.odxmatch.zip`.
It contains 2,599 articles and 253 verified artifacts. User reports Camerupt versus
Sealeo had audio and visual Incinerate stutter lasting roughly 4–5 seconds within
Replay 0:45–1:10. No continuous video for this match was supplied in this review;
the exact stutter onset and number of repeated effects remain unverified.

## Clock alignment and move evidence

Replay's full-recording counter includes 26.316630 seconds before Match Start.
All times in this table use that recording counter, not seconds since GO.

| Time | Archived player move label | Independent opponent HP loss |
| --- | --- | --- |
| 0:45.820 | Turtonator | 12.35 percentage points |
| 0:48.296 | Turtonator | 12.23 percentage points |
| 0:51.427 | Camerupt | 12.23 percentage points |
| 0:53.432 | Camerupt | 11.86 percentage points |
| 0:55.784 | Camerupt | 12.07 percentage points |
| 0:58.371 | Camerupt | 11.74 percentage points |

These six damage-supported uses have measured gaps of 2.48, 3.13, 2.01, 2.35,
and 2.59 seconds. The archive's Incinerate reference duration is 2.5 seconds.
There is no rapid explosion of canonical move uses in this exchange. This does
not establish that every actual use was counted correctly or identify the
reported 4–5-second effect burst. Another border-only player use occurs at
1:09.882, after the captured Vaporeon entry evidence; it is not part of the
six damage-supported hits against Sealeo.

Camerupt's entry was captured at 0:44.103 but published 9.28 seconds later.
The first two uses above still carry Turtonator in original testimony. This is
a confirmed live attribution problem; this review has not executed Replay's
later identity projection to determine whether its displayed actor is repaired.
Do not rewrite these original articles.

## Cry trial result

The four archives contain respectively 4/7/5/6 localized cries and 0/1/2/2
departure candidates in chronological order. The new reader is operating on the
Pixel, but timely live acceptance has not passed.

In the latest match, Sealeo's repeat cry at 1:05.387 produces a departure
candidate 16.734 seconds later. Its independently confirmed faint is already
published before that candidate. Camerupt's localized cry arrives 32.660 seconds
after its captured onset. Vaporeon's repeat-cry candidate arrives 15.531 seconds
late; there is no archived Vaporeon faint article. A candidate must not invent
that missing faint.

All 3,126 accepted testimonies completed according to the latest processing
summary. Across this entire recording, sampled stage totals include article
broadcast 22.45 seconds elapsed / 5.77 seconds thread CPU, cadence inference
17.82 / 4.36 seconds, and Fast Move inference 16.22 / 4.27 seconds. These are
aggregate stage measurements, not measurements of the reported stutter window
or proof that Overdex caused Pokémon GO's effects to repeat.

## Narrow next investigation

`LiveLocalizedCryWitness` measures its work interval across waveform matching,
artifact preservation, and `awaitRawArticlePublished`, then waits twice that
entire interval. Publication waiting can therefore increase subsequent deliberate
waiting. Newly known species also trigger a batch of older windows before the
worker returns to fresh audio. These are source-confirmed scheduling risks;
their individual contribution to this phone's delays has not been measured.

Reproduce a slow-publication case, measure recognition versus preservation versus
publication wait, and test that catch-up work cannot hold fresh windows behind an
expired batch. Choose a narrowly scoped pacing fix from that result. Keep source
audio, capture clocks, reference verification, independent faint requirements,
and raw testimony intact. Do not treat this review as a completed recognition
fix or a reason to raise move counts to match the repeated effects.
