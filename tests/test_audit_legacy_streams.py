import unittest
import sys
from pathlib import Path

# Ensure root directory is in sys.path to import scripts
ROOT = Path(__file__).resolve().parent.parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from scripts.audit_legacy_streams import fetch, probe

class TestAuditLegacyStreams(unittest.TestCase):
    def test_fetch_rejects_non_http_schemes(self):
        invalid_urls = [
            "file:///etc/passwd",
            "ftp://example.com/stream.m3u8",
            "gopher://example.com",
            "javascript:alert(1)",
        ]
        for url in invalid_urls:
            with self.subTest(url=url):
                with self.assertRaises(ValueError) as ctx:
                    fetch(url)
                self.assertIn("Unsupported URL scheme", str(ctx.exception))

    def test_probe_handles_invalid_scheme(self):
        number, status = probe((1214, "file:///etc/passwd"))
        self.assertEqual(number, 1214)
        self.assertTrue(status.startswith("suspect: ValueError: Unsupported URL scheme"))

if __name__ == "__main__":
    unittest.main()
