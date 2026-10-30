"""Bounded, absolute-scale PCM visualizations; never modifies playback WAV bytes.

Spectrogram columns contain 64 low-to-high frequency bands, each the strongest
FFT-bin amplitude in that band. Colors use a shared -90..0 dBFS scale. This is a
listening aid, not the recognizer's features or a probability of any move.
"""
from array import array
from io import BytesIO
import math
import sys
import wave

RATE = 8000
WINDOW = 256
ROWS = 64
MAX_COLUMNS = 240
WAVEFORM_BINS = 320
MIN_DB = -90
MAX_DB = 0
MAX_SECONDS = 30
_HANN = tuple(0.5 - 0.5 * math.cos(2 * math.pi * i / (WINDOW - 1)) for i in range(WINDOW))
_WINDOW_SUM = sum(_HANN)
_REVERSED = tuple(int(f"{i:08b}"[::-1], 2) for i in range(WINDOW))
_TWIDDLES = {
    size: tuple(complex(math.cos(-2 * math.pi * j / size), math.sin(-2 * math.pi * j / size))
                for j in range(size // 2))
    for size in (2, 4, 8, 16, 32, 64, 128, 256)
}


def _decode(data):
    try:
        with wave.open(BytesIO(data), "rb") as audio:
            frames, rate, channels = audio.getnframes(), audio.getframerate(), audio.getnchannels()
            if (audio.getcomptype() != "NONE" or audio.getsampwidth() != 2 or channels not in (1, 2)
                    or not 1000 <= rate <= 192000 or frames <= 0 or frames > rate * MAX_SECONDS):
                raise ValueError("Expected nonempty PCM16 mono/stereo WAV, at most 30 seconds")
            raw = audio.readframes(frames)
            if len(raw) != frames * channels * 2:
                raise ValueError("Incomplete PCM WAV")
    except (wave.Error, EOFError) as error:
        raise ValueError("Invalid PCM WAV") from error
    samples = array("h")
    samples.frombytes(raw)
    if sys.byteorder != "little":
        samples.byteswap()
    if channels == 1:
        mono = [sample / 32768.0 for sample in samples]
    else:
        mono = [(samples[i] + samples[i + 1]) / 65536.0 for i in range(0, len(samples), 2)]
    return mono, rate, channels


def _resample(samples, source_rate):
    """Deterministic windowed-sinc resampling with anti-alias filtering.

Eight-lobe Hann kernels share weights for recurring rational sample phases.
Samples outside the original file are zero, never an invented repeated tail.
"""
    if source_rate == RATE:
        return samples
    count = (len(samples) * RATE + source_rate - 1) // source_rate
    cutoff = 0.47 * min(1.0, RATE / source_rate)
    radius = math.ceil(8 * max(1.0, source_rate / RATE))
    kernels = {}
    result = []
    for index in range(count):
        base, phase = divmod(index * source_rate, RATE)
        kernel = kernels.get(phase)
        if kernel is None:
            fraction = phase / RATE
            kernel = []
            for offset in range(-radius + 1, radius + 1):
                distance = offset - fraction
                if abs(distance) >= radius:
                    continue
                argument = 2 * cutoff * distance
                sinc = 1.0 if argument == 0 else math.sin(math.pi * argument) / (math.pi * argument)
                weight = 2 * cutoff * sinc * (0.5 + 0.5 * math.cos(math.pi * distance / radius))
                kernel.append((offset, weight))
            total = sum(weight for _, weight in kernel)
            kernel = tuple((offset, weight / total) for offset, weight in kernel)
            kernels[phase] = kernel
        result.append(sum(samples[base + offset] * weight for offset, weight in kernel
                          if 0 <= base + offset < len(samples)))
    return result


def _spectrum(samples, start):
    # Integer sample alignment avoids the amplitude attenuation that fractional
    # linear interpolation would introduce, especially near Nyquist.
    windowed = [(samples[start + index] if start + index < len(samples) else 0.0) * weight
                for index, weight in enumerate(_HANN)]
    values = [complex(windowed[index], 0) for index in _REVERSED]
    for size, twiddles in _TWIDDLES.items():
        half = size // 2
        for begin in range(0, WINDOW, size):
            for j, twiddle in enumerate(twiddles):
                first = values[begin + j]
                second = values[begin + j + half] * twiddle
                values[begin + j] = first + second
                values[begin + j + half] = first - second
    # A bin-centered full-scale sine has amplitude 1 (0 dBFS). DC/Nyquist
    # require no doubling; a frequency band records its peak, not total power.
    amplitudes = [abs(value) * (1 if index in (0, WINDOW // 2) else 2) / _WINDOW_SUM
                  for index, value in enumerate(values[:WINDOW // 2 + 1])]
    result = []
    for row in range(ROWS):
        bins = amplitudes[row * 2:row * 2 + (3 if row == ROWS - 1 else 2)]
        amplitude = max(bins)
        db = max(MIN_DB, min(MAX_DB, 20 * math.log10(max(1e-12, amplitude))))
        result.append(round(255 * (db - MIN_DB) / (MAX_DB - MIN_DB)))
    return result


def analyze_wav(data):
    """Return JSON-ready signal data using only the standard library.

waveform bins uniformly cover the full original duration; paired values are
minimum/maximum mono samples on the absolute -1..1 PCM scale. Spectrogram data
is column-major: values[column * rows + row]. Row zero is the lowest band.
    Exact window starts are frameStartsSeconds; frameStartSeconds/StepSeconds
    give their nominal uniform placement (rounding differs by at most 1/16000 s).
    Window center = start + frameWindowSeconds / 2. paddedEndSeconds is a padding *duration*;
analysisEndSeconds is the last window's end coordinate. Audio stays unchanged.
"""
    mono, source_rate, channels = _decode(data)
    duration = len(mono) / source_rate
    count = min(WAVEFORM_BINS, len(mono))
    waveform = []
    for index in range(count):
        part = mono[index * len(mono) // count:(index + 1) * len(mono) // count]
        waveform.append([min(part), max(part)])
    samples = _resample(mono, source_rate)
    span = max(0, len(samples) - WINDOW)
    columns = min(MAX_COLUMNS, 1 + math.ceil(span / 128))
    step = span / (columns - 1) if columns > 1 else 0.0
    starts = [min(span, round(column * step)) for column in range(columns)]
    values = []
    for start in starts:
        values.extend(_spectrum(samples, start))
    analysis_end = (span + WINDOW) / RATE
    return dict(durationSeconds=duration, sampleRateHz=source_rate, channelCount=channels,
                waveform=waveform, spectrogram=dict(
                    columns=columns, rows=ROWS, values=values, valueLayout="column-major",
                    scale="peak-amplitude-dbfs", maxFrequencyHz=RATE / 2,
                    minDb=MIN_DB, maxDb=MAX_DB, frameStartSeconds=0.0,
                    frameStepSeconds=step / RATE, frameWindowSeconds=WINDOW / RATE,
                    frameStartsSeconds=[start / RATE for start in starts],
                    analysisEndSeconds=analysis_end, paddedEndSeconds=max(0.0, analysis_end - duration),
                    analysisSampleRateHz=RATE))
