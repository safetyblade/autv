import base64
import json
import tempfile
import unittest
from pathlib import Path
from sys import path as sys_path

sys_path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from quarantine_expired_pluto import quarantine
from check_pluto_freshness import check, extract_jwt


def token(exp):
    part = base64.urlsafe_b64encode(json.dumps({"exp": exp}).encode()).decode().rstrip("=")
    return "header." + part + ".signature"


class PlutoQuarantineTest(unittest.TestCase):
    def test_removes_only_stale_pluto_keeps_non_pluto(self):
        now = 1000000000
        stale = "https://a.service-channel-stitcher.pluto.tv/v2/stitch/hls/channel/test/master.m3u8?jwt=" + token(now - 1)
        fresh = "https://a.service-channel-stitcher.pluto.tv/v2/stitch/hls/channel/other/master.m3u8?jwt=" + token(now + 86400)
        other = "https://example.com/working.m3u8"
        data = "#EXTM3U\\n#EXTINF:-1,stale\\n" + stale + "\\n#EXTINF:-1,fresh\\n" + fresh + "\\n#EXTINF:-1,other\\n" + other + "\\n"
        data = data.replace("\\n", "\n")
        with tempfile.TemporaryDirectory() as tmp:
            file = Path(tmp) / "playlist.m3u"
            file.write_text(data)
            self.assertEqual(quarantine(file, now=now), 1)
            result = file.read_text()
            self.assertNotIn(stale, result)
            self.assertNotIn("stale", result)
            self.assertIn(fresh, result)
            self.assertIn(other, result)
            check(file, now=now)

    def test_empty_pluto_requires_explicit_degraded_mode(self):
        with tempfile.TemporaryDirectory() as tmp:
            file = Path(tmp) / "playlist.m3u"
            file.write_text("#EXTM3U\n#EXTINF:-1,ok\nhttps://example.com/stream.m3u8\n")
            with self.assertRaises(SystemExit):
                check(file, now=1000000000)
            check(file, now=1000000000, allow_empty=True)

    def test_jwt_parameter_boundary_extraction(self):
        now = 1000000000
        valid_tok = token(now + 86400)
        url_with_other_param = f"https://a.service-channel-stitcher.pluto.tv/v2/stitch/hls/channel/test/master.m3u8?other_jwt=invalid&jwt={valid_tok}"
        self.assertEqual(extract_jwt(url_with_other_param), valid_tok)
        data = f"#EXTM3U\n#EXTINF:-1,test\n{url_with_other_param}\n"
        with tempfile.TemporaryDirectory() as tmp:
            file = Path(tmp) / "playlist.m3u"
            file.write_text(data)
            self.assertEqual(quarantine(file, now=now), 0)
            check(file, now=now)


if __name__ == "__main__":
    unittest.main()
