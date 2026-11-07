#!/usr/bin/env python3
"""Describe archived HP locations; never learn or apply production presets.

Only confirmed source-time identities label samples. All phase labels remain
UNKNOWN: a measured rectangle alone cannot establish ordinary play versus QTE.
"""
from __future__ import annotations

import argparse
from collections import Counter, defaultdict
import hashlib
import json
import math
from pathlib import Path
import zipfile

SIDES = ("PLAYER", "OPPONENT")
HP_CROPS = {"PlayerHpEvidenceCrop": "PLAYER", "OpponentHpEvidenceCrop": "OPPONENT"}
BOUNDARY_REASONS = {"ENTRY", "SWITCH", "EMPTY_HP"}
SWITCH_KINDS = {"active_pokemon_switched", "pokemon_switched", "switch_witnessed"}


def integer(value):
    return isinstance(value, int) and not isinstance(value, bool)


def source_time(article):
    value = article.get("monotonicTimeNanos")
    return value if integer(value) else None


def fingerprint(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, separators=(",", ":")).encode()).hexdigest()


def species_key(payload):
    name = payload.get("speciesName")
    if not isinstance(name, str) or not name.strip():
        return None
    identifier = payload.get("speciesId")
    return (identifier if integer(identifier) else None, name.strip())


def identity_signature(identity):
    if identity is None:
        return None
    identifier, name = species_key(identity["payload"])
    return identifier, name.casefold()


def valid_bounds(bounds, dimensions):
    if not all(integer(value) for value in (*bounds, *dimensions)):
        return False
    left, top, right, bottom = bounds
    width, height = dimensions
    return width > 0 and height > 0 and 0 <= left < right <= width and 0 <= top < bottom <= height


def calibration_context(articles, dimensions, archive_hash):
    """Only fixed HP evidence-crop provenance, never measured bar positions."""
    rows = {side: defaultdict(list) for side in SIDES}
    for article in articles:
        payload = article.get("payload") or {}
        side = HP_CROPS.get(payload.get("cropName"))
        if payload.get("kind") != "crop_captured" or side is None:
            continue
        size = payload.get("sourceWidth"), payload.get("sourceHeight")
        bounds = tuple(payload.get("crop" + edge) for edge in ("Left", "Top", "Right", "Bottom"))
        if size == dimensions and valid_bounds(bounds, size):
            rows[side][bounds].append(article.get("articleId"))
    regions = {}
    citations = {}
    conflicting = False
    for side in SIDES:
        conflicting |= len(rows[side]) > 1
        regions[side] = list(next(iter(rows[side]))) if len(rows[side]) == 1 else None
        citations[side] = sorted({identifier for ids in rows[side].values() for identifier in ids if identifier})
    complete = all(regions.values()) and not conflicting
    status = ("CONFLICTING_HP_REGION_PROVENANCE" if conflicting else
              "COMPLETE_HP_REGION_PROVENANCE" if complete else
              "PARTIAL_HP_REGION_PROVENANCE" if any(regions.values()) else "UNAVAILABLE")
    evidence = {"source_dimensions": list(dimensions), "hp_regions": regions}
    return {
        "status": status,
        "hp_regions": regions,
        "hp_region_fingerprint": fingerprint(evidence) if any(regions.values()) and not conflicting else None,
        "full_calibration_fingerprint": None,
        "basis": "Exact archived HP crop bounds; not a full saved calibration/profile snapshot.",
        "provenance_article_ids": citations,
        # Incomplete contexts cannot pool unknown calibrations across recordings.
        "unknown_context_archive_scope": None if complete else archive_hash,
    }


def boundary_events(article):
    payload = article.get("payload") or {}
    kind = payload.get("kind")
    at = source_time(article)
    if at is None:
        return []
    if kind in {"match_record_started", "match_ended", "observation_session_stopped_by_user", "team_select_party"}:
        return [(at, side, article) for side in SIDES]
    side = payload.get("side")
    if side not in SIDES:
        return []
    if kind == "species_check_measured" and payload.get("status") == "OPENED" and payload.get("reason") in BOUNDARY_REASONS:
        trigger = payload.get("triggerMonotonicNanos")
        return [(trigger if integer(trigger) and trigger <= at else at, side, article)]
    if kind == "active_pokemon_fainted_witnessed" or kind in SWITCH_KINDS:
        return [(at, side, article)]
    return []


