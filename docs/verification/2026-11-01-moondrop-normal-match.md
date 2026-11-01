# Moondrop normal-match review — November 1, 2026

## Source and boundaries

This was a normal battle. The planned controlled test matches remain pending;
recorded use counts are observations, not a verified manual attack count.

- Match: `00a41b5b-44a7-4e3d-8b3b-99197004d6f1`, recorded about 06:46 local time.
- Original compact: 8,192,662 bytes, 3,060 Articles, all 366 artifacts verified.
  SHA-256: `164393353174db153ce0352f6a4ec527d6baf01c3bd8ef76cffe5147835018f3`.
- Full export from the still-available live source: 192,871,936 bytes, 5,264
  Articles, all 2,226 artifacts verified. SHA-256:
  `353d535f6f3d9e844391952a87bcedc6f6dc4370d82ab796792294942bb0f725`.
- Both copies were checked against stable device hashes. Existing Articles and
  the original completed-match archive were not rewritten.
- Recorded build APK SHA-256:
  `f662b4f391e8bf8a1bed407a7b7c5f5aed7d057b8c51167c00adf236da19462e`.

Private evidence and standalone probes live under
`/home/sean/overdex-device-backups/2026-11-01-normal-match/`.

## What the match recorded

The configured and observed player team agrees: shadow Golbat with Wing Attack,
Greninja with Water Shuriken, and Victreebel with Razor Leaf. Greninja retains
Night Slash/Aerial Ace. Actual appearance order was Golbat, Victreebel, Greninja;
the opponent was Pancham, Blastoise, Steelix. Pancham's Low Kick was identified;
Blastoise and Steelix remained unresolved in the accepted live move identities.

The ledger contains 41 player and 12 opponent supported Fast Move uses. These
counts are incomplete evidence, not a conclusion about how many taps occurred.

Countdown 3 and 2 were witnessed. Match start at source +72.220 seconds came
from countdown-plus-combat inference, published 654 ms later; GO itself was not
observed. The WIN source at +220.937 seconds produced its result text after
3.456 seconds and MatchEnded 18 ms later. Faint/resource evidence identifies
Pancham, Victreebel, and Blastoise as the three observed faints. The result supplies
the ending; no separate Steelix faint was recorded.

## Corrections prompted by this match

### Complete short audio references

Bite's bundled PCM lasts about 354 ms and produces 21 spectral frames. The former
matcher required 25 frames for every reference, silently omitting Bite even when
Blastoise's requested legal pool included it. This prevented complete comparisons.

The matcher now uses the complete 21-frame span for such short references while
retaining 25 frames for longer references. It does not pad or repeat PCM. Query
guards, legal pools, confidence thresholds and completeness checks are unchanged.
New measurements identify the algorithm as `spectral-sequence-v2`. Inference
continues to accept supported v1 recordings, preserves null-version legacy onset
testimony, and rejects unknown spectral formats conservatively.

The actual JVM matcher evaluated 25 retained Blastoise windows. Bite became
available in each comparison and never won. Every previously scored move retained
exactly the same numerical score. Six windows met the existing strict Rollout
comparison against the now-complete opponent pool. Those are six comparison
windows, not six attacks or a rewritten live identity. A clean isolated Bite
reference can still fail strict separation; tests preserve that uncertainty.

### Reuse one species-text resolution per crop

The Blastoise source frame at +114.997 seconds was admitted after 272 ms. Native
OCR used 495 ms; raw `Blastolse` appeared after 3.808 seconds and formal Blastoise
after 7.577 seconds. The pipeline resolved the same fuzzy text in both its OCR
acceptance predicate and formal selection. Repeated misspellings showed similar
non-native cost; exact spellings were faster.

Those two consumers now reuse an exact-text result within the crop, including
rejected results. Each new crop receives a new cache. Typography confidence,
ambiguous rejection, form rules, raw testimony, and source timestamps stay intact.
This removes duplicate work; live latency improvement still needs a new recording.
The first fuzzy scan remains inside the shared reading turn.

