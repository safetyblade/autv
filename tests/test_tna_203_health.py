import sys
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import build_dynamic_sources as mod


class TestTna203Health(unittest.TestCase):
    def test_master_media_segment(self):
        def fake_read(url, max_bytes=65536, allow_partial=False):
            if url.endswith("master.m3u8"):
                return url, b"#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=2000000\nvideo/index.m3u8\n"
            if url.endswith("index.m3u8"):
                return url, b"#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXTINF:6.0,\nsegment.ts\n"
            if url.endswith("segment.ts"):
                return url, b"video data"
            raise ValueError("unknown fixture")
        with patch.object(mod, "read_url", side_effect=fake_read):
            self.assertTrue(mod.valid_hls("https://example.org/master.m3u8"))

    def test_manifest_without_segments_fails(self):
        with patch.object(mod, "read_url", return_value=("https://example.org/a.m3u8", b"#EXTM3U\n#EXT-X-TARGETDURATION:6\n")):
            self.assertFalse(mod.valid_hls("https://example.org/a.m3u8"))

    def test_segment_error_fails(self):
        def fake_read(url, max_bytes=65536, allow_partial=False):
            if url.endswith(".m3u8"):
                return url, b"#EXTM3U\n#EXTINF:6,\nsegment.ts\n"
            raise OSError("upstream segment unavailable")
        with patch.object(mod, "read_url", side_effect=fake_read):
            self.assertFalse(mod.valid_hls("https://example.org/a.m3u8"))


if __name__ == "__main__":
    unittest.main()
