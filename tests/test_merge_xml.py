import unittest
from scripts.merge import xml_escape, normalise, sanitize_attr, sanitize_line

class TestMergeNormalizationSecurity(unittest.TestCase):
    def test_normalise_attribute_breakout_and_line_injection_sanitization(self):
        extinf = '#EXTINF:-1 tvg-id="id1" tvg-logo="https://example.com/logo.png" group-title="News\nInject", News'
        name = 'News "Live"\nDirective'
        url = "https://example.com/stream.m3u8\nhttp://malicious.stream"

        m3u, xml = normalise(name, extinf, url)

        self.assertEqual(len(m3u.splitlines()), 2)
        self.assertNotIn('\r', m3u.splitlines()[0])
        self.assertNotIn('\n', m3u.splitlines()[0])
        self.assertIn('tvg-name="News \'Live\' Directive"', m3u)
        self.assertIn('group-title="News Inject"', m3u)
        self.assertEqual(xml["name"], "News 'Live' Directive")

    def test_sanitize_attr_and_line(self):
        self.assertEqual(sanitize_attr('Test "Quotes"\r\nNewline'), "Test 'Quotes'  Newline")
        self.assertEqual(sanitize_line('Test "Quotes"\r\nNewline'), 'Test "Quotes"  Newline')


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
