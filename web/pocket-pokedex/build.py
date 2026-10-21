#!/usr/bin/env python3
"""Build a small, dependency-free Pokédex from Overdex's existing local assets."""

import base64
import hashlib
import json
import re
import shutil
import unicodedata
import zipfile
from collections import defaultdict
from pathlib import Path

HERE = Path(__file__).resolve().parent
ASSETS = HERE.parents[1] / "app/src/main/assets"
OUTPUT = HERE / "dist"

# These explicitly match the ordinary national-dex sprite at sprites/pokemon/{id}.png.
# Never choose the first Game Master row: it may be a regional, Shadow, or Mega form.
DEFAULT_FORMS = {
    412: "burmy_plant", 413: "wormadam_plant", 421: "cherrim_overcast",
    487: "giratina_altered", 492: "shaymin_land", 555: "darmanitan_standard",
    641: "tornadus_incarnate", 642: "thundurus_incarnate", 645: "landorus_incarnate",
    647: "keldeo_ordinary", 648: "meloetta_aria", 678: "meowstic",
    681: "aegislash_shield", 710: "pumpkaboo_average", 711: "gourgeist_average",
    741: "oricorio_baile", 745: "lycanroc_midday", 774: "minior_core",
    876: "indeedee_male", 877: "morpeko_full_belly", 888: "zacian_hero",
    889: "zamazenta_hero", 892: "urshifu_single_strike", 902: "basculegion_male",
    905: "enamorus_incarnate", 978: "tatsugiri_curly",
}
TYPES = set("normal fire water electric grass ice fighting poison ground flying psychic bug rock ghost dragon dark steel fairy".split())
REGIONS = [(151, "Kanto"), (251, "Johto"), (386, "Hoenn"), (493, "Sinnoh"),
           (649, "Unova"), (721, "Kalos"), (809, "Alola"), (898, "Galar"),
           (905, "Hisui"), (1025, "Paldea")]


def read(name):
    return json.loads((ASSETS / name).read_text(encoding="utf-8"))


def normalized(value):
    value = value.replace("♀", "female").replace("♂", "male")
    value = unicodedata.normalize("NFKD", value).encode("ascii", "ignore").decode()
    return re.sub(r"[^a-z0-9]", "", value.lower())


def script_json(value):
    # Safe both as a standalone JS file and inside the portable HTML's script element.
    return json.dumps(value, ensure_ascii=False, separators=(",", ":")).replace("<", "\\u003c")


def build():
    master = read("gamemaster.json")
    names = {row["id"]: row["species"].split(" (")[0] for row in read("battle_haiku.json")}
    descriptions = {row["id"]: row for row in read("pokemon_species.json")}
    by_dex = defaultdict(list)
    by_key = {}
    for row in master["pokemon"]:
        by_dex[row["dex"]].append(row)
        by_key[row["speciesId"]] = row

    entries = []
    sprites = {}
    for dex in range(1, 1026):
        name = names[dex]
        if dex in DEFAULT_FORMS:
            reference = by_key[DEFAULT_FORMS[dex]]
        else:
            candidates = [row for row in by_dex[dex]
                          if normalized(row["speciesId"]) == normalized(name)]
            if not candidates:
                candidates = [row for row in by_dex[dex]
                              if normalized(row["speciesName"]) == normalized(name)]
            if len(candidates) != 1:
                raise ValueError(f"#{dex} {name} needs an explicit default form: {[r['speciesId'] for r in candidates]}")
            reference = candidates[0]
        assert reference["dex"] == dex
        types = [kind for kind in reference["types"] if kind != "none"]
        assert 1 <= len(types) <= 2 and set(types) <= TYPES, (dex, types)
        stats = reference["baseStats"]
        assert all(isinstance(stats[key], int) and stats[key] > 0 for key in ("atk", "def", "hp"))
        description = descriptions[dex]
        sprite = ASSETS / f"sprites/pokemon/{dex}.png"
        if not sprite.is_file() or not sprite.read_bytes().startswith(b"\x89PNG\r\n\x1a\n"):
            raise ValueError(f"Missing or invalid sprite: {sprite}")
        sprites[str(dex)] = "data:image/png;base64," + base64.b64encode(sprite.read_bytes()).decode()
        entries.append({
            "id": dex, "name": name, "types": types,
            "region": next(region for end, region in REGIONS if dex <= end),
            "genus": description["genus"],
            "description": " ".join(description["flavor_text"].split()),
            "stats": [stats["atk"], stats["def"], stats["hp"]],
            "referenceForm": reference["speciesName"],
            "referenceId": reference["speciesId"],
            "released": reference.get("released", False),
        })

    payload = {"schemaVersion": 1, "referenceDate": master["timestamp"].split()[0], "pokemon": entries}
    data_script = "window.OVERDEX_DATA=" + script_json(payload) + ";\n"
    source = HERE / "src"
    OUTPUT.mkdir(exist_ok=True)
    sprite_output = OUTPUT / "sprites"
    sprite_output.mkdir(exist_ok=True)
    for dex in range(1, 1026):
        shutil.copyfile(ASSETS / f"sprites/pokemon/{dex}.png", sprite_output / f"{dex}.png")
    for name in ("index.html", "styles.css", "app.js", "icon.svg"):
        shutil.copyfile(source / name, OUTPUT / name)
    (OUTPUT / "data.js").write_text(data_script, encoding="utf-8")
    shutil.copyfile(ASSETS / "sprites/LICENCE.txt", OUTPUT / "SPRITES-LICENCE.txt")

    html = (source / "index.html").read_text(encoding="utf-8")
    html = html.replace('<link rel="stylesheet" href="styles.css">',
                        "<style>" + (source / "styles.css").read_text(encoding="utf-8") + "</style>")
    html = html.replace('<link rel="icon" href="icon.svg" type="image/svg+xml">', "")
    html = html.replace('<script src="data.js"></script>',
                        "<script>" + data_script + "window.OVERDEX_SPRITES=" + script_json(sprites) + ";</script>")
    html = html.replace('<script src="app.js"></script>',
                        "<script>" + (source / "app.js").read_text(encoding="utf-8") + "</script>")
    portable = OUTPUT / "overdex-pocket.html"
    portable.write_text(html, encoding="utf-8")
    with zipfile.ZipFile(OUTPUT / "overdex-pocket.zip", "w", zipfile.ZIP_DEFLATED) as bundle:
        bundle.write(portable, portable.name)
        bundle.write(OUTPUT / "SPRITES-LICENCE.txt", "SPRITES-LICENCE.txt")
        bundle.write(HERE / "README.md", "README.md")

    report = {
        "species": len(entries), "sprites": len(sprites),
        "referenceDate": payload["referenceDate"],
        "catalogBytes": len(data_script.encode()), "portableBytes": portable.stat().st_size,
        "zipBytes": (OUTPUT / "overdex-pocket.zip").stat().st_size,
        "sourceHashes": {name: hashlib.sha256((ASSETS / name).read_bytes()).hexdigest()
                         for name in ("gamemaster.json", "pokemon_species.json", "battle_haiku.json")},
        "defaultForms": {str(row["id"]): row["referenceId"] for row in entries},
    }
    (OUTPUT / "build-report.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({key: value for key, value in report.items() if key not in ("sourceHashes", "defaultForms")}, indent=2))


if __name__ == "__main__":
    build()
