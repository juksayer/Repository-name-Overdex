#!/usr/bin/env python3
"""Build portable A/B/C listening homework from unchanged .odxmatch recordings.

Uses the original saved scores and verified WAVs; never reruns recognition,
normalizes audio, relabels recordings, or changes the battle ledger. Human
annotations remain a separate exported JSON file. Python standard library only.
"""
import argparse
import base64
from datetime import datetime, timezone
import hashlib
from io import BytesIO
import json
import math
from pathlib import Path
import random
import re
import wave
import zipfile
try:
    from tools.audio_review_signal import analyze_wav
    from tools.audio_review_context import listening_guide
except ModuleNotFoundError:
    from audio_review_signal import analyze_wav
    from audio_review_context import listening_guide

VERSION = "fast-move-audio-review-v1"
REPO = Path(__file__).resolve().parents[1]
MAX_WAV_BYTES = 4 * 1024 * 1024
MAX_TIMELINE_BYTES = 64 * 1024 * 1024


def digest(data):
    return hashlib.sha256(data).hexdigest()


def file_digest(path):
    result = hashlib.sha256()
    with Path(path).open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            result.update(block)
    return result.hexdigest()


def wav_info(data):
    """Validate playable PCM without transcoding or changing its levels."""
    with wave.open(BytesIO(data)) as audio:
        frames, rate, channels = audio.getnframes(), audio.getframerate(), audio.getnchannels()
        if audio.getsampwidth() != 2 or channels not in (1, 2) or not 1_000 <= rate <= 192_000:
            raise ValueError("Unsupported PCM WAV")
        if frames <= 0 or frames / rate > 30 or len(audio.readframes(frames)) != frames * channels * 2:
            raise ValueError("Incomplete or oversized PCM WAV")
        return dict(durationSeconds=frames / rate, sampleRateHz=rate, channelCount=channels)


def audio_data(data):
    return dict(src="data:audio/wav;base64," + base64.b64encode(data).decode("ascii"),
                sha256=digest(data), **wav_info(data))


def load_references(directory):
    directory = Path(directory)
    manifest = json.loads((directory / "manifest.json").read_text())
    references = {}
    for item in manifest["references"]:
        name = item["file"]
        if Path(name).name != name:
            raise ValueError("Reference filename must stay in its source directory")
        data = (directory / name).read_bytes()
        if len(data) > MAX_WAV_BYTES or digest(data) != item["sha256"]:
            raise ValueError("Reference hash/size mismatch: " + name)
        references[(item["moveName"], item["sha256"])] = audio_data(data)
    return references


def verified_audio(package, payload):
    sha = payload.get("sha256", "")
    path = payload.get("artifactPath")
    count = payload.get("byteCount", 0)
    if (not re.fullmatch(r"[a-f0-9]{64}", sha) or
            path != f"artifacts/audio/sha256/{sha}.wav" or
            payload.get("mediaType") != "audio/wav" or not 0 < count <= MAX_WAV_BYTES):
        raise ValueError("Invalid audio reference")
    entry = package.getinfo(path)
    if entry.file_size != count:
        raise ValueError("Audio byte count differs")
    data = package.read(entry)
    if digest(data) != sha:
        raise ValueError("Audio hash differs")
    audio = audio_data(data)
    if (payload.get("sampleRateHz") != audio["sampleRateHz"] or
            payload.get("channelCount") != audio["channelCount"] or
            abs(payload.get("durationNanos", 0) / 1e9 - audio["durationSeconds"]) > 1 / audio["sampleRateHz"]):
        raise ValueError("Audio header differs from recorded format")
    return audio


def linked_audio(article, by_id):
    pending = list(article.get("evidenceReferences") or []) + list(article.get("predecessorIds") or [])
    seen = set()
    while pending and len(seen) < 512:
        ref = pending.pop(0)
        if ref in seen:
            continue
        seen.add(ref)
        source = by_id.get(ref)
        if source is None:
            continue
        if source["payload"]["kind"] == "audio_captured":
            yield source
        else:
            pending.extend(source.get("evidenceReferences") or [])
            pending.extend(source.get("predecessorIds") or [])


def time_at(article):
    return article.get("monotonicTimeNanos")


