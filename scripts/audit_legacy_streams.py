"""Lightweight, advisory checks for legacy redirect and Pluto HLS endpoints.

Failures are logged, not removed from the curated lineup. GitHub-hosted results
may differ from playback in Australia. No extra Actions job or dependency.
"""
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path
from urllib.parse import urljoin, urlsplit
from urllib.request import Request, urlopen
import re

TIMEOUT = 5
WORKERS = 16
PRIORITY = {1214, 1215, 1216}

def candidates(path):
    entries = []
    header = None
    for line in Path(path).read_text(encoding="utf-8-sig").splitlines():
        if line.startswith("#EXTINF:"):
            header = line
        elif header and line.startswith(("http://", "https://")):
            match = re.search(r'tvg-chno="(\\d+)"', header)
            if match:
                number = int(match.group(1))
                host = urlsplit(line).hostname or ""
                if host == "jmp2.uk" or ("pluto.tv" in host and "service-channel-stitcher" in host):
                    entries.append((number, line))
            header = None
    return sorted(entries, key=lambda entry: (entry[0] not in PRIORITY, entry[0]))

def fetch(url, cap=32768):
    req = Request(url, headers={"User-Agent": "Mozilla/5.0 (Android TV; AUTV check)", "Accept": "application/vnd.apple.mpegurl, application/x-mpegURL, */*"})
    with urlopen(req, timeout=TIMEOUT) as resp:
        if resp.status != 200:
            raise ValueError(f"HTTP {resp.status}")
        return resp.geturl(), resp.read(cap)

def probe(entry):
    number, url = entry
    try:
        for _ in range(3):
            base, content = fetch(url)
            text = content.decode("utf-8-sig", errors="replace")
            if not text.lstrip().startswith("#EXTM3U"):
                raise ValueError("not HLS")
            lines = [x.strip() for x in text.splitlines() if x.strip()]
            if any(x.startswith("#EXT-X-STREAM-INF:") for x in lines):
                idx = next(i for i, x in enumerate(lines) if x.startswith("#EXT-X-STREAM-INF:"))
                if idx + 1 >= len(lines) or lines[idx + 1].startswith("#"):
                    raise ValueError("missing variant")
                url = urljoin(base, lines[idx + 1])
                continue
            if not any(x.startswith("#EXTINF:") for x in lines):
                raise ValueError("no media segments")
            segment = next((x for x in lines if not x.startswith("#")), None)
            if not segment:
                raise ValueError("no segment URI")
            _, data = fetch(urljoin(base, segment), cap=4096)
            if not data:
                raise ValueError("empty media segment")
            return number, "reachable"
        raise ValueError("nested manifest limit")
    except Exception as error:
        return number, f"suspect: {type(error).__name__}: {str(error)[:90]}"

def main():
    streams = candidates("playlist.m3u")
    results = []
    with ThreadPoolExecutor(max_workers=WORKERS) as pool:
        futures = [pool.submit(probe, entry) for entry in streams]
        for future in as_completed(futures):
            results.append(future.result())
    failed = sorted((n, status) for n, status in results if status != "reachable")
    print(f"Legacy/Pluto audit: {len(streams)} checked, {len(results)-len(failed)} reachable, {len(failed)} suspect.")
    for n, status in sorted(results, key=lambda item: (item[0] not in PRIORITY, item[0])):
        if n in PRIORITY or status != "reachable":
            print(f"CH {n}: {status}")
    print("Advisory only: successful checks do not guarantee Australian TV playback; failures may be location-dependent.")

if __name__ == "__main__":
    main()
