import importlib.util
from pathlib import Path
import unittest

SPEC = importlib.util.spec_from_file_location(
    "probe_ten_vic_hls", Path(__file__).resolve().parents[1] / "scripts" / "probe_ten_vic_hls.py"
)
probe = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(probe)


class TenVicProbeTests(unittest.TestCase):
    def test_master_variant_and_codec(self):
        result = probe.summarise(probe.manifest_lines(b"""#EXTM3U
#EXT-X-STREAM-INF:BANDWIDTH=1500000,CODECS="avc1.4d401f,mp4a.40.2"
variant.m3u8?token=DO-NOT-LOG
"""))
        self.assertEqual(len(result["variants"]), 1)
        self.assertEqual(result["variants"][0]["codecs"], "avc1.4d401f,mp4a.40.2")

    def test_aes_key_and_segment_are_distinct(self):
        result = probe.summarise(probe.manifest_lines(b"""#EXTM3U
#EXT-X-KEY:METHOD=AES-128,URI="https://key.fastly.net/abc.key?secret=NO"
#EXTINF:6,
segment.ts?token=NO
"""))
        self.assertEqual(result["key"]["method"], "AES-128")
        self.assertEqual(result["first_segment"], "segment.ts?token=NO")
        self.assertTrue(result["is_live"])

    def test_safety_does_not_allow_arbitrary_origins(self):
        for value in [
            "http://i.mjh.nz/stream.m3u8",
            "https://127.0.0.1/stream.m3u8",
            "https://example.com/stream.m3u8",
            "https://user:pass@i.mjh.nz/x",
            "https://i.mjh.nz:8443/x",
        ]:
            with self.subTest(value=value):
                with self.assertRaises(ValueError):
                    probe.safe_host(value)

    def test_manifest_rejects_html(self):
        with self.assertRaises(ValueError):
            probe.manifest_lines(b"<html>unauthorized</html>")


if __name__ == "__main__":
    unittest.main()