def context_at(articles, start, duration):
    """Source-time observations only. No inferred attack attribution or HP damage formula."""
    ordered = sorted((a for a in articles if time_at(a) is not None), key=time_at)
    context = {"playerSpecies": "Unresolved", "opponentSpecies": "Unresolved", "hpTicks": [], "events": []}
    midpoint = start + duration * 500_000_000
    for side, field in (("PLAYER", "playerSpecies"), ("OPPONENT", "opponentSpecies")):
        identity = [a for a in ordered if time_at(a) <= midpoint and
                    a["payload"]["kind"] == "active_pokemon_species_witnessed" and a["payload"]["side"] == side]
        if identity:
            context[field] = identity[-1]["payload"]["speciesName"]
    for article in ordered:
        at, p = time_at(article), article["payload"]
        offset = (at - start) / 1e9
        if not -2.5 <= offset <= duration + 2.5:
            continue
        if p["kind"] == "active_hp_bar_damage_tick_measured":
            side = p["damagedSide"]
            geometry = [a for a in ordered if 0 <= at - time_at(a) <= 500_000_000 and
                        a["payload"]["kind"] == "active_hp_bar_measured" and a["payload"]["side"] == side]
            g = geometry[-1] if geometry else None
            # A nearby source measurement supplies only geometry, not an invented image.
            context["hpTicks"].append(dict(side=side, timeSeconds=offset,
                beforeFraction=p["beforeFraction"], afterFraction=p["afterFraction"], lostFraction=p["lostFraction"],
                barWidthPixels=(g["payload"]["barRight"] - g["payload"]["barLeft"]) if g else None,
                articleId=article["articleId"], geometryArticleId=g["articleId"] if g else None))
        kind = p["kind"]
        label = None
        if kind == "active_pokemon_species_witnessed":
            label = "Recorded species: " + p["speciesName"]
        elif kind == "fast_move_identified":
            label = "Recorded move conclusion: " + p["moveName"]
        elif kind in ("attack_incoming", "get_ready_witnessed", "match_ended", "active_pokemon_fainted_witnessed"):
            label = kind.replace("_", " ").capitalize()
        elif kind in ("charge_move_used_announced", "charged_move_announced"):
            label = "Charged move: " + p.get("moveName", "announcement")
        if label:
            context["events"].append(dict(timeSeconds=offset, label=label, side=p.get("side")))
    context["hpTicks"] = context["hpTicks"][:16]
    context["events"] = context["events"][:12]
    return context


def candidates_for(payload, references, case_id):
    ranked = []
    for item in payload.get("referenceCandidates", []):
        numbers = (item.get("similarity"), item.get("changingSimilarity"))
        if (any(not isinstance(n, (int, float)) or not math.isfinite(n) or not 0 <= n <= 1 for n in numbers)
                or (item.get("moveName"), item.get("referenceSha256")) not in references):
            continue
        ranked.append(item)
    ranked.sort(key=lambda c: c["similarity"], reverse=True)
    if len(ranked) < 2:
        return []
    chosen = ranked[:2]
    changing = max(ranked[1:], key=lambda c: c["changingSimilarity"])
    third = changing if changing not in chosen else next((c for c in ranked if c not in chosen), None)
    if third:
        chosen.append(third)
    # The letter is deliberately unrelated to matcher rank, stable on reload/export.
    random.Random(case_id).shuffle(chosen)
    result = []
    for index, item in enumerate(chosen):
        reference = references[(item["moveName"], item["referenceSha256"])]
        result.append(dict(code=chr(65 + index), moveName=item["moveName"], referenceSha256=item["referenceSha256"],
            similarity=item["similarity"], changingSimilarity=item["changingSimilarity"],
            matchingOffsetSeconds=item.get("matchingOffsetNanos", 0) / 1e9,
            referenceOffsetSeconds=item.get("referenceOffsetNanos", 0) / 1e9,
            matchingDurationSeconds=item.get("matchedDurationNanos", 0) / 1e9, reference=reference))
    return result


