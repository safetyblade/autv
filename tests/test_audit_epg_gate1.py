import datetime as dt
import unittest
from scripts.audit_epg_gate1 import parse_xmltv_time


class TestAuditEpgGate1(unittest.TestCase):
    def test_parse_xmltv_time_standard_utc(self):
        parsed = parse_xmltv_time("20261107190000 +0000")
        expected = dt.datetime(2026, 11, 7, 19, 0, 0, tzinfo=dt.timezone.utc)
        self.assertEqual(parsed, expected)

    def test_parse_xmltv_time_with_positive_offset(self):
        parsed = parse_xmltv_time("20261107190000 +1000")
        expected = dt.datetime(2026, 11, 7, 9, 0, 0, tzinfo=dt.timezone.utc)
        self.assertEqual(parsed, expected)

    def test_parse_xmltv_time_with_negative_offset(self):
        parsed = parse_xmltv_time("20261107190000 -0500")
        expected = dt.datetime(2026, 11, 8, 0, 0, 0, tzinfo=dt.timezone.utc)
        self.assertEqual(parsed, expected)

    def test_parse_xmltv_time_without_offset(self):
        parsed = parse_xmltv_time("20261107190000")
        expected = dt.datetime(2026, 11, 7, 19, 0, 0, tzinfo=dt.timezone.utc)
        self.assertEqual(parsed, expected)

    def test_parse_xmltv_time_without_seconds(self):
        parsed = parse_xmltv_time("202611071900")
        expected = dt.datetime(2026, 11, 7, 19, 0, 0, tzinfo=dt.timezone.utc)
        self.assertEqual(parsed, expected)

    def test_parse_xmltv_time_without_seconds_with_offset(self):
        parsed = parse_xmltv_time("202611071900 +1000")
        expected = dt.datetime(2026, 11, 7, 9, 0, 0, tzinfo=dt.timezone.utc)
        self.assertEqual(parsed, expected)

    def test_parse_xmltv_time_invalid_values(self):
        self.assertIsNone(parse_xmltv_time(None))
        self.assertIsNone(parse_xmltv_time(""))
        self.assertIsNone(parse_xmltv_time("   "))
        self.assertIsNone(parse_xmltv_time("not-a-timestamp"))
        self.assertIsNone(parse_xmltv_time("20269999999999 +0000"))
        self.assertIsNone(parse_xmltv_time("20269999999999"))


if __name__ == "__main__":
    unittest.main()
