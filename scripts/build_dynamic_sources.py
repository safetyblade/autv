#!/usr/bin/env python3
"""Resolve a small set of rotating/fragile FAST sources before playlist build.

Keep this deliberately narrow: it currently manages only AUTV channel 203.
The generated entry is still a normal static HLS URL in playlist.m3u, so every
generic IPTV client consumes the same result.
"""
from pathlib import Path
import urllib.request

OUTPUT = Path("dynamic_sources.m3u")
TIMEOUT = 12

TNA_203 = [
    ("DistroTV", "https://amg00966-amg00966c1-distrotv-us-7706.playouts.now.amagi.tv/playlist/amg00966-anthem-tnawrestling-distrotvus/playlist.m3u8"),
    ("Samsung direct", "https://dpltey7dr5q2g.cloudfront.net/TNA_Wrestling.m3u8"),
    ("Rakuten", "https://d39g1vxj2ef6in.cloudfront.net/v1/master/3fec3e5cac39a52b2132f9c66c83dae043dc17d4/prod-rakuten-stitched/master.m3u8?ads.xumo_channelId=88883039"),
    ("Pluto US", "https://jmp2.uk/plu-59b722526996084038c01e1b.m3u8"),
    ("Roku", "https://jmp2.uk/rok-6d8659091f745b8b864f438f06c56fae.m3u8"),
]

def valid_hls(url: str) -> bool:
    request = urllib.request.Request(
        url,
        headers={
            "User-Agent": "Mozilla/5.0 (Linux; Android TV) AUTV-refresh/1.0",
            "Accept": "application/vnd.apple.mpegurl, application/x-mpegURL, */*",
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=TIMEOUT) as response:
            if response.status < 200 or response.status >= 300:
                return False
            body = response.read(8192).decode("utf-8", errors="ignore")
            return "#EXTM3U" in body[:2048]
    except Exception as exc:
        print(f"203 candidate failed: {url} ({exc})")
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
