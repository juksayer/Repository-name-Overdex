# Calibration scorecards

**Status: proposal, not implemented.** Audited October 31, 2026. This document describes a bounded first slice and its evidence requirements; it does not claim that saved profiles currently receive scores or that existing recordings establish calibration accuracy.

## Purpose and boundary

Help a trainer compare saved calibration revisions and identify boxes that need adjustment. Show **field health from recorded use** separately from **accuracy on labeled reference frames**. A reader can be available without its target appearing, and it can recognize the wrong text confidently. Neither operation time nor a high similarity value proves accuracy.

Audio can corroborate battle interpretation, but it has no spatial crop geometry. Audio matching quality belongs in an audio report, not a saved calibration score.

## What the current code preserves

- `MatchCalibrationProfileStore` saves named device-described Battle and Team Select regions. Saving the same name/device/size reuses the profile ID, and adjusting a region updates that same file. An ID is not an immutable revision.
- `CropCaptured` retains source dimensions, resolved crop bounds and sometimes capture-time HUD overlap. It does not identify the complete saved profile or revision used. Runtime badge/name locators can recover an effective crop different from the saved rectangle.
- `WitnessOperating` preserves availability transitions. `SourceInputRecord` exists in Custody but is not projected into the archived Reality Timeline. Operation/input availability is not evidence that a target was visible.
- Production `LiveOverlaySpeciesPipeline` records completed OCR-attempt latency, passes, superseded pending crops and the source crop ID. It retains deduplicated raw OCR, including empty readings at a measured position, and word geometry/filter decisions where available. The result reader also records completed-attempt latency and nonblank status. These diagnostics are currently textual, not a uniform typed attempt contract.
- Some other witnesses publish only accepted detections. Countdown, for example, does not preserve every rejected template score. A valid OCR GO result has template similarity zero because no template score established it.

Consequently, counting Articles or dividing accepted detections by captured PNGs is not a valid denominator. Conflation, repeated-frame suppression, alternative OCR treatments, phase gating and sparse media retention all change those counts. Existing archives without profile attribution remain **unattributed**; do not assign them to today's edited profile by name or resemblance.

## Immutable attribution before aggregation

At Match creation, preserve an immutable snapshot of the effective Battle and Team Select calibration configuration, with the saved profile ID/name if present, device model, physical display bounds, capture geometry, schema/contract version and a canonical content hash. Default/unnamed settings need the same attribution. The hash must describe the actual normalized regions, not JSON whitespace or a mutable filename.

If settings change during observation, append a new source-time revision boundary before using them. Earlier crops remain associated with the earlier revision. If only the profile label changes, keep the geometry revision comparable while preserving its historical label.

Keep **saved rectangle** and **effective located rectangle** distinct. Each scored attempt must cite its source crop/measurement, effective geometry and profile revision. Show how often automatic recovery moved a box and by how much; a successful recovered crop is not proof that the saved box was correct. Dynamic capture scaling and pixel rounding remain source provenance, not silent edits to the saved profile.

## Field health from recorded use

Begin with species-name and result-text readers, which already expose useful attempt timings. Add compact typed attempt/episode summaries rather than PNGs or one new Article per display frame. Count one admitted recognition attempt separately from the OCR passes/treatments it runs. Link its outcome to the attempt even when duplicate raw text is suppressed.

For each region and profile revision, report:

- **Coverage:** matches/episodes sampled, eligible requests, actual admitted attempts, completed attempts, skipped/superseded work, and unavailable/occluded/gated intervals with reasons. Attempt rates must name their denominator and time interval; whole-match elapsed time is not a target opportunity.
- **Reading health:** blank versus nonblank results; unresolved versus resolved results; exact vocabulary readings versus repaired/fuzzy readings; first-pass versus multiple-pass recognition. Show raw counts and missing telemetry. A repaired resolution may be correct, and an exact vocabulary word may still name the wrong target.
- **Crop fit:** text/target geometry touching crop edges, measured HUD obstruction, glyph height relative to the recognition input, and saved-to-effective crop displacement where measured. Do not invent contrast or clipping measurements from data that was not retained.
- **Timeliness:** source-to-admission, local/OCR queue wait, native recognition work, postprocessing and source-to-publication delay, with sample counts and median/tail summaries. Slow queue admission must not reduce a crop's visual-quality score; keep scheduling and recognition cost separate.

