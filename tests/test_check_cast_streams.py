import unittest
import sys
from pathlib import Path

# Add native directory to path so check_cast_streams can be imported
native_path = Path(__file__).resolve().parents[1] / "native"
if str(native_path) not in sys.path:
    sys.path.insert(0, str(native_path))

from check_cast_streams import probe

class TestCheckCastStreamsSecurity(unittest.TestCase):
    def test_probe_unsupported_scheme_file(self):
        item = ("TestProvider", "TestChannel", "file:///etc/passwd")
        result = probe(item)
        self.assertEqual(result["result"], "Unsupported URL scheme: file")
        self.assertEqual(result["requests"], [])

    def test_probe_unsupported_scheme_ftp(self):
        item = ("TestProvider", "TestChannel", "ftp://example.com/stream.m3u8")
        result = probe(item)
        self.assertEqual(result["result"], "Unsupported URL scheme: ftp")
        self.assertEqual(result["requests"], [])

if __name__ == "__main__":
    unittest.main()
