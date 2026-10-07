import unittest
from xml.sax.saxutils import escape
from scripts.merge import escape_attr, normalise


class MergeScriptTests(unittest.TestCase):
    def test_escape_attr_escapes_quotes_and_xml_chars(self):
        self.assertEqual(escape_attr('channel"1'), 'channel&quot;1')
        self.assertEqual(escape_attr('<a&b">'), '&lt;a&amp;b&quot;&gt;')

    def test_normalise_and_escape_attr_handles_quotes(self):
        m3u, xml = normalise('Name"1', '#EXTINF:-1 group-title="Australia", Name"1', 'https://example.com/stream.m3u8')
        xml['id'] = 'autv.id"1'
        xml['logo'] = 'https://example.com/logo.png?a="1"'

        formatted_id_attr = f'id="{escape_attr(xml["id"])}"'
        formatted_logo_attr = f'src="{escape_attr(xml["logo"])}"'

        self.assertEqual(formatted_id_attr, 'id="autv.id&quot;1"')
        self.assertEqual(formatted_logo_attr, 'src="https://example.com/logo.png?a=&quot;1&quot;"')


if __name__ == "__main__":
    unittest.main()
