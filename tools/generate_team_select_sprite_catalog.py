#!/usr/bin/env python3
"""Build the compact Team Select sprite descriptor catalogue used on-device.

The input is the PokeMiners ``Pokemon - 256x256/Addressable Assets`` folder.
Only measurements derived from the images are written; the source PNGs are not
copied into the APK.
"""

from __future__ import annotations

import argparse
import colorsys
import re
from pathlib import Path

from PIL import Image


FILE_PATTERN = re.compile(r"pm(\d+)(.*?)\.icon\.png$")
SIZE = 64


def descriptor(path: Path) -> list[float]:
    image = Image.open(path).convert("RGBA").resize((SIZE, SIZE), Image.Resampling.LANCZOS)
    hue = [0] * 24
    saturation = [0] * 8
    value = [0] * 8
    xs: list[int] = []
    ys: list[int] = []

    for y in range(6, SIZE):
        for x in range(3, 61):
            red, green, blue, alpha = image.getpixel((x, y))
            if alpha < 80:
                continue
            highest = max(red, green, blue)
            lowest = min(red, green, blue)
            if highest > 238 and highest - lowest < 18:
                continue
            h, s, v = colorsys.rgb_to_hsv(red / 255, green / 255, blue / 255)
            if s < 0.07 and v > 0.91:
                continue
            hue[min(23, int(h * 24))] += 1
            saturation[min(7, int(s * 8))] += 1
            value[min(7, int(v * 8))] += 1
            xs.append(x)
            ys.append(y)

    def normalized(values: list[int]) -> list[float]:
        total = sum(values) or 1
        return [item / total for item in values]

    count = len(xs)
    shape = [count / (58 * 58)]
    if xs:
        shape.extend(
            [
                (max(xs) - min(xs) + 1) / SIZE,
                (max(ys) - min(ys) + 1) / SIZE,
                sum(xs) / count / SIZE,
                sum(ys) / count / SIZE,
            ]
        )
    else:
        shape.extend([0.0, 0.0, 0.0, 0.0])
    return normalized(hue) + normalized(saturation) + normalized(value) + shape


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()

    records: list[tuple[int, str, Path]] = []
    for path in args.source.glob("pm*.icon.png"):
        match = FILE_PATTERN.fullmatch(path.name)
        if not match:
            continue
        variant = match.group(2)
        # Shiny is appearance evidence, not a different species. Costumes add
        # many near-duplicates and are intentionally left to their base image.
        if ".s" in variant or ".c" in variant:
            continue
        records.append((int(match.group(1)), variant.removeprefix(".") or "base", path))

    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("w", encoding="utf-8", newline="\n") as output:
        output.write("# team-select-sprite-descriptors-v1\n")
        output.write("# Pokemon GO image measurements; source artwork is not embedded here.\n")
        for species_id, variant, path in sorted(records, key=lambda item: (item[0], item[1])):
            values = ",".join(f"{value:.8f}" for value in descriptor(path))
            output.write(f"{species_id}\t{variant}\t{values}\n")

    print(f"Wrote {len(records)} descriptors to {args.output}")


if __name__ == "__main__":
    main()
