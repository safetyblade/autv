#!/usr/bin/env python3
"""Read-only manifest probe for four non-login channel candidates listed by BINGE.

Runs on a GitHub-hosted runner, NOT the owner's Australian television.
No playback, entitlements, DRM, ad bypass, or source publication.
Outputs status, media signature and final hostname; never prints full media URLs.
"""
import json
import urllib.error
import urllib.parse
import urllib.request

SOURCES = [
    ("TVSN", "official broadcaster CDN", "https://tvsnhlslivetest.akamaized.net/hls/live/2034711/TVSN-MSL4/master.m3u8"),
    ("Al Jazeera English", "broadcaster HLS", "https://live-hls-apps-aje-fa.getaj.net/AJE/index.m3u8"),
    ("Al Jazeera English backup", "broadcaster HLS", "https://live-hls-apps-aje-v3-fa.getaj.net/AJE/index.m3u8"),
    ("NHK World-Japan", "broadcaster HLS", "https://masterpl.hls.nhkworld.jp/hls/w/live/smarttv.m3u8"),
    ("NHK World-Japan backup", "broadcaster HLS", "https://media-osa.hls.nhkworld.jp/hls/w/live/master.m3u8"),
    ("CGTN English", "broadcaster HLS", "https://english-livebkali.cgtn.com/live/encgtn.m3u8"),
    ("CGTN English backup", "licensed FAST endpoint", "https://amg00405-rakutentv-cgtn-rakuten-i9tar.amagi.tv/master.m3u8"),
]

def inspect(name, description, url):
    u = urllib.parse.urlsplit(url)
    assert u.scheme == "https" and u.username is None and u.password is None and not u.query
    request = urllib.request.Request(url, headers={
        "Accept": "application/vnd.apple.mpegurl, application/x-mpegURL, */*",
        "User-Agent": "AUTV-Public-Channel-Read-Only-Diagnostic",
        "Range": "bytes=0-1023",
    })
    result = dict(channel=name, source_kind=description, original_host=u.hostname)
    try:
        with urllib.request.urlopen(request, timeout=11) as rsp:
            data = rsp.read(1024)
            result.update(status=rsp.status, final_host=urllib.parse.urlsplit(rsp.url).hostname,
                content_type=rsp.headers.get("Content-Type","").split(";")[0],
                is_hls=data.lstrip().startswith(b"#EXTM3U"),
                has_variants=b"#EXT-X-STREAM-INF" in data,
                has_media_segments=b"#EXTINF" in data,
                sample_bytes=len(data))
    except urllib.error.HTTPError as exc:
        result.update(status=exc.code, final_host=urllib.parse.urlsplit(exc.url).hostname,
            is_hls=False)
    except Exception as exc:
        result.update(status=type(exc).__name__, is_hls=False)
    return result

for row in SOURCES:
    print(json.dumps(inspect(*row), sort_keys=True), flush=True)