def collect_cases(paths, references):
    cases, missing = [], 0
    seen_matches = set()
    for path in map(Path, paths):
        with zipfile.ZipFile(path) as package:
            if package.getinfo("timeline.json").file_size > MAX_TIMELINE_BYTES:
                raise ValueError("Timeline exceeds review limit")
            timeline = json.loads(package.read("timeline.json"))
            if timeline["matchId"] in seen_matches:
                continue
            seen_matches.add(timeline["matchId"])
            articles = [a for a in timeline["articles"] if a.get("matchId") == timeline["matchId"]]
            by_id = {a["articleId"]: a for a in articles}
            starts = [a for a in articles if a["payload"]["kind"] == "match_started" and time_at(a) is not None]
            origin_kind = "Match Start"
            if not starts:
                starts = [a for a in articles if a["payload"]["kind"] == "match_record_started" and time_at(a) is not None]
                origin_kind = "recording start"
            if not starts:
                continue
            origin = min(map(time_at, starts))
            source_sha = file_digest(path)
            for article in articles:
                p = article["payload"]
                if p["kind"] != "fast_move_sound_measured":
                    continue
                case_id = digest((timeline["matchId"] + ":" + article["articleId"]).encode())[:20]
                candidates = candidates_for(p, references, case_id)
                if len(candidates) < 2:
                    continue
                sources = list(linked_audio(article, by_id))
                if not sources:
                    missing += 1
                    continue
                source = sources[0]
                try:
                    recording = verified_audio(package, source["payload"])
                except (KeyError, ValueError, wave.Error, EOFError):
                    missing += 1
                    continue
                start = time_at(source)
                if start is None:
                    missing += 1
                    continue
                ranked = sorted(p["referenceCandidates"], key=lambda c: c["similarity"], reverse=True)
                best = ranked[0]
                gap = best["similarity"] - ranked[1]["similarity"]
                changing_lead = best["changingSimilarity"] - max(c["changingSimilarity"] for c in ranked[1:])
                reason = ("The spectrum and changing-spectrum leaders disagree." if changing_lead < 0 else
                          "Close original-spectrum scores." if gap < .008 else
                          "Strong original match; changing-spectrum separation is below the strict threshold." if changing_lead < .04 else
                          "A clearer comparison, included as a control.")
                stamp = datetime.fromtimestamp(source["perceivedAt"] / 1000, timezone.utc).isoformat()
                cases.append(dict(id=case_id, archiveName=path.name, matchId=timeline["matchId"],
                    sourceSha256=source_sha, articleId=article["articleId"], audioArticleId=source["articleId"],
                    relativeSeconds=(start - origin) / 1e9, timeOrigin=origin_kind, capturedAtUtc=stamp,
                    accepted=bool(p.get("referenceMatchAccepted")), priorityReason=reason,
                    scoreGap=gap, changingLead=changing_lead, analysisVersion=p.get("analysisVersion"),
                    candidateCoverage=f"{len(ranked)} candidates retained in this recording", recording=recording,
                    candidates=candidates, context=context_at(articles, start, recording["durationSeconds"])))
    return cases, missing


def select_cases(cases, ids, limit):
    if ids:
        by_id = {c["articleId"]: c for c in cases}
        absent = [value for value in ids if value not in by_id]
        if absent or len(ids) != len(set(ids)):
            raise ValueError("Requested comparisons are missing/unplayable or repeated: " + ", ".join(absent))
        return [by_id[value] for value in ids]
    selected, groups = [], set()
    # Prefer close/reversed comparisons; keep a bounded variety of battles and sounds.
    for case in sorted(cases, key=lambda c: (c["accepted"], c["changingLead"] >= 0, c["scoreGap"])):
        group = (case["matchId"], tuple(sorted(c["moveName"] for c in case["candidates"])))
        if group in groups:
            continue
        selected.append(case)
        groups.add(group)
        if len(selected) >= limit:
            break
    return selected


def case_metadata(case):
    """Portable source and context metadata, without embedding the WAVs again."""
    return {**{k: v for k, v in case.items() if k not in ("recording", "candidates")},
        "recording": {k: v for k, v in case["recording"].items() if k not in ("src", "signal")},
        "candidates": [{**{k: v for k, v in item.items() if k != "reference"},
            "reference": {k: v for k, v in item["reference"].items() if k not in ("src", "signal")}}
            for item in case["candidates"]]}


