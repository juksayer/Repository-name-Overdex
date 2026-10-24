#!/usr/bin/env python3
"""Build an offline visual-effect review from a video and an unchanged .odxmatch.

Requires PyAV, NumPy and Pillow. --offset is archive record-relative seconds
minus video PTS seconds, NOT MatchStarted time. Never writes battle testimony.
The sampled MP4 pixels differ from MediaProjection; this is a diagnostic, not
a reproduction of the original detector's measurements or an attack counter.
"""
import argparse
import base64
from bisect import bisect_right
import hashlib
from io import BytesIO
import json
import math
from pathlib import Path
import zipfile

import numpy as np
from PIL import Image

VERSION = "visual-review-v1"
GRID = 24
SIDES = ("PLAYER", "OPPONENT")
KINDS = {
    "fast_move_recipient_visual_artifact_measured", "fast_move_recipient_visual_cadence_measured",
    "active_hp_bar_damage_tick_measured", "hp_bar_border_pulse", "active_hp_bar_border_pulse_observed",
    "fast_move_use_observed", "fast_move_identified", "active_pokemon_species_witnessed",
    "active_pokemon_fainted_witnessed", "attack_incoming", "get_ready_witnessed",
    "charge_move_used_announced", "charged_move_announced", "match_ended", "qte_grade_witnessed",
}


def sha256(path):
    digest = hashlib.sha256()
    with Path(path).open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def next_sample_time(start, at, fps):
    """Fixed time buckets avoid turning 24/30 fps input into 12/15 fps output."""
    return start + (math.floor((at - start) * fps + 1e-7) + 1) / fps


