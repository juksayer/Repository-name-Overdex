import sys
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from inspect_charge_fill_layers import energy_hypotheses, intersect_hypotheses, separator, separator_by_contrast
from PIL import Image,ImageDraw

class FillLayersTest(unittest.TestCase):
    def test_layer_ambiguity_is_preserved(self):
        values = energy_hypotheses(35,75,35)
        self.assertEqual([0,1,2],[x["completed_layers"] for x in values])
        self.assertAlmostEqual(79.333333,values[-1]["energy"],places=5)

    def test_two_costs_resolve_ambiguous_layers(self):
        joint=intersect_hypotheses(energy_hypotheses(33.5,75,35),energy_hypotheses(7.5,75,55))
        self.assertEqual(1,len(joint))
        self.assertEqual((2,1),(joint[0]["left_layers"],joint[0]["right_layers"]))
        self.assertTrue(joint[0]["low"] < 80 < joint[0]["high"])

    def test_missing_or_conflicting_evidence_does_not_force_energy(self):
        self.assertEqual([],energy_hypotheses(None,75,35))
        self.assertEqual([],intersect_hypotheses(energy_hypotheses(55,75,35),[]))
        self.assertEqual([],intersect_hypotheses(energy_hypotheses(0,75,35),energy_hypotheses(0,75,55)))

    def test_whole_bright_disk_is_not_separator(self):
        self.assertIsNone(separator(Image.new("RGB",(200,200),"white"),100,100))

    def test_contrast_recovers_line_under_bright_upper_fill(self):
        image=Image.new("RGB",(200,200),"#111122")
        draw=ImageDraw.Draw(image)
        draw.rectangle((0,0,199,120),fill="#aaaaaa")
        draw.rectangle((20,120,180,123),fill="white")
        self.assertIsNone(separator(image,100,100))
        self.assertEqual(21.5,separator_by_contrast(image,100,100))

    def test_contrast_rejects_uniform_bright_and_multiple_lines(self):
        image=Image.new("RGB",(200,200),"white")
        self.assertIsNone(separator_by_contrast(image,100,100))
        image=Image.new("RGB",(200,200),"#111122")
        draw=ImageDraw.Draw(image)
        draw.line((20,80,180,80),fill="white",width=2)
        draw.line((20,120,180,120),fill="white",width=2)
        self.assertIsNone(separator_by_contrast(image,100,100))

    def test_single_line_outside_icon(self):
        image=Image.new("RGB",(200,200),"#003090")
        ImageDraw.Draw(image).rectangle((20,120,180,123),fill="white")
        self.assertEqual(21.5,separator(image,100,100))

if __name__=="__main__":
    unittest.main()
