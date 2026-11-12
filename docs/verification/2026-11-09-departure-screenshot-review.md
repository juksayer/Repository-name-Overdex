# Send-out and departure screenshot review — November 9, 2026

Read-only visual review of the user's 13 desktop screenshots, supporting the
[opponent hand / Poké Ball sampling experiment](../plans/2026-11-09-opponent-hand-box.md).
No classifier, recognition threshold, event inference, or result-music gate was
implemented or validated by this review. No build or device operation was run.

## Source and geometry

The originals remain unchanged in `/home/sean/Pictures/`, named
`Screenshot from 2026-11-09 <time>.png`, for these times:
`16-18-37`, `16-19-00`, `16-21-27`, `16-21-48`, `16-22-05`, `16-22-18`,
`16-23-37`, `16-24-35`, `16-25-05`, `16-25-12`, `16-26-52`, `16-27-06`,
and `16-27-16`. Each original is **3360 × 1080**, including desktop and player
chrome. The portrait playback image occupies approximately X 2163–2613 and
Y 47–1046; this inspection rectangle is not phone calibration.

The original recording was located at
`/home/sean/Desktop/ overdex related/Screen_recording_20261102_112438.mp4`
(the directory name begins with a space). Its MP4 video track header reports
**816 × 1808**. This was read from the container metadata without decoding or
transcoding the recording. Do not treat the whole desktop screenshot as a phone
frame, or silently assume a native 1080 × 2400 source. Any extracted reference
must retain its actual source dimensions and account for aspect mapping.

## Observations and limits

| Screenshot times | Visible evidence |
| --- | --- |
| 16-18-37 through 16-21-48 | `Go, Quagsire!`, a ball held near the opponent's hand, an extended open hand with a ball in flight, then a ball near the ground. These support entrance context. |
| 16-22-05 and 16-22-18 | Quagsire materializes. The player's badge has changed from Gastrodon to Skarmory, and a large foreground ball belongs to the concurrent player transition. This is not an isolated opponent-only example. |
| 16-23-37 through 16-25-12 | Brave Bird impact, a smaller Quagsire amid sparkles and Shadow aura, then no visible Quagsire and a starburst beneath an empty HP bar. The opponent badge already shows two dim balls in 16-24-35 and 16-25-05 while Quagsire remains visible and its animated HP bar retains a small yellow remnant; 16-23-37 shows one dim ball. |
| 16-27-06 | A red/white ball is clear in the opponent's hand while Obstagoon remains visible with substantial yellow HP. Ball presence alone does not distinguish recall or faint. |
| 16-27-16 | Player Skarmory's Shadow aura covers much of the opponent's field. Occlusion can originate from either side. |

The hand is too small or ambiguous in the Quagsire disappearance examples to
prove a closed-to-open sequence. Shrinking, disappearance, hand pose, ball
presence, HP animation, and resource-badge loss remain independent observations.
The screenshots do not establish their precise relative onset times or prove
that any one cue reliably identifies a faint, voluntary switch, or recall.

Filename times are desktop capture times, not source video times. The player's
current position is rounded, and hover-seek tooltips sometimes show a different
position. The collected pictures therefore provide visual examples, not a
contiguous frame sequence or timing/frequency measurement.

## Private inspection artifacts

Derived images were created only for inspection under
`/home/sean/overdex-device-backups/2026-11-09-calibration-preview/`:

- `portrait-contact-sheet.jpg`: all 13 isolated playback images.
- `avatar-contact-sheet.png`: enlarged avatar examples.
- `recall-hand-details.png`: enlarged hand details for 16-24-35, 16-25-05,
  16-25-12, and 16-27-06.
- `<time>-portrait.png`: each individual inspection crop.

These derived artifacts are outside the repository. Source screenshots and the
recording were preserved; no full-video processing was performed.
