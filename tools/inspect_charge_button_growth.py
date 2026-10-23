#!/usr/bin/env python3
"""Offline experiment only: measure charge-button rings, never write battle testimony.

Requires PyAV and Pillow. Centers/radius bounds are calibrated in source-video pixels.
Example:
  python inspect_charge_button_growth.py recording.mp4 --output review \
    --button 252,1620 --button 564,1620 --radius 60,110
Output retains source hash, frame PTS/time base, per-frame ring scores, and candidate
evidence images. Candidates are not verified taps, accepted moves, or QTE boundaries.
"""
import argparse
import csv
import hashlib
import json
import math
import statistics
from collections import deque
from pathlib import Path

def measure_ring(image, center, radius_bounds):
    """Score achromatic bright circumference, excluding the icon/touch interior."""
    pixels = image.load()
    cx, cy = center
    scores = []
    for radius in range(radius_bounds[0], radius_bounds[1] + 1):
        hits = 0
        for i in range(72):
            angle = i * math.tau / 72
            x, y = round(cx + radius * math.cos(angle)), round(cy + radius * math.sin(angle))
            if 0 <= x < image.width and 0 <= y < image.height:
                pixel = pixels[x, y][:3]
                hits += min(pixel) > 190 and max(pixel) - min(pixel) < 50
        scores.append((hits / 72, radius))
    score, radius = max(scores)
    return (radius if score >= .65 else None), score

class GrowthEpisode:
    """Two-frame expansion after a stable baseline; source-time bounded disappearance."""
    def __init__(self, growth_frames_min=2):
        self.growth_frames_min = growth_frames_min
        self.history = deque(maxlen=12)
        self.pending = None
        self.last_visible = None
        self.missing = 0
        self.growth_frames = 0

    def accept(self, at, radius, both_missing):
        if radius is None:
            self.missing += 1
            if self.pending and both_missing and self.missing >= 2:
                result = dict(self.pending, controls_missing_at=at)
                confirmed = self.growth_frames >= self.growth_frames_min and at - self.pending["growth_at"] <= .5
                self.reset()
                return result if confirmed else None
            if self.last_visible is not None and at - self.last_visible > .5:
                self.reset()
            return None
        self.missing = 0
        self.last_visible = at
        if self.pending and at - self.pending["growth_at"] > .5:
            self.reset()
        baseline = statistics.median(self.history) if len(self.history) >= 8 else None
        stable = baseline is not None and max(self.history) - min(self.history) <= baseline * .08
        if stable and radius >= baseline * 1.12:
            if self.pending is None:
                self.pending = dict(growth_at=at, baseline_radius=baseline, expanded_radius=radius)
            self.growth_frames += 1
        else:
            self.pending = None
            self.growth_frames = 0
            self.history.append(radius)
        return None

    def reset(self):
        self.history.clear()
        self.pending = None
        self.last_visible = None
        self.missing = 0
        self.growth_frames = 0

def main():
    import av
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("video", type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--button", action="append", required=True,
                        help="center x,y in video pixels, one per slot")
    parser.add_argument("--radius", default="60,110", help="minimum,maximum ring radius")
    args = parser.parse_args()
    centers = [tuple(map(int, item.split(","))) for item in args.button]
    bounds = tuple(map(int, args.radius.split(",")))
    if len(centers) != 2 or any(len(x) != 2 for x in centers) or len(bounds) != 2 or not 0 < bounds[0] <= bounds[1]:
        parser.error("Supply two x,y centers and positive ascending radius bounds")
    args.output.mkdir(parents=True, exist_ok=True)
    source_hash = hashlib.sha256(args.video.read_bytes()).hexdigest()
    container = av.open(str(args.video))
    stream = container.streams.video[0]
    detectors = [GrowthEpisode(), GrowthEpisode()]
    candidates = []
    recent = deque()
    with (args.output / "measurements.csv").open("w", newline="") as handle:
        writer = csv.writer(handle)
        writer.writerow(["frame", "pts", "time_base", "seconds", "slot", "radius_px", "ring_score"])
        for index, frame in enumerate(container.decode(video=0)):
            if frame.time is None:
                raise ValueError("Video frame lacks a source timestamp")
            at = float(frame.time)
            image = frame.to_image().convert("RGB")
            recent.append((at, index, image))
            while recent and at - recent[0][0] > .75:
                recent.popleft()
            measured = [measure_ring(image, center, bounds) for center in centers]
            both_missing = all(radius is None for radius, _ in measured)
            for slot, ((radius, score), detector) in enumerate(zip(measured, detectors), 1):
                writer.writerow([index, frame.pts, str(frame.time_base), at, slot, radius, score])
                result = detector.accept(at, radius, both_missing)
                if result:
                    evidence = []
                    for time, number, picture in recent:
                        filename = f"frame-{number:05d}.png"
                        path = args.output / filename
                        if not path.exists():
                            picture.save(path)
                        evidence.append(dict(file=filename, seconds=time,
                                             sha256=hashlib.sha256(path.read_bytes()).hexdigest()))
                    candidates.append(dict(slot=slot, **result, status="INFERRED_TEST_CANDIDATE",
                                           evidence=evidence))
    report = dict(source=str(args.video.resolve()), source_sha256=source_hash,
                  width=stream.width, height=stream.height,
                  configuration=dict(centers=centers, radius_bounds=bounds,
                                     ring_score_min=.65, growth_ratio_min=1.12,
                                     growth_frames_min=2, missing_frames_min=2,
                                     transition_window_seconds=.5),
                  limitations="Single-clip experiment; no tap detection or game acceptance claim. Missing ring may mean fade or occlusion.",
                  candidates=candidates)
    (args.output / "report.json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps({k: v for k, v in report.items() if k != "candidates"}, indent=2))
    print("Candidates:", [{k: v for k, v in x.items() if k != "evidence"} for x in candidates])

if __name__ == "__main__":
    main()
