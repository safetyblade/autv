import sys
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import audit_legacy_streams as audit_mod

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "native"))
import check_cast_streams as cast_mod


class TestSchemeValidation(unittest.TestCase):
    def test_audit_legacy_streams_disallowed_scheme(self):
        with self.assertRaises(ValueError) as ctx:
            audit_mod.fetch("file:///etc/passwd")
        self.assertIn("Unsupported URL scheme: file", str(ctx.exception))

    def test_check_cast_streams_disallowed_scheme(self):
        with self.assertRaises(ValueError) as ctx:
            cast_mod.fetch("file:///etc/passwd")
        self.assertIn("Unsupported URL scheme: file", str(ctx.exception))

    def test_audit_legacy_streams_disallowed_redirect_scheme(self):
        class MockResponse:
            status = 200
            def geturl(self):
                return "file:///etc/passwd"
            def read(self, cap=32768):
                return b"secret"
            def __enter__(self):
                return self
            def __exit__(self, *args):
                pass

        with patch("audit_legacy_streams.urlopen", return_value=MockResponse()):
            with self.assertRaises(ValueError) as ctx:
                audit_mod.fetch("https://example.com/stream.m3u8")
            self.assertIn("Unsupported URL scheme: file", str(ctx.exception))

    def test_check_cast_streams_disallowed_redirect_scheme(self):
        class MockResponse:
            status = 200
            url = "file:///etc/passwd"
            headers = {}
            def read(self, cap=1048576):
                return b"secret"
            def __enter__(self):
                return self
            def __exit__(self, *args):
                pass

        with patch("urllib.request.urlopen", return_value=MockResponse()):
            with self.assertRaises(ValueError) as ctx:
                cast_mod.fetch("https://example.com/stream.m3u8")
            self.assertIn("Unsupported URL scheme: file", str(ctx.exception))


if __name__ == "__main__":
    unittest.main()
