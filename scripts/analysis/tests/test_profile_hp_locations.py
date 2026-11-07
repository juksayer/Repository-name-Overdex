import importlib.util
import contextlib
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest import mock
import zipfile

SPEC = importlib.util.spec_from_file_location("profile_hp_locations", Path(__file__).resolve().parents[1] / "profile_hp_locations.py")
profiler = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(profiler)

SECOND = 1_000_000_000


def article(identifier, at, payload, source="TEST", match="match"):
    return {"articleId": identifier, "matchId": match, "monotonicTimeNanos": at,
            "perceivedAt": 123_000 + (at or 0) // 1_000_000, "recordedAt": 999_999,
            "sourceId": source, "payload": payload, "evidenceReferences": []}


def identity(identifier="identity", at=0, name="Raichu", species_id=26, side="PLAYER", **kw):
    return article(identifier, at, {"kind": "active_pokemon_species_witnessed", "speciesName": name,
                                  "speciesId": species_id, "side": side}, **kw)


def hp(identifier="hp", at=SECOND, bounds=(20, 200, 210, 218), dimensions=(672, 1493), side="PLAYER", **kw):
    return article(identifier, at, {"kind": "active_hp_bar_measured", "side": side,
                                  **dict(zip(("barLeft", "barTop", "barRight", "barBottom"), bounds)),
                                  "sourceWidth": dimensions[0], "sourceHeight": dimensions[1]}, **kw)


def crop(identifier, side, bounds, dimensions=(672, 1493), **kw):
    name = "PlayerHpEvidenceCrop" if side == "PLAYER" else "OpponentHpEvidenceCrop"
    return article(identifier, 0, {"kind": "crop_captured", "cropName": name,
                                  **dict(zip(("cropLeft", "cropTop", "cropRight", "cropBottom"), bounds)),
                                  "sourceWidth": dimensions[0], "sourceHeight": dimensions[1]}, **kw)


def complete_calibration(prefix="", **kw):
    return [crop(prefix + "crop-player", "PLAYER", (0, 100, 280, 1000), **kw),
            crop(prefix + "crop-opponent", "OPPONENT", (300, 100, 672, 1000), **kw)]


class HpLocationProfileTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)

    def archive(self, rows, name="test.zip", match="match"):
        path = self.directory / name
        with zipfile.ZipFile(path, "w") as package:
            package.writestr("timeline.json", json.dumps({"schemaVersion": 1, "matchId": match, "articles": rows}))
        return path

    def analyze(self, rows, **kw):
        return profiler.analyze([self.archive(rows)], **kw)

    def test_cli_cannot_overwrite_a_source_archive(self):
        source = self.archive([identity(), hp()])
        original = source.read_bytes()
        with mock.patch.object(sys, "argv", ["profile_hp_locations.py", str(source), "--output", str(source)]), \
                contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit) as error:
            profiler.main()
        self.assertEqual(2, error.exception.code)
        self.assertEqual(original, source.read_bytes())

    def test_source_time_not_delivery_order_selects_identity_without_backfilling(self):
        result = self.analyze([hp("early", 0), hp("same", SECOND), hp("after", 2 * SECOND), identity(at=SECOND)])
        self.assertEqual(1, result["accepted_samples"])
        self.assertEqual("after", result["profiles"][0]["samples"][0]["hp_article_id"])
        self.assertEqual(1, result["skipped_samples"]["NO_UNAMBIGUOUS_ACTIVE_SPECIES"])
        self.assertEqual(1, result["skipped_samples"]["SAME_TIMESTAMP_IDENTITY_OR_BOUNDARY"])

    def test_conflicting_same_timestamp_names_stay_unknown_until_new_confirmation(self):
        result = self.analyze([identity(), identity("a", SECOND, "Raichu (Alolan)"), identity("b", SECOND, "Raichu"),
                               hp("same", SECOND), hp("unresolved", 2 * SECOND), identity("resolved", 3 * SECOND), hp("safe", 4 * SECOND)])
        self.assertEqual(1, result["accepted_samples"])
        self.assertEqual("safe", result["profiles"][0]["samples"][0]["hp_article_id"])

    def test_faint_prevents_species_from_carrying_into_replacement_geometry(self):
        faint = article("faint", SECOND, {"kind": "active_pokemon_fainted_witnessed", "side": "PLAYER", "speciesName": "Raichu"})
        result = self.analyze([identity(), faint, hp("during", SECOND), hp("unknown", 2 * SECOND), identity("new", 3 * SECOND, "Golbat", 42), hp("good", 4 * SECOND)])
        self.assertEqual(["Golbat"], [p["species_name"] for p in result["profiles"]])
        self.assertEqual(1, result["accepted_samples"])

    def test_delayed_named_faint_of_old_species_does_not_retire_confirmed_replacement(self):
        faint = article("old-faint", 2 * SECOND, {"kind": "active_pokemon_fainted_witnessed", "side": "PLAYER", "speciesName": "Raichu"})
        result = self.analyze([identity(), identity("new", SECOND, "Golbat", 42), faint, hp("new-hp", 3 * SECOND)])
        self.assertEqual(1, result["accepted_samples"])
        self.assertEqual("Golbat", result["profiles"][0]["species_name"])

    def test_delayed_entry_check_invalidates_at_trigger_source_time(self):
        entry = article("entry", 4 * SECOND, {"kind": "species_check_measured", "side": "PLAYER", "status": "OPENED",
                                              "reason": "ENTRY", "triggerMonotonicNanos": SECOND})
        result = self.analyze([identity(), hp("before", SECOND // 2), hp("after-trigger", 2 * SECOND), entry])
        self.assertEqual(1, result["accepted_samples"])
        self.assertEqual("before", result["profiles"][0]["samples"][0]["hp_article_id"])

    def test_identity_freshness_is_bounded_and_not_replaced_by_team_configuration(self):
        configured = article("configured", 0, {"kind": "player_team_slot_configured", "slot": 1, "speciesName": "Raichu", "speciesId": 26})
        result = self.analyze([configured, identity(), hp("boundary", 5 * SECOND), hp("old", 5 * SECOND + 1)])
        self.assertEqual(1, result["accepted_samples"])
        self.assertEqual(1, result["skipped_samples"]["STALE_IDENTITY_CONFIRMATION"])
        result = self.analyze([identity(source="TEAM_SELECT_LEAD_INFERENCE"), hp()])
        self.assertEqual(0, result["accepted_samples"])

    def test_forms_and_source_dimensions_remain_separate(self):
        rows = [identity(), hp("ordinary"), identity("alolan", 2 * SECOND, "Raichu (Alolan)"), hp("form", 3 * SECOND),
                hp("other-size", 4 * SECOND, dimensions=(1080, 2400))]
        result = self.analyze(rows)
        self.assertEqual(3, result["profile_count"])
        self.assertEqual({"Raichu", "Raichu (Alolan)"}, {p["species_name"] for p in result["profiles"]})

    def test_exact_hp_provenance_pools_matches_but_keeps_all_citations_and_separate_clocks(self):
        one = self.archive([identity(), hp(), *complete_calibration()], "one.zip")
        two = self.archive([identity("identity2", match="match2"), hp("hp2", match="match2"), *complete_calibration("2", match="match2")], "two.zip", "match2")
        result = profiler.analyze([one, two])
        self.assertEqual(1, result["profile_count"])
        profile = result["profiles"][0]
        self.assertEqual(2, profile["sample_count"])
        self.assertEqual(2, len(profile["source_time_ranges_by_match"]))
        self.assertEqual(["2crop-player", "crop-player"], profile["calibration"]["provenance_article_ids"]["PLAYER"])
        self.assertIsNone(profile["calibration"]["full_calibration_fingerprint"])

    def test_unknown_calibrations_cannot_silently_pool_different_archives(self):
        one = self.archive([identity(), hp()], "one.zip")
        two = self.archive([identity("id2", match="match2"), hp("hp2", match="match2")], "two.zip", "match2")
        result = profiler.analyze([one, two])
        self.assertEqual(2, result["profile_count"])
        self.assertTrue(all(p["calibration"]["unknown_context_archive_scope"] for p in result["profiles"]))

    def test_distinct_or_conflicting_calibration_bounds_do_not_mix(self):
        conflicting = [*complete_calibration(), crop("changed", "PLAYER", (0, 120, 280, 1000))]
        result = self.analyze([identity(), hp(), *conflicting])
        self.assertEqual(0, result["accepted_samples"])
        self.assertEqual(1, result["skipped_samples"]["AMBIGUOUS_CALIBRATION_BOUNDS"])

    def test_extents_modes_ties_and_source_citations_are_explicitly_not_presets(self):
        result = self.analyze([identity(), hp("one", SECOND, bounds=(20, 200, 210, 218)),
                               hp("two", 2 * SECOND, bounds=(20, 240, 210, 258)), *complete_calibration()], bin_size=16)
        profile = result["profiles"][0]
        self.assertEqual([20, 200, 210, 258], profile["observed_extent"])
        self.assertEqual(2, profile["modal_position"]["tied_mode_count"])
        self.assertEqual(1, profile["modal_position"]["maximum_bin_sample_count"])
        self.assertEqual({"one", "two", "identity"}, set(profile["cited_article_ids"]))
        self.assertEqual("UNKNOWN", profile["phase"])
        self.assertIsNone(profile["production_preset"])
        self.assertEqual(64, len(profile["archive_sha256s"][0]))

    def test_duplicate_archives_and_duplicate_articles_do_not_inflate_counts(self):
        rows = [identity(), hp(), *complete_calibration()]
        one = self.archive(rows, "one.zip")
        two = self.archive([*rows, article("extra", 9 * SECOND, {"kind": "raw_text", "value": "extra"})], "two.zip")
        result = profiler.analyze([one, one, two])
        self.assertEqual(1, result["accepted_samples"])
        self.assertEqual(1, result["skipped_samples"]["DUPLICATE_ARCHIVE_INPUT"])
        self.assertEqual(1, result["skipped_samples"]["DUPLICATE_HP_ARTICLE"])
        self.assertEqual(2, len(result["profiles"][0]["archive_sha256s"]))

    def test_conflicting_duplicate_article_abstains(self):
        one = self.archive([identity(), hp(), *complete_calibration()], "one.zip")
        two = self.archive([identity(), hp(bounds=(20, 300, 210, 318)), *complete_calibration()], "two.zip")
        result = profiler.analyze([one, two])
        self.assertEqual(0, result["accepted_samples"])
        self.assertEqual(2, result["skipped_samples"]["CONFLICTING_DUPLICATE_ARTICLE"])

    def test_invalid_geometry_and_missing_source_time_do_not_use_wall_time_fallback(self):
        result = self.analyze([identity(), hp("bad", bounds=(-1, 100, 200, 120)), hp("no-source", at=None),
                               hp("no-size", dimensions=(None, None)), hp("wrong-match", match="different")])
        self.assertEqual(0, result["accepted_samples"])
        self.assertEqual(2, result["skipped_samples"]["INVALID_OR_UNSOURCED_GEOMETRY"])
        self.assertEqual(1, result["skipped_samples"]["HP_WITHOUT_MONOTONIC_SOURCE_TIME"])
        self.assertEqual(1, result["skipped_samples"]["MISMATCHED_MATCH_ID"])

    def test_match_end_excludes_geometry_even_if_a_late_identity_exists(self):
        result = self.analyze([identity(), hp("before", SECOND), article("end", 2 * SECOND, {"kind": "match_ended"}),
                               identity("late", 3 * SECOND), hp("after", 4 * SECOND)])
        self.assertEqual(1, result["accepted_samples"])
        self.assertEqual(1, result["skipped_samples"]["AT_OR_AFTER_MATCH_END"])

    def test_analysis_does_not_modify_the_input_archive(self):
        path = self.archive([identity(), hp()])
        before = path.read_bytes()
        profiler.analyze([path])
        self.assertEqual(before, path.read_bytes())


if __name__ == "__main__":
    unittest.main()
