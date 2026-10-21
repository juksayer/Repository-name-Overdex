#!/usr/bin/env python3
"""Read-only L/R measurements from an odxmatch archive; never assigns a Pokemon side.

Usage: python3 tools/inspect_stereo_audio.py MATCH.odxmatch.zip --output REPORT.json
Mono probe artifacts remain mono. Only genuine two-channel artifacts get L/R metrics.
"""
import argparse
import array
import collections
import hashlib
import io
import json
import math
import sys
import wave
import zipfile
from pathlib import Path


def measure_wav(data):
    with wave.open(io.BytesIO(data), "rb") as wav:
        if wav.getsampwidth() != 2 or wav.getcomptype() != "NONE":
            raise ValueError("Expected uncompressed PCM-16")
        channels, rate, frames = wav.getnchannels(), wav.getframerate(), wav.getnframes()
        if channels not in (1, 2) or not frames:
            raise ValueError("Expected nonempty mono or stereo PCM")
        pcm = wav.readframes(frames)
        if len(pcm) != frames * channels * 2:
            raise ValueError("Truncated PCM frames")
    samples = array.array("h", pcm)
    if sys.byteorder != "little":
        samples.byteswap()
    streams = [samples[c::channels] for c in range(channels)]
    rms = [math.sqrt(sum(x * x for x in stream) / frames) / 32768 for stream in streams]
    result = dict(channels=channels, sample_rate_hz=rate, frames=frames,
                  duration_seconds=frames / rate, channel_rms=rms)
    if channels == 2:
        left, right = streams
        sum_l, sum_r = sum(left), sum(right)
        energy_l, energy_r = sum(x * x for x in left), sum(x * x for x in right)
        variance_l = max(0, energy_l - sum_l * sum_l / frames)
        variance_r = max(0, energy_r - sum_r * sum_r / frames)
        covariance = sum(l * r for l, r in zip(left, right)) - sum_l * sum_r / frames
        denominator = math.sqrt(variance_l * variance_r)
        correlation = max(-1, min(1, covariance / denominator)) if denominator else None
        mono_rms = math.sqrt(sum(((l + r) / 2) ** 2 for l, r in zip(left, right)) / frames) / 32768
        stereo_rms = math.sqrt((rms[0] ** 2 + rms[1] ** 2) / 2)
        result.update(
            identical_channels=left == right, correlation=correlation,
            difference_rms=math.sqrt(sum((l - r) ** 2 for l, r in zip(left, right)) / frames) / 32768,
            mono_rms=mono_rms,
            left_over_right_db=20 * math.log10(rms[0] / rms[1]) if min(rms) > 0 else None,
            mono_over_stereo_db=20 * math.log10(mono_rms / stereo_rms) if mono_rms > 0 and stereo_rms > 0 else None,
            mono_cancels_completely=stereo_rms > 0 and mono_rms == 0,
        )
    return result


def inspect_archive(path):
    with zipfile.ZipFile(path) as archive:
        manifest = json.loads(archive.read("manifest.json"))
        timeline = json.loads(archive.read(manifest["timelineEntry"]))
        articles = timeline["articles"]
        by_path = collections.defaultdict(list)
        statuses = []
        for article in articles:
            payload = article["payload"]
            kind = payload.get("kind", payload.get("type"))
            if kind == "audio_captured":
                by_path[payload["artifactPath"]].append(dict(
                    article_id=article["articleId"], cue=payload["cueKind"], source=payload.get("captureSource", "MICROPHONE"),
                    source_monotonic_nanos=article.get("monotonicTimeNanos"),
                    declared_channels=payload["channelCount"], declared_rate=payload["sampleRateHz"],
                ))
            elif kind == "audio_input_status":
                statuses.append(payload)
        clips = []
        for artifact in manifest["artifacts"]:
            name = artifact["relativePath"]
            if artifact.get("mediaType") != "audio/wav":
                continue
            if archive.getinfo(name).file_size > 64 * 1024 * 1024:
                raise ValueError("Audio artifact exceeds inspection limit")
            data = archive.read(name)
            if len(data) != artifact["byteCount"] or hashlib.sha256(data).hexdigest() != artifact["sha256"]:
                raise ValueError("Artifact length or hash mismatch: " + name)
            metrics = measure_wav(data)
            requests = by_path[name]
            if any(r["declared_channels"] != metrics["channels"] or r["declared_rate"] != metrics["sample_rate_hz"] for r in requests):
                raise ValueError("WAV format disagrees with testimony: " + name)
            clips.append(dict(artifact=name, sha256=artifact["sha256"], requests=requests, **metrics))
        return dict(match_id=manifest["matchId"], audio_artifacts=len(clips),
                    channel_counts=dict(collections.Counter(c["channels"] for c in clips)),
                    capture_statuses=statuses, clips=clips,
                    interpretation="Channel differences are measurements, not evidence of which Pokemon made the sound. "
                                   "Null correlation or dB ratios mean undefined (for example silence). "
                                   "Separate artifacts may overlap in time; do not sum their durations as match duration.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    if args.output and args.output.resolve() == args.archive.resolve():
        parser.error("Report must not replace the source archive")
    report = json.dumps(inspect_archive(args.archive), indent=2, allow_nan=False) + "\n"
    if args.output:
        args.output.write_text(report)
    else:
        print(report, end="")


if __name__ == "__main__":
    main()
