# Adjusting calibration from the Directory LCD

The Directory LCD normally displays retained battle crops, with their capture time.
Press Down after the last visible directory entry to select the first crop, or tap
any crop directly. Up/Left select the previous crop; Down/Right select the next.
Up from the first crop, or B while selecting, returns to the directory.

Press A to edit the selected crop's position. The LCD shows its source-pixel X/Y,
unchanged width/height, full source dimensions, an enlarged crop, and a small full
image with the crop outlined. Drag either image, or press an arrow to move one
source pixel. A (or the LCD's Save control) saves to the active Match Calibration
profile. B/Cancel discards the draft. A second B returns to the directory. Size
changes still belong in Match Calibration.

Editing uses a complete saved battle image, identified as **Saved battle frame**
or **Saved battle reference**. It does not pan a historical cropped PNG or invent
pixels outside it. Entering the LCD loads the source on demand and freezes it for
the edit. When focus returns to the directory, the LCD returns to its unchanged
historical crops; those may still show an older position until a new battle is
recorded. Saved calibration affects the next Droidball observation session.

Available fields are player/opponent species names, HP bars, Poké Balls, and the
announcement. Existing profile selection and every other box remain unchanged.
If another editor changes the selected profile or crop while a draft is open,
saving is refused; cancel the draft to reload calibration before retrying.

## Charged-move progression text

Match Calibration now includes **QTE Progress Text (Nice / Great / Excellent)**.
It is independent of Announcement, Charge-Move Execution and QTE Type Icons.
The default contains the observed progression line in the retained NICE and
EXCELLENT frames; adjust it for each device. Older profiles retain all their
saved coordinates and default only this new field.

A dedicated `QteProgressTextCrop` capture preserves this region every 500 ms during
an active battle. This slice supplies the box and retained pixels for review;
it does not yet add progression-grade OCR testimony or change replay grading.

## New reference screenshots

Use the existing [screenshot library instructions](../verification/2026-10-30-calibration-screenshot-library.md#preparing-the-new-library).
For each screen size, include separate full-screen NICE, GREAT, and EXCELLENT
references, plus normal battle layouts with one and two charged moves. Include
Team Select (league, restrictions and CP limit), VS, countdown/GO, shield choice,
a faint/replacement timer, and both result screens. Keep original resolution and
system bars. Turn off Overdex overlays and pointer-location/tap indicators for
these reference images. Put each device's images in its own model folder; Match
Calibration's **This screen** filter and device/shape choices already support
selecting the relevant references.
