#!/usr/bin/env python3
"""Extract candidate Fast Move examples from verified, unchanged odxmatch PCM.

Usage: python3 tools/import_captured_fast_move_audio.py PLAN.json --output-dir NEW_DIR

Plan paths are relative to the plan's directory. Example plan:
{
  "version": 1,
  "sourceArchive": {"path": "match.odxmatch.zip", "sha256": "<archive SHA-256>",
                    "matchId": "<recorded Match ID>"},
  "confirmation": {"source": "user", "note": "Golduck has Confusion equipped."},
  "samples": [{"id": "confusion-reference-01", "role": "reference",
    "moveName": "Confusion", "speciesName": "Golduck",
    "audioArticleId": "<AudioCaptured Article ID>", "sourceSha256": "<WAV SHA-256>",
    "startFrame": 2400, "frameCount": 24000, "notes": "One isolated effect."}]
}

Roles are reference, holdout, or rejected: human study labels, never runtime
admission. Trims copy whole interleaved PCM frames, retaining source rate,
channels, and levels. No resampling, channel separation, denoising, or synthesis
is performed. Outputs include provenance.json and WAVs; the app's library and
original archive are never modified. An existing output directory is refused.
"""
from __future__ import annotations

import argparse
import array
from fractions import Fraction
import hashlib
from io import BytesIO
import json
import math
from pathlib import Path, PurePosixPath
import re
import struct
import sys
import wave
import zipfile

VERSION = "captured-fast-move-audio-candidates-v1"
MAX_PLAN_BYTES = 1024 * 1024
MAX_TIMELINE_BYTES = 64 * 1024 * 1024
MAX_MANIFEST_BYTES = 4 * 1024 * 1024
MAX_WAV_BYTES = 4 * 1024 * 1024
ROLES = {"reference", "holdout", "rejected"}
WINDOWS_RESERVED = {"CON", "PRN", "AUX", "NUL"} | {
    prefix + str(number) for prefix in ("COM", "LPT") for number in range(1, 10)
}


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def file_digest(path: Path) -> str:
    result = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            result.update(block)
    return result.hexdigest()


def text(value, label: str, maximum: int = 200) -> str:
    if not isinstance(value, str) or not value.strip() or len(value) > maximum or "\x00" in value:
        raise ValueError(f"{label} must be nonempty text, at most {maximum} characters")
    return value


def sha(value, label: str) -> str:
    if not isinstance(value, str) or not re.fullmatch(r"[a-f0-9]{64}", value):
        raise ValueError(f"{label} must be a lowercase SHA-256 hash")
    return value


def integer(value, label: str, minimum: int = 0) -> int:
    if type(value) is not int or value < minimum:
        raise ValueError(f"{label} must be an integer >= {minimum}")
    return value