def sampling_bounds(p):
    """Original Android integer geometry, before video scaling."""
    w, h = p.get("sourceWidth"), p.get("sourceHeight")
    if not w or not h:
        return None
    l, t, r, b = (p[k] for k in ("barLeft", "barTop", "barRight", "barBottom"))
    if not (0 <= l < r <= w and 0 <= t < b <= h):
        return None
    bw, bh = r - l, b - t
    return [max(0, l - bw // 2), max(0, t - bw // 4),
            min(w, r + bw // 2), min(h, b + max(bw * 3 // 2, bh * 12))]


def sample_grid(image, p):
    bounds = sampling_bounds(p)
    if bounds is None:
        return None
    l, t, r, b = bounds
    sx, sy = image.width / p["sourceWidth"], image.height / p["sourceHeight"]
    # Sample centers in source space first, matching Kotlin truncation.
    xs = (l + ((np.arange(GRID) + .5) * (r - l) / GRID).astype(int))
    ys = (t + ((np.arange(GRID) + .5) * (b - t) / GRID).astype(int))
    xs = np.clip((xs * sx).astype(int), 0, image.width - 1)
    ys = np.clip((ys * sy).astype(int), 0, image.height - 1)
    pixels = np.asarray(image.convert("RGB"))[ys[:, None], xs[None, :]].copy()
    return pixels, [l * sx, t * sy, r * sx, b * sy]


class Timeline:
    def __init__(self, document):
        self.match_id = document["matchId"]
        self.articles = document["articles"]
        origins = [a["monotonicTimeNanos"] for a in self.articles
                   if a["payload"]["kind"] == "match_record_started" and a.get("monotonicTimeNanos") is not None]
        if not origins:
            raise ValueError("Record-start monotonic time is missing; do not guess the video alignment")
        self.origin = min(origins)
        self.hp = {}
        self.times = {}
        for side in SIDES:
            rows = [a for a in self.articles if a["payload"]["kind"] == "active_hp_bar_measured"
                    and a["payload"]["side"] == side and a.get("monotonicTimeNanos") is not None]
            rows.sort(key=lambda a: a["monotonicTimeNanos"])
            self.hp[side] = rows
            self.times[side] = [a["monotonicTimeNanos"] for a in rows]

    def hp_at(self, side, nanos, max_age):
        index = bisect_right(self.times[side], nanos) - 1
        if index < 0:
            return None
        row = self.hp[side][index]
        age = (nanos - row["monotonicTimeNanos"]) / 1e9
        return (row, age) if 0 <= age <= max_age else None

    def events(self, start, end, offset):
        result = []
        for a in self.articles:
            if a.get("monotonicTimeNanos") is None or a["payload"]["kind"] not in KINDS:
                continue
            at = (a["monotonicTimeNanos"] - self.origin) / 1e9 - offset
            if not start <= at <= end:
                continue
            item = dict(videoSeconds=at, article=a)
            p = a["payload"]
            if p["kind"] == "fast_move_recipient_visual_artifact_measured":
                match = self.hp_at(p["damagedSide"], a["monotonicTimeNanos"], 0)
                if match:
                    hp = match[0]
                    item.update(originalSamplingBounds=sampling_bounds(hp["payload"]),
                                geometryArticleId=hp["articleId"],
                                sourceSize=[hp["payload"].get("sourceWidth"), hp["payload"].get("sourceHeight")])
            result.append(item)
        return sorted(result, key=lambda e: e["videoSeconds"])


class DiagnosticDetector:
    """Port the color-burst thresholds for inspection; never claim a move/type.

    Unlike the live reader, deliberately reset on missing/relocated geometry or
    frame gaps. Expose those resets so changed scenery is not a hidden baseline.
    """
    def __init__(self):
        self.reset()
        self.last_emission = None

    def reset(self):
        self.baseline = None
        self.peak = None
        self.started = None
        self.last_time = None
        self.bounds = None
        self.previous_center = None

    def accept(self, pixels, bounds, at):
        reason = None
        if self.last_time is not None and (at <= self.last_time or at - self.last_time > .2):
            reason = "Frame gap / non-increasing time"
        elif self.bounds is not None and max(abs(a-b) for a,b in zip(bounds, self.bounds)) > 4:
            reason = "Sampling region moved"
        if reason:
            self.reset()
        self.last_time, self.bounds = at, bounds
        if self.baseline is None:
            self.baseline = pixels.copy()
            return dict(status=reason or "Baseline", changed=[], fraction=0, score=0, center=None, arrow=None)
        distances = np.abs(pixels.astype(float) - self.baseline.astype(float)).mean(axis=2) / 255
        mask = distances >= .12
        yy, xx = np.nonzero(mask)
        count = len(xx)
        fraction = count / (GRID * GRID)
        distance = float(distances[mask].mean()) if count else 0
        score = fraction * distance
        center = [float((xx+.5).mean()/GRID), float((yy+.5).mean()/GRID)] if count else None
        arrow = [self.previous_center, center] if center and self.previous_center else None
        self.previous_center = center
        result = dict(status="Quiet", changed=(yy*GRID+xx).tolist(), fraction=fraction,
                      meanDistance=distance, score=score, center=center, arrow=arrow,
                      rgb=[round(float(x), 1) for x in pixels[mask].mean(axis=0)] if count else None)
        # Show hue composition of changed pixels, including gray/white explicitly.
        hsv = np.asarray(Image.fromarray(pixels).convert("HSV"))
        selected = hsv[mask]
        if count:
            chromatic = selected[:, 1] >= 45
            bins = np.bincount((selected[chromatic, 0].astype(int) * 12 // 256), minlength=12)
            result["hueBins"] = bins.tolist() + [int((~chromatic).sum())]
        receded = self.peak and (fraction <= .035 or distance < .16 or score <= self.peak["score"] * .55)
        if receded:
            peak = self.peak
            if self.last_emission is None or peak["peak"] - self.last_emission >= .3:
                result["candidate"] = dict(onset=self.started, peak=peak["peak"], release=at)
                self.last_emission = peak["peak"]
                result["status"] = "Candidate released (not a confirmed attack)"
            self.baseline, self.peak, self.started = pixels.copy(), None, None
        else:
            if fraction >= .08 and distance >= .16:
                if self.started is None:
                    self.started = at
                if self.peak is None or score > self.peak["score"]:
                    self.peak = dict(peak=at, score=score)
                result["status"] = "Color burst in progress"
            elif self.peak is None:
                self.baseline = pixels.copy()
            if self.started is not None and at - self.started >= 1.5:
                self.baseline, self.peak, self.started = pixels.copy(), None, None
                result["status"] = "Long change absorbed as scene change"
        return result


def jpeg_data(image, width=540):
    preview = image.copy()
    preview.thumbnail((width, 1600))
    out = BytesIO()
    preview.save(out, format="JPEG", quality=85)
    return "data:image/jpeg;base64," + base64.b64encode(out.getvalue()).decode()


def build(args):
    import av
    with zipfile.ZipFile(args.archive) as package:
        member = package.getinfo("timeline.json")
        if member.file_size > 100_000_000:
            raise ValueError("Timeline exceeds the review tool's 100 MB limit")
        timeline = Timeline(json.loads(package.read(member)))
    config = dict(version=VERSION, start=args.start, end=args.end, fps=args.fps, offset=args.offset,
                  alignmentUncertaintySeconds=args.alignment_uncertainty, maxHpAgeSeconds=args.max_hp_age,
                  grid=GRID, sceneResetPixels=4, gapResetSeconds=.2)
    sources = dict(video=str(args.video.resolve()), videoSha256=sha256(args.video),
                   archive=str(args.archive.resolve()), archiveSha256=sha256(args.archive),
                   matchId=timeline.match_id, recordStartMonotonicNanos=timeline.origin)
    set_id = hashlib.sha256(json.dumps([sources, config], sort_keys=True).encode()).hexdigest()[:20]
    rows, candidates = [], []
    detectors = {s: DiagnosticDetector() for s in SIDES}
    next_at = args.start
    prior_pts = None
    with av.open(str(args.video)) as container:
        stream = container.streams.video[0]
        container.seek(max(0, int((args.start - 2) / float(stream.time_base))), stream=stream, backward=True)
        for frame in container.decode(stream):
            if frame.pts is None:
                raise ValueError("Frame has no presentation timestamp")
            at = float(frame.pts * frame.time_base)
            if at > args.end:
                break
            if at + 1e-9 < next_at:
                continue
            if prior_pts is not None and at <= prior_pts:
                raise ValueError("Video presentation timestamps are not increasing")
            prior_pts, next_at = at, next_sample_time(args.start, at, args.fps)
            image = frame.to_image().convert("RGB")
            nanos = timeline.origin + round((at + args.offset) * 1e9)
            row = dict(seconds=at, pts=frame.pts, timeBase=str(frame.time_base),
                       width=image.width, height=image.height, image=jpeg_data(image), sides={})
            for side in SIDES:
                found = timeline.hp_at(side, nanos, args.max_hp_age)
                sampled = sample_grid(image, found[0]["payload"]) if found else None
                if sampled is None:
                    detectors[side].reset()
                    row["sides"][side] = dict(status="HP geometry unavailable", changed=[])
                    continue
                hp, age = found
                pixels, bounds = sampled
                measurement = detectors[side].accept(pixels, bounds, at)
                measurement.update(bounds=bounds, geometryArticleId=hp["articleId"], geometryAgeSeconds=age,
                                   hpFraction=hp["payload"]["filledFraction"],
                                   sourceSize=[hp["payload"]["sourceWidth"], hp["payload"]["sourceHeight"]])
                if measurement.get("candidate"):
                    candidates.append(dict(side=side, **measurement["candidate"]))
                row["sides"][side] = measurement
            rows.append(row)
    if not rows:
        raise ValueError("No frames in the requested span")
    events = timeline.events(args.start, args.end, args.offset)
    intervals = [b["seconds"] - a["seconds"] for a, b in zip(rows, rows[1:])]
    config["sampledIntervals"] = dict(count=len(rows), medianSeconds=float(np.median(intervals)) if intervals else None,
                                     maxSeconds=max(intervals) if intervals else None)
    report = dict(schemaVersion=1, reviewSetId=set_id, sources=sources, config=config, candidates=candidates,
                  events=events, frames=rows,
                  status="OFFLINE_DIAGNOSTIC_NOT_BATTLE_TESTIMONY",
                  note="Masks are recomputed from compressed video with approximate clock alignment. Arrows show changed-area centroid displacement, not proven particle direction. Candidate bursts are not attacks. Original Articles are unchanged.")
    template = Path(__file__).parent / "templates" / "fast_move_visual_review.html"
    data = json.dumps(report, separators=(",", ":"), allow_nan=False).replace("<", "\\u003c")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(template.read_text().replace("__REVIEW_DATA__", data))
    # Compact companion for provenance and automated review; original source paths stay local.
    summary = {**report, "frames": [{k: v for k, v in row.items() if k != "image"} for row in rows]}
    args.output.with_suffix(".json").write_text(json.dumps(summary, indent=2) + "\n")
    print(json.dumps(dict(output=str(args.output), frames=len(rows), candidates=len(candidates),
                          originalEvents=len(events), reviewSetId=set_id)))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("video", type=Path)
    parser.add_argument("archive", type=Path)
    parser.add_argument("--offset", type=float, required=True)
    parser.add_argument("--alignment-uncertainty", type=float, required=True)
    parser.add_argument("--start", type=float, required=True)
    parser.add_argument("--end", type=float, required=True)
    parser.add_argument("--fps", type=float, default=20)
    parser.add_argument("--max-hp-age", type=float, default=.25)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if not (all(math.isfinite(x) for x in (args.start, args.end, args.offset, args.fps, args.alignment_uncertainty, args.max_hp_age))
            and 0 <= args.start < args.end and args.end - args.start <= 60 and 10 <= args.fps <= 30
            and 0 <= args.alignment_uncertainty <= 2 and 0 <= args.max_hp_age <= 1):
        parser.error("Use a span up to 60 seconds, 10–30 fps, uncertainty 0–2 s, and HP age 0–1 s")
    protected = {args.video.resolve(), args.archive.resolve()}
    if args.output.resolve() in protected or args.output.with_suffix(".json").resolve() in protected:
        parser.error("Output must not overwrite a source")
    build(args)


if __name__ == "__main__":
    main()
