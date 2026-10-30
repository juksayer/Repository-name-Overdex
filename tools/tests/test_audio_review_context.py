import copy
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("audio_review_context", Path(__file__).parents[1] / "audio_review_context.py")
context = importlib.util.module_from_spec(spec)
spec.loader.exec_module(context)


def article(at, kind, ident=None, **payload):
    return {"articleId": ident or f"{kind}-{at}", "monotonicTimeNanos": round(at * 1e9),
            "payload": {"kind": kind, **payload}}


def active(at, species="Golbat", side="PLAYER", **kwargs):
    return article(at, "active_pokemon_species_witnessed", side=side, speciesName=species, **kwargs)


def configured(at, species="Golbat", move="Wing Attack", slot=1):
    return article(at, "player_team_slot_configured", slot=slot, speciesName=species, fastMoveName=move)


def guide(articles, start=10, seconds=1.2, human=None):
    return context.listening_guide(articles, round(start * 1e9), seconds, human)


class AudioReviewContextTest(unittest.TestCase):
    def test_matching_configured_move_and_lead_inference_keep_provenance(self):
        roster = article(1, "player_team_roster_confirmation", confirmed=True, speciesBySlot=["Golbat"])
        species = active(5, speciesId=42)
        species["sourceId"] = "TEAM_SELECT_LEAD_INFERENCE"
        result = guide([roster, configured(2), species])["player"]
        self.assertEqual(("Golbat", 42, "Wing Attack", "configured"),
                         tuple(result[k] for k in ("speciesName", "speciesId", "moveName", "status")))
        self.assertIn("Lead inferred", result["speciesBasis"])
        self.assertEqual([species["articleId"], "player_team_slot_configured-2", roster["articleId"]], result["sourceArticleIds"])

    def test_confirmed_replacement_rejects_old_moves_even_with_same_species(self):
        rows = [configured(1), article(2, "player_team_roster_confirmation", confirmed=True, speciesBySlot=["Golbat"]), active(3)]
        rows.append(article(4, "fast_move_identified", side="PLAYER", speciesName="Golbat",
                            moveName="Wing Attack", basis="CONFIGURED_PLAYER_TEAM_AND_ACTIVE_SPECIES"))
        result = guide(rows)
        self.assertIsNone(result["player"]["moveName"])
        self.assertEqual("unknown", result["player"]["status"])
        self.assertTrue(any("excluded" in note for note in result["contextNotes"]))

    def test_config_of_another_species_or_form_does_not_supply_move(self):
        rows = [configured(1, "Mawile", "Fire Fang"), active(3, "Galvantula")]
        result = guide(rows, human={"opponent": {"speciesName": "Mawile", "moveName": "Fire Fang"}})
        self.assertIsNone(result["player"]["moveName"])
        self.assertIsNone(result["opponent"]["moveName"])
        self.assertTrue(any("not applied" in note for note in result["contextNotes"]))
        self.assertIsNone(guide([configured(1, "Raichu", "Charm"), active(3, "Raichu (Alolan)")])["player"]["moveName"])

    def test_source_clock_filters_future_identity_and_sorts_late_delivery(self):
        before = article(9, "fast_move_identified", side="PLAYER", speciesName="Golbat", moveName="Wing Attack", basis="AUDIO_REFERENCE_REPEATED_UNIQUE_LEGAL_SIDE")
        before["recordedAt"] = 99999999999999  # Late processing does not shift source time.
        after = article(10.1, "fast_move_identified", side="PLAYER", speciesName="Golbat", moveName="Air Slash", basis="CADENCE_ASSUMPTION")
        rows = [after, before, active(5)]
        original = copy.deepcopy(rows)
        result = guide(rows)["player"]
        self.assertEqual(("Wing Attack", "inferred"), (result["moveName"], result["status"]))
        self.assertEqual(original, rows)
        self.assertNotIn(after["articleId"], result["sourceArticleIds"])

    def test_human_confirmation_is_species_scoped_and_activity_is_not_inferred(self):
        rows = [active(1, "Scrafty"), article(2, "fast_move_identified", side="PLAYER", speciesName="Scrafty", moveName="Snarl", basis="AUDIO_REFERENCE_REPEATED_UNIQUE_LEGAL_SIDE")]
        result = guide(rows, human={"player": {"speciesName": "Scrafty", "moveName": "Snarl", "basis": "User confirmed Snarl after this match."}, "activityNote": "User reported continuous attacks."})
        self.assertEqual("user_confirmed", result["player"]["status"])
        self.assertEqual("User confirmed Snarl after this match.", result["player"]["basis"])
        self.assertEqual("User reported continuous attacks.", result["activityNote"])
        self.assertIsNone(guide(rows)["activityNote"])

    def test_switch_inside_window_is_not_retroactively_start_species(self):
        rows = [configured(1), active(2), active(10.4, "Greninja"), active(10.6, "Greninja")]
        result = guide(rows)
        self.assertEqual("Golbat", result["player"]["speciesName"])
        switches = [note for note in result["contextNotes"] if "changes to Greninja" in note]
        self.assertEqual(1, len(switches))
        self.assertIn("+0.400 s", switches[0])
        self.assertEqual("Greninja", guide(rows, start=10.4)["player"]["speciesName"])

    def test_faint_clears_current_species_but_delayed_other_faint_does_not(self):
        faint = article(9, "active_pokemon_fainted_witnessed", side="PLAYER", speciesName="Golbat")
        self.assertIsNone(guide([active(2), faint])["player"]["speciesName"])
        self.assertEqual("Greninja", guide([active(2), active(8, "Greninja"), faint])["player"]["speciesName"])

    def test_duplicate_species_with_different_moves_require_slot(self):
        rows = [configured(1, move="Wing Attack"), configured(2, move="Bite", slot=2), active(3)]
        self.assertIsNone(guide(rows)["player"]["moveName"])
        rows.append(active(4, playerTeamSlot=2))
        self.assertEqual("Bite", guide(rows)["player"]["moveName"])

    def test_new_appearance_does_not_reuse_old_inferred_move(self):
        rows = [active(1), article(2, "fast_move_identified", side="PLAYER", speciesName="Golbat", moveName="Bite", basis="AUDIO"), active(3, "Greninja"), active(9)]
        self.assertEqual("unknown", guide(rows)["player"]["status"])

    def test_raw_audio_candidates_do_not_establish_side_or_move(self):
        sound = article(9, "fast_move_sound_measured", referenceMatchAccepted=True, referenceCandidates=[{"moveName": "Volt Switch", "similarity": .999}])
        result = guide([active(2, "Galvantula"), sound])
        self.assertEqual("unknown", result["player"]["status"])
        self.assertIsNone(result["player"]["moveName"])
        self.assertIn("Only 1.2 s", result["captureNote"])
        for start, duration in [(None, 1), (True, 1), (1, float("nan")), (1, 0)]:
            with self.assertRaises(ValueError):
                context.listening_guide([], start, duration)


if __name__ == "__main__":
    unittest.main()