def read_json(data: bytes, label: str):
    def unique_object(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise ValueError(f"{label} contains duplicate key: {key}")
            result[key] = value
        return result
    try:
        def invalid_constant(value):
            raise ValueError(f"{label} contains non-JSON numeric constant: {value}")
        return json.loads(data, object_pairs_hook=unique_object, parse_constant=invalid_constant)
    except (UnicodeError, json.JSONDecodeError) as error:
        raise ValueError(f"Invalid {label}: {error}") from error


def archive_bytes(package: zipfile.ZipFile, name: str, maximum: int) -> bytes:
    entries = [entry for entry in package.infolist() if entry.filename == name]
    if len(entries) != 1 or entries[0].is_dir() or entries[0].file_size > maximum:
        raise ValueError(f"Archive entry is missing, duplicated, or too large: {name}")
    return package.read(entries[0])


def decode_pcm(data: bytes) -> tuple[int, int, int, bytes]:
    """Require a finalized, bounded RIFF with exactly one PCM format/data chunk."""
    if (len(data) < 44 or data[:4] != b"RIFF" or data[8:12] != b"WAVE" or
            struct.unpack_from("<I", data, 4)[0] != len(data) - 8):
        raise ValueError("Expected a complete PCM16 RIFF WAV")
    at, seen, data_size = 12, set(), 0
    while at + 8 <= len(data):
        kind, size = struct.unpack_from("<4sI", data, at)
        end = at + 8 + size
        if end > len(data) or end + (size & 1) > len(data):
            raise ValueError("Incomplete WAV chunk")
        if kind in (b"fmt ", b"data"):
            if kind in seen:
                raise ValueError("Duplicate WAV format/data chunk")
            seen.add(kind)
        if kind == b"data":
            data_size = size
        at = end + (size & 1)
    if at != len(data) or seen != {b"fmt ", b"data"}:
        raise ValueError("Incomplete WAV format/data layout")
    try:
        with wave.open(BytesIO(data), "rb") as source:
            rate, channels, frames = source.getframerate(), source.getnchannels(), source.getnframes()
            if (source.getsampwidth() != 2 or source.getcomptype() != "NONE" or
                    channels not in (1, 2) or not 1_000 <= rate <= 192_000 or
                    frames <= 0 or frames > rate * 30 or data_size % (channels * 2)):
                raise ValueError("Expected nonempty PCM16 mono/stereo WAV, at most 30 seconds")
            pcm = source.readframes(frames)
            if len(pcm) != frames * channels * 2:
                raise ValueError("Incomplete PCM frames")
    except (wave.Error, EOFError) as error:
        raise ValueError(f"Invalid PCM16 WAV: {error}") from error
    return rate, channels, frames, pcm


def encode_pcm(rate: int, channels: int, pcm: bytes) -> bytes:
    output = BytesIO()
    with wave.open(output, "wb") as result:
        result.setnchannels(channels)
        result.setsampwidth(2)
        result.setframerate(rate)
        result.writeframes(pcm)
    return output.getvalue()


def signal_metrics(pcm: bytes) -> dict:
    values = array.array("h", pcm)
    if sys.byteorder != "little":
        values.byteswap()
    return {
        "peakAbsolutePcm16": max(abs(value) for value in values),
        "rmsPcm16": math.sqrt(sum(value * value for value in values) / len(values)),
        "zeroSampleCount": values.count(0),
        "clippedSampleCount": sum(value in (-32768, 32767) for value in values),
        "interleavedSampleCount": len(values),
        "interpretation": "Level diagnostics only; no identity or cleanliness verdict.",
    }


def prepare_candidates(plan_path: Path) -> tuple[dict, dict[str, bytes]]:
    """Validate every source/trim before writing any output; deterministic result."""
    if plan_path.stat().st_size > MAX_PLAN_BYTES:
        raise ValueError("Plan exceeds size limit")
    plan_bytes = plan_path.read_bytes()
    plan = read_json(plan_bytes, "plan")
    if not isinstance(plan, dict) or type(plan.get("version")) is not int or plan["version"] != 1:
        raise ValueError("Expected plan version 1")
    origin, confirmation, samples = plan.get("sourceArchive"), plan.get("confirmation"), plan.get("samples")
    if not isinstance(origin, dict) or not isinstance(confirmation, dict):
        raise ValueError("sourceArchive and confirmation must be objects")
    relative = text(origin.get("path"), "sourceArchive.path", 4096)
    if PurePosixPath(relative).is_absolute() or "\\" in relative or ":" in relative:
        raise ValueError("sourceArchive.path must be a relative portable path from the plan directory")
    expected_sha = sha(origin.get("sha256"), "sourceArchive.sha256")
    match_id = text(origin.get("matchId"), "sourceArchive.matchId")
    confirmed = {"source": text(confirmation.get("source"), "confirmation.source"),
                 "note": text(confirmation.get("note"), "confirmation.note", 4000)}
    if not isinstance(samples, list) or not 1 <= len(samples) <= 50:
        raise ValueError("samples must contain between 1 and 50 entries")
    source_path = plan_path.parent / relative
    if file_digest(source_path) != expected_sha:
        raise ValueError("Source archive SHA-256 does not match the plan")
    outputs, records, identities, intervals = {}, [], set(), []
    with zipfile.ZipFile(source_path) as package:
        manifest = read_json(archive_bytes(package, "manifest.json", MAX_MANIFEST_BYTES), "archive manifest")
        if not isinstance(manifest, dict) or manifest.get("matchId") != match_id:
            raise ValueError("Archive manifest Match ID does not match the plan")
        timeline_name = manifest.get("timelineEntry")
        if timeline_name != "timeline.json":
            raise ValueError("Expected archive timeline.json")
        timeline = read_json(archive_bytes(package, timeline_name, MAX_TIMELINE_BYTES), "archive timeline")
        if not isinstance(timeline, dict) or timeline.get("matchId") != match_id:
            raise ValueError("Archive timeline Match ID does not match the plan")
        articles, artifacts = timeline.get("articles"), manifest.get("artifacts")
        if not isinstance(articles, list) or not isinstance(artifacts, list):
            raise ValueError("Archive Articles and artifact inventory must be lists")
        cache = {}
        for number, sample in enumerate(samples, 1):
            if not isinstance(sample, dict):
                raise ValueError(f"Sample {number} must be an object")
            identifier = text(sample.get("id"), f"Sample {number} id", 64)
            if (not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_-]*", identifier) or
                    identifier.upper() in WINDOWS_RESERVED or identifier.lower() in identities):
                raise ValueError(f"Sample id is duplicated or not a portable filename: {identifier}")
            identities.add(identifier.lower())
            role = sample.get("role")
            if not isinstance(role, str) or role not in ROLES:
                raise ValueError(f"Sample {identifier} role must be reference, holdout, or rejected")
            move = text(sample.get("moveName"), f"Sample {identifier} moveName")
            species = text(sample.get("speciesName"), f"Sample {identifier} speciesName")
            article_id = text(sample.get("audioArticleId"), f"Sample {identifier} audioArticleId")
            source_sha = sha(sample.get("sourceSha256"), f"Sample {identifier} sourceSha256")
            start = integer(sample.get("startFrame"), f"Sample {identifier} startFrame")
            count = integer(sample.get("frameCount"), f"Sample {identifier} frameCount", 1)
            notes = sample.get("notes", "")
            if not isinstance(notes, str) or len(notes) > 4000:
                raise ValueError(f"Sample {identifier} notes must be text, at most 4000 characters")
            matches = [article for article in articles if isinstance(article, dict) and
                       article.get("articleId") == article_id]
            if len(matches) != 1 or matches[0].get("matchId") != match_id:
                raise ValueError(f"Source AudioCaptured Article is missing, duplicated, or from another Match: {article_id}")
            article = matches[0]
            payload = article.get("payload", {})
            artifact_path = f"artifacts/audio/sha256/{source_sha}.wav"
            if (not isinstance(payload, dict) or payload.get("kind") != "audio_captured" or
                    payload.get("sha256") != source_sha or payload.get("artifactPath") != artifact_path or
                    payload.get("mediaType") != "audio/wav"):
                raise ValueError(f"Source Article does not identify the planned WAV: {article_id}")
            listed = [entry for entry in artifacts if isinstance(entry, dict) and entry.get("relativePath") == artifact_path]
            if (len(listed) != 1 or listed[0].get("sha256") != source_sha or
                    listed[0].get("mediaType") != "audio/wav" or
                    listed[0].get("byteCount") != payload.get("byteCount")):
                raise ValueError(f"Source WAV inventory differs from testimony: {article_id}")
            if source_sha not in cache:
                data = archive_bytes(package, artifact_path, MAX_WAV_BYTES)
                if digest(data) != source_sha:
                    raise ValueError(f"Source WAV SHA-256 mismatch: {article_id}")
                cache[source_sha] = (len(data), decode_pcm(data))
            size, (rate, channels, frames, pcm) = cache[source_sha]
            duration = payload.get("durationNanos")
            if (payload.get("byteCount") != size or payload.get("sampleRateHz") != rate or
                    payload.get("channelCount") != channels or type(duration) is not int or
                    abs(duration * rate - frames * 1_000_000_000) > 1_000_000_000):
                raise ValueError(f"Source WAV format/duration differs from testimony: {article_id}")
            if start + count > frames:
                raise ValueError(f"Sample {identifier} frame range exceeds its source WAV")
            trimmed_pcm = pcm[start * channels * 2:(start + count) * channels * 2]
            derived = encode_pcm(rate, channels, trimmed_pcm)
            filename = identifier + ".wav"
            outputs[filename] = derived
            at = article.get("monotonicTimeNanos")
            if at is not None and type(at) is not int:
                raise ValueError(f"Invalid source monotonic timestamp: {article_id}")
            intervals.append((identifier, role, source_sha, start, start + count, rate,
                              Fraction(at) + Fraction(start * 1_000_000_000, rate) if at is not None else None,
                              Fraction(at) + Fraction((start + count) * 1_000_000_000, rate) if at is not None else None,
                              digest(trimmed_pcm)))
            records.append({
                "id": identifier, "role": role, "moveName": move, "speciesName": species,
                "labelBasis": "Human-confirmed equipped move; not automatic acoustic identity.",
                "notes": notes,
                "source": {"audioArticleId": article_id, "artifactPath": artifact_path,
                           "sha256": source_sha, "byteCount": size, "sampleRateHz": rate,
                           "channelCount": channels, "frameCount": frames,
                           "perceivedAt": article.get("perceivedAt"), "monotonicTimeNanos": at,
                           "durationNanos": duration, "captureSource": payload.get("captureSource"),
                           "cueKind": payload.get("cueKind")},
                "trim": {"startFrame": start, "frameCount": count, "endFrameExclusive": start + count,
                         "windowOffsetNanos": start * 1_000_000_000 // rate,
                         "durationNanos": count * 1_000_000_000 // rate,
                         "clockBasis": "Exact source sample frames; displayed nanoseconds are rounded down."},
                "derived": {"file": filename, "sha256": digest(derived), "byteCount": len(derived),
                            "pcmSha256": digest(trimmed_pcm), "sampleRateHz": rate,
                            "channelCount": channels, "sampleWidthBytes": 2, "frameCount": count},
                "signal": signal_metrics(trimmed_pcm),
                "matcherDuration": {"analysisVersion": "spectral-sequence-v2",
                                    "minimumMono8kFrames": 2816,
                                    "meetsMinimum": count * 8_000 // rate >= 2816,
                                    "interpretation": "Necessary duration only; no quality, identity, or admission verdict."},
            })
    for index, first in enumerate(intervals):
        for second in intervals[index + 1:]:
            if {first[1], second[1]} != {"reference", "holdout"}:
                continue
            same_source_overlap = first[2] == second[2] and max(first[3], second[3]) < min(first[4], second[4])
            timed_overlap = first[6] is not None and second[6] is not None and max(first[6], second[6]) < min(first[7], second[7])
            if same_source_overlap or timed_overlap or first[8] == second[8]:
                raise ValueError(f"Reference/holdout source audio overlaps or repeats: {first[0]} / {second[0]}")
    provenance = {
        "version": VERSION, "planSha256": digest(plan_bytes),
        "sourceArchive": {"path": relative, "sha256": expected_sha, "matchId": match_id},
        "confirmation": confirmed, "samples": records,
        "processing": "Whole PCM frames copied; original rate, channels, and levels retained. WAV header rebuilt.",
        "admissionStatus": "Candidates only. No runtime reference or coverage claim is created.",
        "holdoutScope": "All samples are from one source Match; these are not independent-battle validation.",
    }
    return provenance, outputs


