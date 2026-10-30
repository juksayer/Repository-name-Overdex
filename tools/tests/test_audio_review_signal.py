from array import array
from io import BytesIO
import importlib.util
import math
from pathlib import Path
import sys
import unittest
import wave

spec = importlib.util.spec_from_file_location("audio_review_signal", Path(__file__).parents[1] / "audio_review_signal.py")
signal = importlib.util.module_from_spec(spec)
spec.loader.exec_module(signal)


def wav(samples, rate=8000, channels=1, width=2):
    values = array("h", samples)
    if sys.byteorder != "little":
        values.byteswap()
    out = BytesIO()
    with wave.open(out, "wb") as audio:
        audio.setnchannels(channels)
        audio.setsampwidth(width)
        audio.setframerate(rate)
        audio.writeframes(values.tobytes())
    return out.getvalue()


def tone(amplitude=0.5, rate=8000, frequency=1000, seconds=0.2):
    return [round(32767 * amplitude * math.sin(2 * math.pi * frequency * i / rate))
            for i in range(round(rate * seconds))]


def column(spectrogram, index):
    return spectrogram["values"][index * spectrogram["rows"]:(index + 1) * spectrogram["rows"]]


def db(value):
    return -90 + value / 255 * 90


class AudioReviewSignalTest(unittest.TestCase):
    def test_one_khz_peak_has_correct_frequency_and_source_time(self):
        samples = [0] * 800 + tone(seconds=0.1) + [0] * 800
        raw = wav(samples)
        result = signal.analyze_wav(raw)
        spectrum = result["spectrogram"]
        self.assertEqual(raw, wav(samples))
        self.assertEqual(0.3, result["durationSeconds"])
        self.assertEqual("column-major", spectrum["valueLayout"])
        centers = [start + 0.016 for start in spectrum["frameStartsSeconds"]]
        middle = min(range(len(centers)), key=lambda i: abs(centers[i] - 0.15))
        bands = column(spectrum, middle)
        self.assertEqual(16, bands.index(max(bands)))  # 1000..1062.5 Hz band
        self.assertAlmostEqual(-6.02, db(bands[16]), delta=0.4)
        self.assertEqual(0, max(column(spectrum, 0)))
        self.assertEqual(0, max(column(spectrum, spectrum["columns"] - 1)))
        self.assertAlmostEqual(0.3, spectrum["analysisEndSeconds"])

    def test_stereo_is_equally_averaged_and_opposite_channels_cancel(self):
        samples = tone()
        stereo = [value for sample in samples for value in (sample, -sample)]
        result = signal.analyze_wav(wav(stereo, channels=2))
        self.assertEqual(2, result["channelCount"])
        self.assertTrue(all(pair == [0, 0] for pair in result["waveform"]))
        self.assertTrue(all(value == 0 for value in result["spectrogram"]["values"]))

    def test_absolute_scale_preserves_a_twenty_db_amplitude_difference(self):
        loud = signal.analyze_wav(wav(tone(amplitude=0.8)))
        quiet = signal.analyze_wav(wav(tone(amplitude=0.08)))
        loud_peak = max(column(loud["spectrogram"], 2))
        quiet_peak = max(column(quiet["spectrogram"], 2))
        self.assertAlmostEqual(20, db(loud_peak) - db(quiet_peak), delta=0.5)
        self.assertAlmostEqual(10, max(p[1] for p in loud["waveform"]) /
                               max(p[1] for p in quiet["waveform"]), delta=0.01)

    def test_resampling_preserves_one_khz_and_rejects_alias_from_seven_khz(self):
        reference = signal.analyze_wav(wav(tone()))["spectrogram"]
        resampled = signal.analyze_wav(wav(tone(rate=48000), rate=48000))["spectrogram"]
        aliased = signal.analyze_wav(wav(tone(rate=48000, frequency=7000), rate=48000))["spectrogram"]
        self.assertEqual(16, column(resampled, 3).index(max(column(resampled, 3))))
        self.assertAlmostEqual(db(max(column(reference, 3))), db(max(column(resampled, 3))), delta=0.5)
        self.assertLess(db(max(column(aliased, 3))), -60)

    def test_full_file_waveform_keeps_first_and_last_peaks_and_short_padding(self):
        samples = [-32768] + [0] * 798 + [32767]
        result = signal.analyze_wav(wav(samples))
        self.assertEqual(-1, result["waveform"][0][0])
        self.assertEqual(32767 / 32768, result["waveform"][-1][1])
        self.assertEqual(320, len(result["waveform"]))
        short = signal.analyze_wav(wav([1000] * 8))
        self.assertEqual(8, len(short["waveform"]))
        self.assertEqual(0.001, short["durationSeconds"])
        spectrum = short["spectrogram"]
        self.assertEqual(1, spectrum["columns"])
        self.assertEqual(0, spectrum["frameStepSeconds"])
        self.assertEqual(0.032, spectrum["analysisEndSeconds"])
        self.assertAlmostEqual(0.031, spectrum["paddedEndSeconds"])

    def test_column_cap_reaches_the_end_of_a_long_clip(self):
        result = signal.analyze_wav(wav([0] * (8000 * 5 + 37)))
        spectrum = result["spectrogram"]
        self.assertEqual(240, spectrum["columns"])
        self.assertEqual(240 * 64, len(spectrum["values"]))
        self.assertAlmostEqual(result["durationSeconds"], spectrum["analysisEndSeconds"])
        self.assertAlmostEqual(result["durationSeconds"],
                               239 * spectrum["frameStepSeconds"] + spectrum["frameWindowSeconds"])
        self.assertEqual(0, spectrum["paddedEndSeconds"])
        self.assertEqual(spectrum["columns"], len(spectrum["frameStartsSeconds"]))
        self.assertAlmostEqual(result["durationSeconds"], spectrum["frameStartsSeconds"][-1] + 0.032)
        for i, start in enumerate(spectrum["frameStartsSeconds"]):
            self.assertAlmostEqual(start * 8000, round(start * 8000))
            self.assertAlmostEqual(start, i * spectrum["frameStepSeconds"], delta=1 / 16000 + 1e-12)

    def test_window_placement_does_not_attenuate_a_high_frequency_tone(self):
        result = signal.analyze_wav(wav(tone(frequency=3000, seconds=0.213)))
        spectrum = result["spectrogram"]
        amplitudes = [max(column(spectrum, index)) for index in range(spectrum["columns"])]
        self.assertLessEqual(max(amplitudes) - min(amplitudes), 1)
        self.assertAlmostEqual(-6.02, db(max(amplitudes)), delta=0.4)

    def test_rejects_invalid_incomplete_empty_non_pcm16_and_overlong_files(self):
        valid = wav(tone())
        for data in (b"not wav", valid[:-1], wav([]), wav([0] * 80, width=1),
                     wav([0] * 80, channels=4), wav([0] * 30001, rate=1000)):
            with self.subTest(length=len(data)), self.assertRaises(ValueError):
                signal.analyze_wav(data)


if __name__ == "__main__":
    unittest.main()