def attach_signal_previews(cases, guides=None):
    """Analyze only the selected media. Derived plots never change review identity."""
    cache = {}
    media_items = [media for case in cases for media in
                   [case["recording"], *(c["reference"] for c in case["candidates"])]]
    media_items.extend(item["reference"] for guide in (guides or {}).values()
                       for item in guide["additionalReferences"])
    for media in media_items:
        sha = media["sha256"]
        if sha not in cache:
            source = base64.b64decode(media["src"].split(",", 1)[1], validate=True)
            if digest(source) != sha:
                raise ValueError("Signal source hash differs")
            cache[sha] = analyze_wav(source)
        media["signal"] = cache[sha]


def make_listening_guides(cases, paths, references, human_context=None, catalogue_path=None):
    """Supplement original comparisons with sourced context; never rewrite their IDs or scores."""
    catalogue_path = Path(catalogue_path or REPO / "app/src/main/assets/gamemaster.json")
    catalogue_bytes = catalogue_path.read_bytes()
    catalogue = json.loads(catalogue_bytes)
    move_names = {row["moveId"]: row["name"] for row in catalogue["moves"]}
    normalize = lambda value: re.sub(r"[^a-z0-9]", "", str(value or "").casefold())
    species = {normalize(row["speciesName"]): row for row in catalogue["pokemon"]}
    archives = {}
    for path in map(Path, paths):
        with zipfile.ZipFile(path) as package:
            if package.getinfo("timeline.json").file_size > MAX_TIMELINE_BYTES:
                raise ValueError("Timeline exceeds review limit")
            timeline = json.loads(package.read("timeline.json"))
        archives[timeline["matchId"]] = (path, timeline, file_digest(path))
    result = {}
    for case in cases:
        path, timeline, archive_sha = archives[case["matchId"]]
        if archive_sha != case["sourceSha256"]:
            raise ValueError("Listening guide archive differs from original comparison")
        articles = [row for row in timeline["articles"] if row.get("matchId") == case["matchId"]]
        by_id = {row["articleId"]: row for row in articles}
        source = by_id[case["audioArticleId"]]
        with zipfile.ZipFile(path) as package:
            if verified_audio(package, source["payload"])["sha256"] != case["recording"]["sha256"]:
                raise ValueError("Listening guide audio differs from original comparison")
        start = time_at(source)
        guide = listening_guide(articles, start, case["recording"]["durationSeconds"],
                                (human_context or {}).get(case["articleId"]))
        plan = ((human_context or {}).get(case["articleId"]) or {}).get("reviewTask")
        if plan and (plan.get("status") not in ("needs_review", "diagnostic") or not isinstance(plan.get("reason"), str)):
            raise ValueError("Listening review plan must declare its status and reason")
        guide["reviewTask"] = plan or dict(status="diagnostic", reason="Not yet audited as an unresolved opponent-move assignment.")
        pools = {}
        for side in ("player", "opponent"):
            identity = guide[side]
            row = species.get(normalize(identity["speciesName"]))
            changed = False
            for article in articles:
                p = article["payload"]
                if not (start < (time_at(article) or 0) < start + case["recording"]["durationSeconds"] * 1e9 and p.get("side") == side.upper()):
                    continue
                if (p.get("kind") == "active_pokemon_species_witnessed" and
                    normalize(p.get("speciesName")) != normalize(identity["speciesName"])) or (
                    p.get("kind") == "active_pokemon_fainted_witnessed" and
                    (not p.get("speciesName") or normalize(p["speciesName"]) == normalize(identity["speciesName"]))):
                    changed = True
            names = [move_names[move] for move in row["fastMoves"] if move in move_names] if row else []
            pools[side] = dict(resolved=bool(row and names and not changed),
                               moveNames=names if not changed else [])
        guide["legalMoves"] = pools
        guide["catalogue"] = dict(sha256=digest(catalogue_bytes), timestamp=catalogue.get("timestamp"))
        guide["source"] = dict(matchId=case["matchId"], archiveSha256=case["sourceSha256"],
                               audioArticleId=case["audioArticleId"], audioSha256=case["recording"]["sha256"])
        known_player = guide["player"].get("moveName") if guide["player"]["status"] in ("configured", "user_confirmed") else None
        guide["candidateRoles"] = {}
        for candidate in case["candidates"]:
            name = candidate["moveName"]
            role = ("known_player" if name == known_player else
                    "legal_opponent" if pools["opponent"]["resolved"] and name in pools["opponent"]["moveNames"] else
                    "possible_player" if pools["player"]["resolved"] and name in pools["player"]["moveNames"] else
                    "outside_both_movepools" if all(p["resolved"] for p in pools.values()) else "unassigned")
            guide["candidateRoles"][candidate["code"]] = role
        supplemental = list(pools["opponent"]["moveNames"]) if pools["opponent"]["resolved"] else []
        if known_player:
            supplemental.append(known_player)
        guide["additionalReferences"], guide["missingReferences"] = [], []
        original_names = {c["moveName"] for c in case["candidates"]}
        for name in dict.fromkeys(supplemental):
            if name in original_names:
                continue
            choices = sorted((sha, media) for (move, sha), media in references.items() if move == name)
            if not choices:
                guide["missingReferences"].append(name)
                continue
            sha, media = choices[0]
            guide["additionalReferences"].append(dict(moveName=name, code="legal:" + name,
                role="known_player" if name == known_player else "legal_opponent", reference=media))
        # Bind the supplemental knowledge separately, keeping original answers portable.
        fingerprint = {**guide, "additionalReferences": [{**{k: v for k, v in item.items() if k != "reference"},
            "referenceSha256": item["reference"]["sha256"]} for item in guide["additionalReferences"]]}
        guide["revision"] = digest(json.dumps(fingerprint, sort_keys=True).encode())
        result[case["id"]] = guide
    return result


