import unittest
from scripts.epg_gate1_priority import prioritise

def row(number,status,coverage="0",published="YES"):
    return {"number":str(number),"name":f"Channel {number}","genre":"Sport",
            "tvg_id":f"id{number}","status":status,"published":published,
            "forward_24h_coverage_pct":coverage,"programme_events":"0"}

class TestEpgPriority(unittest.TestCase):
    def test_missing_record_first_then_stale_then_gaps(self):
        items=prioritise([row(3,"CURRENT_PROGRAMME","40"),row(2,"NO_CURRENT_PROGRAMME","90"),
                          row(1,"NO_EPG_CHANNEL"),row(4,"CURRENT_PROGRAMME","95")])
        self.assertEqual([x["number"] for x in items],["1","2","3"])
        self.assertEqual([x["priority"] for x in items],[1,2,3])
    def test_never_prioritise_unpublished_channels(self):
        self.assertEqual(prioritise([row(1,"NO_EPG_CHANNEL",published="NO")]),[])
    def test_empty_and_fully_covered(self):
        self.assertEqual(prioritise([row(1,"CURRENT_PROGRAMME","100")]),[])
if __name__=="__main__":unittest.main()