def import_candidates(plan_path: Path, output_dir: Path) -> dict:
    if output_dir.exists() or output_dir.is_symlink():
        raise ValueError("Output directory already exists; choose a new private directory")
    provenance, outputs = prepare_candidates(plan_path)
    outputs["provenance.json"] = (json.dumps(provenance, indent=2, sort_keys=True, allow_nan=False) + "\n").encode()
    output_dir.parent.mkdir(parents=True, exist_ok=True)
    output_dir.mkdir()  # Exclusive creation also catches a concurrent writer.
    written = []
    try:
        for name, data in outputs.items():
            target = output_dir / name
            with target.open("xb") as result:
                written.append(target)
                result.write(data)
    except OSError:
        for target in written:
            try:
                target.unlink(missing_ok=True)
            except OSError:
                pass
        try:
            output_dir.rmdir()
        except OSError:
            pass
        raise
    return provenance


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("plan", type=Path)
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args()
    try:
        result = import_candidates(args.plan, args.output_dir)
    except (OSError, ValueError, KeyError, TypeError, zipfile.BadZipFile, RuntimeError) as error:
        parser.exit(2, f"Import failed: {error}\n")
    print(f"Wrote {len(result['samples'])} candidate WAVs and provenance to {args.output_dir}")
    print("Runtime audio library unchanged; labels and duration checks are not acoustic validation.")


if __name__ == "__main__":
    main()
