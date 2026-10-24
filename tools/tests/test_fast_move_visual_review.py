import importlib.util
from pathlib import Path
import unittest
import numpy as np
from PIL import Image

spec = importlib.util.spec_from_file_location("visual_review", Path(__file__).parents[1] / "build_fast_move_visual_review.py")
review = importlib.util.module_from_spec(spec)
spec.loader.exec_module(review)


class VisualReviewTest(unittest.TestCase):
    def test_fixed_sampling_buckets_preserve_requested_twenty_fps(self):
        for source_fps in (24, 30):
            next_at, selected = 0, []
            for index in range(source_fps * 2):
                at = index / source_fps
                if at + 1e-9 >= next_at:
                    selected.append(at)
                    next_at = review.next_sample_time(0, at, 20)
            self.assertEqual(40, len(selected))
            self.assertEqual(sorted(set(selected)), selected)

    def test_source_geometry_clips_and_scales_axes_independently(self):
        p = dict(barLeft=5, barTop=10, barRight=25, barBottom=12, sourceWidth=100, sourceHeight=100)
        self.assertEqual([0, 5, 35, 42], review.sampling_bounds(p))
        _, bounds = review.sample_grid(Image.new("RGB", (200, 300)), p)
        self.assertEqual([0, 15, 70, 126], bounds)

    def test_missing_dimensions_or_invalid_rectangle_is_unavailable(self):
        self.assertIsNone(review.sampling_bounds({}))
        p = dict(barLeft=25, barTop=10, barRight=5, barBottom=12, sourceWidth=100, sourceHeight=100)
        self.assertIsNone(review.sampling_bounds(p))

    def test_join_does_not_borrow_future_or_other_side_geometry(self):
        def hp(side, time):
            return dict(articleId=str(time), monotonicTimeNanos=time,
                        payload=dict(kind="active_hp_bar_measured", side=side))
        t = review.Timeline(dict(matchId="test", articles=[
            dict(monotonicTimeNanos=0, payload=dict(kind="match_record_started")), hp("PLAYER", 100), hp("OPPONENT", 200)]))
        self.assertIsNone(t.hp_at("OPPONENT", 150, 1))
        self.assertEqual("100", t.hp_at("PLAYER", 100, 0)[0]["articleId"])
        self.assertIsNone(t.hp_at("PLAYER", 101, 0))

    def test_static_images_and_repeated_burst_do_not_multiply_candidates(self):
        detector = review.DiagnosticDetector()
        neutral = np.full((24, 24, 3), 80, dtype=np.uint8)
        burst = neutral.copy()
        burst[8:16, 12:20] = [235, 55, 35]
        bounds = [0, 0, 100, 100]
        self.assertEqual([], detector.accept(neutral, bounds, 1)["changed"])
        self.assertNotIn("candidate", detector.accept(burst, bounds, 1.1))
        self.assertNotIn("candidate", detector.accept(burst, bounds, 1.2))
        out = detector.accept(neutral, bounds, 1.3)
        self.assertAlmostEqual(1.1, out["candidate"]["peak"])
        self.assertNotIn("candidate", detector.accept(neutral, bounds, 1.4))

    def test_gap_and_region_relocation_reset_without_motion_claim(self):
        neutral = np.full((24,24,3), 80, dtype=np.uint8)
        for bounds, at in [([10,0,110,100], .1), ([0,0,100,100], .4)]:
            d = review.DiagnosticDetector()
            d.accept(neutral, [0,0,100,100], 0)
            m = d.accept(neutral+100, bounds, at)
            self.assertEqual([], m["changed"])
            self.assertIsNone(m["arrow"])
            self.assertNotIn("candidate", m)

    def test_clock_origin_must_be_explicit(self):
        with self.assertRaises(ValueError):
            review.Timeline(dict(matchId="test", articles=[]))


if __name__ == "__main__":
    unittest.main()
