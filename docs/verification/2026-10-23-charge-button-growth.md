# Offline charge-button growth experiment

Source: Screen_recording_20261023_133216.mp4, 816×1808, duration about 7.88 s.
SHA256: b02b896df58cad657f548379ecaa732b71462ca8fd2429a84b3ea03f7d9a456d

Original video, per-frame CSV, candidate PNG evidence with hashes, report and sampling
results are retained in /home/sean/overdex-device-backups/2026-10-23-charge-button-experiment.
The tracked CSV in charge-button-growth/20261023-133216.csv allows repeating the
temporal sensitivity test without the video. Pixels are required to retest measurement.

## Measurement and result

Calibrated source-video centers: slot 1 (252,1620), slot 2 (564,1620).
Search ring radii 60–110 px; sample 72 circumference points, minimum 65% bright
achromatic pixels (channel minimum >190, channel spread <50). A stable recent
baseline (8–12 measurements, spread <=8%) precedes at least 12% growth. Two missing
measurements with both rings unavailable must follow within 0.5 s.

Full-rate recording: one candidate, slot 1 (Brutal Swing).
- Growth first measured at 4.794578 s: radius 77 -> 99 px (28.6%).
- Other button remains near 75 px before fading.
- Disappearance corroboration delivered at 4.882078 s.
- This detector reports ring unavailability during fading, not literal completion of disappearance.
- Manual review: contact around 4.690 s, clear controls gone around 4.935 s,
  Dark QTE icons around 6.7–6.8 s. Contact and QTE icons are not measured by this tool.

## Sampling sensitivity

Resampled existing measurements at 20 different phase offsets per rate. These are
simulations on ONE clip, not 20 independent trials or population accuracy.

| Minimum enlarged frames | 10 FPS | 20 FPS | 30 FPS |
|---|---:|---:|---:|
| 1, plus disappearance corroboration | 12/20 | 20/20 | 20/20 |
| 2, plus disappearance corroboration | 0/20 | 3/20 | 15/20 |

The visible expansion is too brief to require two frames reliably at 20 FPS.
A single growth measurement plus independent charge-phase confirmation is a better
candidate for a future live witness. Full-rate raw measurements remain preserved.

## Boundaries and next tests

Offline only; no Android changes, installation, timeline changes or energy spending.
A candidate is NOT a verified tap, accepted move, or exact QTE start. Ring fading,
occlusion and geometry changes can mimic disappearance. Thresholds were selected
using this clip and need independent recordings, other types, resolutions and layouts.

Next recordings: ordinary Fast Move taps away from charge controls; taps on an
unready charge button; successful selections of both slots; repeated charge taps;
opponent charge interruption, switch and faint. Preserve source footage, registered
button calibration and capture times. Match inferred request to subsequent Get Ready,
Attack Incoming or QTE evidence without fabricating missing observations.

Run tools/inspect_charge_button_growth.py with PyAV and Pillow (see --help).
Run tools/test_charge_growth_sampling.py on the saved CSV.
Eight unit checks cover temporal ordering, one-frame flashes, missing controls,
expiration, normal-size recovery, and a synthetic interior touch that must not
change the measured border.
