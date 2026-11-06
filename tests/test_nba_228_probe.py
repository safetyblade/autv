import unittest
from scripts.probe_nba_228 import entries,first_media_url,analyse_manifest

class NbaProbeTests(unittest.TestCase):
    def test_exact_channel_number_and_id(self):
        lines=(
            '#EXTM3U\n'
            '#EXTINF:-1 tvg-chno="228" tvg-id="99951383",The NBA Channel\n'
            'https://example.org/nba.m3u8\n'
            '#EXTINF:-1 tvg-chno="229" tvg-id="another",Hoop TV\n'
            'https://example.org/hoops.m3u8\n')
        ch=entries(lines,match_number="228")
        self.assertEqual((ch["name"],ch["id"]),("The NBA Channel","99951383"))
        self.assertEqual(entries(lines,match_id="99951383")["number"],"228")
    def test_master_hls_and_relative_variant(self):
        a="#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=100000\nvariant/high.m3u8\n"
        self.assertEqual(analyse_manifest({"http_status":200,"text":a}),"HLS_MASTER")
        self.assertEqual(first_media_url("https://example.org/path/master.m3u8",a),
                         ("https://example.org/path/variant/high.m3u8","variant"))
    def test_media_and_segment(self):
        a="#EXTM3U\n#EXTINF:5.0,\nsegment0.ts\n"
        self.assertEqual(analyse_manifest({"http_status":200,"text":a}),"HLS_MEDIA")
        self.assertEqual(first_media_url("https://example.org/live/media.m3u8",a)[1],"segment")
    def test_http_failure_and_html_are_not_playable(self):
        self.assertEqual(analyse_manifest({"http_status":403,"text":""}),"HTTP_DENIED")
        self.assertEqual(analyse_manifest({"http_status":200,"text":"<html>blocked</html>"}),"NON_HLS_RESPONSE")
if __name__=="__main__":unittest.main()
