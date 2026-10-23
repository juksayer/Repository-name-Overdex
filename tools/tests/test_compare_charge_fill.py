import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from compare_charge_fill import compare_pair

class ComparisonTest(unittest.TestCase):
    def sample(self, offset):
        return dict(status='EXPERIMENTAL_SEPARATOR', centerY=100, separatorY=100+offset,
                    radius=75, pixelTolerance=2)
    def test_joint_intersection_is_independent_of_count(self):
        left, right = self.sample(33.5), self.sample(7)
        good=compare_pair(left,right,[35,55],80)
        bad=compare_pair(left,right,[35,55],20)
        self.assertEqual('COMPATIBLE',good['status'])
        self.assertEqual('COUNT_DISAGREES',bad['status'])
        self.assertEqual(good['candidates'],bad['candidates'])
    def test_missing_is_not_zero(self):
        self.assertEqual('UNKNOWN',compare_pair(None,self.sample(7),[35,55],0)['status'])
    def test_archive_cli_uses_serialized_article_fields(self):
        import json, subprocess, tempfile, zipfile
        with tempfile.TemporaryDirectory() as directory:
            archive=Path(directory)/'sample.zip'
            articles=[dict(articleId=str(slot),monotonicTimeNanos=100,
                           payload=dict(kind='player_charge_button_fill_height_measured',slot=slot,
                                        **self.sample(offset))) for slot,offset in [(1,33.5),(2,7)]]
            with zipfile.ZipFile(archive,'w') as z:
                z.writestr('timeline.json',json.dumps(dict(articles=articles)))
            output=subprocess.check_output([sys.executable,str(Path(__file__).resolve().parents[1]/'compare_charge_fill.py'),
                str(archive),'--costs','35','55','--counted-energy','80','--start-nanos','100','--end-nanos','100'])
            row=json.loads(output)['results'][0]
            self.assertEqual('COMPATIBLE',row['status'])
            self.assertEqual(['1','2'],row['source_articles'])
