import sys
import unittest
from pathlib import Path

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


if __name__ == "__main__":
    unittest.main()
