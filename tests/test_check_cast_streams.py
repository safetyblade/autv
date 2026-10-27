import unittest
import sys
from pathlib import Path

# Add native directory to path so check_cast_streams can be imported
native_path = Path(__file__).resolve().parents[1] / "native"
if str(native_path) not in sys.path:
    sys.path.insert(0, str(native_path))

from unittest.mock import patch
from check_cast_streams import probe

class TestCheckCastStreamsSecurity(unittest.TestCase):
    @patch("check_cast_streams.fetch")
    def test_probe_handles_empty_codecs(self, mock_fetch):
        manifest_text = (
            "#EXTM3U\n"
            '#EXT-X-STREAM-INF:BANDWIDTH=1000000,CODECS=""\n'
            "video_1.m3u8\n"
        ).encode("utf-8")
        media_text = (
            "#EXTM3U\n"
            "#EXTINF:10.0,\n"
            "segment1.ts\n"
        ).encode("utf-8")
        mock_fetch.side_effect = [
            (manifest_text, "https://example.com/master.m3u8", {"status": 200, "host": "example.com", "bytes": len(manifest_text)}),
            (media_text, "https://example.com/video_1.m3u8", {"status": 200, "host": "example.com", "bytes": len(media_text)}),
            (b"dummy_segment", "https://example.com/segment1.ts", {"status": 200, "host": "example.com", "bytes": 13}),
        ]
        with patch("subprocess.run") as mock_run:
            mock_run.return_value.stdout = '{"streams": []}'
            item = ("TestProvider", "TestChannel", "https://example.com/master.m3u8")
            result = probe(item)
            self.assertEqual(result.get("advertised_codecs"), [""])

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
