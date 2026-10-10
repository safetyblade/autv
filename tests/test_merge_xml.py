import unittest
from scripts.merge import normalise, xml_escape

class TestXmlEscape(unittest.TestCase):
    def test_normalise_sanitizes_m3u_fields(self):
        extinf = '#EXTINF:-1 tvg-id="id\n1" tvg-name="Name \n Line" tvg-logo="http://logo.png\n" group-title="Group \n Title", Untrusted Name\nHeader'
        m3u, xml = normalise('Untrusted Name\nHeader', extinf, 'https://example.com/stream.m3u8')
        lines = m3u.splitlines()
        self.assertEqual(len(lines), 2)
        self.assertNotIn('\r', lines[0])
        self.assertNotIn('\n', lines[0])
        self.assertIn('tvg-name="Name   Line"', lines[0])
        self.assertIn('group-title="Group   Title"', lines[0])
        self.assertTrue(lines[0].endswith(',Untrusted Name Header'))

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
