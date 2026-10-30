import base64
import importlib.util
from io import BytesIO
import json
from pathlib import Path
import sys
import tempfile
import unittest
import wave
import zipfile

sys.path.insert(0, str(Path(__file__).parents[1]))
spec = importlib.util.spec_from_file_location("audio_review", Path(__file__).parents[1] / "build_fast_move_audio_review.py")
review = importlib.util.module_from_spec(spec)
spec.loader.exec_module(review)


def wav():
    out = BytesIO()
    with wave.open(out, "wb") as audio:
        audio.setnchannels(1)
        audio.setsampwidth(2)
        audio.setframerate(8000)
        audio.writeframes(b"\x01\x00" * 9600)
    return out.getvalue()


def article(id, kind, nanos, **payload):
    return dict(articleId=id, matchId="battle", perceivedAt=1000 + nanos // 1_000_000,
                recordedAt=9000, monotonicTimeNanos=nanos, evidenceReferences=[], predecessorIds=[],
                payload=dict(kind=kind, **payload))


def candidate(name, sha, original, changing):
    return dict(moveName=name, referenceSha256=sha, similarity=original, changingSimilarity=changing,
                matchingOffsetNanos=160_000_000, referenceOffsetNanos=32_000_000, matchedDurationNanos=416_000_000)


