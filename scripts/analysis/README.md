# HP location evidence profiles

`profile_hp_locations.py` is a read-only, standard-library Python 3.11+ analysis
tool for one or more `.odxmatch` ZIPs. It describes **recorded measurements**. It
does not change calibration, train recognition, infer attacks, or install/apply
species presets in Overdex.

```sh
python3 scripts/analysis/profile_hp_locations.py /absolute/path/match.odxmatch.zip \
  --output /absolute/path/hp-location-evidence-profiles.json
```

Additional archives can follow the first argument. `--bin-size 16` controls
quantization in captured-source pixels. `--identity-max-age-seconds 5` is the
default maximum age of the latest confirmed active-species observation. A larger
value admits older associations; it does not establish that no switch occurred.

The JSON contains:

- Source dimensions, side, exact species name/ID (including form text), and fixed
  HP crop provenance where the archive supplies it.
- Observed bounding extent and per-edge ranges.
- Quantized bar-center bins, their sample counts, and **all tied modes**.
- Archive SHA-256s, HP and identity Article citations, underlying identity evidence
  references, and source-time ranges separately for each Match. Independent
  monotonic clocks are not merged into a single cross-Match interval.
- Every accepted sample and explicit reasons for abstaining from other samples.

Identity association uses source monotonic time, never Article delivery time.
There is no wall-time fallback or backfilling from a later species reading.
Conflicting same-time names and same-time identity changes abstain. Faints,
switches, and entry/empty-HP check boundaries retire the affected identity;
source trigger time is used for delayed entry checks when available. An explicitly
named late faint of an old species does not retire an independently confirmed,
differently named replacement. Configured-team/lead inference cannot label a
sample. Data at or after the recorded Match end is excluded.

All samples and profiles currently have **phase `UNKNOWN`**. Neither an HP
rectangle nor a source-time mode proves ordinary play, a stationary home
position, or QTE. The mode is weighted by unique archived HP Articles, not elapsed
time; uneven capture/recognition rates can bias it. Camera movement, countdown
occlusion, incorrect species attribution, and false geometry may remain in the
underlying measurements. The tool deliberately supplies no production preset.

The HP-region fingerprint hashes exact fixed `PlayerHpEvidenceCrop` and
`OpponentHpEvidenceCrop` bounds plus captured-source dimensions. It is **not a
full saved calibration/profile fingerprint**; that field stays unavailable.
Complete matching HP-region contexts may pool Matches. Partial/unknown contexts
stay scoped to their own archive. Conflicting fixed bounds in one archive cause
abstention rather than inventing a calibration-change interval. Duplicate inputs
and duplicate HP Articles cannot inflate sample counts.

Compact exports may omit uncited crops. Missing calibration provenance or target
images therefore does not establish missing capture. No phone model, physical
display size, or full calibration snapshot is inferred from source dimensions.

Run focused standalone tests without building the Android app:

```sh
python3 -m unittest discover -s scripts/analysis/tests -p 'test_profile_hp_locations.py' -v
```