def archive_samples(document, archive_hash, max_identity_age_nanos):
    articles = document["articles"]
    match_id = document.get("matchId")
    ended_at = min((source_time(row) for row in articles if source_time(row) is not None and
                    (row.get("payload") or {}).get("kind") == "match_ended"), default=None)
    groups = defaultdict(lambda: {"identities": defaultdict(list), "boundaries": defaultdict(list), "hp": []})
    skips = Counter()
    for article in articles:
        payload = article.get("payload") or {}
        at = source_time(article)
        kind, side = payload.get("kind"), payload.get("side")
        if match_id and article.get("matchId", match_id) != match_id:
            if kind == "active_hp_bar_measured":
                skips["MISMATCHED_MATCH_ID"] += 1
            continue
        if kind == "active_hp_bar_measured":
            if at is None:
                skips["HP_WITHOUT_MONOTONIC_SOURCE_TIME"] += 1
            elif ended_at is not None and at >= ended_at:
                skips["AT_OR_AFTER_MATCH_END"] += 1
            else:
                groups[at]["hp"].append(article)
        if kind == "active_pokemon_species_witnessed" and side in SIDES and at is not None:
            # Configured/lead inference is not a witnessed location label.
            if species_key(payload) and "INFERENCE" not in article.get("sourceId", ""):
                groups[at]["identities"][side].append(article)
        for when, affected_side, event in boundary_events(article):
            groups[when]["boundaries"][affected_side].append(event)

    active = {}
    contexts = {}
    samples = []
    for at, group in sorted(groups.items()):
        blocked = set()
        for side, events in group["boundaries"].items():
            effective = []
            for event in events:
                payload = event["payload"]
                # A delayed, explicitly named old faint cannot retire a new,
                # differently named combatant whose own identity was witnessed.
                if payload["kind"] == "active_pokemon_fainted_witnessed" and active.get(side):
                    named = species_key(payload)
                    if named and named[1].casefold() != active[side]["payload"]["speciesName"].strip().casefold():
                        continue
                effective.append(event)
            if effective:
                blocked.add(side)
                active.pop(side, None)
        next_identities = {}
        for side, identities in group["identities"].items():
            signatures = {identity_signature(identity) for identity in identities}
            if len(signatures) != 1:
                blocked.add(side)
                active.pop(side, None)
                continue
            identity = sorted(identities, key=lambda row: row.get("articleId", ""))[0]
            if identity_signature(active.get(side)) != identity_signature(identity):
                blocked.add(side)  # No source ordering can resolve a same-timestamp identity change.
            next_identities[side] = identity

        for article in group["hp"]:
            payload = article["payload"]
            side = payload.get("side")
            if side not in SIDES:
                skips["INVALID_SIDE"] += 1
                continue
            if side in blocked:
                skips["SAME_TIMESTAMP_IDENTITY_OR_BOUNDARY"] += 1
                continue
            identity = active.get(side)
            if identity is None:
                skips["NO_UNAMBIGUOUS_ACTIVE_SPECIES"] += 1
                continue
            if at - source_time(identity) > max_identity_age_nanos:
                skips["STALE_IDENTITY_CONFIRMATION"] += 1
                continue
            dimensions = payload.get("sourceWidth"), payload.get("sourceHeight")
            bounds = tuple(payload.get("bar" + edge) for edge in ("Left", "Top", "Right", "Bottom"))
            if not valid_bounds(bounds, dimensions) or not article.get("articleId"):
                skips["INVALID_OR_UNSOURCED_GEOMETRY"] += 1
                continue
            if dimensions not in contexts:
                contexts[dimensions] = calibration_context(articles, dimensions, archive_hash)
            context = contexts[dimensions]
            if context["status"] == "CONFLICTING_HP_REGION_PROVENANCE":
                skips["AMBIGUOUS_CALIBRATION_BOUNDS"] += 1
                continue
            species_id, name = species_key(identity["payload"])
            samples.append({
                "match_id": article.get("matchId", document.get("matchId")),
                "hp_article_id": article["articleId"],
                "source_monotonic_nanos": at,
                "source_wall_time_millis": article.get("perceivedAt"),
                "side": side,
                "species_id": species_id,
                "species_name": name,
                "source_dimensions": list(dimensions),
                "bounds": list(bounds),
                "phase": "UNKNOWN",
                "identity_article_id": identity["articleId"],
                "identity_source_monotonic_nanos": source_time(identity),
                "identity_source_id": identity.get("sourceId"),
                "identity_confidence": identity.get("confidence"),
                "identity_evidence_article_ids": identity.get("evidenceReferences") or [],
                "archive_sha256s": [archive_hash],
                "calibration": context,
            })
        active.update(next_identities)
    return samples, skips


