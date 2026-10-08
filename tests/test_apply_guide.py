import unittest
from scripts.apply_guide import set_attr, set_name

class TestApplyGuideSecurity(unittest.TestCase):
    def test_set_attr_with_special_regex_chars(self):
        extinf = '#EXTINF:-1 tvg-name="Old Name" group-title="Old Group",Channel Name'

        # Test backslash and regex backreference replacement (e.g. \1, \g<0>)
        result = set_attr(extinf, "tvg-name", r"AC\DC \1 \g<0>")
        self.assertIn('tvg-name="AC\\DC \\1 \\g<0>"', result)

        # Ensure it works when attribute is missing
        result_new = set_attr('#EXTINF:-1,Channel Name', 'tvg-chno', '101')
        self.assertIn('tvg-chno="101"', result_new)

    def test_set_name_with_special_regex_chars(self):
        extinf = '#EXTINF:-1 tvg-name="Channel",Old Channel Name'

        # Test backslash and regex backreferences in channel display name
        result = set_name(extinf, r"Channel 24/7 \1 \g<0>")
        self.assertEqual(result, r'#EXTINF:-1 tvg-name="Channel",Channel 24/7 \1 \g<0>')

if __name__ == "__main__":
    unittest.main()
