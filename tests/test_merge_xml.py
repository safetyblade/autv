import unittest
from scripts.merge import normalise, xml_escape

class TestNormaliseSanitization(unittest.TestCase):
    def test_normalise_sanitizes_newlines_and_quotes(self):
        extinf = '#EXTINF:-1 tvg-id="id-123" tvg-name="Name With Newline" tvg-logo="http://logo.com/a.png" group-title="Group Title",Channel'
        m3u, xml = normalise("Channel\n\"Quote\"", extinf, "https://example.com/stream\r\n.m3u8")

        self.assertNotIn("\r", m3u)
        # m3u has exactly 2 lines: EXTINF line and URL line
        lines = m3u.splitlines()
        self.assertEqual(len(lines), 2)
        self.assertTrue(lines[0].startswith("#EXTINF:-1 "))
        self.assertEqual(lines[1], "https://example.com/stream  .m3u8")

        # Newline and quote sanitization in channel name and url
        self.assertTrue(lines[0].endswith(',Channel "Quote"'))

        extinf_quoted = '#EXTINF:-1 tvg-id="id-123" tvg-name="Name" tvg-logo="http://logo.com/a.png" group-title="Group",Channel'
        # Test fallback when tvg-name is derived from name with newlines and quotes
        m3u_q, xml_q = normalise('Name\n"Quote"', '#EXTINF:-1,Channel', 'https://example.com')
        lines_q = m3u_q.splitlines()
        self.assertIn('tvg-name="Name \'Quote\'"', lines_q[0])
        self.assertEqual(xml_q["name"], "Name 'Quote'")

class TestXmlEscape(unittest.TestCase):
    def test_xml_escape_quotes_and_entities(self):
        self.assertEqual(
            xml_escape('channel"id\' & <tag>'),
            'channel&quot;id&apos; &amp; &lt;tag&gt;'
        )

    def test_xml_escape_empty_or_none(self):
        self.assertEqual(xml_escape(""), "")
        self.assertEqual(xml_escape(None), "")

if __name__ == "__main__":
    unittest.main()
