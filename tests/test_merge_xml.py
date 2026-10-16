from pathlib import Path
import tempfile
import unittest
from scripts.merge import xml_escape, add_selected

class TestXmlEscape(unittest.TestCase):
    def test_xml_escape_quotes_and_entities(self):
        self.assertEqual(
            xml_escape('channel"id\' & <tag>'),
            'channel&quot;id&apos; &amp; &lt;tag&gt;'
        )

    def test_xml_escape_empty_or_none(self):
        self.assertEqual(xml_escape(""), "")
        self.assertEqual(xml_escape(None), "")


class TestAddSelected(unittest.TestCase):
    def test_add_selected_exact_substring_and_missing(self):
        entries = [
            ("CNN International", '#EXTINF:-1 tvg-id="cnn" tvg-name="CNN International",CNN International', "http://example.com/cnn.m3u8"),
            ("BBC News", '#EXTINF:-1 tvg-id="bbc" tvg-name="BBC News",BBC News', "http://example.com/bbc.m3u8"),
            ("BBC News HD", '#EXTINF:-1 tvg-id="bbc-hd" tvg-name="BBC News HD",BBC News HD', "http://example.com/bbchd.m3u8"),
        ]

        with tempfile.TemporaryDirectory() as tmp_dir:
            selected_file = Path(tmp_dir) / "selected.txt"
            selected_file.write_text("cnn international\nBBC\nNonExistent Channel\n", encoding="utf-8")

            extras = []
            xml_channels = []
            missing = add_selected("TEST", entries, selected_file, extras, xml_channels)

            self.assertEqual(missing, ["NonExistent Channel"])
            self.assertEqual(len(extras), 2)
            self.assertIn("CNN International", extras[0])
            # "BBC" should match "BBC News" via substring match
            self.assertIn("BBC News", extras[1])

    def test_add_selected_first_match_preservation(self):
        # Ensure duplicate case-insensitive names resolve to the FIRST match in entries
        entries = [
            ("Discovery Channel", '#EXTINF:-1 tvg-id="disc1",Discovery Channel', "http://example.com/disc1.m3u8"),
            ("discovery channel", '#EXTINF:-1 tvg-id="disc2",discovery channel', "http://example.com/disc2.m3u8"),
        ]

        with tempfile.TemporaryDirectory() as tmp_dir:
            selected_file = Path(tmp_dir) / "selected.txt"
            selected_file.write_text("Discovery Channel\n", encoding="utf-8")

            extras = []
            xml_channels = []
            missing = add_selected("TEST", entries, selected_file, extras, xml_channels)

            self.assertEqual(missing, [])
            self.assertEqual(len(extras), 1)
            self.assertIn("http://example.com/disc1.m3u8", extras[0])


if __name__ == "__main__":
    unittest.main()
