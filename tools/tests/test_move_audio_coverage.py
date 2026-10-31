import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location("audit_move_audio_coverage",
    Path(__file__).resolve().parents[1] / "audit_move_audio_coverage.py")
audit_module = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(audit_module)


class MoveAudioCoverageTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.assets = self.root / "app/src/main/assets"
        self.assets.mkdir(parents=True)
        self.supplied = self.root / "supplied"
        self.supplied.mkdir()

    def fixture(self, fast, charged=(), references=()):
        moves = [{"moveId": identifier, "name": name, "type": "normal", "power": 5,
                  "energyGain": 8, "energy": 0, "turns": 2} for identifier, name in fast + list(charged)]
        catalogue = {"timestamp": "fixture", "pokemon": [{"speciesName": "Example (Alolan)",
            "dex": 26, "released": True, "fastMoves": [identifier for identifier, _ in fast],
            "chargedMoves": [identifier for identifier, _ in charged]}], "moves": moves}
        (self.assets / "gamemaster.json").write_text(json.dumps(catalogue))
        for category in ("fast", "charged"):
            directory = self.assets / f"{category}_move_sounds"
            directory.mkdir(exist_ok=True)
            entries = []
            for kind, name, filename in references:
                if kind != category:
                    continue
                path = directory / filename
                path.write_bytes(b"unique reference " + filename.encode())
                entries.append({"file": filename, "moveName": name, "sha256": audit_module.sha256(path)})
            (directory / "manifest.json").write_text(json.dumps({"references": entries}))

    def test_exact_normalization_does_not_turn_hidden_power_family_into_typed_coverage(self):
        self.fixture([("LOCK_ON", "Lock-On"), ("HIDDEN_BUG", "Hidden Power (Bug)")],
            references=[("fast", "Lock On", "a.wav"), ("fast", "Hidden Power", "b.wav")])
        summary = audit_module.audit(self.root, self.supplied)["categories"]["fast"]
        rows = {row["name"]: row for row in summary["rows"]}
        self.assertTrue(rows["Lock-On"]["exact_covered"])
        self.assertFalse(rows["Hidden Power (Bug)"]["exact_covered"])
        self.assertEqual("family_reference_needs_validation", rows["Hidden Power (Bug)"]["status"])
        self.assertEqual(["Example (Alolan)"], rows["Hidden Power (Bug)"]["example_pokemon"])

    def test_multiple_ids_and_reference_variants_do_not_inflate_unique_name_coverage(self):
        self.fixture([("A", "Charge Beam"), ("A_VARIANT", "Charge Beam")],
            references=[("fast", "Charge Beam", "a.wav"), ("fast", "Charge Beam", "b.wav")])
        summary = audit_module.audit(self.root, self.supplied)["categories"]["fast"]
        self.assertEqual(2, summary["referenced_move_ids"])
        self.assertEqual(1, summary["unique_names"])
        self.assertEqual(2, summary["manifest_reference_entries"])
        self.assertEqual(1, summary["exact_covered_names"])

    def test_corrupt_manifest_file_is_not_reported_as_usable_coverage(self):
        self.fixture([("A", "Confusion")], references=[("fast", "Confusion", "a.wav")])
        (self.assets / "fast_move_sounds/a.wav").write_bytes(b"corrupt")
        summary = audit_module.audit(self.root, self.supplied)["categories"]["fast"]
        self.assertEqual(["a.wav"], summary["unusable_manifest_files"])
        self.assertEqual(1, summary["exact_missing_names"])

    def test_numbered_nonzero_asset_variant_and_spelling_alias_remain_review_items(self):
        self.fixture([], [("SKULL_BASH", "Skull Bash"), ("VISE_GRIP", "Vise Grip")])
        (self.supplied / "130-1_skull_bash.wav").write_bytes(b"source")
        (self.supplied / "011-0_vice_grip.wav").write_bytes(b"source")
        summary = audit_module.audit(self.root, self.supplied)["categories"]["charged"]
        rows = {row["name"]: row for row in summary["rows"]}
        self.assertEqual("source_asset_available", rows["Skull Bash"]["status"])
        self.assertEqual("spelling_alias_needs_review", rows["Vise Grip"]["status"])
        self.assertEqual(["011-0_vice_grip.wav"], rows["Vise Grip"]["alias_source_files"])
        self.assertEqual(0, summary["exact_covered_names"])

    def test_non_damaging_transform_and_misclassified_struggle_are_not_ordinary_recording_tasks(self):
        self.fixture([("TRANSFORM", "Transform"), ("STRUGGLE", "Struggle")])
        rows = {row["name"]: row for row in audit_module.audit(self.root, self.supplied)["categories"]["fast"]["rows"]}
        self.assertEqual("special_action_review", rows["Transform"]["status"])
        self.assertEqual("catalogue_category_review", rows["Struggle"]["status"])

    def test_absent_source_inventory_is_not_claimed_to_have_no_file(self):
        self.fixture([("BUBBLE", "Bubble")])
        result = audit_module.audit(self.root, self.root / "not-present")
        self.assertFalse(result["supplied_assets_checked"])
        self.assertEqual("source_inventory_not_checked", result["categories"]["fast"]["rows"][0]["status"])


if __name__ == "__main__":
    unittest.main()
