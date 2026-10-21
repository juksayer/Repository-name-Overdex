# Resource strips from the October 19–20 Moondrop matches

These 23 PNGs are unchanged, preserved crop artifacts from three match archives.
`manifest.json` records the source archive hash, Match ID, CropCaptured article ID,
crop hash, captured HUD bounds, original reported count, and reviewed expected
count. Coordinates are local to the crop, not the physical display.

The `*-covered.png` files show Overdex sprite cards/question marks. Previously
their pale backgrounds and dark shapes passed as spent Poké Balls, including a
false zero that fainted Sealeo. They are unavailable evidence (`null`), not zero
survivors. Some covered strips previously produced a positive count, which is
equally unsupported.

The `*-partial.png` strips retain the real upper badge glyphs while the lower
part overlaps the HUD. Their expected counts were checked against visible red
or dark Poké Ball caps and purple shield glyphs. Excluding HUD pixels must not
discard those readable caps. The `*-clear.png` strips preserve existing behavior,
including actually spent shields.

`OpponentBattleResourceCounterTest` exercises the production counter with each
frame's recorded bounds. A separate synthetic regression rejects a HUD covering
a resource's upper cap even when its lower dark rim remains visible.

Original archives are retained outside Git in
`/home/sean/overdex-device-backups/2026-10-20-match-review/`.
No archived testimony or calibration coordinates were changed.
