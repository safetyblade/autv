#!/usr/bin/env python3
"""Read-only receiver-style URL checks. HTTP success is not a Chromecast playback test."""
import concurrent.futures
import json
import re
import subprocess
import tempfile
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ORIGIN = "https://www.gstatic.com"


def normalise(url):
    base, separator, query = url.partition("?")
    if not separator:
        return url.split("#", 1)[0]
    parameters = [p for p in query.split("#", 1)[0].split("&")
                  if not re.search(r"\[[A-Za-z_][A-Za-z0-9_]*]|\{[A-Za-z_][A-Za-z0-9_]*}",
                                   urllib.parse.unquote_plus(p.partition("=")[2]))]
    return base + ("?" + "&".join(parameters) if parameters else "")


def fetch(url, segment=False):
    headers = {"Origin": ORIGIN, "User-Agent": "Mozilla/5.0 CrKey/1.56.500000"}
    if segment:
        headers["Range"] = "bytes=0-1048575"
    with urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=15) as response:
        body = response.read(1048576)
        info = {"status": response.status, "host": urllib.parse.urlsplit(response.url).hostname,
                "cors": response.headers.get("Access-Control-Allow-Origin"),
                "content_type": response.headers.get("Content-Type"), "bytes": len(body)}
        return body, response.url, info


def probe(item):
    provider, name, original = item
    result = {"provider": provider, "channel": name, "macro_normalised": normalise(original) != original, "requests": []}
    try:
        url = normalise(original)
        attempted = url
        for _ in range(5):
            attempted = url
            body, url, info = fetch(url)
            result["requests"].append(info)
            text = body.decode("utf-8-sig")
            if not text.startswith("#EXTM3U"):
                result["result"] = "Not an HLS manifest"
                return result
            variants = re.findall(r'#EXT-X-STREAM-INF:([^\n]+)\n([^#\n]+)', text)
            if variants:
                result["advertised_codecs"] = [re.search(r'CODECS="([^"]+)"', v[0]).group(1)
                                              for v in variants if 'CODECS="' in v[0]]
                url = urllib.parse.urljoin(url, variants[0][1].strip())
                continue
            lines = [line.strip() for line in text.splitlines() if line.strip() and not line.startswith("#")]
            if not lines:
                result["result"] = "No media segments"
                return result
            result["encrypted"] = '#EXT-X-KEY:' in text and 'METHOD=NONE' not in text
            # Sample an early segment to avoid fetching a not-yet-published live edge.
            segment = urllib.parse.urljoin(url, lines[min(1, len(lines) - 1)])
            attempted = segment
            body, _, info = fetch(segment, segment=True)
            result["requests"].append(info)
            with tempfile.NamedTemporaryFile(suffix=".ts") as sample:
                sample.write(body); sample.flush()
                checked = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "stream=codec_name,profile,codec_type", "-of", "json", sample.name],
                                         capture_output=True, text=True, timeout=15)
                result["segment_codecs"] = json.loads(checked.stdout or "{}").get("streams", [])
            result["result"] = "Manifest and segment fetched; receiver playback still requires device test"
            return result
        result["result"] = "Too many nested manifests"
    except urllib.error.HTTPError as error:
        result["result"] = f"HTTP {error.code}"
    except urllib.error.URLError as error:
        result["requested_host"] = urllib.parse.urlsplit(attempted).hostname
        result["result"] = ("Cloud proxy denied CONNECT (403) at this request/redirect stage"
                            if "Tunnel connection failed: 403" in str(error.reason)
                            else f"Network failure: {type(error.reason).__name__}")
    except Exception as error:
        result["result"] = f"Fetch/probe failed: {type(error).__name__}"
    return result


if __name__ == "__main__":
    rows = []
    for line in (Path(__file__).resolve().parents[1] / "playlist.m3u").read_text().splitlines():
        if line.startswith("#EXTINF"):
            name = line.rsplit(",", 1)[-1]
        elif line.startswith("http"):
            rows.append((name, line))
    samples = [
        ("Pluto (published jmp2 redirect)", lambda n, u: n == "Sky News" and "/plu-" in u),
        ("Plex AU", lambda n, u: n == "USA TODAY" and "plex.tv" in u),
        ("Amagi/LG AU", lambda n, u: "skynews" in u and "amagi.tv" in u),
        ("Curated/direct", lambda n, u: n == "Hoop TV" and "sofast.tv" in u),
        ("Custom/Tubi", lambda n, u: n == "Watch AEW" and "tubi.io" in u),
    ]
    selected = [(provider, n, u) for provider, match in samples for n, u in rows if match(n, u)]
    with concurrent.futures.ThreadPoolExecutor(max_workers=5) as pool:
        print(json.dumps(list(pool.map(probe, selected)), indent=2))