def build_review(cases, output, references, missing=0, available=0, listening_guides=None):
    if not cases:
        raise ValueError("No playable saved comparisons found")
    signature = [case_metadata(c) for c in cases]
    review_id = digest(json.dumps(signature, sort_keys=True).encode())[:20]
    attach_signal_previews(cases, listening_guides)
    data = dict(version=VERSION, reviewSetId=review_id,
        createdAtUtc=datetime.now(timezone.utc).isoformat(),
        summary=dict(caseCount=len(cases), archiveCount=len({c["matchId"] for c in cases}),
            availableComparisons=available, skippedMissingMedia=missing, referenceFiles=len(references),
            namedMoves=len({name for name, _ in references}),
            missingNamedReferences=[name for name in ("Bubble", "Fire Fang") if name not in {n for n, _ in references}],
            notes="Selected retained comparisons, not a recall or accuracy benchmark."),
        cases=cases, listeningGuides=listening_guides or {})
    encoded = json.dumps(data, ensure_ascii=True, separators=(",", ":")).replace("<", "\\u003c")
    template = (REPO / "tools/templates/fast_move_audio_review.html").read_text()
    if template.count("__REVIEW_DATA_JSON__") != 1:
        raise ValueError("Review template needs exactly one data marker")
    for marker, filename in (("__AUDIO_SIGNAL_JS__", "audio_review_signal.js"),
                             ("__AUDIO_PLAYBACK_JS__", "audio_review_playback.js")):
        script = (REPO / "tools/templates" / filename).read_text()
        if template.count(marker) != 1 or "</script" in script.lower():
            raise ValueError("Review script marker or inline script is unsafe")
        template = template.replace(marker, script)
    output = Path(output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(template.replace("__REVIEW_DATA_JSON__", encoded))
    return data


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archives", nargs="+", type=Path)
    parser.add_argument("--references", type=Path, default=REPO / "app/src/main/assets/fast_move_sounds")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--article-id", action="append", default=[], help="Curated source Article; repeat to retain review order")
    parser.add_argument("--limit", type=int, default=12)
    parser.add_argument("--listening-context", type=Path, help="Optional separately sourced human context keyed by sound Article ID")
    args = parser.parse_args()
    if not 1 <= args.limit <= 40:
        parser.error("--limit must be 1 through 40")
    references = load_references(args.references)
    all_cases, missing = collect_cases(args.archives, references)
    cases = select_cases(all_cases, args.article_id, args.limit)
    human_context = json.loads(args.listening_context.read_text()) if args.listening_context else None
    guides = make_listening_guides(cases, args.archives, references, human_context)
    data = build_review(cases, args.output, references, missing, len(all_cases), guides)
    print(json.dumps(dict(path=str(args.output), reviewSetId=data["reviewSetId"], **data["summary"])))


if __name__ == "__main__":
    main()
