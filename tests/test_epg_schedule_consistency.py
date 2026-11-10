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

    def test_instant_timestamp_variations_and_edge_cases(self):
        # Fast path 20-char tests (+0000 UTC, positive offset, negative offset)
        self.assertEqual(instant("20261106120000 +0000"), T)
        self.assertEqual(instant("20261106070000 -0500"), T)
        self.assertEqual(instant("   20261106120000 +0000   "), T)

        # Fallback path tests (12-char with zone, 12-char without zone, invalid)
        expected_12 = datetime(2026, 11, 6, 12, 0, 0, tzinfo=timezone.utc)
        self.assertEqual(instant("202611061200 +0000"), expected_12)
        self.assertEqual(instant("202611061200"), expected_12)
        self.assertEqual(instant("20261106120000"), expected_12)

        # Invalid strings and edge cases
        self.assertIsNone(instant(None))
        self.assertIsNone(instant(""))
        self.assertIsNone(instant("   "))
        self.assertIsNone(instant("invalid timestamp"))
        self.assertIsNone(instant("20261106990000 +0000"))  # Invalid hour
        self.assertIsNone(instant("20261106120000 +9999"))  # Invalid tz offset

    def test_matching_programmes(self):
        self.assertEqual(comparison(blocks("same"),blocks("same"),T),(96,96,0))
    def test_different_regional_timetables(self):
        shared,agree,conflict=comparison(blocks("a"),blocks("b"),T)
        self.assertEqual((shared,agree,conflict),(96,0,96))
    def test_reports_strong_and_conflict(self):
        rows=[row("one.xml.gz"),row("two.xml.gz","b")]
        program={("one.xml.gz","a"):blocks("x"),("two.xml.gz","b"):blocks("x")}
        self.assertEqual(produce(rows,program,T)[0]["finding"],"CROSS_SOURCE_SCHEDULE_AGREEMENT")
        program[("two.xml.gz","b")]=blocks("y")
        self.assertEqual(produce(rows,program,T)[0]["finding"],"REGIONAL_OR_SERVICE_CONFLICT")
    def test_same_feed_variants_not_independent_confirmation(self):
        rows=[row("samsung.xml.gz","a"),row("samsung.xml.gz","b")]
        programmes={("samsung.xml.gz","a"):blocks("same"),
                    ("samsung.xml.gz","b"):blocks("same")}
        self.assertEqual(produce(rows,programmes,T)[0]["finding"],"SAME_FEED_VARIANT_AGREEMENT")
    def test_different_source_conflict_is_not_applied(self):
        rows=[row("roku.xml.gz"),row("samsung.xml.gz","b")]
        programmes={("roku.xml.gz","a"):blocks("west"),
                    ("samsung.xml.gz","b"):blocks("east")}
        self.assertEqual(produce(rows,programmes,T)[0]["finding"],"REGIONAL_OR_SERVICE_CONFLICT")

    def test_held_or_identity_mismatch_not_compared(self):
        rows=[row("one.xml.gz"),row("two.xml.gz","b","BLOCKED_STREAM_IDENTITY_CONFLICT")]
        self.assertEqual(produce(rows,{},T),[])
    def test_insufficient_current_overlap(self):
        rows=[row("one.xml.gz"),row("two.xml.gz","b")]
        self.assertEqual(produce(rows,{},T)[0]["finding"],"INSUFFICIENT_SHARED_TIMES")
if __name__=="__main__": unittest.main()
