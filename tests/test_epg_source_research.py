import gzip
import csv
import tempfile
import unittest
from datetime import datetime,timezone
from pathlib import Path
from scripts.research_epg_sources import canonical, similarity, inspect_source, candidate_report

NOW=datetime(2026,11,6,12,tzinfo=timezone.utc)
class ResearchTests(unittest.TestCase):
    def source(self, p):
        xml='''<tv>
        <channel id="mutv-live"><display-name>MUTV</display-name></channel>
        <channel id="charge"><display-name>CHARGE!</display-name></channel>
        <channel id="totally-other"><display-name>Drama Network</display-name></channel>
        <programme channel="mutv-live" start="20261106110000 +0000" stop="20261106130000 +0000"><title>Fixture</title></programme>
        <programme channel="mutv-live" start="20261106130000 +0000" stop="20261106140000 +0000"><title>Fixture 2</title></programme>
        </tv>'''
        with gzip.open(p,"wb") as f:f.write(xml.encode())
    def test_exact_names_without_false_matches(self):
        self.assertEqual(similarity("CHARGE!","CHARGE")[0],100)
        self.assertEqual(similarity("MUTV","Manchester United TV")[0],0)
    def test_candidate_has_source_and_real_time(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/"epg_plex_au.xml.gz";self.source(p)
            q=[{"number":"216","name":"MUTV"},{"number":"848","name":"CHARGE!"}]
            rows=inspect_source(p,q,NOW)
            byname={x["channel_name"]:x for x in rows}
            self.assertEqual(byname["MUTV"]["current_programmes"],1)
            self.assertEqual(byname["MUTV"]["future_24h_programmes"],1)
            self.assertEqual(byname["MUTV"]["source_file"],p.name)
            self.assertEqual(byname["CHARGE!"]["current_programmes"],0)
    def test_all_unmatched_channels_preserved_even_without_sources(self):
        queue=[{"priority":"1","status":"NO_EPG_CHANNEL","number":"216","name":"MUTV"},
               {"priority":"1","status":"NO_EPG_CHANNEL","number":"847","name":"Comet TV"},
               {"priority":"2","status":"NO_CURRENT_PROGRAMME","number":"15","name":"Other"}]
        rows,available=candidate_report(queue,[],NOW)
        self.assertEqual(len(rows),2)
        self.assertTrue(all(x["status"]=="SOURCE_DISCOVERY_REQUIRED" for x in rows))
        self.assertEqual(available,[])
if __name__=="__main__": unittest.main()
