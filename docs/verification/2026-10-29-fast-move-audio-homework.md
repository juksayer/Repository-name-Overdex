# Fast Move audio listening homework — October 29, 2026

## Delivered review

A standalone, 2.3 MB HTML sheet contains six curated comparisons from three
unchanged desktop-backed-up Matches. There were 80 playable saved comparisons
in the source pool; none had missing audio media. The chosen set deliberately
contains close ties, disagreement between score families, a clearer comparison,
and a controlled-test case whose strong accepted audio candidate needs scrutiny.
It is not an accuracy or recall benchmark.

Review set: `5ebbf8e737c27daea997`.

- Browser: `http://127.0.0.1:8788/audio-review.html`
- Offline copy: `/home/sean/Desktop/Overdex Fast Move Audio Homework.html`
- Generated original: `/home/sean/.codex/visualizations/2026/09/13/01a09cc7-b682-79f0-ad52-76698424dade/fast-move-visual-review/audio-review.html`
- Private provenance, selected-case metadata, answers used for QA, and screenshot:
  `/home/sean/overdex-device-backups/2026-10-29-fast-move-audio-review/`.

Generated recordings and private answers are not committed to Git. The sheet
embeds WAVs and needs no server or network after opening. The local server also
continues serving the existing Incinerate and Fire Fang visual review sheets.

## Listening and answer handling

Each reference displays its move name immediately, and answer choices use move
names. Candidate letters remain only as stable reference headings, shuffled
independently of score rank. The candidates include the two closest original-
spectrum matches and the strongest changing-spectrum alternative (or a third
original candidate if that alternative is already included). Scores, species,
nearby HP changes, and test context remain hidden until explicitly revealed.
Answers also allow mixed audio, another sound, no audible Fast Move, or uncertainty.
Confidence, notes, review time, name-display history, and context-reveal history
are separate fields. Earlier blind judgments remain distinguishable from new
judgments made with names visible.

Native audio controls offer full clips, single-player playback, loop, and
1×/0.75×/0.5× speed. The simplified `Recording window` and `Reference window`
buttons play exact PCM excerpts at the saved changing-spectrum alignment.
Excerpt WAVs copy whole source frames without normalization, resampling, or
changing samples. Native controls now seek and loop only inside that excerpt;
the precise clock shows excerpt elapsed time. Source ranges and plot cursors
remain relative to the full original file. The original-spectrum search may
have chosen a different segment whose offset was not saved. Full-file length
is explicitly labeled and is not a measurement of the move's turn duration.

Every recording and reference has a waveform and spectrogram, with the selected
section highlighted in amber. Waveforms retain full-file peaks on a shared
digital full-scale axis. Spectrograms use equal stereo downmix, anti-aliased
8 kHz analysis, Hann FFT256 windows, and a shared -90 to 0 dBFS color scale over
0–4 kHz. Data is bounded to 320 waveform bins and 240 × 64 spectral cells, with
exact analysis window times and padding retained. There is no per-clip boosting.
These are listening aids rather than the matcher's exact features or move labels.
Only selected clips are analyzed, cached by source hash. Playback redraws cached
plots plus overlays; navigation cleans up canvases, observers, and excerpt URLs.

There is one battle recording per case. Several references use the same section
of it, so identical playback is expected. The UI marks exact shared intervals;
nearby intervals may also overlap heavily. All saved windows in this set are
416 ms. In case 3 all three use 0.672–1.088 s; case 2 shares 0.144–0.560 s, case 4
shares 0.656–1.072 s, and case 6 shares 0.688–1.104 s. File hashes and manifest
names were audited without finding swapped reference assignments. This checks
file attribution, not the original catalogue's naming accuracy.

Drafts persist per review set. Portable JSON exports contain source Match,
archive hash, Article, WAV hash, reference hashes, letter assignments, playback
windows, and reviews. Import validates the entire file before merging anything.
Mismatched sets, sources, hashes, duplicate/unknown IDs, or invalid answers are
rejected. Importing an older draft preserves existing reveal and name-display
history. Optional name-history fields default safely for old v1 exports. Derived
plots are excluded from review identity: the existing set ID, source hashes,
candidate assignments, and all existing user answers remain unchanged. User
answers were backed up before replacing the sheet. QA used a separate `-qa-v2`
set; the actual sheet retained the user's **1 of 6 reviewed**, drafts, and notes.

Human answers do not modify Timeline Articles or automatically train the matcher.

## Source cases

