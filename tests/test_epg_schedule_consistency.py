import unittest
from datetime import datetime,timedelta,timezone
from scripts.compare_epg_programmes import comparison,produce,instant

T=datetime(2026,11,6,12,tzinfo=timezone.utc)
def blocks(name,offset=0):
    return [(T+timedelta(hours=j),T+timedelta(hours=j+1),name+str(j+offset)) for j in range(24)]
def row(source,sid="a",status="REVIEW_REQUIRED"):
    return {"number":"307","channel_name":"MyTime Movie Network","candidate_source_file":source,"candidate_source_id":sid,"review_status":status}

class CompareProgrammeTests(unittest.TestCase):
    def test_timezone_correctness(self):
        self.assertEqual(instant("20261106230000 +1100"),T)
    def test_matching_programmes(self):
        self.assertEqual(comparison(blocks("same"),blocks("same"),T),(96,96,0))
    def test_different_regional_timetables(self):
        shared,agree,conflict=comparison(blocks("a"),blocks("b"),T)
        self.assertEqual((shared,agree,conflict),(96,0,96))
    def test_reports_strong_and_conflict(self):
        rows=[row("one.xml.gz"),row("two.xml.gz","b")]
        program={("one.xml.gz","a"):blocks("x"),("two.xml.gz","b"):blocks("x")}
        self.assertEqual(produce(rows,program,T)[0]["finding"],"STRONG_SCHEDULE_AGREEMENT")
        program[("two.xml.gz","b")]=blocks("y")
        self.assertEqual(produce(rows,program,T)[0]["finding"],"REGIONAL_OR_SERVICE_CONFLICT")
    def test_held_or_identity_mismatch_not_compared(self):
        rows=[row("one.xml.gz"),row("two.xml.gz","b","BLOCKED_STREAM_IDENTITY_CONFLICT")]
        self.assertEqual(produce(rows,{},T),[])
    def test_insufficient_current_overlap(self):
        rows=[row("one.xml.gz"),row("two.xml.gz","b")]
        self.assertEqual(produce(rows,{},T)[0]["finding"],"INSUFFICIENT_SHARED_TIMES")
if __name__=="__main__": unittest.main()