### Select HP samples near measured HP frames

All seven LCD sample slots survived compact export. However, both HP samples
were at +212.437152 seconds, 564 ms after the last nearby HP measurement, during a
bar-free camera frame. Merely retaining those pixels did not validate the target.

HP selection now prefers raw images within 150 ms of an HP observation from either
side, with matching known source dimensions. Own-side measured geometry resolves
ties but is not required, so a wrong own-side crop can still be inspected when
the opposite side establishes visible battle UI. Start-only/legacy evidence with
no HP measurements retains its honest raw fallback. Other fields retain their
existing selection policy.

Exact shared-selector evaluation on the full recording chose:

| Side | Source time | Article | Original pixels |
| --- | ---: | --- | --- |
| Player | +210.403420 s | `f87db6d9-138d-4ea8-a727-34225167c4e3` | Green HP bar inside the recorded region |
| Opponent | +210.368654 s | `ff603224-4617-4c19-a827-f07c537beea5` | Red HP sliver inside the recorded region |

The other five selected IDs were unchanged. Full-to-compact-to-cold selection
equality passed. Only two of 157 raw HP captures need inspection retention. The
seven selected images total 1,108,827 bytes, 40,871 bytes more than the prior seven;
there is no additional capture or OCR work. Desktop metadata selection had a
16.9 ms median across 20 runs after warmup; this is not a phone benchmark.

The old compact alone contains only the two distant HP samples. It cannot supply
the earlier pixels; the verified full recording preserves them for review.

## Next acceptance issue: charged-move calibration and phase capture

No charge/QTE event was accepted, although the full frames visibly contain:

- +95.038 s: Pancham used Crunch and BLOCKED.
- +101.137 s: Golbat's bubble QTE; +103.173 s NICE; +105.215 s EXCELLENT.
- +107.223 s: player Poison Fang, defender shield and BLOCKED.
- +113.689 s: Go, Blastoise.

At 672 × 1493 source resolution, the Poison Fang text is around y334–359 while
the nearby announcement crop covers y467–539. Normal charged-move labels are
around y1425–1445, while the saved label crop covers y1191–1280. All 26 label
readings were blank. Fill tracking recorded 143 UNKNOWN_RIM, 32 UNKNOWN_SEPARATOR
and one EXPERIMENTAL_SEPARATOR. The execution crop contains NICE clearly but no
corresponding phase event was published. These regions and their gating need
checking with the saved frames before interpreting another controlled test.

The +212.437 bar-free frame is not sufficient to identify a charge phase: similar
hexagons appear during normal combat, and a final KO/end camera is also possible.
No calibration was adjusted during this review.

## Validation and installation

- Final full JVM suite: **1,592 tests across 236 suites**, zero failures, errors,
  or skips. The debug application and instrumentation APKs built successfully.
- Focused tests cover both matcher versions, unknown-format rejection, incomplete
  legal pools, the unchanged October 24 recorded Magcargo preference, short audio
  references, per-crop text reuse, HP frame proximity and compact/cold equivalence.
- Actual JVM probes evaluated all 25 retained Blastoise audio windows and all
  5,264 full-recording Articles. Raw full-frame inspection verified the selected
  HP bars and the missed charged-move text; no archived testimony was rewritten.
- Updated Moondrop `MDPH00124112500414` with Droidball stopped, then cold-launched
  the Directory Tree. The original compact contains only distant HP samples, so
  its HP slots correctly become unselected rather than displaying a known bar-free
  frame. The other five previews remain available. Future compact saves can retain
  the earlier near-frame HP samples demonstrated by the full-recording probe.
- All **17** checked calibration/team/layout files stayed byte-for-byte identical;
  all **four** application database tables retained the same rows and values.
- No new live battle was performed after installation. Species-latency improvement
  and complete live audio attribution remain acceptance checks for a later match.

Installed APK SHA-256:
`14a011f5d1e07934b1aa6c2ebbf8200ca957c4e38d0c573fac051bd87bf34a26`.