These are diagnostic health measures, not precision/recall or a probability of correctness. Do not average Article confidence, template similarity and OCR resolver confidence into one percentage. A witness's configured threshold/margin can be shown in its own units, alongside whether rejected alternatives were actually measured.

## Opportunities and labeled accuracy

Every evaluated field episode/frame needs an independent expectation and its basis:

| State | Treatment |
| --- | --- |
| Target visible and labeled in the full source frame | An evaluable positive opportunity. A misplaced crop can fail; do not require the target to fall inside that crop before admitting the opportunity. |
| Target explicitly labeled absent | A negative example for false-positive evaluation, not a failed detection. |
| Target expected from an independent battle cue but visibility unverified | An expected opportunity for diagnostics only; exclude from verified accuracy. An entry cue or open species-check window alone does not prove a readable badge existed. |
| Source missing/unavailable, occluded by presentation, or recognizer gated | Show the coverage/scheduling reason separately. Do not silently count it as a visual-recognition failure or hide it from the coverage report. |
| Presence or expected value unknown | Unscored. Display **Unknown / Not measured**, never zero. |

The first accuracy benchmark should reuse the trainer's new full-screen screenshot library, with explicit per-frame labels for target presence, expected text/glyph/count/type and target bounds where available. Keep source hashes, labels and benchmark version. Include negative examples and difficult frames, not only clear successful screenshots. Do not derive a label solely from the same witness being evaluated.

Run each candidate profile on the **same labeled frames** using the same production crop/recognizer path. Start with species text and result text. Report correct, wrong, unresolved and missed positive cases; false positives on labeled absent cases; exact/repaired reading counts; and native processing cost. Keep saved-only geometry and the normal locator-assisted path separate so recovery cannot conceal a bad saved rectangle.

Only compare profiles within the same device/layout, capture orientation/aspect, benchmark version and witness/reference/model version. A Moondrop score is not directly comparable with a Pixel or tablet score. Different match mixes and recognizer versions are not controlled profile comparisons. Deduplicate repeated source frames and group correlated frames into episodes so a long unchanged badge cannot dominate the result. Show the number of independent examples and the covered fields before naming a profile better.

## Smallest useful implementation and UI

1. Add immutable calibration attribution and source-time revision boundaries. Add typed, bounded species/result attempt summaries; retain existing raw observations and original timestamps.
2. Add a native **Saved profiles / Results** dialog reachable from Match Calibration's **SAVE AS** dialog, using the established physical controls/native-dialog pattern rather than placing interactive controls underneath GlassShield. List profile revision, device/layout, evaluated matches/episodes, fields measured and last evaluation. A profile with no attributed evidence says **Not measured**.
3. Open a profile's field-health report showing the preceding counts and separate timing/fit diagnostics. Keep unattributed legacy matches in a clearly separate report. Do not rank profiles by “accuracy” at this stage.
4. Add the labeled same-frame species/result benchmark and its results beside field health. A bounded score such as **18/20 labeled targets correct** can accompany wrong/unresolved/absent-case results and benchmark coverage. A whole-profile accuracy score remains unavailable until its required fields have representative labeled coverage. No automatic profile activation or replacement is part of this slice.

Later expansion can cover countdown, HP, type icons, resources and other crops after each reader has a clear attempt contract, visibility labels and an appropriate success definition. The first release of this feature should not promise to score every witness.

## Validation required before calling the slice complete

- Editing a region creates a different immutable geometry revision; old summaries retain their original attribution. Renaming, restoring profiles, default profiles, runtime recovery and mid-session changes are covered.
- Unknown, absent, gated and unavailable inputs cannot become missed positive detections. Wrong crop placement still fails a known-visible labeled target.
- Identical/repeated crops, duplicate publication, multiple OCR treatments and conflated/skipped frames cannot inflate opportunities or successes.
- Per-region health counts remain correct when raw text is suppressed; queue delay is separated from native read cost. Missing legacy telemetry stays unknown.
- A controlled benchmark evaluates competing profiles against identical labeled frames and keeps device/layout/benchmark/witness versions separate. A high-confidence wrong answer still counts wrong.
- Profile review is reachable through the actual ODX-Fi controls, and inspecting a result does not overwrite the active profile. A build or emulator check alone does not establish live recognition accuracy.

No app code, telemetry, score, UI or validation result is implemented by this proposal.
