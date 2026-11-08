#!/usr/bin/env python3
"""Reproduce Overdex's original soft paper-turn effect; no external samples."""

import math
import random
import struct
import wave
from pathlib import Path


def main():
    destination = Path(__file__).resolve().parents[1] / "app/src/main/res/raw/field_notes_page_turn.wav"
    rate = 44100
    duration = 0.38
    rng = random.Random(619)
    low = slow = 0.0
    samples = []
    for frame in range(round(rate * duration)):
        t = frame / rate
        noise = rng.uniform(-1.0, 1.0)
        low += 0.24 * (noise - low)
        slow += 0.025 * (noise - slow)
        # Two soft scrapes: lift the leaf, then settle it onto the next page.
        scrape = math.exp(-((t - 0.105) / 0.065) ** 2)
        settle = 0.5 * math.exp(-((t - 0.245) / 0.048) ** 2)
        edge = min(1.0, t / 0.02, (duration - t) / 0.035)
        samples.append((low - slow) * (scrape + settle) * edge)
    gain = 0.55 / max(abs(value) for value in samples)
    pcm = b"".join(struct.pack("<h", round(value * gain * 32767)) for value in samples)
    with wave.open(str(destination), "wb") as output:
        output.setnchannels(1)
        output.setsampwidth(2)
        output.setframerate(rate)
        output.writeframes(pcm)
    print(f"{destination}: {duration:.2f}s, original procedural paper rustle")


if __name__ == "__main__":
    main()
