# Recorded no-shields preparation cue

This is a minimal source-evidence fixture from the second match in the supplied
November 11 evening recording, Match `e6fa7745-8827-4fd0-a4ec-659d1bbf7ad7`.

- `articles.json` contains 36 original Article objects, selected with their full
  transitive evidence/predecessor closure. Each object is copied literally from
  the original archive JSON, preserving number representations, nulls, IDs,
  sequence numbers, payloads and all clocks. The array is a fixture, not a full
  archive.
- `no-shields.png` is the byte-identical retained `MatchOutcomeCrop`: 464 × 217
  pixels from source bounds `(100,641)..(564,858)` in a 672 × 1493 frame. It has
  not been resized, recolored or re-encoded.
- `provenance.json` records source and fixture hashes, named Article roles,
  per-object hashes and timing calculations. Other crops' metadata is included
  through the reference closure; their image files are not bundled here.

The result-probe crop at Match +141.205292316 seconds contains the exact native
text “No Protect Shields remaining!” at source word bounds approximately
`(168,696)..(504,725)`. Its RAW OCR Article preserves that full text and the
original word geometry; a separate threshold treatment contains different text
and remains unchanged. It was not a recognized win or loss.

The preceding player's ordinary HP bar was measured 357.628615 ms before this
image, at `(38,790)..(231,807)`, with fill `0.7195767`. A second measurement uses
the same captured frame as the prompt, at `(38,791)..(231,808)`. The archived
entry/identity chain establishes that this appearance belongs to Camerupt,
beginning at Match +115.182554776 seconds. Both geometry measurements satisfy
the existing five-second pre-cue reference-age bound.

The reader's recorded 3,366 ms total puts recognition completion at approximately
Match +144.571292316 seconds. This estimate comes from truncated millisecond
diagnostics; it is not an exact native callback clock. The final Mud Bomb
announcement image was captured at +142.727025316, and the last archived player
HP value is still nonzero at +143.986837855. Recognition completion therefore
follows the announcement by about 1.844 seconds. These are distinct clocks and
must not be shifted to make a test succeed.

The fixture has no final player zero HP or final faint. It supports testing a
bounded phase handoff and preserves the missing-event boundary. The earlier
Attack Incoming Article included by reference closure belongs to the preceding
phase at approximately +120.656 seconds. Likewise, historical configured
“Gourgeist (Average)” text is copied source material, not a new size assertion.
