#!/usr/bin/env python3
"""Audit the checked-in battle catalogue against bundled and supplied move audio.

This is an inventory, not proof that filename labels identify audible moves or
that a catalogue-listed move can currently be equipped in Pokemon GO.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import re
from collections import Counter, defaultdict
from pathlib import Path


def move_key(name: str) -> str:
    """Same uppercase/alphanumeric normalization used by the live matcher."""
    return "".join(character for character in name.upper() if character.isalnum())


def asset_key(filename: str) -> str:
    stem = re.sub(r"^\d+-\d+_", "", Path(filename).stem)
    # The suffix is an asset naming convention, not another move name.
    return move_key(stem.removesuffix("_fast"))


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def source_index(directory: Path | None) -> dict[str, list[str]]:
    result: dict[str, list[str]] = defaultdict(list)
    if directory and directory.is_dir():
        for path in sorted(directory.rglob("*.wav")):
            result[asset_key(path.name)].append(str(path.relative_to(directory)))
    return dict(result)


def classify(name: str, category: str, exact: bool, sources: list[str],
             family_references: list[str], sources_checked: bool = True) -> str:
    if exact:
        return "covered_exact_name"
    if category == "fast" and name == "Struggle":
        return "catalogue_category_review"
    if category == "fast" and name == "Transform":
        return "special_action_review"
    if " (" in name and family_references:
        return "family_reference_needs_validation"
    if name == "Vise Grip" and sources:
        return "spelling_alias_needs_review"
    if sources:
        return "source_asset_available"
    return "missing_source_asset" if sources_checked else "source_inventory_not_checked"


def audit(root: Path, source_assets: Path | None = None) -> dict:
    assets = root / "app/src/main/assets"
    catalogue_path = assets / "gamemaster.json"
    catalogue = json.loads(catalogue_path.read_text())
    moves = {move["moveId"]: move for move in catalogue["moves"]}
    supplied = source_index(source_assets)
    result = {
        "scope": "Checked-in gamemaster move IDs referenced by species/form fastMoves or chargedMoves; not a live-game completeness claim.",
        "catalogue_timestamp": catalogue.get("timestamp"),
        "catalogue_sha256": sha256(catalogue_path),
        "species_form_entries": len(catalogue["pokemon"]),
        "supplied_assets_checked": bool(source_assets and source_assets.is_dir()),
        "categories": {},
    }
    referenced_ids: set[str] = set()
    for category, field, directory_name in (
        ("fast", "fastMoves", "fast_move_sounds"),
        ("charged", "chargedMoves", "charged_move_sounds"),
    ):
        directory = assets / directory_name
        manifest_path = directory / "manifest.json"
        references = json.loads(manifest_path.read_text())["references"]
        by_name: dict[str, list[dict]] = defaultdict(list)
        unusable = []
        for reference in references:
            path = directory / reference["file"]
            if not path.is_file() or sha256(path) != reference["sha256"]:
                unusable.append(reference["file"])
                continue
            by_name[move_key(reference["moveName"])].append(reference)
        groups: dict[str, dict] = {}
        unresolved = set()
        for pokemon in catalogue["pokemon"]:
            for identifier in pokemon.get(field, []):
                referenced_ids.add(identifier)
                move = moves.get(identifier)
                if move is None:
                    unresolved.add(identifier)
                    continue
                key = move_key(move["name"])
                group = groups.setdefault(key, {"name": move["name"], "moves": {}, "pokemon": {}})
                group["moves"][identifier] = move
                group["pokemon"][pokemon["speciesName"]] = pokemon
        rows = []
        for key, group in sorted(groups.items(), key=lambda pair: pair[1]["name"]):
            name = group["name"]
            family = name.split(" (")[0]
            family_key = move_key(family)
            exact_references = by_name.get(key, [])
            family_references = by_name.get(family_key, []) if family_key != key else []
            sources = supplied.get(key, [])
            alias_sources = []
            if not sources and name == "Vise Grip":
                alias_sources = supplied.get(move_key("Vice Grip"), [])
            family_sources = supplied.get(family_key, []) if family_key != key else []
            # Prefer examples with a released, ordinary form; retain all examples in JSON.
            examples = sorted(group["pokemon"].values(), key=lambda pokemon: (
                not pokemon.get("released", False), "(" in pokemon["speciesName"],
                pokemon["dex"], pokemon["speciesName"],
            ))
            records = list(group["moves"].values())
            rows.append({
                "name": name,
                "move_ids": sorted(group["moves"]),
                "types": sorted({move["type"] for move in records}),
                "turns": sorted({move.get("turns", 0) for move in records}) if category == "fast" else [],
                "cycle_seconds": sorted({move.get("turns", 0) * 0.5 for move in records}) if category == "fast" else [],
                "power": sorted({move["power"] for move in records}),
                "energy_gain": sorted({move.get("energyGain", 0) for move in records}),
                "energy_cost": sorted({abs(move.get("energy", 0)) for move in records}),
                "exact_covered": bool(exact_references),
                "status": classify(name, category, bool(exact_references), sources + alias_sources,
                                   [reference["file"] for reference in family_references] + family_sources,
                                   result["supplied_assets_checked"]),
                "bundled_files": [reference["file"] for reference in exact_references],
                "source_files": sources,
                "alias_source_files": alias_sources,
                "family_bundled_files": [reference["file"] for reference in family_references],
                "family_source_files": family_sources,
                "example_pokemon": [pokemon["speciesName"] for pokemon in examples[:6]],
                "catalogue_species_form_count": len(examples),
                "any_released_example": any(pokemon.get("released", False) for pokemon in examples),
                "all_move_ids_unlisted": all(move.get("unlisted", False) for move in records),
            })
        counts = Counter(row["status"] for row in rows)
        result["categories"][category] = {
            "manifest_sha256": sha256(manifest_path),
            "manifest_reference_entries": len(references),
            "manifest_distinct_names": len({move_key(reference["moveName"]) for reference in references}),
            "referenced_move_ids": len({identifier for group in groups.values() for identifier in group["moves"]}),
            "unique_names": len(rows),
            "exact_covered_names": sum(row["exact_covered"] for row in rows),
            "exact_missing_names": sum(not row["exact_covered"] for row in rows),
            "status_counts": dict(sorted(counts.items())),
            "manifest_names_outside_species_pools": sorted({reference["moveName"] for reference in references
                                                           if move_key(reference["moveName"]) not in groups}),
            "unusable_manifest_files": unusable,
            "unresolved_move_ids": sorted(unresolved),
            "rows": rows,
        }
    result["catalogue_moves_outside_species_pools"] = [
        {"move_id": identifier, "name": move["name"]}
        for identifier, move in sorted(moves.items()) if identifier not in referenced_ids
    ]
    return result


def write_csv(path: Path, rows: list[dict]) -> None:
    columns = ["name", "status", "exact_covered", "move_ids", "types", "turns", "cycle_seconds", "power",
               "energy_gain", "energy_cost", "example_pokemon", "bundled_files", "source_files",
               "alias_source_files", "family_bundled_files", "family_source_files", "any_released_example",
               "all_move_ids_unlisted"]
    with path.open("w", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=columns, lineterminator="\n")
        writer.writeheader()
        for row in rows:
            writer.writerow({column: " | ".join(map(str, row[column])) if isinstance(row[column], list)
                             else row[column] for column in columns})


def write_outputs(result: dict, output: Path) -> None:
    output.mkdir(parents=True, exist_ok=True)
    (output / "move-audio-coverage.json").write_text(json.dumps(result, indent=2) + "\n")
    for category, summary in result["categories"].items():
        write_csv(output / f"{category}-move-audio-coverage.csv", summary["rows"])
        write_csv(output / f"missing-{category}-move-audio.csv",
                  [row for row in summary["rows"] if not row["exact_covered"]])


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--source-assets", type=Path,
                        default=Path.home() / "Downloads/pogo_assets-master/Sounds/Pokemon Moves")
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args()
    result = audit(args.root, args.source_assets)
    write_outputs(result, args.output_dir)
    for category, summary in result["categories"].items():
        print(f"{category}: {summary['exact_covered_names']}/{summary['unique_names']} exact covered; "
              f"{summary['exact_missing_names']} exact name gaps; {summary['status_counts']}")
    if any(summary["unusable_manifest_files"] or summary["unresolved_move_ids"]
           for summary in result["categories"].values()):
        raise SystemExit("Unusable manifest entries or unresolved catalogue move IDs need review.")


if __name__ == "__main__":
    main()
