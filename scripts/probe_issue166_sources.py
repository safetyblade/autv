#!/usr/bin/env python3
"""Read-only, English-only AUTV candidate/fallback media trial.

This script NEVER edits the published playlist, channel guide or selected lists.
It consumes provider M3Us downloaded by the dedicated discovery workflow and
outputs a small evidence report. Stream URIs (which may carry signed tokens)
are never written to reports or CI logs.
"""
import csv
import json
import re
import sys
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urljoin, urlsplit
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]
CATALOGUE = ROOT / "channel_guide.csv"
PUBLISHED = ROOT / "playlist.m3u"
SOURCES = ROOT / "discovery_sources"
OUTPUT = ROOT / "audits" / "issue166_candidate_probe.json"
TARGETS = {
    "Watch AEW": ["Watch AEW", "WatchAEW"],
    "CW Presents WWE NXT": ["CW Presents WWE NXT"],
    "TNA Wrestling Channel": ["TNA Wrestling Channel", "TNA Wrestling"],
    "Comedy Dynamics": ["Comedy Dynamics"],
    "Whose Line Is It Anyway?": ["Whose Line Is It Anyway?", "Whose Line Is It Anyway"],
    "Just For Laughs Gags": ["Just For Laughs Gags", "Just for Laughs Gags"],
    "Wrestling Central": ["Wrestling Central"],
    "FITE 24/7": ["FITE 24/7", "FITE by Triller 24/7", "TrillerTV 24/7"],
    "ESPN The Ocho": ["ESPN The Ocho", "ESPN8 The Ocho"],
}
# ESPN/ESPN2 require subscriber-provider authentication; no unauthorised public trial.
PREMIUM_NOT_SCANNED = ["ESPN", "ESPN2"]
ALLOWED_SOURCE_NAMES = ("roku", "tubi", "xumo", "plex_au", "pluto_us", "pluto_ca", "pluto_uk")
ATTR_ID = re.compile(r'(?:^|\s)tvg-id="([^"]+)"')
ATTR_LANG = re.compile(r'(?:^|\s)(?:tvg-language|language)="([^"]+)"')
ATTR_NO = re.compile(r'(?:^|\s)tvg-chno="(\d+)"')

def key(text):
    return re.sub(r"[^a-z0-9]+", "", (text or "").casefold())

NAME_MAP = {key(alias): name for name, aliases in TARGETS.items() for alias in aliases}

def parse_entries(body):
    lines = body.splitlines()
    for i, line in enumerate(lines):
        if not line.startswith("#EXTINF:"):
            continue
        name = line.rsplit(",", 1)[-1].strip()
        url = next((lines[j].strip() for j in range(i + 1, len(lines))
                    if lines[j].strip() and not lines[j].lstrip().startswith("#")), "")
        if not url.startswith("https://"):
            continue
        cid = ATTR_ID.search(line)
        lang = ATTR_LANG.search(line)
        if lang and lang.group(1).lower() not in {"en", "eng", "english", "en-us", "en-gb", "en-au"}:
            continue
        yield {"name": name, "url": url,
               "id": cid.group(1) if cid else "", "line": line}

def fetch(url, limit=65536):
    if urlsplit(url).scheme != "https":
        raise ValueError("non-https")
    req = Request(url, headers={"User-Agent": "AUTV-ReadOnly-Discovery/1.0",
                                "Accept": "application/vnd.apple.mpegurl, */*"})
    with urlopen(req, timeout=6) as response:
        final = response.geturl()
        if urlsplit(final).scheme != "https":
            raise ValueError("redirected-to-non-https")
        if response.status != 200:
            raise ValueError("http-error")
        return final, response.read(limit)

