#!/usr/bin/env python3
"""Read-only report for curated fallbacks and published channel coverage.

Distinguishes catalogued, published and genuinely tested (unknown) playback.
Never probes protected endpoints, guesses rights, or logs stream query strings.
"""
import csv
import re
from pathlib import Path
from urllib.parse import urlsplit

NUMBER = re.compile(r'(?:^|\s)tvg-chno="(\d+)"')
ID = re.compile(r'(?:^|\s)tvg-id="([^"]+)"')

def entries(path):
    lines = path.read_text(encoding="utf-8-sig").splitlines()
    result = []
    for pos, line in enumerate(lines[:-1]):
        if line.startswith("#EXTINF:"):
            name = line.rsplit(",", 1)[-1].strip()
            url = lines[pos+1].strip()
            if url.startswith("https://"):
                result.append((name, line, url))
    return result

def main():
    with Path("channel_guide.csv").open(encoding="utf-8-sig",newline="") as f:
        approved = [r for r in csv.DictReader(f)
                    if r["Status"].strip().casefold() == "active"]
    published = {}
    for name, header, url in entries(Path("playlist.m3u")):
        if m := NUMBER.search(header):
            published[m.group(1)] = (name, header, url)
    curated = {}
    for name, header, url in entries(Path("custom.m3u")):
        curated.setdefault(name.casefold(), (header, url))
    out = Path("audits/curated_fallback_coverage.csv")
    out.parent.mkdir(exist_ok=True)
    with out.open("w",newline="",encoding="utf-8") as f:
        fields = ["channel_number","channel_name","published","curated_fallback",
                  "curated_provider_id","curated_host","currently_published_host",
                  "live_playback","action"]
        w=csv.DictWriter(f,fieldnames=fields); w.writeheader()
        for r in approved:
            number=r["Channel number"].strip()
            name=r["Channel name"].strip()
            found=published.get(number)
            backup=curated.get(name.casefold())
            if not found and not backup:
                action="DISCOVER_AUTHORISED_SOURCE"
            elif not found:
                action="CHECK_REFRESH_PUBLICATION"
            else:
                action="MONITOR_AND_VERIFY_PLAYBACK"
            w.writerow({
                "channel_number":number,"channel_name":name,
                "published":"YES" if found else "NO",
                "curated_fallback":"YES" if backup else "NO",
                "curated_provider_id":(ID.search(backup[0]).group(1) if backup and ID.search(backup[0]) else ""),
                "curated_host":urlsplit(backup[1]).hostname if backup else "",
                "currently_published_host":urlsplit(found[2]).hostname if found else "",
                "live_playback":"NOT_TESTED_BY_WORKFLOW",
                "action":action,
            })
    print(f"Approved {len(approved)}; published {len(published)}; curated fallback coverage "
          f"{sum(r['Channel name'].strip().casefold() in curated for r in approved)}")
    print("No stream URLs, signed tokens, or authentication parameters exposed.")
    print("Reports are not media-segment or Australian TV playback checks.")

if __name__ == "__main__":
    main()
