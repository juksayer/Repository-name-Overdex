# Fast Move audio listening homework — October 29, 2026

## Delivered review

A standalone, 1.3 MB HTML sheet contains six curated comparisons from three
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

The user first hears the unmodified recorded window and stable lettered
references. Candidate letters are shuffled independently of score rank. Names,
scores, species, nearby HP changes, and test context remain hidden until explicitly
revealed. Answers allow a reference, mixed audio, another sound, no audible Fast
Move, or uncertainty. Confidence, notes, review time, and reveal history are
separate fields. Revealing before or after a blind answer is auditable.

Native audio controls offer full clips, single-player playback, loop, and
1×/0.75×/0.5× speed. Saved alignment buttons play the changing-spectrum window;
the original-spectrum search may have chosen a different segment. Sound-file
length is not a measurement of the move's turn duration. Audio is not normalized
or re-encoded.

Drafts persist per review set. Portable JSON exports contain source Match,
archive hash, Article, WAV hash, reference hashes, letter assignments, playback
windows, and reviews. Import validates the entire file before merging anything.
Mismatched sets, sources, hashes, duplicate/unknown IDs, or invalid answers are
rejected. Importing an older draft preserves existing reveal history. QA used a
separate `-qa` review set; the delivered sheet was left at **0 of 6 reviewed**.

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

`tools/build_fast_move_audio_review.py` and its HTML template use only the Python
standard library. Example:

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

- Nine Python tests passed, including source-clock alignment, corrupted/missing
  media, hash/header/path checks, reference choice, HP geometry joins, stable set
  identity, context changes, and script-closing text.
- Browser checks passed for WAV playback, single-player arbitration, loop/speed,
  answer selection, reload restoration, actual JSON download, valid import,
  rejection of foreign and partly invalid imports, preserved reveal history,
  six-decimal close scores, and rendered HP/test context.
- The default narrow browser pane displayed the responsive controls without
  requiring a viewport override. The final screenshot is retained locally.

The related Android timing fix is separately documented in
[HP source-time cadence](2026-10-29-hp-source-time-cadence.md). No audio thresholds
were raised in response to these unreviewed examples.
