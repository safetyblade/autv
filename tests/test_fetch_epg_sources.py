import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import tempfile
import threading
import unittest

import scripts.fetch_epg_sources as fetch


class _EpgHandler(BaseHTTPRequestHandler):
    """Serves a controllable EPG body with ETag validation."""

    body = b"<tv>version-1</tv>"
    etag = '"v1"'
    mode = "ok"  # ok | error
    seen_headers = []

    def do_GET(self):
        type(self).seen_headers.append(dict(self.headers))
        if type(self).mode == "error":
            self.send_response(500)
            self.end_headers()
            self.wfile.write(b"boom")
            return
        if self.headers.get("If-None-Match") == type(self).etag:
            self.send_response(304)
            self.end_headers()
            return
        self.send_response(200)
        self.send_header("ETag", type(self).etag)
        self.send_header("Content-Length", str(len(type(self).body)))
        self.end_headers()
        self.wfile.write(type(self).body)

    def log_message(self, *args):
        pass


class TestFetchEpgSources(unittest.TestCase):
    def setUp(self):
        _EpgHandler.body = b"<tv>version-1</tv>"
        _EpgHandler.etag = '"v1"'
        _EpgHandler.mode = "ok"
        _EpgHandler.seen_headers = []
        self.server = ThreadingHTTPServer(("127.0.0.1", 0), _EpgHandler)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.addCleanup(self.server.shutdown)
        self.url = f"http://127.0.0.1:{self.server.server_address[1]}/epg.xml.gz"
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        root = Path(self.tmp.name)
        self.cache_dir = root / "cache"
        self.out_dir = root / "out"
        self.sources = {"epg_test.xml.gz": self.url}

    def run_fetch(self):
        return fetch.fetch_all(
            cache_dir=self.cache_dir, out_dir=self.out_dir, sources=self.sources
        )

    def test_first_fetch_is_fresh_and_records_validators(self):
        results = self.run_fetch()
        self.assertEqual(results[0].status, "fresh")
        self.assertEqual(
            (self.out_dir / "epg_test.xml.gz").read_bytes(), b"<tv>version-1</tv>"
        )
        manifest = json.loads((self.cache_dir / "manifest.json").read_text())
        self.assertEqual(manifest["epg_test.xml.gz"]["etag"], '"v1"')
        self.assertTrue(manifest["epg_test.xml.gz"]["sha256"])

    def test_second_fetch_sends_validators_and_reuses_cache_on_304(self):
        self.run_fetch()
        # The origin body changes but the ETag does not: a 304 means the
        # cached copy is authoritative, and the (empty) 304 response body
        # must never overwrite it.
        _EpgHandler.body = b"<tv>changed-but-not-modified</tv>"
        results = self.run_fetch()
        self.assertEqual(results[0].status, "not-modified")
        self.assertEqual(
            (self.out_dir / "epg_test.xml.gz").read_bytes(), b"<tv>version-1</tv>"
        )
        conditional = _EpgHandler.seen_headers[-1]
        self.assertEqual(conditional.get("If-None-Match"), '"v1"')

    def test_changed_etag_downloads_the_new_body(self):
        self.run_fetch()
        _EpgHandler.body = b"<tv>version-2</tv>"
        _EpgHandler.etag = '"v2"'
        results = self.run_fetch()
        self.assertEqual(results[0].status, "fresh")
        self.assertEqual(
            (self.out_dir / "epg_test.xml.gz").read_bytes(), b"<tv>version-2</tv>"
        )

    def test_server_error_falls_back_to_cached_body(self):
        self.run_fetch()
        _EpgHandler.mode = "error"
        results = self.run_fetch()
        self.assertEqual(results[0].status, "fallback")
        self.assertEqual(
            (self.out_dir / "epg_test.xml.gz").read_bytes(), b"<tv>version-1</tv>"
        )

    def test_server_error_without_cache_fails_loudly(self):
        _EpgHandler.mode = "error"
        with self.assertRaises(fetch.FetchError):
            self.run_fetch()
        self.assertFalse((self.out_dir / "epg_test.xml.gz").exists())

    def test_304_with_empty_cache_fails_loudly(self):
        # A cache manifest claiming a validator but no cached body must not
        # turn a 304 into an empty guide source.
        self.cache_dir.mkdir(parents=True)
        (self.cache_dir / "manifest.json").write_text(
            json.dumps({"epg_test.xml.gz": {"etag": '"v1"', "last_modified": ""}})
        )
        with self.assertRaises(fetch.FetchError):
            self.run_fetch()
        self.assertFalse((self.out_dir / "epg_test.xml.gz").exists())


if __name__ == "__main__":
    unittest.main()
