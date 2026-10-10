import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from merge import replace_pluto_redirect, fresh_pluto_by_id

ID = "63f87d057533d80008ab9549"
DIRECT = f"https://cfd-v4-service-channel-stitcher-use1-1.prd.pluto.tv/v2/stitch/hls/channel/{ID}/master.m3u8?jwt=fresh"
REDIRECT = f"https://jmp2.uk/plu-{ID}.m3u8"
EXT = f'#EXTINF:-1 tvg-id="{ID}" tvg-name="SpongeBob SquarePants",SpongeBob SquarePants'

class TestPlutoReplacement(unittest.TestCase):
    def test_replaces_matching_redirect(self):
        self.assertEqual(replace_pluto_redirect(EXT, REDIRECT, {ID: DIRECT}), DIRECT)

    def test_does_not_invent_missing_replacement(self):
        self.assertEqual(replace_pluto_redirect(EXT, REDIRECT, {}), REDIRECT)

    def test_leaves_roku_unchanged(self):
        roku = "https://jmp2.uk/rok-12345.m3u8"
        self.assertEqual(replace_pluto_redirect(EXT, roku, {ID: DIRECT}), roku)

    def test_direct_source_lookup_requires_authenticated_v2(self):
        self.assertEqual(fresh_pluto_by_id([("SpongeBob", EXT, DIRECT)]), {ID: DIRECT})
        self.assertEqual(fresh_pluto_by_id([("SpongeBob", EXT, REDIRECT)]), {})

if __name__ == "__main__":
    unittest.main()
