import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from inspect_charge_button_growth import GrowthEpisode, measure_ring
from PIL import Image, ImageDraw

class ChargeGrowthTests(unittest.TestCase):
    def prepared(self):
        state = GrowthEpisode()
        for i in range(12):
            self.assertIsNone(state.accept(i * .025, 75, False))
        return state

    def test_growth_then_controls_disappear(self):
        state = self.prepared()
        self.assertIsNone(state.accept(.30, 98, False))
        self.assertIsNone(state.accept(.325, 99, False))
        self.assertIsNone(state.accept(.35, None, True))
        event = state.accept(.375, None, True)
        self.assertEqual(.30, event["growth_at"])
        self.assertIsNone(state.accept(.40, None, True))

    def test_single_flash_does_not_confirm(self):
        state = self.prepared()
        state.accept(.30, 99, False)
        state.accept(.325, None, True)
        self.assertIsNone(state.accept(.35, None, True))

    def test_disappearance_without_growth_does_not_confirm(self):
        state = self.prepared()
        state.accept(.30, None, True)
        self.assertIsNone(state.accept(.325, None, True))

    def test_other_button_must_disappear(self):
        state = self.prepared()
        state.accept(.30, 99, False)
        state.accept(.325, 99, False)
        state.accept(.35, None, False)
        self.assertIsNone(state.accept(.375, None, False))

    def test_late_disappearance_does_not_confirm(self):
        state = self.prepared()
        state.accept(.30, 99, False)
        state.accept(.325, 99, False)
        state.accept(.85, None, True)
        self.assertIsNone(state.accept(.875, None, True))

    def test_one_frame_policy_still_requires_disappearance(self):
        state = GrowthEpisode(growth_frames_min=1)
        for i in range(12):
            state.accept(i * .025, 75, False)
        self.assertIsNone(state.accept(.30, 99, False))
        self.assertIsNone(state.accept(.325, None, True))
        self.assertEqual(.30, state.accept(.35, None, True)["growth_at"])

    def test_one_frame_growth_returning_to_normal_is_not_transition(self):
        state = GrowthEpisode(growth_frames_min=1)
        for i in range(12):
            state.accept(i * .025, 75, False)
        state.accept(.30, 99, False)
        state.accept(.325, 75, False)
        state.accept(.35, None, True)
        self.assertIsNone(state.accept(.375, None, True))

    def test_touch_inside_does_not_change_border(self):
        image = Image.new("RGB", (250, 250), "#182535")
        draw = ImageDraw.Draw(image)
        draw.ellipse((50,50,200,200), outline="white", width=5)
        before = measure_ring(image, (125,125), (60,110))
        draw.ellipse((115,115,135,135), fill="white")
        self.assertEqual(before, measure_ring(image, (125,125), (60,110)))
        self.assertIsNotNone(before[0])

if __name__ == "__main__":
    unittest.main()
