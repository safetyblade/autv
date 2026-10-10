#!/usr/bin/env python3
"""Resolve a small set of rotating/fragile FAST sources before playlist build.

Keep this deliberately narrow: it currently manages only AUTV channel 203.
The generated entry is still a normal static HLS URL in playlist.m3u, so every
generic IPTV client consumes the same result.
"""
from pathlib import Path
import urllib.request
import urllib.parse

OUTPUT = Path("dynamic_sources.m3u")
TIMEOUT = 12

TNA_203 = [
    ("Samsung direct", "https://dpltey7dr5q2g.cloudfront.net/TNA_Wrestling.m3u8"),
    ("DistroTV", "https://amg00966-amg00966c1-distrotv-us-7706.playouts.now.amagi.tv/playlist/amg00966-anthem-tnawrestling-distrotvus/playlist.m3u8"),
    ("Rakuten", "https://d39g1vxj2ef6in.cloudfront.net/v1/master/3fec3e5cac39a52b2132f9c66c83dae043dc17d4/prod-rakuten-stitched/master.m3u8?ads.xumo_channelId=88883039"),
    ("Pluto US", "https://jmp2.uk/plu-59b722526996084038c01e1b.m3u8"),
    ("Roku", "https://jmp2.uk/rok-6d8659091f745b8b864f438f06c56fae.m3u8"),
]

def read_url(url: str, max_bytes: int = 65536, allow_partial: bool = False) -> tuple[str, bytes]:
    """Fetch bounded data; return final URL so relative HLS references resolve."""
    request = urllib.request.Request(
        url,
        headers={
            "User-Agent": "Mozilla/5.0 (Linux; Android TV) AUTV-refresh/1.0",
            "Accept": "application/vnd.apple.mpegurl, application/x-mpegURL, */*",
        },
    )
    with urllib.request.urlopen(request, timeout=TIMEOUT) as response:
        if not 200 <= response.status < 300:
            raise ValueError(f"HTTP {response.status}")
        data = response.read(max_bytes if allow_partial else max_bytes + 1)
        if not allow_partial and len(data) > max_bytes:
            raise ValueError("Response exceeds inspection limit")
        return response.url, data


def valid_hls(url: str) -> bool:
    """Verify master, media playlist and a non-empty segment, not just #EXTM3U."""
    try:
        current = url
        for _ in range(3):
            resolved, body = read_url(current)
            manifest = body.decode("utf-8-sig", errors="replace")
            if not manifest.lstrip().startswith("#EXTM3U"):
                raise ValueError("Not an HLS manifest")
            lines = [line.strip() for line in manifest.splitlines() if line.strip()]
            if any(line.startswith("#EXT-X-STREAM-INF:") for line in lines):
                index = next(i for i, line in enumerate(lines) if line.startswith("#EXT-X-STREAM-INF:"))
                if index + 1 >= len(lines) or lines[index + 1].startswith("#"):
                    raise ValueError("Master has no variant URI")
                current = urllib.parse.urljoin(resolved, lines[index + 1])
                continue
            if not any(line.startswith("#EXTINF:") for line in lines):
                raise ValueError("Media playlist has no segments")
            segments = [line for line in lines if not line.startswith("#")]
            if not segments:
                raise ValueError("No media segment URL")
            segment_url = urllib.parse.urljoin(resolved, segments[0])
            # A bounded sample checks availability, not playback compatibility.
            _, sample = read_url(segment_url, max_bytes=8192, allow_partial=True)
            if not sample:
                raise ValueError("Empty media segment")
            return True
        raise ValueError("Too many nested HLS master playlists")
    except Exception as exc:
        print(f"203 candidate failed: {type(exc).__name__}: {exc}")
        return False

def main():
    chosen = None
    for source, url in TNA_203:
        print(f"Checking TNA 203 via {source}...")
        if valid_hls(url):
            chosen = (source, url)
            print(f"TNA 203 selected: {source}")
            break

    if chosen is None:
        # Do not publish a known-dead 203 entry. The previous generated playlist
        # remains in git if the workflow fails before publish.
        raise SystemExit("No healthy TNA 203 HLS candidate found; refusing refresh")

    source, url = chosen
    OUTPUT.write_text(
        '#EXTM3U\n'
        '#EXTINF:-1 tvg-id="tna-wrestling-channel" tvg-name="TNA Wrestling DistroTV" '
        'tvg-logo="https://provider-static.plex.tv/epg/cms/production/5d4f68c3-82af-45e7-b2c4-39aff69f3ce0/TNAWrestling_Plex_LogoforDarkBG_1500x1000_-_Alex_TushinghamRDX.png" '
        'group-title="Sport",TNA Wrestling DistroTV\n'
        f'{url}\n',
        encoding="utf-8",
    )
    print(f"Wrote {OUTPUT} using {source}")

if __name__ == "__main__":
    main()
