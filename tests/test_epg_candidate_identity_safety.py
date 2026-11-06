import unittest
from scripts.triage_epg_candidate_identity import stream_clues,triage

class TestEpgCandidateIdentitySafety(unittest.TestCase):
    def listing(self,number,name,url):
        return {number:{"channel_name":name,"tvg_id":"autv.fixture","stream":url}}
    def row(self,number,name,candidate="123",source="epg_pluto_gb.xml.gz"):
        return {"number":number,"channel_name":name,"candidate_source_id":candidate,
                "source_file":source,"source_display_name":name,"current_programmes":"1"}
    def test_known_bloodline_medical_identity_conflict(self):
        url="https://4399c7ad.wurl.com/master/f36d25e7e52f1ba8d7e56eb859c636563214f541/U2Ftc3VuZy1nYl9NZWRpY2FsRGV0ZWN0aXZlc19ITFM/playlist.m3u8"
        clues=stream_clues(url)
        self.assertIn("MedicalDetectives",clues["embedded_identity"])
        result=triage([self.row("501","Bloodline Detectives")],
                      self.listing("501","Bloodline Detectives",url))
        self.assertEqual(result[0]["review_status"],"BLOCKED_STREAM_IDENTITY_CONFLICT")
        self.assertEqual(result[0]["approved_crosswalk"],"NO")
    def test_xumo_stream_id_disagrees_with_epg_candidate(self):
        url="https://example.com/master.m3u8?ads.xumo_channelId=99984056&ads.xumo_streamId=99984056"
        result=triage([self.row("759","Xena Warrior Princess","99951412","epg_xumo.xml.gz")],
                      self.listing("759","Xena Warrior Princess",url))
        self.assertEqual(result[0]["review_status"],"PROVIDER_ID_MISMATCH")
    def test_no_candidates_never_approved(self):
        result=triage([self.row("216","MUTV","","")],self.listing("216","MUTV","https://example.com/live.m3u8"))
        self.assertEqual(result[0]["review_status"],"SOURCE_DISCOVERY_REQUIRED")
    def test_matching_xumo_ids_are_still_not_autoapproved(self):
        result=triage([self.row("759","Xena Warrior Princess","99984056","epg_xumo.xml.gz")],
                      self.listing("759","Xena Warrior Princess","https://example.com/live.m3u8?ads.xumo_channelId=99984056"))
        self.assertEqual(result[0]["review_status"],"REVIEW_REQUIRED")
        self.assertEqual(result[0]["approved_crosswalk"],"NO")
if __name__=="__main__":unittest.main()
