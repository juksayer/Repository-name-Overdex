#!/usr/bin/env python3
"""Offline, manually selected stable frames. Requires PyAV/Pillow.
Find narrow horizontal separators outside the central type icon. Never assume
the layer number from brightness: report every energy interpretation up to cap,
then intersect both buttons. Not a live detector; taps/fades can still interfere.
"""
import argparse
import hashlib
import json
import math
from pathlib import Path

def separator(image, cx, cy, radius=75):
    pixels = image.load()
    inner = radius - 10
    rows = []
    for dy in range(-inner, inner + 1):
        extent = math.isqrt(inner * inner - dy * dy)
        dxs = list(range(max(round(radius / 3), extent // 2), extent + 1))
        if len(dxs) < 8:
            continue
        scores = []
        for side in [-1, 1]:
            vals = [pixels[cx + side * dx, cy + dy][:3] for dx in dxs]
            scores.append(sum(min(v) > 110 and max(v) - min(v) < 140 for v in vals) / len(vals))
        if min(scores) > .7:
            rows.append(dy)
    groups = []
    for row in rows:
        if not groups or row != groups[-1][-1] + 1:
            groups.append([])
        groups[-1].append(row)
    # Bright whole disks and multiple lines do not supply a unique boundary.
    if len(groups) != 1 or not 1 <= len(groups[0]) <= 8:
        return None
    return (groups[0][0] + groups[0][-1]) / 2

def separator_by_contrast(im,cx,cy,r=75):
 """Offline trial: a narrow locally bright line, including on a light upper fill.
 Not a live energy reading; unknown or multiple lines remain unresolved.
 """
 pix=im.load();inner=r-10;rows=[]
 for dy in range(-inner+4,inner-3):
  extent=math.isqrt(inner*inner-dy*dy);dxs=list(range(max(round(r/3),extent//2),extent+1))
  if len(dxs)<8:continue
  hits=[]
  for side in [-1,1]:
   n=0
   for dx in dxs:
    val=lambda y:sum(pix[cx+side*dx,cy+y][:3])/3
    if val(dy)-max(val(dy-4),val(dy+4))>18:n+=1
   hits.append(n/len(dxs))
  if min(hits)>.7:rows.append(dy)
 groups=[]
 for y in rows:
  if not groups or y!=groups[-1][-1]+1:groups.append([])
  groups[-1].append(y)
 if len(groups)!=1 or len(groups[0])>8:return None
 return sum([groups[0][0],groups[0][-1]])/2

def energy_hypotheses(boundary, radius, cost, uncertainty_px=2, cap=100):
    if boundary is None or radius <= 0 or cost <= 0 or abs(boundary) > radius:
        return []
    fraction = (radius - boundary) / (2 * radius)
    error = uncertainty_px * cost / (2 * radius)
    result = []
    for layers in range(cap // cost + 1):
        energy = cost * (layers + fraction)
        if energy - error <= cap:
            result.append(dict(completed_layers=layers, energy=energy,
                               low=max(0, energy-error), high=min(cap, energy+error)))
    return result

def intersect_hypotheses(left, right):
    return [dict(low=max(a["low"], b["low"]), high=min(a["high"], b["high"]),
                 left_layers=a["completed_layers"], right_layers=b["completed_layers"])
            for a in left for b in right if max(a["low"], b["low"]) <= min(a["high"], b["high"])]

def main():
    import av
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("video", type=Path)
    parser.add_argument("--times", required=True, help="comma separated manually selected stable times")
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--method", choices=["brightness", "contrast"], default="brightness")
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    targets = iter(sorted(map(float, args.times.split(","))))
    target = next(targets)
    results = []
    with av.open(str(args.video)) as container:
        for frame in container.decode(video=0):
            if frame.time is None or frame.time < target:
                continue
            image = frame.to_image().convert("RGB")
            if image.size != (816,1808):
                raise ValueError("This experiment is calibrated only for 816x1808 input")
            reader = separator if args.method == "brightness" else separator_by_contrast
            boundaries = [reader(image, cx, 1620) for cx in [252,564]]
            hypotheses = [energy_hypotheses(y,75,cost) for y,cost in zip(boundaries,[35,55])]
            name = f"frame-{frame.pts}.png"
            image.save(args.output / name)
            results.append(dict(seconds=float(frame.time), pts=frame.pts, time_base=str(frame.time_base),
                                evidence=name, evidence_sha256=hashlib.sha256((args.output/name).read_bytes()).hexdigest(),
                                separator_offset_y=boundaries, hypotheses=hypotheses,
                                joint=intersect_hypotheses(*hypotheses)))
            target = next(targets, None)
            if target is None:
                break
    report = dict(method=args.method, source_sha256=hashlib.sha256(args.video.read_bytes()).hexdigest(),
                  configuration=dict(centers=[[252,1620],[564,1620]],fill_radius=75,costs=[35,55],
                                     pixel_uncertainty=2,energy_cap=100),
                  status="EXPERIMENTAL_HEIGHT_MODEL_NOT_CALIBRATED_ENERGY",
                  note="Manually selected frames; layer counts are hypotheses. Intervals reflect pixel tolerance only, not total uncertainty.",
                  frames=results)
    (args.output / "report.json").write_text(json.dumps(report,indent=2)+"\n")
    for row in results:
        print(row["seconds"], row["separator_offset_y"], row["joint"])

if __name__ == "__main__":
    main()
