import copy
import importlib.util
from io import BytesIO
import json
from pathlib import Path
import struct
import subprocess
import sys
import tempfile
import unittest
import warnings
import wave
import zipfile

SCRIPT = Path(__file__).resolve().parents[1] / "import_captured_fast_move_audio.py"
SPEC = importlib.util.spec_from_file_location("import_captured_fast_move_audio", SCRIPT)
importer = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(importer)


def wav_bytes(frames=4800, channels=2, rate=48000, width=2):
    output = BytesIO()
    with wave.open(output, "wb") as result:
        result.setnchannels(channels)
        result.setsampwidth(width)
        result.setframerate(rate)
        # Distinct channels and signed extremes catch downmixing, normalization,
        # byte/sample versus frame offsets, and sign changes.
        values = (-32768, 901, -71, 32767, 0, -321, 777, 3)
        pcm = b"".join(struct.pack("<h", values[index % len(values)])
                       for index in range(frames * channels)) if width == 2 else bytes(frames * channels * width)
        result.writeframes(pcm)
    return output.getvalue(), pcm


class ImportCapturedFastMoveAudioTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.plan_path = self.root / "plan.json"
        self.archive_path = self.root / "match.odxmatch.zip"
        self.output = self.root / "candidates"
        self.audio, self.pcm = wav_bytes()
        self.audio_sha = importer.digest(self.audio)
        self.artifact_path = f"artifacts/audio/sha256/{self.audio_sha}.wav"
        self.article = {
            "articleId": "audio-1", "matchId": "match-1", "perceivedAt": 1000,
            "monotonicTimeNanos": 1_000_000_000,
            "payload": {"kind": "audio_captured", "artifactPath": self.artifact_path,
                        "sha256": self.audio_sha, "mediaType": "audio/wav",
                        "byteCount": len(self.audio), "sampleRateHz": 48000,
                        "channelCount": 2, "durationNanos": 100_000_000,
                        "captureSource": "PLAYBACK", "cueKind": "FAST_MOVE"},
        }
        self.timeline = {"matchId": "match-1", "articles": [self.article]}
        self.manifest = {
            "matchId": "match-1", "timelineEntry": "timeline.json",
            "artifacts": [{"relativePath": self.artifact_path, "sha256": self.audio_sha,
                           "mediaType": "audio/wav", "byteCount": len(self.audio)}],
        }
        self.plan = {
            "version": 1,
            "sourceArchive": {"path": "match.odxmatch.zip", "sha256": "", "matchId": "match-1"},
            "confirmation": {"source": "user", "note": "Golduck has Confusion equipped; music off."},
            "samples": [{"id": "confusion-01", "role": "reference", "moveName": "Confusion",
                         "speciesName": "Golduck", "audioArticleId": "audio-1", "sourceSha256": self.audio_sha,
                         "startFrame": 101, "frameCount": 2003, "notes": "Candidate, not admitted."}],
        }
        self.write_archive()

    def write_plan(self):
        self.plan_path.write_text(json.dumps(self.plan))

    def write_archive(self, duplicates=()):
        with zipfile.ZipFile(self.archive_path, "w") as package:
            entries = {"manifest.json": json.dumps(self.manifest).encode(),
                       "timeline.json": json.dumps(self.timeline).encode(),
                       self.artifact_path: self.audio}
            for name, data in entries.items():
                package.writestr(name, data)
            with warnings.catch_warnings():
                warnings.simplefilter("ignore", UserWarning)
                for name in duplicates:
                    package.writestr(name, entries[name])
        self.plan["sourceArchive"]["sha256"] = importer.file_digest(self.archive_path)
        self.write_plan()

    def fails_without_output(self, pattern):
        with self.assertRaisesRegex(ValueError, pattern):
            importer.import_candidates(self.plan_path, self.output)
        self.assertFalse(self.output.exists())

    def test_trim_copies_exact_interleaved_frames_and_preserves_originals(self):
        before_archive = self.archive_path.read_bytes()
        before_plan = self.plan_path.read_bytes()
        result = importer.import_candidates(self.plan_path, self.output)
        candidate = (self.output / "confusion-01.wav").read_bytes()
        rate, channels, frames, pcm = importer.decode_pcm(candidate)
        self.assertEqual((48000, 2, 2003), (rate, channels, frames))
        self.assertEqual(self.pcm[101 * 4:2104 * 4], pcm)
        self.assertEqual(before_archive, self.archive_path.read_bytes())
        self.assertEqual(before_plan, self.plan_path.read_bytes())
        record = result["samples"][0]
        self.assertEqual(self.audio_sha, record["source"]["sha256"])
        self.assertEqual(importer.digest(candidate), record["derived"]["sha256"])
        self.assertEqual(2104, record["trim"]["endFrameExclusive"])
        self.assertEqual(101 * 1_000_000_000 // 48000, record["trim"]["windowOffsetNanos"])
        self.assertEqual(32768, record["signal"]["peakAbsolutePcm16"])
        self.assertGreater(record["signal"]["clippedSampleCount"], 0)
        self.assertEqual(result, json.loads((self.output / "provenance.json").read_text()))
        self.assertIn("Candidates only", result["admissionStatus"])

    def test_identical_plan_is_reproducible_in_another_output_directory(self):
        importer.import_candidates(self.plan_path, self.output)
        other = self.root / "another-output"
        importer.import_candidates(self.plan_path, other)
        self.assertEqual({p.name: p.read_bytes() for p in self.output.iterdir()},
                         {p.name: p.read_bytes() for p in other.iterdir()})

    def test_short_study_cut_is_allowed_but_does_not_meet_matcher_duration(self):
        result = importer.import_candidates(self.plan_path, self.output)
        self.assertFalse(result["samples"][0]["matcherDuration"]["meetsMinimum"])

    def test_mono_original_rate_and_exact_duration_boundary_are_retained(self):
        self.audio, self.pcm = wav_bytes(frames=6000, channels=1, rate=8000)
        self.audio_sha = importer.digest(self.audio)
        self.artifact_path = f"artifacts/audio/sha256/{self.audio_sha}.wav"
        self.article["payload"].update(artifactPath=self.artifact_path, sha256=self.audio_sha,
            byteCount=len(self.audio), sampleRateHz=8000, channelCount=1, durationNanos=750_000_000)
        self.manifest["artifacts"] = [{"relativePath": self.artifact_path, "sha256": self.audio_sha,
                                       "mediaType": "audio/wav", "byteCount": len(self.audio)}]
        self.plan["samples"][0].update(sourceSha256=self.audio_sha, startFrame=0, frameCount=2815)
        self.plan["samples"].append(dict(self.plan["samples"][0], id="confusion-02", startFrame=3000, frameCount=2816))
        self.write_archive()
        result = importer.import_candidates(self.plan_path, self.output)
        self.assertEqual([False, True], [s["matcherDuration"]["meetsMinimum"] for s in result["samples"]])
        self.assertEqual((8000, 1, 2816), importer.decode_pcm((self.output / "confusion-02.wav").read_bytes())[:3])

    def test_wrong_archive_hash_is_rejected(self):
        self.plan["sourceArchive"]["sha256"] = "0" * 64
        self.write_plan()
        self.fails_without_output("archive SHA-256")

    def test_source_article_must_identify_the_planned_wav(self):
        self.plan["samples"][0]["sourceSha256"] = "0" * 64
        self.write_plan()
        self.fails_without_output("does not identify the planned WAV")

    def test_tampered_wav_does_not_gain_trust_from_archive_hash(self):
        self.audio = self.audio[:-1] + bytes([self.audio[-1] ^ 1])
        self.write_archive()
        self.fails_without_output("WAV SHA-256 mismatch")

    def test_match_ids_agree_across_plan_manifest_timeline_and_article(self):
        for location in (self.manifest, self.timeline, self.article):
            with self.subTest(location=location):
                location["matchId"] = "another-match"
                self.write_archive()
                self.fails_without_output("Match ID|another Match")
                location["matchId"] = "match-1"

    def test_source_must_be_a_unique_audio_captured_article(self):
        self.article["payload"]["kind"] = "fast_move_sound_measured"
        self.write_archive()
        self.fails_without_output("does not identify")
        self.article["payload"]["kind"] = "audio_captured"
        self.timeline["articles"].append(copy.deepcopy(self.article))
        self.write_archive()
        self.fails_without_output("duplicated")

    def test_duplicate_zip_source_is_rejected(self):
        for name in ("manifest.json", "timeline.json", self.artifact_path):
            with self.subTest(name=name):
                self.write_archive(duplicates=[name])
                self.fails_without_output("duplicated")

    def test_inventory_must_agree_with_source_testimony(self):
        self.manifest["artifacts"][0]["byteCount"] += 1
        self.write_archive()
        self.fails_without_output("inventory differs")

    def test_testimony_format_and_duration_must_agree_with_wav(self):
        for field, wrong in (("sampleRateHz", 44100), ("channelCount", 1),
                             ("durationNanos", 300_000_000), ("byteCount", 200)):
            with self.subTest(field=field):
                original = self.article["payload"][field]
                self.article["payload"][field] = wrong
                self.write_archive()
                self.fails_without_output("inventory differs|format/duration differs")
                self.article["payload"][field] = original

    def test_partial_and_non_pcm16_wavs_are_rejected(self):
        examples = [self.audio[:-2], wav_bytes(width=1)[0], b"not audio",
                    self.audio[:4] + bytes(4) + self.audio[8:]]
        for data in examples:
            with self.subTest(size=len(data)):
                with self.assertRaisesRegex(ValueError, "PCM16|Incomplete|Invalid"):
                    importer.decode_pcm(data)

    def test_finalized_data_chunk_cannot_hide_a_partial_interleaved_frame(self):
        partial = bytearray(self.audio[:-1])
        # The odd data chunk gets a pad byte, giving a complete RIFF container
        # but leaving one incomplete stereo PCM frame inside it.
        partial.append(0)
        struct.pack_into("<I", partial, 40, len(self.audio) - 45)
        with self.assertRaisesRegex(ValueError, "PCM16"):
            importer.decode_pcm(bytes(partial))

    def test_invalid_frame_ranges_and_noninteger_offsets_are_rejected(self):
        for field, value in (("startFrame", -1), ("startFrame", 1.5), ("startFrame", True),
                             ("frameCount", 0), ("frameCount", -1), ("frameCount", 4801)):
            with self.subTest(field=field, value=value):
                old = self.plan["samples"][0][field]
                self.plan["samples"][0][field] = value
                self.write_plan()
                self.fails_without_output("integer|frame range")
                self.plan["samples"][0][field] = old

    def test_output_filenames_are_portable_unique_and_cannot_escape(self):
        for identifier in ("../escape", "x/y", "a\\b", ".", "CON", "lpt1", "file.wav", "a:bad"):
            with self.subTest(identifier=identifier):
                self.plan["samples"][0]["id"] = identifier
                self.write_plan()
                self.fails_without_output("portable filename")
        self.plan["samples"][0]["id"] = "clip"
        self.plan["samples"].append(dict(self.plan["samples"][0], id="CLIP"))
        self.write_plan()
        self.fails_without_output("duplicated")

    def test_all_samples_validate_before_output_creation(self):
        self.plan["samples"].append(dict(self.plan["samples"][0], id="bad-later", frameCount=999999))
        self.write_plan()
        self.fails_without_output("frame range")

    def test_existing_output_and_symlink_are_never_replaced(self):
        self.output.mkdir()
        sentinel = self.output / "keep.txt"
        sentinel.write_text("Keep me")
        with self.assertRaisesRegex(ValueError, "already exists"):
            importer.import_candidates(self.plan_path, self.output)
        self.assertEqual("Keep me", sentinel.read_text())
        link = self.root / "output-link"
        link.symlink_to(self.root / "not-yet-present", target_is_directory=True)
        with self.assertRaisesRegex(ValueError, "already exists"):
            importer.import_candidates(self.plan_path, link)
        self.assertTrue(link.is_symlink())

    def test_reference_and_holdout_cannot_reuse_overlapping_source_frames(self):
        self.plan["samples"].append(dict(self.plan["samples"][0], id="confusion-holdout", role="holdout", startFrame=500))
        self.write_plan()
        self.fails_without_output("Reference/holdout.*overlaps")

    def test_different_audio_windows_cannot_hide_same_time_reference_holdout_leak(self):
        other_article = copy.deepcopy(self.article)
        other_article["articleId"] = "audio-2"
        # Same source audio effect in overlapping capture windows: even though
        # chosen local frame ranges are disjoint, source-time ranges overlap.
        other_article["monotonicTimeNanos"] -= 2500 * 1_000_000_000 // 48000
        self.timeline["articles"].append(other_article)
        self.plan["samples"][0].update(startFrame=0, frameCount=2000)
        self.plan["samples"].append(dict(self.plan["samples"][0], id="holdout", role="holdout",
                                        audioArticleId="audio-2", startFrame=2500))
        self.write_archive()
        self.fails_without_output("Reference/holdout.*overlaps")

    def test_duplicate_pcm_cannot_be_called_an_independent_holdout(self):
        # Repeated PCM at disjoint timestamps remains the same test signal.
        self.plan["samples"][0].update(startFrame=0, frameCount=2000)
        self.plan["samples"].append(dict(self.plan["samples"][0], id="holdout", role="holdout", startFrame=2500))
        self.write_plan()
        self.fails_without_output("Reference/holdout.*repeats")

    def test_distinct_nonoverlapping_same_battle_holdout_retains_scope_warning(self):
        self.plan["samples"][0].update(startFrame=0, frameCount=2000)
        self.plan["samples"].append(dict(self.plan["samples"][0], id="holdout", role="holdout", startFrame=2501))
        self.write_plan()
        result = importer.import_candidates(self.plan_path, self.output)
        self.assertIn("not independent-battle validation", result["holdoutScope"])

    def test_missing_confirmation_unknown_role_and_nonrelative_paths_are_rejected(self):
        examples = [("confirmation", {}), ("role", "admitted"), ("path", "/tmp/source.zip"),
                    ("path", "C:\\source.zip")]
        for field, value in examples:
            with self.subTest(field=field, value=value):
                plan = copy.deepcopy(self.plan)
                if field == "confirmation":
                    plan[field] = value
                elif field == "role":
                    plan["samples"][0][field] = value
                else:
                    plan["sourceArchive"][field] = value
                self.plan_path.write_text(json.dumps(plan))
                self.fails_without_output("confirmation|role must|relative portable")

    def test_duplicate_json_keys_and_invalid_json_fail_clearly(self):
        for content in ('{"version":1,"version":2}', 'not-json', '{"version":NaN}'):
            self.plan_path.write_text(content)
            self.fails_without_output("duplicate key|Invalid plan|non-JSON numeric constant")

    def test_cli_reports_bad_input_without_traceback_or_partial_output(self):
        self.plan["sourceArchive"]["sha256"] = "0" * 64
        self.write_plan()
        run = subprocess.run([sys.executable, str(SCRIPT), str(self.plan_path), "--output-dir", str(self.output)],
                             capture_output=True, text=True, check=False)
        self.assertEqual(2, run.returncode)
        self.assertIn("Import failed: Source archive SHA-256", run.stderr)
        self.assertNotIn("Traceback", run.stderr)
        self.assertFalse(self.output.exists())


if __name__ == "__main__":
    unittest.main()
