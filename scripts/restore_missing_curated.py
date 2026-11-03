#!/usr/bin/env python3
"""Recover approved curated channel entries missing from the published playlist.

Conservative publication repair: existing channels and their URLs are never changed;
only a missing, guide-approved, exactly name-matched custom M3U entry is appended.
It does not establish stream availability, Australian distribution rights or playback.
"""
import csv
import re
from pathlib import Path

GUIDE = Path("channel_guide.csv")
CUSTOM = Path("custom.m3u")
PLAYLIST = Path("playlist.m3u")
CHANNEL = re.compile(r'(?:^|\s)tvg-chno="(\d+)"')
ATTR_NAME = re.compile(r'(?:^|\s)tvg-name="([^"]+)"')

def entries(text):
    lines = text.splitlines()
    i = 0
    while i < len(lines):
        line = lines[i].strip()
        if not line.startswith("#EXTINF:"):
            i += 1
            continue
        j = i + 1
        while j < len(lines) and (not lines[j].strip() or lines[j].lstrip().startswith("#")):
            j += 1
        if j >= len(lines):
            break
        uri = lines[j].strip()
        yield line, uri
        i = j + 1

def main():
    with GUIDE.open(newline="", encoding="utf-8-sig") as f:
        guide = {
            r["Channel name"].strip().casefold(): r
            for r in csv.DictReader(f)
            if r.get("Status", "").strip().casefold() == "active"
        }
    source = CUSTOM.read_text(encoding="utf-8-sig")
    published = PLAYLIST.read_text(encoding="utf-8-sig")
    if not published.startswith("#EXTM3U"):
        raise SystemExit("Refusing to repair malformed playlist")
    existing = {m.group(1) for line, _ in entries(published) if (m := CHANNEL.search(line))}
    additions = []
    for line, uri in entries(source):
        name = line.rsplit(",", 1)[-1].strip()
        row = guide.get(name.casefold())
        if row is None:
            continue
        number = row["Channel number"].strip()
        if number in existing:
            continue
        if not uri.startswith("https://"):
            continue
        line = re.sub(r'\s+tvg-chno="[^"]*"', "", line)
        line = line.replace("#EXTINF:-1", f'#EXTINF:-1 tvg-chno="{number}"', 1)
        line = re.sub(r'\s+group-title="[^"]*"', "", line)
        line = line.replace("#EXTINF:-1", f'#EXTINF:-1 group-title="{row["Genre"].strip()}"', 1)
        additions.append((int(number), line, uri))
        existing.add(number)
    if not additions:
        print("No guide-approved missing curated entries to restore.")
        return
    blocks = [f"{line}\n{uri}" for _, line, uri in sorted(additions)]
    PLAYLIST.write_text(published.rstrip() + "\n" + "\n".join(blocks) + "\n", encoding="utf-8")
    print(f"Restored {len(additions)} approved missing curated listings; no existing entry changed.")
    for number, line, _ in sorted(additions):
        print(f"Added {number}: {line.rsplit(',',1)[-1]}")
    print("WARNING: publication does not prove HLS segment reachability or live TV playback.")

if __name__ == "__main__":
    main()