class AudioReviewTest(unittest.TestCase):
    def setUp(self):
        self.bytes = wav()
        self.sha = review.digest(self.bytes)
        self.path = f"artifacts/audio/sha256/{self.sha}.wav"
        self.references = {(name, self.sha): review.audio_data(self.bytes) for name in ("Alpha", "Beta", "Gamma")}
        self.candidates = [candidate("Alpha", self.sha, .965, .80), candidate("Beta", self.sha, .961, .90),
                           candidate("Gamma", self.sha, .94, .77)]
        self.capture = article("capture", "audio_captured", 2_000_000_000, artifactPath=self.path,
            sha256=self.sha, byteCount=len(self.bytes), mediaType="audio/wav", sampleRateHz=8000,
            channelCount=1, durationNanos=1_200_000_000)
        self.sound = article("sound", "fast_move_sound_measured", 3_200_000_000,
            referenceCandidates=self.candidates, referenceMatchAccepted=False, analysisVersion="spectral-sequence-v1")
        self.sound["evidenceReferences"] = ["capture"]

    def package(self, path, articles=None, data=None):
        rows = articles or [article("start", "match_started", 1_000_000_000), self.capture, self.sound]
        with zipfile.ZipFile(path, "w") as z:
            z.writestr("timeline.json", json.dumps(dict(matchId="battle", articles=rows)))
            z.writestr(self.path, self.bytes if data is None else data)

    def test_capture_reference_follows_evidence_links_and_stops_cycles(self):
        intermediate = article("intermediate", "raw_testimony", 1)
        intermediate["evidenceReferences"] = ["intermediate", "capture"]
        self.sound["evidenceReferences"] = ["intermediate"]
        linked = list(review.linked_audio(self.sound, {a["articleId"]: a for a in [intermediate, self.capture]}))
        self.assertEqual(["capture"], [a["articleId"] for a in linked])

    def test_uses_wav_source_clock_not_late_measurement_and_never_changes_archive(self):
        with tempfile.TemporaryDirectory() as root:
            path = Path(root) / "match.zip"
            self.package(path)
            before = path.read_bytes()
            cases, missing = review.collect_cases([path], self.references)
            self.assertEqual(0, missing)
            self.assertEqual(1, len(cases))
            self.assertEqual(1, cases[0]["relativeSeconds"])
            self.assertAlmostEqual(.004, cases[0]["scoreGap"])
            self.assertAlmostEqual(-.10, cases[0]["changingLead"])
            self.assertFalse(cases[0]["accepted"])
            self.assertEqual(self.bytes, base64.b64decode(cases[0]["recording"]["src"].split(",", 1)[1]))
            self.assertEqual(before, path.read_bytes())

    def test_corrupt_or_missing_source_media_is_skipped_without_a_replacement(self):
        with tempfile.TemporaryDirectory() as root:
            path = Path(root) / "bad.zip"
            self.package(path, data=self.bytes[:-2])
            cases, missing = review.collect_cases([path], self.references)
            self.assertEqual([], cases)
            self.assertEqual(1, missing)

    def test_audio_hash_portable_path_and_header_are_verified(self):
        with tempfile.TemporaryDirectory() as root:
            path = Path(root) / "match.zip"
            self.package(path)
            with zipfile.ZipFile(path) as z:
                for changed in ({"artifactPath": "../outside.wav"}, {"sha256": "0" * 64},
                                {"channelCount": 2}, {"sampleRateHz": 48000}, {"durationNanos": 0}):
                    with self.subTest(changed=changed), self.assertRaises((ValueError, KeyError)):
                        review.verified_audio(z, {**self.capture["payload"], **changed})

    def test_hp_geometry_never_borrows_a_future_or_different_side_measurement(self):
        rows = [article("old-geometry", "active_hp_bar_measured", 100_000_000,
                         side="PLAYER", barLeft=0, barRight=200),
                article("other-side", "active_hp_bar_measured", 950_000_000,
                         side="OPPONENT", barLeft=0, barRight=500),
                article("tick", "active_hp_bar_damage_tick_measured", 1_000_000_000,
                         damagedSide="PLAYER", beforeFraction=.7, afterFraction=.65, lostFraction=.05),
                article("future-geometry", "active_hp_bar_measured", 1_010_000_000,
                         side="PLAYER", barLeft=0, barRight=190)]
        tick = review.context_at(rows, 1_000_000_000, 1.2)["hpTicks"][0]
        self.assertIsNone(tick["barWidthPixels"])
        rows.append(article("same-side", "active_hp_bar_measured", 960_000_000,
                            side="PLAYER", barLeft=12, barRight=202))
        tick = review.context_at(rows, 1_000_000_000, 1.2)["hpTicks"][0]
        self.assertEqual(190, tick["barWidthPixels"])
        self.assertEqual(.05, tick["lostFraction"])
        self.assertEqual("same-side", tick["geometryArticleId"])

    def test_letters_are_stable_and_include_disagreeing_changing_spectrum_leader(self):
        p = dict(referenceCandidates=self.candidates)
        a = review.candidates_for(p, self.references, "case-001")
        b = review.candidates_for(p, self.references, "case-001")
        self.assertEqual(a, b)
        self.assertEqual({"A", "B", "C"}, {c["code"] for c in a})
        self.assertEqual({"Alpha", "Beta", "Gamma"}, {c["moveName"] for c in a})
        self.assertEqual(self.sha, a[0]["reference"]["sha256"])

    def test_includes_strongest_changing_spectrum_alternative_even_at_a_low_original_rank(self):
        refs = {**self.references, ("Delta", self.sha): review.audio_data(self.bytes)}
        rows = [candidate("Alpha", self.sha, .98, .95), candidate("Beta", self.sha, .94, .80),
                candidate("Gamma", self.sha, .93, .77), candidate("Delta", self.sha, .90, .92)]
        choices = review.candidates_for(dict(referenceCandidates=rows), refs, "alternative")
        self.assertEqual({"Alpha", "Beta", "Delta"}, {c["moveName"] for c in choices})

    def test_curated_selection_is_exact_and_rejects_missing_or_repeated_ids(self):
        cases = [dict(articleId="one"), dict(articleId="two")]
        self.assertEqual([cases[1], cases[0]], review.select_cases(cases, ["two", "one"], 1))
        for ids in (["missing"], ["one", "one"]):
            with self.assertRaises(ValueError):
                review.select_cases(cases, ids, 12)

    def test_review_id_is_repeatable_and_embedded_archive_text_cannot_close_script(self):
        with tempfile.TemporaryDirectory() as root:
            path = Path(root) / "match.zip"
            self.package(path)
            cases, missing = review.collect_cases([path], self.references)
            cases[0]["archiveName"] = '</script><script>alert("bad")</script>'
            output = Path(root) / "review.html"
            first = review.build_review(cases, output, self.references)
            second = review.build_review(cases, output, self.references)
            self.assertEqual(first["reviewSetId"], second["reviewSetId"])
            text = output.read_text()
            self.assertNotIn(cases[0]["archiveName"], text)
            self.assertIn("\\u003c/script>", text)
            self.assertNotIn("__REVIEW_DATA_JSON__", text)
            self.assertNotIn("__AUDIO_SIGNAL_JS__", text)
            self.assertNotIn("__AUDIO_PLAYBACK_JS__", text)
            self.assertIn("OverdexAudioSignals", text)
            self.assertIn("OverdexAudioWindows", text)
            cases[0]["context"]["userReportedPattern"] = "User recalled only one team attacking; not a clip label."
            changed = review.build_review(cases, output, self.references)
            self.assertNotEqual(first["reviewSetId"], changed["reviewSetId"])

    def test_derived_signal_views_preserve_review_identity_and_original_wavs(self):
        with tempfile.TemporaryDirectory() as root:
            path = Path(root) / "match.zip"
            self.package(path)
            cases, _ = review.collect_cases([path], self.references)
            before = json.dumps([review.case_metadata(c) for c in cases], sort_keys=True)
            source = cases[0]["recording"]["src"]
            review.attach_signal_previews(cases)
            self.assertEqual(before, json.dumps([review.case_metadata(c) for c in cases], sort_keys=True))
            self.assertEqual(source, cases[0]["recording"]["src"])
            self.assertIn("waveform", cases[0]["recording"]["signal"])
            self.assertIs(cases[0]["recording"]["signal"], cases[0]["candidates"][0]["reference"]["signal"])


if __name__ == "__main__":
    unittest.main()
