# Match archive and replay LCD controls

The CRT retains GlassShield. Match controls use the existing custom LCD content surface; custom content owns its gestures so the shell does not swallow button taps.

- Archive viewer: match ID, article count, selected article, Mark Start, Mark End + Save, Open Replay, article details, Back, and excerpt status are on a scrollable LCD panel. The CRT retains the article list and selected article details. Up/down selects articles; left/right selects excerpt/replay actions; A activates; B returns.
- Timeline Viewer / Match Summary: Open Match Archive, side-by-side Save Compact / Save Full, export status, saving indicator, and Back are on the LCD. Save controls require an available recording and are disabled while an export is underway.
- Replay: the progress bar spans the available LCD width. Horizontal drag uses the measured width rather than a fixed 600-pixel assumption. Tap toggles playback. Vertical scrolling exposes controls if accessibility font sizing or a small LCD requires it. DRAG SCRUB is a single unwrapped label. Reset, Play/Pause and Back are tappable.
- Archived crop recovery reports reading, verifying/OCR, and species-resolution stages. Reading and verification show actual completed/total artifact counts; stages without a meaningful count use an indeterminate indicator. Crop verification failure is displayed rather than leaving an indefinite loading message.

Build: assembleDebug passed and the APK was installed on MDPH00124112500414. On-device touch verification is pending an unlocked phone.

## New recordings reviewed

The full `2026-09-27_09-35-10.odxmatch.zip` (match 85caabc5-1358-4c00-b4c5-eff9089262af) has 4,417 articles, 16 active-species confirmations, four countdown glyph records and MatchStarted. It has no active HP crops or fast-move measurements. Both HP capture workers report unavailable. GO was perceived at approximately 18.2 seconds but recorded at 51.2 seconds. The capture-routing/timing problem remains unresolved; the LCD changes do not claim to fix it.

The compact `2026-09-27_09-43-52.odxmatch.zip` has ten active-species confirmations and no fast-move measurements.
