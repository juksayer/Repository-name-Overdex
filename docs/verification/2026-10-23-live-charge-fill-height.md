# Experimental live charge-button fill height

## Behavior

The existing controls-crop consumer now shares its resolved bitmap with a separate
`PLAYER_CHARGE_BUTTON_FILL_HEIGHT_WITNESS` measurement path, at most twice per
second. No second OCR reader or per-sample PNG encoding is introduced. Its raw
Articles are separate from the existing fill-step witness and do not generate Fast
Move uses, modify energy, infer a charge, or select a fill layer.

The reader searches for a bright circular rim separately in each half of the
calibrated controls territory. It checks a narrow horizontal separator on both
sides of the central icon. Captured fields include one-based button slot, crop
origin/dimensions, source dimensions, circle center/radius, separator Y, normalized
fill HEIGHT, and pixel tolerance. Coordinates are in captured crop pixels.

Unavailable rim/separator means null/unknown, not zero. A found separator remains
`EXPERIMENTAL_SEPARATOR`, with no invented confidence probability. The initial
six-pixel tolerance (scaled for larger crops) allows for rim/center error as well
as separator thickness; it is not a calibrated statistical uncertainty bound.
These records survive archive serialization and are readable in the Article viewer.

## Comparison

`tools/compare_charge_fill.py` reads a checkpoint window from an archive and takes
verified left/right costs and a counted-energy checkpoint explicitly. It enumerates
all possible completed layers up to the energy cap, intersects the two buttons,
and reports compatible, disagreement, or unknown. The expected count does not
select a layer or alter candidates. It never writes the source archive.

Example (use actual monotonic timestamps from the new archive):

```sh
python3 tools/compare_charge_fill.py MATCH.odxmatch.zip \
  --costs 35 55 --counted-energy 80 --start-nanos START --end-nanos END
```

This is an audit tool, not automatic live reconciliation. Costs/button order and
an energy-stable checkpoint must be verified before comparison. Counted energy is
itself derived evidence, not ground truth. Existing archives have no new live
height Articles; their source frames can still be examined offline.

## Verification

- 1,067 Android unit tests passed, no failures/errors/skips; debug APK built.
- 16 Python charge-analysis tests passed, including actual ZIP/JSON CLI input.
- Archive round-trip test preserves unknowns, geometry, and capture timing.
- Synthetic image tests cover central-icon interference, multiple/absent lines,
  bright disks, translated circular rims, and retained layer ambiguity.
- A temporary JVM diagnostic read five saved 816x1808 frames from
  `99fe41cb-a61f-40bd-994a-e6d7a6301a59` with the default calibrated territory.
  Four of ten button readings recovered separators (two paired frames); the
  remaining six were unknown. No hand-entered centers were supplied. The saved
  diagnostic is `/tmp/odx-fill-recorded-check.xml`, not a portable regression fixture.
- Desktop sample processing ranged roughly 6–28 ms per button. This is not a
  phone performance result.

## Remaining acceptance boundary

Record a live match with known moves and deliberate pauses to verify crop placement,
rim acquisition, tolerance, live cost, and agreement with counted energy. Taps,
animation, faded buttons, differing device geometry, and partial crops can still
cause unknowns or misleading experimental lines. Do not feed these readings into
HUD energy corrections until those cases have been evaluated. Future sampling can
track an established rim to reduce search work once the live evidence supports it.
