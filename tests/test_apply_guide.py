import unittest
from scripts.apply_guide import canonical, parse_entries, set_attr, set_name


class TestApplyGuide(unittest.TestCase):
    def test_canonical_normalization(self):
        self.assertEqual(canonical("10 ABC News (Australia) Geo"), "abc news")
        self.assertEqual(canonical("  Seven & Eight  "), "seven and eight")
        self.assertEqual(canonical("100   Test Channel!!!  "), "test channel")
        self.assertEqual(canonical(None), "")

    def test_set_attr_existing_attribute(self):
        extinf = '#EXTINF:-1 tvg-chno="10" group-title="General" tvg-name="Old Name", Old Name'
        updated = set_attr(extinf, "tvg-chno", "101")
        self.assertIn('tvg-chno="101"', updated)
        self.assertNotIn('tvg-chno="10"', updated)

    def test_set_attr_missing_attribute(self):
        extinf = '#EXTINF:-1 group-title="General", Old Name'
        updated = set_attr(extinf, "tvg-chno", "101")
        self.assertIn('#EXTINF:-1 tvg-chno="101"', updated)

    def test_set_name(self):
        extinf = '#EXTINF:-1 tvg-chno="101", Old Name'
        updated = set_name(extinf, "New Name")
        self.assertEqual(updated, '#EXTINF:-1 tvg-chno="101",New Name')

    def test_parse_entries(self):
        m3u_content = (
            '#EXTM3U\n'
            '#EXTINF:-1 tvg-id="ch1", Test Channel 1\n'
            'http://example.com/stream1.m3u8\n'
            '#EXTINF:-1 tvg-id="ch2", Test Channel 2\n'
            'http://example.com/stream2.m3u8\n'
        )
        header, entries = parse_entries(m3u_content)
        self.assertEqual(header, "#EXTM3U")
        self.assertEqual(len(entries), 2)
        self.assertEqual(entries[0], ("Test Channel 1", '#EXTINF:-1 tvg-id="ch1", Test Channel 1', "http://example.com/stream1.m3u8"))
        self.assertEqual(entries[1], ("Test Channel 2", '#EXTINF:-1 tvg-id="ch2", Test Channel 2', "http://example.com/stream2.m3u8"))


if __name__ == "__main__":
    unittest.main()
