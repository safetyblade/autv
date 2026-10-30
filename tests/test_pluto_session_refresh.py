import base64
import json
import unittest
from urllib.parse import parse_qs, urlsplit
from unittest.mock import patch
from scripts.refresh_pluto_sessions import get_session, refresh, token_expiry


def jwt(exp):
    payload = base64.urlsafe_b64encode(json.dumps({"exp": exp}).encode()).decode().rstrip("=")
    return "head." + payload + ".sig"


class TestPlutoSessionRefresh(unittest.TestCase):
    def test_refresh_keeps_channel_identity_and_non_pluto_urls(self):
        old = jwt(100)
        new = jwt(3000000000)
        url = "https://example.service-channel-stitcher.pluto.tv/v2/stitch/hls/channel/abc/master.m3u8?jwt=" + old + "&country=US"
        source = "#EXTM3U\n#EXTINF:-1,Test\n" + url + "\n#EXTINF:-1,NonPluto\nhttps://example.com/a.m3u8\n"
        result, count = refresh(source, new, "device-test")
        self.assertEqual(count, 1)
        self.assertNotIn(old, result)
        self.assertIn("https://example.com/a.m3u8", result)
        replaced = result.splitlines()[2]
        self.assertIn("/channel/abc/master.m3u8", replaced)
        query = parse_qs(urlsplit(replaced).query)
        self.assertEqual(query["jwt"][0], new)
        self.assertEqual(query["country"][0], "US")
        self.assertEqual(query["clientID"][0], "device-test")

    def test_fails_malformed_token(self):
        with self.assertRaises(ValueError):
            token_expiry("not-a-jwt")

    def test_get_session_disallowed_redirect_scheme(self):
        class MockResponse:
            def geturl(self):
                return "file:///etc/passwd"
            def __enter__(self):
                return self
            def __exit__(self, *args):
                pass

        with patch("scripts.refresh_pluto_sessions.urlopen", return_value=MockResponse()):
            with self.assertRaises(RuntimeError) as ctx:
                get_session()
            self.assertIn("Unsupported URL scheme: file", str(ctx.exception))


if __name__ == "__main__":
    unittest.main()
