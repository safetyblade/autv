import csv
import gzip
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts/build_epg.py"
HEADER = "target_tvg_id,source_tvg_id,source_file,approved_by,reason\n"

class TestEpgBuilderCrosswalkIntegration(unittest.TestCase):
    def run_fixture(self, crosswalk, source_file="epg_plex_au.xml.gz"):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root/"playlist.m3u").write_text(
                '#EXTM3U\n#EXTINF:-1 tvg-chno="216" tvg-id="MUTV.uk@SD" tvg-name="MUTV" group-title="Sport",MUTV\nhttps://example.invalid/test.m3u8\n',
                encoding="utf-8")
            (root/"channel_guide.csv").write_text(
                "Channel number,Channel name,Genre,Subgenre,EPG,Source,Alternate source,Status,Description\n"
                "216,MUTV,Sport,Football,Channel only,Curated,,Active,Fixture\n",
                encoding="utf-8")
            (root/"epg_channel_crosswalk.csv").write_text(HEADER+crosswalk, encoding="utf-8")
            tv = ET.Element("tv")
            ch = ET.SubElement(tv,"channel",id="verified-provider-mutv")
            ET.SubElement(ch,"display-name").text="Verified provider's stable identifier"
            prog=ET.SubElement(tv,"programme",
                channel="verified-provider-mutv",start="20261106000000 +0000",stop="20261106010000 +0000")
            ET.SubElement(prog,"title").text="Programme from verified source"
            with gzip.open(root/source_file,"wb") as handle:
                handle.write(ET.tostring(tv,encoding="utf-8",xml_declaration=True))
            result=subprocess.run([sys.executable,str(SCRIPT)],cwd=root,text=True,capture_output=True)
            if result.returncode:
                return result,None,None
            with gzip.open(root/"epg.xml.gz","rb") as stream:
                epg=ET.parse(stream).getroot()
            with (root/"audits/epg_gate1_match_trace.csv").open(newline="",encoding="utf-8") as stream:
                trace=list(csv.DictReader(stream))
            return result,epg,trace

    def test_approved_crosswalk_retrieves_a_differently_named_channel(self):
        entry="MUTV.uk@SD,verified-provider-mutv,epg_plex_au.xml.gz,Mara,Verified same channel and region from source evidence\n"
        run,epg,trace=self.run_fixture(entry)
        self.assertEqual(run.returncode,0,run.stderr)
        self.assertEqual(epg.find("programme").attrib["channel"],"MUTV.uk@SD")
        self.assertEqual(epg.find("programme/title").text,"Programme from verified source")
        self.assertEqual(trace[0]["method"],"approved_crosswalk")
        self.assertEqual(trace[0]["source_file"],"epg_plex_au.xml.gz")

    def test_wrong_source_is_not_silently_substituted(self):
        entry="MUTV.uk@SD,verified-provider-mutv,epg_roku_all.xml.gz,Mara,Verified same channel and region from source evidence\n"
        run,_,_=self.run_fixture(entry)
        self.assertNotEqual(run.returncode,0)
        self.assertIn("wrong provider",run.stderr)

    def test_no_override_cannot_invent_an_unrelated_schedule(self):
        run,epg,trace=self.run_fixture("")
        self.assertEqual(run.returncode,0,run.stderr)
        self.assertEqual(len(epg.findall("programme")),0)
        self.assertEqual(trace[0]["method"],"missing_source")

if __name__=="__main__":
    unittest.main()