def media_probe(url):
    """A short technical check from the CI runner, not an Australian TV test."""
    try:
        seen = set()
        for _ in range(3):
            if url in seen:
                return "LOOP"
            seen.add(url)
            base, body = fetch(url)
            data = body.decode("utf-8-sig", errors="replace")
            if not data.lstrip().startswith("#EXTM3U"):
                return "NOT_HLS"
            lines = [line.strip() for line in data.splitlines() if line.strip()]
            for i, line in enumerate(lines):
                if line.startswith("#EXT-X-KEY") and ("SAMPLE-AES" in line or 'KEYFORMAT=' in line):
                    return "ENCRYPTED_OR_DRM"
            for i, line in enumerate(lines):
                if line.startswith("#EXT-X-STREAM-INF"):
                    next_uri = next((x for x in lines[i+1:] if not x.startswith("#")), None)
                    if not next_uri:
                        return "NO_VARIANT"
                    url = urljoin(base, next_uri)
                    break
            else:
                if not any(x.startswith("#EXTINF:") for x in lines):
                    return "NO_SEGMENTS"
                part = next((x for x in lines if not x.startswith("#")), None)
                if not part:
                    return "NO_SEGMENT_URI"
                _, payload = fetch(urljoin(base, part), limit=4096)
                return "SEGMENT_REACHABLE" if payload else "EMPTY_SEGMENT"
        return "NESTED_LIMIT"
    except Exception as error:
        # Never log full exception text: it may include signed URLs/tokens.
        return "FETCH_" + type(error).__name__.upper()

def run(probe=True):
    with CATALOGUE.open(newline="", encoding="utf-8-sig") as f:
        approved = {key(r["Channel name"]): r for r in csv.DictReader(f)
                    if r["Status"].casefold().strip() == "active"}
    current = {}
    for entry in parse_entries(PUBLISHED.read_text(encoding="utf-8-sig")):
        current[key(entry["name"])] = entry
    candidates = {}
    for src in ALLOWED_SOURCE_NAMES:
        file = SOURCES / (src + ".m3u")
        if not file.exists():
            continue
        for entry in parse_entries(file.read_text(encoding="utf-8-sig", errors="replace")):
            target = NAME_MAP.get(key(entry["name"]))
            if not target:
                continue
            candidates.setdefault(target, []).append({"source": src, **entry})
    tasks = []
    rows = []
    for target in TARGETS:
        current_entry = current.get(key(target))
        guide = approved.get(key(target))
        matches = candidates.get(target, [])
        dedup = set()
        for entry in matches:
            signature = (entry["source"], entry["url"])
            if signature in dedup:
                continue
            dedup.add(signature)
            if current_entry and entry["url"] == current_entry["url"]:
                continue
            tasks.append((target, entry, guide, bool(current_entry)))
        rows.append({"channel": target, "existing_number": guide["Channel number"] if guide else None,
                     "primary_published": bool(current_entry), "candidate_source_count": len(dedup),
                     "distinct_candidate_count": sum(1 for e in matches if not current_entry or e["url"] != current_entry["url"]),
                     "status": "EXISTING_FALLBACK_ONLY" if guide else "NEW_CHANNEL_TRIAL_ONLY"})
    test_results = []
    if probe:
        with ThreadPoolExecutor(max_workers=6) as pool:
            futures = {pool.submit(media_probe, e["url"]): (target, e, guide, has_primary)
                       for target, e, guide, has_primary in tasks[:30]}
            for fut in as_completed(futures):
                target, e, guide, has_primary = futures[fut]
                test_results.append({"channel": target, "source": e["source"],
                                     "source_host": urlsplit(e["url"]).hostname,
                                     "tvg_id": e["id"], "status": fut.result(),
                                     "role": "FALLBACK_CANDIDATE" if guide else "NEW_TRIAL_CANDIDATE",
                                     "primary_preserved": bool(has_primary)})
    test_results.sort(key=lambda r: (r["channel"], r["source"]))
    return {"generated_at_utc": datetime.now(timezone.utc).isoformat(),
            "evidence_scope": "GitHub runner only; no AU playback, licence or rights verification",
            "published_playlist_modified": False, "channel_guide_modified": False,
            "premium_not_probed": PREMIUM_NOT_SCANNED, "targets": rows, "probes": test_results}

def main():
    result = run(probe="--catalogue-only" not in sys.argv)
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(f"Discovery inspected {len(result['targets'])} identities and tested {len(result['probes'])} distinct streams.")
    for row in result["targets"]:
        print(f"{row['channel']}: existing={bool(row['existing_number'])}, found={row['candidate_source_count']}")
    for item in result["probes"]:
        print(f"{item['channel']} [{item['source']}] {item['status']} ({item['role']})")
    print("Read-only: no production source or channel number changed. CI reachability is not Australian device playback.")

if __name__ == "__main__":
    main()
