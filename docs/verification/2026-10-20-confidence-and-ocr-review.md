# Article confidence, OCR review, and future compilations

Date: 2026-10-20. Checkout: `/home/sean/AndroidStudioProjects/Overdex`.

## Implemented in this change

- Archive article rows put the source name and confidence on separate full-width lines. The old unweighted row let a long source ID squeeze the confidence into a narrow vertical strip.
- Missing article/recognition confidence reads **Not scored**. Article details explain that an absent score is neither zero nor a false observation; an existing score is not a calibrated probability.
- Species article details expose the recorded OCR text and catalogue-match basis when present.
- Event inspector sequence and time use separate lines.
- `tools/build_ocr_review.py` creates a standalone, offline-capable HTML review desk from existing archives. It follows evidence references to retained PNGs, verifies their SHA-256 and byte count, and preserves crop/frame coordinates. It never edits an archive.
- Reviewers can transcribe a crop, distinguish visible text from a contextual reconstruction, mark it unreadable or already correct, and add notes. Browser drafts, JSON export, and loading that export work. Labels remain separate from both the original timeline and recognition rules.

This does not change live recognition, infer missing confidence scores, train ML Kit, or implement the future clip editor.

## What the current confidence values mean

`RealityArticle.confidence` is nullable. The same nullability survives custody and archive mapping. Capture records, bookkeeping, and raw OCR often have no score.

In the Moondrop recording `c12270c6-6aa9-449d-8a4e-a0667fa52936`:

- 4,628 articles total; 2,262 have no confidence score.
- These include 559 raw-text records, 499 crop records, and 475 witness-operation records.
- A species catalogue match can score 0.98 from an exact text match plus expected capitalization. `SpeciesTextResolver` assigns 0.82 to an unambiguous single missing character and 0.76 to two. These are rules chosen in code, not measured correctness frequencies.
- Other witness scores measure different things, including image similarity, HP geometry, and evidence agreement. They are not directly interchangeable.

Do not fill absent scores with 1.0 or average unrelated witness scores into a purported probability. A species identity score also does not establish its side or the timing of its entry.

For quantified recognition reliability, retain the score method/version and relevant features, gather human-reviewed examples, and measure correctness on held-out matches. Split by match and device where possible so adjacent frames do not masquerade as independent confirmations. Report sample counts, false positives, abstentions, identity/side errors, and observed correctness by score range. Labelled corrections are the beginning of that dataset, not an accuracy claim.

ML Kit exposes word-level confidence, but our current raw-reading path does not retain it. Availability depends on the reader/Play services version; unavailable confidence must remain distinct from a real zero. See [ML Kit Text.Element](https://developers.google.com/android/reference/com/google/mlkit/vision/text/Text.Element).

## Review material and correction workflow

Generated desk:

`/home/sean/overdex-device-backups/2026-10-20-paired-review/ocr-review/index.html`

It embeds 48 unique announcement/species readings from the two perspectives of the 09:42 match:

- Moondrop: `c12270c6-6aa9-449d-8a4e-a0667fa52936`
- Pixel: `1ce39878-043f-4b4b-8db2-b860588ba9d9`

Review set: `75e163a69dfb9aed6ce3`. No selected reading lacked its source image. This is a curated review set, not a random accuracy sample.

Example: article `247a94b8-a4a8-4627-a1bd-e01466fd961d` says `Greninja used NIgn Slasn:`. The retained image is 572 × 56 pixels, with the top of the lettering clipped. It is still useful for comparing what a human can read with the OCR output. Its source hash is `8ada316ab228f7056f4e9353b90a93a53536d57904585cbb4d514225d0a1226d`.

The review desk records the original text, article and match IDs, image hash, crop coordinates, OCR treatment, reviewer transcription, notes, and whether the transcription depends on context. A downloaded corrections file can be loaded back into the same review set.

Next steps after user labels arrive:

1. Inspect recurrent errors and separate clipping/occlusion from character confusion.
2. Add representative positive and ambiguous/negative regression cases.
3. Improve catalogue-constrained matching or crop/preprocessing rules where justified. Avoid global substitutions that turn ambiguous fragments into forced names.
4. Compare the change against held-out recordings before deploying it.

Human review does not modify the original OCR article. If reviewed conclusions later feed replay, publish a separate reviewed conclusion with its evidence and revision. Context-only labels must not be used as visual ground truth.

Replay-only archives preserve the recorded text, timestamps, geometry, and available recognition metadata, but contain no original PNG/audio. Retain selected ambiguous samples or the PC backups if later pixel-level review is desired.

## Future clip library and compilation editor — planned

Existing `ReplayExcerpt` and `ReplayExcerptStore` save intervals with source match ID, timeline hash, monotonic times, article IDs, and an optional title. The current mark/save controls do not request a title. There is no clip library, reorder editor, compilation player, or event-search supercut workflow yet.

The intended user flow:

1. In Replay, mark a start and end at the playback cursor; name the saved clip.
2. Repeat in other matches.
3. Open the clip library, select clips, drag them into order, and save a **Compilation**.
4. Add notes anchored to clip-relative time or an original event.
5. Search recorded events by owned Pokémon identity, species/form, size, shadow status, move, knockout, and other supported facts. Preview results with configurable lead-in/out, then add selected results to a supercut.

Implementation boundaries:

- Compilations are a separate document type. They must not create a match, alter outcomes, or double-count battle statistics.
- Retain source match/article identity even when the compilation uses its own playback clock.
- Preserve enough replay data and the state before each clip to reconstruct species, HP, energy, shields, and status at its start. Simply slicing articles loses that initial state.
- The newest-50 match retention rule must not strand saved clips. Pin a referenced source or retain a small verified replay snapshot with the clip; include source media only when the clip actually requires it.
- Keep commentary and coaching annotations separate from raw observation. A disputed event can be annotated without rewriting the record.
- Show whether a search hit was directly observed, inferred, or reviewed. Do not silently turn inferred events into recorded facts.
- Build named clips and protected storage first, then ordering/playback, then event search and coaching annotations. Video rendering/export can follow separately.

## Validation

- Full Android unit suite: **951 tests, 0 failures, 0 errors, 0 skipped**.
- `:app:assembleDebug`: passed.
- APK SHA-256: `e69441ece65c4f59ba94a798c602df1d414983287722e68b178123b139b7f256`.
- Review desk: generated from real retained evidence; 48 source images passed byte-count/SHA and geometry checks.
- Browser checks passed for sample selection, full-crop display, editing, draft persistence after reload, JSON download, and reloading that downloaded file. The test transcription was removed from the review desk afterward; all samples are left unreviewed for the user.
- A labelled smoke-test export is kept outside Downloads at `ocr-review-smoke-export.json` beside the review directory; it explicitly says it is a UI test, not user testimony.
- No phone was connected during these checks. The new Android layout still needs installation and visual validation on the Moondrop.