Times are the AudioCaptured window starts relative to the recorded Match Start.

| Case | Source | Time | Why included |
| --- | --- | --- | --- |
| 1 | Oct 29 Moondrop `b933d564` | +2.625 s | Ember 0.965326 vs Wing Attack 0.960998; changing-spectrum ranking reverses. |
| 2 | Same Match | +6.231 s | Ember 0.9668061 vs Wing Attack 0.9668029; approximately 0.0000032 apart. |
| 3 | Same Match | +19.840 s | Strong Wing Attack original score, but insufficient changing-spectrum separation. |
| 4 | Same Match | +13.226 s | Clearer accepted Wing Attack comparison; a control, not a human-certified label. |
| 5 | Oct 28 Pixel Candela `a66482db` | +75.280 s | Ember/Snarl disagreement; user confirmed Scrafty's Snarl and continuous attacks. |
| 6 | Oct 25 Moondrop controlled battle `moondrop.odxmatch.zip` | +204.837 s | Accepted Volt Switch candidate during the test where the user reported only Mawile attacking with Fire Fang. |

The user-reported patterns in cases 5 and 6 are identified as human context,
not source testimony or automatic clip labels. Both sides and other battle sounds
can contribute to a recording.

## Matcher audit

The bundled manifest has 72 files representing 71 named moves. The 354 ms Bite
reference is shorter than the current 416 ms minimum comparison window, leaving
70 named candidates eligible for this matcher. Bubble and Fire Fang have no named
reference in this catalogue. A missing reference must not force another move's
label onto an unfamiliar sound.

The matcher compares 416 ms spectral sequences inside 1.2-second windows, using
the original spectrum and a version with steady background components reduced.
Scores are similarities, not probabilities. Current strict acceptance requires
original similarity ≥ 0.93, original lead ≥ 0.008, changing similarity ≥ 0.80, and
changing lead over all alternatives ≥ 0.04. Independent best offsets are searched
for original and changing spectra; saved offsets describe the changing fit.

Plausible rejected comparisons retain the full ranking at a bounded rate. Strict
matches retain the union of the best original and changing candidates. Very weak,
skipped, and unretained windows are not represented, so this set cannot measure
overall false-positive rates or missed attacks.

## Reusable generator and validation

`tools/build_fast_move_audio_review.py`, `tools/audio_review_signal.py`, and the
inlined HTML/JavaScript templates need no third-party dependencies. Example:

```sh
python3 tools/build_fast_move_audio_review.py MATCH.odxmatch.zip \
  --output audio-review.html --limit 12
```

Repeat `--article-id SOURCE_ARTICLE_ID` to curate exact comparisons. WAV headers,
lengths, hashes, and repository-relative artifact paths are verified. Capture
times come from AudioCaptured, not late analysis delivery. HP geometry joins use
only a same-side prior source measurement within 500 ms. Review-set identity binds
the source, candidate metadata, scores, and displayed context. Embedded archive
strings are escaped and rendered through textContent.

- Eighteen Python tests passed, including source-clock alignment, corrupted/missing
  media, hash/header/path checks, reference choice, HP geometry joins, stable set
  identity, context changes, script-closing text, fixed amplitude/frequency scales,
  stereo cancellation, alias rejection, peak retention, bounds, and padding.
- Seventeen Node tests passed for exact excerpt samples and headers, stereo frame
  boundaries, nonzero source offsets, padded RIFF chunks, malformed inputs, range
  bounds, source immutability, inline safety, legacy blind/revealed answer migration,
  name-exposure history, and atomic provenance rejection. Generated scripts parse cleanly.
- Browser checks passed for WAV playback, single-player arbitration, loop/speed,
  answer selection, reload restoration, actual JSON download, valid import,
  rejection of foreign and partly invalid imports, preserved reveal history,
  six-decimal close scores, and rendered HP/test context.
- Updated browser checks passed for immediately visible move names, named answer
  choices, actual 416 ms excerpt duration, source-relative plot selection/cursor,
  full-source switching, loop/speed arbitration, and shared-window labels. Legacy
  imports preserved the prior reviewed timestamp and notes; a new named judgment
  exported `reviewedWithNames=true` without falsely marking scores revealed.
- The default narrow browser pane displayed the responsive controls without
  requiring a viewport override. The final screenshot is retained locally.

The related Android timing fix is separately documented in
[HP source-time cadence](2026-10-29-hp-source-time-cadence.md). No audio thresholds
were raised in response to these unreviewed examples.
