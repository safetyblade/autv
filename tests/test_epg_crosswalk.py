import csv
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

from scripts.epg_crosswalk import load_crosswalk

HEADER="target_tvg_id,source_tvg_id,source_file,approved_by,reason\n"
SOURCES=[Path("epg_plex_au.xml.gz"),Path("epg_roku_all.xml.gz")]

class TestEpgCrosswalk(unittest.TestCase):
    def check(self, body, targets=None):
        with TemporaryDirectory() as directory:
            path=Path(directory)/"epg_channel_crosswalk.csv"
            path.write_text(HEADER+body,encoding="utf-8")
            return load_crosswalk(path, targets or {"MUTV.uk@SD"}, SOURCES)

    def test_empty_mapping_does_not_change_existing_guide(self):
        self.assertEqual(self.check(""), {})

    def test_approved_known_id_is_accepted(self):
        mapping=self.check("MUTV.uk@SD,mutv-verified,epg_plex_au.xml.gz,Mara,Confirmed same programme service and region\n")
        self.assertEqual(mapping["MUTV.uk@SD"].source_id,"mutv-verified")
        self.assertEqual(mapping["MUTV.uk@SD"].source_file,"epg_plex_au.xml.gz")

    def test_unpublished_targets_rejected(self):
        with self.assertRaisesRegex(ValueError,"Unpublished"):
            self.check("Unknown,src,epg_plex_au.xml.gz,Mara,Verified channel evidence\n")

    def test_unapproved_or_short_reason_rejected(self):
        with self.assertRaises(ValueError):
            self.check("MUTV.uk@SD,src,epg_plex_au.xml.gz,,Verified channel evidence\n")
        with self.assertRaises(ValueError):
            self.check("MUTV.uk@SD,src,epg_plex_au.xml.gz,Mara,short\n")

    def test_unknown_source_rejected(self):
        with self.assertRaisesRegex(ValueError,"source"):
            self.check("MUTV.uk@SD,src,epg_pluto_ru.xml.gz,Mara,Confirmed same programme service and region\n")

    def test_duplicate_targets_rejected(self):
        record="MUTV.uk@SD,src,epg_plex_au.xml.gz,Mara,Confirmed same programme service and region\n"
        with self.assertRaisesRegex(ValueError,"Duplicate"):
            self.check(record*2)

    def test_explicit_identity_hold_blocks_approval(self):
        with TemporaryDirectory() as directory:
            folder=Path(directory)
            (folder/"epg_channel_crosswalk.csv").write_text(
                HEADER+"MUTV.uk@SD,src,epg_plex_au.xml.gz,Mara,Confirmed same service and territory\n",
                encoding="utf-8")
            (folder/"epg_crosswalk_holds.csv").write_text(
                "target_tvg_id,reason,reference\n"
                "MUTV.uk@SD,Conflicting stream identity pending physical confirmation,https://example.invalid/issue\n",
                encoding="utf-8")
            with self.assertRaisesRegex(ValueError,"identity hold"):
                load_crosswalk(folder/"epg_channel_crosswalk.csv",{"MUTV.uk@SD"},SOURCES)

    def test_bad_header_rejected(self):
        with TemporaryDirectory() as directory:
            p=Path(directory)/"bad.csv"
            p.write_text("target,source\nfoo,bar\n",encoding="utf-8")
            with self.assertRaisesRegex(ValueError,"header"):
                load_crosswalk(p,{"foo"},SOURCES)

if __name__=="__main__":
    unittest.main()
