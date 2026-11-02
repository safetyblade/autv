#!/usr/bin/env python3
"""Read-only historical AUTV channel source discovery.

Searches local git history of playlist.m3u for current unpublished channel IDs.
Never prints or stores complete URLs, query strings, JWTs or signed media paths.
Run with full git history (actions/checkout fetch-depth: 0).
"""
import csv
import re
import subprocess
from collections import defaultdict
from pathlib import Path
from urllib.parse import urlsplit

EXTINF = re.compile(r'(?:^|\s)tvg-chno="(\d+)"')

def git(*args):
    return subprocess.run(["git", *args], capture_output=True, text=True, check=True).stdout

def historical_sources(ref_limit=500):
    revisions = git("log", "--format=%H", "--", "playlist.m3u").splitlines()[:ref_limit]
    found = defaultdict(list)
    for sha in revisions:
        result = subprocess.run(["git","show",f"{sha}:playlist.m3u"],capture_output=True,text=True)
        if result.returncode:
            continue
        lines = result.stdout.splitlines()
        for i, line in enumerate(lines[:-1]):
            if not line.startswith("#EXTINF:"):
                continue
            match = EXTINF.search(line)
            if not match:
                continue
            url = lines[i+1].strip()
            parsed = urlsplit(url)
            if parsed.scheme not in ("http","https"):
                continue
            key=(sha,parsed.hostname or "")
            if key not in found[match.group(1)]:
                found[match.group(1)].append(key)
    return found, len(revisions)

def main():
    target=Path("audits/36_channel_outcome_2026-11-03.csv")
    with target.open(newline="",encoding="utf-8") as f:
        channels=list(csv.DictReader(f))
    found, inspected=historical_sources()
    out=Path("audits/36_channel_history_review.csv")
    with out.open("w",newline="",encoding="utf-8") as f:
        fields=["channel_number","channel_name","historical_playlist_present",
                "last_observed_commit","historical_source_host","live_validation"]
        writer=csv.DictWriter(f,fieldnames=fields)
        writer.writeheader()
        for channel in channels:
            hits=found.get(channel["channel_number"],[])
            sha,host=hits[0] if hits else ("","")
            writer.writerow({"channel_number":channel["channel_number"],
                             "channel_name":channel["channel_name"],
                             "historical_playlist_present":"YES" if hits else "NOT_FOUND",
                             "last_observed_commit":sha,
                             "historical_source_host":host,
                             "live_validation":"NOT_TESTED"})
    print(f"History revisions inspected: {inspected}; channels with prior published URLs: "
          f"{sum(bool(found.get(c['channel_number'])) for c in channels)}/{len(channels)}")
    print("No URL or token is output. Historical presence is not live-playback evidence.")

if __name__=="__main__":
    main()
