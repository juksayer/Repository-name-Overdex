# Partial result-label recognition — November 11, 2026

## Problem and scope

The [read-only Moondrop review](2026-11-11-moondrop-team-count-review.md)
found three retained result screens with no derived MatchEnded. The two losses
read `OOD EFFOR` after GOOD EFFORT was clipped at the crop edges. The latest
win's entire translucent YOU WIN label was inside its crop, but OCR read
`oU WINy`. Both readings exceed the old string resolver's one-edit tolerance.

This change interprets the preserved OCR more flexibly using expected labels,
word size and source-frame position. It does not recapture/rewrite these old
matches, change calibration, add native OCR passes, or repair missing faint
events. The new logic is enabled for both registered result witnesses.

## Implemented behavior

- Strong partial titles can resolve from one source frame. All retained text
  must align uniquely with an expected label, with at least four matching WIN
  letters or five matching LOSS letters and at most one wrong/extra glyph.
- Word boxes must be result-sized and centrally located. Geometry is restored
  from original crop coordinates to the original source frame; it is not
  measured against the enlarged OCR input. Both one-line and two-line loss
  layouts are supported.
- Isolated W/WIN or FF/EFF signatures need their narrower layout positions and
  two different source crops at different times, 100 ms–3 s apart. RAW and
  contrast treatments of the same crop cannot corroborate each other.
- Small effectiveness lettering, unrelated words, mixed labels and missing or
  impossible geometry remain unresolved. This is an additional typed path;
  the existing strict string-only resolver is unchanged.
- The original raw OCR stays in Custody. A derived
  `MatchOutcomeLabelWitnessed` records the interpretation basis, original OCR,
  result and supporting-frame count, citing the original raw Articles. Their
  crop references remain reachable in compact archives. MatchEnded cites the
  accepted label and inherits its source time; a weak pair uses its confirming
  frame's time and retains both supporting observations.
- Strong recovery reads remain valid if newer unrelated OCR finishes first.
  A metadata cache miss retrieves the referenced crop from the immutable
  Timeline; it does not discard a delayed result or load its PNG again.
- Ordered observation restart/ignore/stop events clear pending weak support.
  Stopped/ignored attempts cannot accept a result until a newer restart;
  pre-restart crop times cannot affect the new attempt. Late older lifecycle
  events cannot pause a newer attempt.
- An accepted MatchEnded cancels the result OCR workers and collection. An
  unconfirmed weak fragment does not stop them.

Exact thresholds and the proposed avatar-motion witness are recorded in the
[event rules](../reference/battle-event-rules.md#partial-result-label-recognition--november-11)
and [rulebook](../reference/battle-rulebook.md).

## Validation evidence

The retained [OCR fixture](../../app/src/test/resources/battle/outcome/nov11-moondrop-result-ocr.json)
contains **22 real readings** from the three reviewed Moondrop archives:
five positive treatments of three result crops and 17 negative readings,
including effectiveness text, party selection, rewards, medals, potion and
catch screens. These are saved native OCR outputs and geometry, not a new run
of the native recognizer. Five treatments are not five independent matches.

Focused checks cover:

- All recorded positives and negatives, missing letters, one substituted/extra
  character, unrelated/mixed text, absent/small/impossible geometry, both loss
  layouts, source scaling and the real four-pixel OCR crop overrun.
- Synthetic isolated W/FF signatures and independent-frame confirmation,
  duplicate treatments/timestamps, ordering, expiry and conflicting labels.
- The real crop → custody → phrase → MatchEnded path for both partial labels,
  original text/reference/time preservation, worker shutdown, effectiveness
  rejection and late recovery after more than 256 newer crops.
- Real Match lifecycle tests for restart, ignore and stop, including old crops
  arriving or finishing recognition after a restart and late lifecycle events.
- Typed result source validation, invalid payload rejection, serialization and
  compact-archive retention of the complete supporting evidence chain.

Independent source review found and prompted fixes for lifecycle leakage,
strong partials being rejected after newer reads, and recovery crops outliving
the small metadata cache. The regression cases above cover each correction.

Final validation:

- Focused JVM run: **55 tests across 6 suites**, no failures, errors or skips.
- Full `:app:testDebugUnitTest :app:assembleDebug`: **2,384 tests across 313
  suites**, no failures, errors or skips; debug APK build successful.
- Independent implementation review: all three reported issues fixed; no
  remaining actionable finding in the final review.
- `git diff --check` passed. Local documentation links resolve, and all 15
  original trainer rules remain preserved in the rulebook.

Private build/log/XML evidence is saved under
`/home/sean/overdex-device-backups/2026-11-11-partial-result-labels/`.
The frozen `overdex-partial-result-labels-debug.apk` is 247,714,318 bytes,
SHA-256 `9f46ee4a92b86721135dfc640c49de694ef188f16b465cb8ddaa9404d883f4f2`.

## Moondrop installation — November 11

After the user requested installation for new matches, the frozen APK above was
installed on the Moondrop MD-PH-001, serial `MDPH00124112500414`, physical size
1080×2400. Read-only preflight confirmed no active MediaProjection or Droidball
service. The previous APK and 439 durable-data files were backed up before
replacement; every file in the 394,359,296-byte backup was independently hashed
against the stopped app. Loose source artifacts and generated caches were
excluded and remain on the phone.

The installed APK hash matches the tested artifact. All 439 backed-up files
were byte-identical immediately after replacement. After a successful cold
launch, all 414 checked calibration, reference, match/archive, registration,
replay and selected settings files still matched. The only changed pre-existing
files were Firestore local state and the profile-installation marker. The home
screen rendered, the app process remained alive, and no new crash-buffer entry
appeared. Droidball was left stopped for the trainer to launch normally.

Evidence is under the private build directory's `phone-update/` folder. The
Pixel was not connected and was not updated. No new battle has been validated
by these installation checks.

## Remaining acceptance boundary

Existing match archives and calibration records remain unchanged. The three
reviewed recordings predate the receipt-based resource wake-up fix; this new
Moondrop installation includes that fix as well as partial result recognition.

The classifier's recorded-input checks do not establish live recognition
accuracy, end-to-end phone latency, or portability to every display/layout.
Isolated W/FF cases are synthetic; the real corpus contains fuller partial
titles. New recorded battles should check false positives, result delivery,
source timestamps and successful archive completion on both devices.

Victory-jump and loss head-drop recognition is **not implemented**. The proposal
requires a calibrated trainer region, labeled temporal positive and negative
sequences, and bounded after-battle attention. A still pose alone is not a
motion witness. Result-label interpretation also does not determine whether
the ending was elimination, concession, timeout or a simultaneous-KO case.
