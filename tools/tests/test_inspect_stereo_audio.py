import hashlib
import importlib.util
import io
import json
from pathlib import Path
import struct
import tempfile
import unittest
import wave
import zipfile

spec = importlib.util.spec_from_file_location("inspect_stereo_audio", Path(__file__).parents[1] / "inspect_stereo_audio.py")
audio = importlib.util.module_from_spec(spec)
spec.loader.exec_module(audio)


def wav(left, right=None):
    stream = io.BytesIO()
    with wave.open(stream, "wb") as output:
        output.setnchannels(1 if right is None else 2)
        output.setsampwidth(2)
        output.setframerate(48_000)
        samples = left if right is None else [s for pair in zip(left, right) for s in pair]
        output.writeframes(struct.pack("<" + "h" * len(samples), *samples))
    return stream.getvalue()


class StereoInspectionTest(unittest.TestCase):
    def test_mono_is_not_promoted_to_stereo(self):
        result = audio.measure_wav(wav([1, 2, -3]))
        self.assertEqual(1, result["channels"])
        self.assertNotIn("correlation", result)

    def test_identical_and_different_channels(self):
        left = [1000, -1000, 1000, -1000]
        same = audio.measure_wav(wav(left, left))
        self.assertTrue(same["identical_channels"])
        self.assertEqual(1, same["correlation"])
        self.assertEqual(0, same["difference_rms"])
        different = audio.measure_wav(wav(left, [1000, 1000, -1000, -1000]))
        self.assertFalse(different["identical_channels"])
        self.assertEqual(0, different["correlation"])

    def test_cancellation_and_silence_are_explicit(self):
        inverse = audio.measure_wav(wav([1000, -1000], [-1000, 1000]))
        self.assertEqual(-1, inverse["correlation"])
        self.assertTrue(inverse["mono_cancels_completely"])
        silence = audio.measure_wav(wav([0, 0], [0, 0]))
        self.assertIsNone(silence["correlation"])
        self.assertFalse(silence["mono_cancels_completely"])
        json.dumps(silence, allow_nan=False)

    def test_archive_hash_and_declared_format_are_verified_without_mutation(self):
        data = wav([1000, -1000], [500, -500])
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "match.zip"
            def save(channels=2, digest=None):
                artifact = dict(relativePath="audio.wav", sha256=digest or hashlib.sha256(data).hexdigest(),
                                byteCount=len(data), mediaType="audio/wav")
                with zipfile.ZipFile(path, "w") as archive:
                    archive.writestr("manifest.json", json.dumps(dict(matchId="test", timelineEntry="timeline.json", artifacts=[artifact])))
                    payload = dict(kind="audio_captured", artifactPath="audio.wav", cueKind="FAST_MOVE_IMPACT",
                                   captureSource="PLAYBACK_POKEMON_GO", channelCount=channels, sampleRateHz=48_000)
                    archive.writestr("timeline.json", json.dumps(dict(articles=[dict(articleId="a", payload=payload)])))
                    archive.writestr("audio.wav", data)
            save()
            before = path.read_bytes()
            report = audio.inspect_archive(path)
            self.assertEqual(1, report["channel_counts"][2])
            self.assertEqual(before, path.read_bytes())
            save(channels=1)
            with self.assertRaises(ValueError):
                audio.inspect_archive(path)
            save(digest="0" * 64)
            with self.assertRaises(ValueError):
                audio.inspect_archive(path)


if __name__ == "__main__":
    unittest.main()
