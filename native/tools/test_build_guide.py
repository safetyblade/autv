import csv
import json
from pathlib import Path
import tempfile
import unittest

from build_guide import HEADERS, build


class BuildGuideTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        root = Path(self.directory.name)
        self.guide, self.playlist, self.output = root / "guide.csv", root / "playlist.m3u", root / "guide.json"
        self.playlist.write_text('#EXTM3U\n#EXTINF:-1 tvg-chno="100" tvg-id="news",News\nhttps://example.com/live.m3u8\n')
        self.output.write_text('{"channels":[{"number":297}]}')

    def write_rows(self, *rows):
        with self.guide.open("w", newline="") as handle:
            writer = csv.writer(handle)
            writer.writerow(HEADERS)
            writer.writerows(rows)

    def row(self, number=100, status="Active"):
        return [number, "News", "News", "Australian / Local", "Full", "Curated", "", status, "Description"]

    def run_build(self):
        return build(self.guide, self.playlist, self.output)

    def assert_rejected(self):
        original = self.output.read_text()
        with self.assertRaises((ValueError, csv.Error)):
            self.run_build()
        self.assertEqual(original, self.output.read_text())

    def test_extra_csv_fields_are_rejected_without_overwriting_output(self):
        self.write_rows(self.row() + ["spillover", "more"])
        self.assert_rejected()

    def test_broken_quoting_is_rejected(self):
        self.write_rows(self.row())
        with self.guide.open("a") as handle:
            handle.write('296,Formula 1,Sport,Motorsport,Channel only,Samsung US,,Active,"description", archive, extras"\n')
        self.assert_rejected()

    def test_duplicate_numbers_are_rejected_including_inactive_rows(self):
        self.write_rows(self.row(), self.row(status="Inactive"))
        self.assert_rejected()

    def test_missing_required_fields_and_short_rows_are_rejected(self):
        for index in (0, 1, 2, 3, 4, 5, 7, 8):
            row = self.row()
            row[index] = ""
            self.write_rows(row)
            self.assert_rejected()
        self.write_rows(self.row()[:-1])
        self.assert_rejected()

    def test_missing_headers_are_rejected(self):
        self.guide.write_text("Channel number,Channel name\n100,News\n")
        self.assert_rejected()

    def test_refresh_removes_old_channel_even_when_stale_playlist_has_it(self):
        self.write_rows(self.row())
        with self.playlist.open("a") as handle:
            handle.write('#EXTINF:-1 tvg-chno="297",Removed\nhttps://example.com/stale.m3u8\n')
        result = self.run_build()
        self.assertEqual([100], [channel["number"] for channel in result["channels"]])
        self.assertEqual(result, json.loads(self.output.read_text()))
        self.assertEqual(1, result["activeCount"])
        self.assertEqual(1, result["availableCount"])

    def test_only_active_rows_are_emitted_and_unavailable_is_allowed(self):
        self.write_rows(self.row(200), self.row(100), self.row(300, "Inactive"))
        result = self.run_build()
        self.assertEqual([100, 200], [channel["number"] for channel in result["channels"]])
        self.assertEqual(2, result["activeCount"])
        self.assertEqual(1, result["availableCount"])
        self.assertIsNone(result["channels"][1]["streamUrl"])

    def test_missing_playlist_url_cannot_borrow_the_next_channels_stream(self):
        self.write_rows(self.row(100), self.row(200))
        self.playlist.write_text('#EXTM3U\n#EXTINF:-1 tvg-chno="100",News\n#EXTINF:-1 tvg-chno="200",Other\nhttps://example.com/other.m3u8\n')
        result = self.run_build()
        self.assertIsNone(result["channels"][0]["streamUrl"])
        self.assertEqual(1, result["availableCount"])


if __name__ == "__main__":
    unittest.main()
