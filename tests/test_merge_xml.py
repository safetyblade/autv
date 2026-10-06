import unittest
from scripts.merge import xml_escape

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
