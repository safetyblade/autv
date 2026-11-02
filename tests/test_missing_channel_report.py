"""Unit tests for read-only catalogue audit."""
import tempfile
import unittest
from pathlib import Path
from scripts.report_missing_channels import audit

class AuditTests(unittest.TestCase):
    def test_missing_and_extra_are_not_playback_verdicts(self):
        with tempfile.TemporaryDirectory() as tmp:
            guide = Path(tmp) / "guide.csv"
            playlist = Path(tmp) / "list.m3u"
            guide.write_text(
                "Channel number,Channel name,Genre,Source,Alternate source,Status\n"
                "501,Bloodline Detectives,Crime,Wurl,Pluto,Active\n"
                "510,FBI Files,Crime,Tubi,Pluto,Active\n"
                "516,The First 48,Crime,Xumo,Pluto,Active\n"
                "517,Retired Example,Crime,Test,,Inactive\n", encoding="utf-8")
            playlist.write_text(
                '#EXTM3U\n#EXTINF:-1 tvg-chno="501",Bloodline\nhttps://example.com/a.m3u8\n'
                '#EXTINF:-1 tvg-chno="516",First 48\nhttps://example.com/b.m3u8\n'
                '#EXTINF:-1 tvg-chno="999",Unknown\nhttps://example.com/c.m3u8\n',
                encoding="utf-8")
            missing, extra, dupes, approved, published = audit(guide, playlist)
            self.assertEqual([x["channel_number"] for x in missing], ["510"])
            self.assertEqual(missing[0]["playback_status"], "NOT_TESTED")
            self.assertEqual(missing[0]["disposition"], "HOLD_REPLACEMENT_FIRST")
            self.assertEqual(extra, ["999"])
            self.assertEqual(dupes, [])
            self.assertEqual((approved, published), (3, 3))

if __name__ == "__main__":
    unittest.main()