def location_profile(samples, bin_size):
    sample = samples[0]
    edges = list(zip(*(row["bounds"] for row in samples)))
    bins = defaultdict(list)
    for row in samples:
        left, top, right, bottom = row["bounds"]
        bins[((left + right) // (2 * bin_size), (top + bottom) // (2 * bin_size))].append(row)
    ranked = sorted(bins.items(), key=lambda item: (-len(item[1]), item[0]))
    maximum = len(ranked[0][1])
    all_ids = sorted({identifier for row in samples for identifier in
                     [row["hp_article_id"], row["identity_article_id"], *row["identity_evidence_article_ids"]]})
    calibration = dict(sample["calibration"])
    calibration["provenance_article_ids"] = {
        side: sorted({identifier for row in samples for identifier in row["calibration"]["provenance_article_ids"][side]})
        for side in SIDES
    }
    by_match = defaultdict(list)
    for row in samples:
        by_match[row["match_id"]].append(row)
    return {
        "species_id": sample["species_id"], "species_name": sample["species_name"], "side": sample["side"],
        "source_dimensions": sample["source_dimensions"], "calibration": calibration,
        "phase": "UNKNOWN", "sample_count": len(samples),
        "observed_extent": [min(edges[0]), min(edges[1]), max(edges[2]), max(edges[3])],
        "edge_ranges": {edge: [min(values), max(values)] for edge, values in zip(("left", "top", "right", "bottom"), edges)},
        "modal_position": {
            "anchor": "BAR_CENTER", "bin_size_pixels": bin_size, "binning": "floor(center / bin_size)",
            "weighting": "One per unique archived HP Article; not time-weighted residence probability.",
            "maximum_bin_sample_count": maximum, "tied_mode_count": sum(len(rows) == maximum for _, rows in ranked),
            "bins": [{"center_x_range": [x * bin_size, (x + 1) * bin_size],
                      "center_y_range": [y * bin_size, (y + 1) * bin_size],
                      "upper_bounds_exclusive": True, "sample_count": len(rows), "is_mode": len(rows) == maximum,
                      "hp_article_ids": [row["hp_article_id"] for row in rows]} for (x, y), rows in ranked],
        },
        "archive_sha256s": sorted({digest for row in samples for digest in row["archive_sha256s"]}),
        "source_time_ranges_by_match": [{
            "match_id": match_id,
            "source_monotonic_time_range_nanos": [min(row["source_monotonic_nanos"] for row in rows), max(row["source_monotonic_nanos"] for row in rows)],
            "hp_article_ids": [row["hp_article_id"] for row in rows],
            "archive_sha256s": sorted({digest for row in rows for digest in row["archive_sha256s"]}),
        } for match_id, rows in sorted(by_match.items())],
        "cited_article_ids": all_ids,
        "samples": sorted(samples, key=lambda row: (row["match_id"], row["source_monotonic_nanos"], row["hp_article_id"])),
        "production_preset": None,
    }


def analyze(paths, bin_size=16, identity_max_age_seconds=5.0):
    if not integer(bin_size) or not 1 <= bin_size <= 128:
        raise ValueError("bin_size must be an integer from 1 through 128")
    if not math.isfinite(identity_max_age_seconds) or identity_max_age_seconds <= 0:
        raise ValueError("identity_max_age_seconds must be positive and finite")
    skips = Counter()
    archives = []
    candidates = defaultdict(list)
    seen_archives = set()
    for path in sorted(map(Path, paths), key=str):
        with path.open("rb") as stream:
            digest = hashlib.file_digest(stream, "sha256").hexdigest()
        if digest in seen_archives:
            skips["DUPLICATE_ARCHIVE_INPUT"] += 1
            continue
        seen_archives.add(digest)
        with zipfile.ZipFile(path) as package:
            document = json.loads(package.read("timeline.json"))
        if not isinstance(document.get("articles"), list):
            raise ValueError(f"No article list in {path}")
        samples, rejected = archive_samples(document, digest, int(identity_max_age_seconds * 1_000_000_000))
        skips.update(rejected)
        archives.append({"path": str(path.resolve()), "sha256": digest, "match_id": document.get("matchId"),
                         "article_count": len(document["articles"]), "candidate_samples": len(samples),
                         "skipped_samples": dict(sorted(rejected.items()))})
        for row in samples:
            candidates[(row["match_id"], row["hp_article_id"])].append(row)
    groups = defaultdict(list)
    for copies in candidates.values():
        signatures = {fingerprint({key: row[key] for key in ("side", "species_id", "species_name", "source_dimensions", "bounds", "source_monotonic_nanos")}) for row in copies}
        if len(signatures) != 1:
            skips["CONFLICTING_DUPLICATE_ARTICLE"] += len(copies)
            continue
        # Prefer a complete exact HP context when the same Article is present
        # in both full and compact exports; never count it twice.
        copies.sort(key=lambda row: (row["calibration"]["status"] != "COMPLETE_HP_REGION_PROVENANCE", row["archive_sha256s"][0]))
        row = copies[0]
        complete = {copy["calibration"]["hp_region_fingerprint"] for copy in copies if copy["calibration"]["status"] == "COMPLETE_HP_REGION_PROVENANCE"}
        if len(complete) > 1:
            skips["CONFLICTING_DUPLICATE_CALIBRATION"] += len(copies)
            continue
        row["archive_sha256s"] = sorted({digest for copy in copies for digest in copy["archive_sha256s"]})
        skips["DUPLICATE_HP_ARTICLE"] += len(copies) - 1
        context = row["calibration"]
        key = fingerprint({**{key: row[key] for key in ("side", "species_id", "species_name", "source_dimensions", "phase")},
                           "hp_region_fingerprint": context["hp_region_fingerprint"], "calibration_status": context["status"],
                           "unknown_context_archive_scope": context["unknown_context_archive_scope"]})
        groups[key].append(row)
    profiles = [location_profile(rows, bin_size) for _, rows in sorted(groups.items())]
    return {
        "schema_version": 1,
        "scope": "Archived measurement-location evidence only; no app settings or production presets are changed.",
        "phase_policy": "All samples remain UNKNOWN; an HP rectangle alone does not identify ordinary play or QTE.",
        "identity_policy": {"maximum_confirmation_age_seconds": identity_max_age_seconds,
                            "same_timestamp_change": "ABSTAIN", "configured_lead_inference": "EXCLUDED",
                            "boundaries": "Witnessed faint/entry/empty-HP/switch; entry trigger source time when available."},
        "calibration_policy": "Partition exact fixed HP crop provenance by source size; incomplete/unknown context stays archive-scoped. No full calibration fingerprint is inferred.",
        "trust_boundary": "Recorded geometry/identity may themselves be wrong or occluded. Modal position is not an ordinary-play anchor or a validated preset.",
        "archives": archives, "accepted_samples": sum(row["sample_count"] for row in profiles),
        "skipped_samples": dict(sorted((key, count) for key, count in skips.items() if count)),
        "profile_count": len(profiles), "profiles": profiles,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archives", nargs="+", type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--bin-size", type=int, default=16)
    parser.add_argument("--identity-max-age-seconds", type=float, default=5.0)
    args = parser.parse_args()
    if args.output.resolve() in {path.resolve() for path in args.archives}:
        parser.error("The output must be separate from every source archive.")
    result = analyze(args.archives, args.bin_size, args.identity_max_age_seconds)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2, allow_nan=False) + "\n")
    print(f"{result['accepted_samples']} source-time samples; {result['profile_count']} location evidence profiles.")
    print("All phases UNKNOWN; no ordinary-play home position or production preset learned.")
    print(args.output.resolve())


if __name__ == "__main__":
    main()
