#!/usr/bin/env python3
"""Replay measured frames through candidate policies at simulated sample rates.
This is a timing-sensitivity experiment, not an accuracy estimate for new matches.
"""
import argparse
import csv
import json
from pathlib import Path
from inspect_charge_button_growth import GrowthEpisode

def run(measurements):
    frames = {}
    for row in csv.DictReader(measurements.open()):
        frames.setdefault(float(row["seconds"]), {})[int(row["slot"])] = (
            int(row["radius_px"]) if row["radius_px"] else None)
    results = []
    for minimum in [1, 2]:
        for hz in [10, 20, 30]:
            runs = []
            for phase in range(20):
                next_at = phase / 20 / hz
                states = [GrowthEpisode(minimum), GrowthEpisode(minimum)]
                events = []
                for at, radii in frames.items():
                    if at < next_at:
                        continue
                    while next_at <= at:
                        next_at += 1 / hz
                    for slot, state in enumerate(states, 1):
                        event = state.accept(at, radii[slot], all(v is None for v in radii.values()))
                        if event:
                            events.append(dict(slot=slot, **event))
                runs.append(events)
            results.append(dict(growth_frames_min=minimum, hz=hz, phases=20,
                                phases_detecting_slot_1=sum(any(e["slot"] == 1 for e in r) for r in runs),
                                events=runs))
    return results

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("measurements", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    result = run(args.measurements)
    args.output.write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps([{k: v for k, v in r.items() if k != "events"} for r in result], indent=2))
