import unittest
from scripts.apply_guide import set_attr, set_name

class TestApplyGuideSecurity(unittest.TestCase):
    def test_set_attr_backreference_safety(self):
        extinf = '#EXTINF:-1 tvg-name="Old Name", Old Name'
        result = set_attr(extinf, "tvg-name", r"Channel \1 & \2")
        self.assertIn('tvg-name="Channel \\1 & \\2"', result)

    def test_set_attr_quote_and_newline_sanitization(self):
        extinf = '#EXTINF:-1 tvg-name="Old Name", Old Name'
        result = set_attr(extinf, "tvg-name", 'Channel "Quote"\nNew Directive')
        self.assertIn('tvg-name="Channel \'Quote\' New Directive"', result)
        self.assertNotIn('\n', result)
        self.assertNotIn('"Quote"', result)

    def test_set_name_backreference_and_newline_safety(self):
        extinf = '#EXTINF:-1 tvg-name="Name", Old Name'
        result = set_name(extinf, "New Name \\1\nDirective")
        self.assertTrue(result.endswith(",New Name \\1 Directive"))
        self.assertNotIn('\n', result)

    def test_set_attr_new_attribute(self):
        extinf = '#EXTINF:-1 tvg-name="Name", Old Name'
        result = set_attr(extinf, "group-title", "News")
        self.assertIn('group-title="News"', result)

    def test_set_attr_prefixed_key_boundary_safety(self):
        extinf = '#EXTINF:-1 custom-tvg-name="fake_name" tvg-name="old_name", Channel'
        result = set_attr(extinf, "tvg-name", "new_name")
        self.assertIn('custom-tvg-name="fake_name"', result)
        self.assertIn('tvg-name="new_name"', result)

    def test_canonical_normalization(self):
        from scripts.apply_guide import canonical
        self.assertEqual(canonical("101  SpongeBob (AU) geo "), "spongebob")
        self.assertEqual(canonical("Rock & Roll"), "rock and roll")

if __name__ == "__main__":
    unittest.main()
