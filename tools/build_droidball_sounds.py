#!/usr/bin/env python3
"""Build runtime copies of the three user-supplied Sonic journey effects."""
from array import array
import math
from pathlib import Path
import wave

ROOT = Path(__file__).resolve().parents[1]
RATE = 22050


def save(name, samples):
    path = ROOT / "app/src/main/res/raw" / name
    pcm = array("h", (round(max(-1, min(1, x)) * 32767) for x in samples))
    with wave.open(str(path), "wb") as output:
        output.setparams((1, 2, RATE, 0, "NONE", "not compressed"))
        output.writeframes(pcm.tobytes())
    print(f"{name}: {len(pcm) / RATE:.3f}s, {path.stat().st_size} bytes")


def convert(name):
    source = ROOT / "tools/audio_sources/droidball" / name
    with wave.open(str(source)) as wav:
        assert (wav.getframerate(), wav.getnchannels(), wav.getsampwidth()) == (44100, 2, 2)
        stereo = array("h", wav.readframes(wav.getnframes()))
    mono = [(stereo[i] + stereo[i + 1]) / 65536 for i in range(0, len(stereo), 2)]
    # Low-pass before halving the sample rate, without changing speed or pitch.
    half = 24
    taps = []
    cutoff = .225
    for k in range(-half, half + 1):
        sinc = 2 * cutoff if k == 0 else math.sin(2 * math.pi * cutoff * k) / (math.pi * k)
        taps.append(sinc * (.5 + .5 * math.cos(math.pi * k / half)))
    total = sum(taps)
    taps = [x / total for x in taps]
    samples = [sum(mono[min(len(mono) - 1, max(0, i + k))] * tap
                   for k, tap in zip(range(-half, half + 1), taps)) for i in range(0, len(mono), 2)]
    # Very short click protection at the cut boundaries, leaving the recognizable sound intact.
    fade = round(RATE * .003)
    for i in range(fade):
        samples[i] *= i / fade
        samples[-i - 1] *= i / fade
    return samples


if __name__ == "__main__":
    save("droidball_roll.wav", convert("sonic_spin.wav"))
    save("droidball_skid.wav", convert("skid.wav"))
    save("droidball_edge_impact.wav", convert("sonic_pinball_bumper.wav"))
