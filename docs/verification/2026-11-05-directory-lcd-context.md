# Contextual directory LCD and explicit calibration — November 5, 2026

The user asked to reserve the directory's calibration crops until requested and
use its normal LCD to preview the selected folder or explain a selected workflow.

## Behavior

- The ordinary LCD shows the selected folder's actual children even when the
  folder is collapsed on the CRT. Dynamic custom binders and their exact names
  remain intact. A chain leading to one workflow shows its purpose instead of a
  redundant one-item list; empty folders are described honestly.
- Workflow previews give a short purpose and contextual controls checked against
  the actual screen handlers. The preview scrolls on the LCD and resets to its
  heading when the directory selection changes. It carries no battle crops or
  placeholder resource counts.
- A new top-level Calibration entry opens the existing crop inspection/editor
  on the directory LCD. Hold A on Launch Droidball is a second entry. It does
  not also perform the short Launch action. The ordinary entry remains usable
  without a long-press-capable controller.
- Normal Down at the final directory row stays in the tree. Previewing folders
  and workflows does not request the editor's full source frame. Crop feed
  collection and source loading occur only when calibration is requested.
- A edits/saves the selected crop; arrows select a crop or move a position draft.
  B first cancels a draft and then closes calibration, returning to the same
  directory selection. Up from the first crop also returns to the normal LCD.
- Unsaved edits and in-flight saves prevent tree selection or Start from hiding
  the draft. A save retains its immutable source/profile snapshot until it
  finishes. Closing calibration releases the optional full reference.
- Existing inline Add Pokémon instructions, keyboard and save notices retain
  priority. Ordinary interrupted-observation notices are retained; an explicit
  calibration visit may temporarily cover one, then restores it without
  acknowledging it. Starting a session retains its existing readiness/Stop flow.

No crop-coordinate defaults, saved profiles, recognition, archives or card data
were changed by this feature. The full Match Calibration screen remains available
through Intueor; the new directory utility is the existing position-only LCD editor.

## Verification

- Full JVM suite: 1,754 tests across 257 suites; zero failures, errors or skips.
- Debug APK and instrumentation APK assembly passed.
- Eight new model tests cover collapsed children, dynamic binder names,
  single-workflow and empty folders, missing selection, fallback text and actual
  control distinctions.
- API 34 emulator: 16 focused native checks passed across selection previews (3),
  LCD mode/navigation (3), existing inline owned-Pokémon editing (4), and the
  existing crop editor (6). The first LCD-mode run exposed a stale snapshot in
  the test harness: the actual tree and visible CRT had moved, but a parent
  `SideEffect` had not recopied the state. The corrected harness reads the live
  tree and still requires one callback per physical tap and the actual selected
  CRT semantics. All three mode tests passed on the final run.
- Moondrop, actual app: Down to collapsed Battle showed its children; selecting
  Match showed the replay-editor purpose. The new Calibration row opened crops.
  In a position draft, Right moved one unsaved pixel, Start kept the draft visible,
  and B cancelled without writing. A second B restored the selected utility's
  explanation. Hold A on Launch Droidball opened crops without opening readiness
  or starting capture; B restored the normal Launch explanation.
- The same APK was installed successfully on the Moondrop and Pixel 4. The Pixel
  was locked at the final screen check, so its new LCD presentation was not
  visually accepted. No phone instrumentation was run.
- Before/after checks on both phones found identical application database table
  contents and match-archive filename/size/mtime inventories. Protected calibration,
  current-team, registration, and replay files were unchanged (27 on Moondrop,
  6 on Pixel). No Droidball service or MediaProjection was active after checking.
- Host and both installed APKs have SHA-256
  `6d31ec8e0867a791e9df3d6dab6d44d96f3aab05b7a9700aed3d2e6552e78401`.

The feature changes directory presentation and entry controls. These checks do
not establish battle-recognition accuracy. Ordinary interrupted-observation
notice priority and existing registration ownership were also reviewed in the
production host wiring; no interrupted capture was fabricated on a phone.

Private backups and device evidence:
`/home/sean/overdex-device-backups/2026-11-05-directory-lcd-context/`.
