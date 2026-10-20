#!/usr/bin/env python3
"""Generate an empty dynamic-source playlist.

The retired TNA 203 multi-provider resolver has been removed. Future approved
dynamic sources can be added independently without blocking playlist refresh.
"""
from pathlib import Path
from urllib.parse import urljoin, urlsplit
from urllib.request import Request, urlopen


def read_url(url, max_bytes=65536, allow_partial=False):
    parsed = urlsplit(url)
    if parsed.scheme not in ("http", "https"):
        raise ValueError(f"Unsupported URL scheme: {parsed.scheme}")
    req = Request(url, headers={"User-Agent": "Mozilla/5.0 (Android TV; AUTV check)"})
    with urlopen(req, timeout=5) as resp:
        if resp.status != 200:
            raise ValueError(f"HTTP {resp.status}")
        final_scheme = urlsplit(resp.geturl()).scheme
        if final_scheme not in ("http", "https"):
            raise ValueError(f"Unsupported URL scheme: {final_scheme}")
        return resp.geturl(), resp.read(max_bytes)


def valid_hls(url):
    try:
        base, content = read_url(url)
        text = content.decode("utf-8-sig", errors="replace")
        if not text.lstrip().startswith("#EXTM3U"):
            return False
        lines = [x.strip() for x in text.splitlines() if x.strip()]
        if any(x.startswith("#EXT-X-STREAM-INF:") for x in lines):
            idx = next(i for i, x in enumerate(lines) if x.startswith("#EXT-X-STREAM-INF:"))
            if idx + 1 >= len(lines) or lines[idx + 1].startswith("#"):
                return False
            return valid_hls(urljoin(base, lines[idx + 1]))
        if not any(x.startswith("#EXTINF:") for x in lines):
            return False
        segment = next((x for x in lines if not x.startswith("#")), None)
        if not segment:
            return False
        _, data = read_url(urljoin(base, segment), max_bytes=4096, allow_partial=True)
        return bool(data)
    except Exception:
        return False


def main():
    Path("dynamic_sources.m3u").write_text("#EXTM3U\n", encoding="utf-8")
    print("No approved dynamic sources; wrote empty dynamic_sources.m3u")


if __name__ == "__main__":
    main()
