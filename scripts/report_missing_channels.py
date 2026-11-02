#!/usr/bin/env python3
"""Read-only AUTV catalogue discrepancy and replacement-first audit.

Usage:
 python3 scripts/report_missing_channels.py --guide channel_guide.csv --playlist playlist.m3u --output audits/current_missing_channels.csv

A missing published entry is NOT proof that a source is dead. This script makes
no network requests and cannot establish Australian Android TV playback.
"""
import argparse
import csv
import re
from pathlib import Path

NUMBER = re.compile(r'(?:^|\s)tvg-chno="([0-9]+)"')

def playlist_numbers(path):
    text = Path(path).read_text(encoding="utf-8-sig")
    numbers = set()
    duplicates = set()
    for line in text.splitlines():
        if not line.startswith("#EXTINF:"):
            continue
        hit = NUMBER.search(line)
        if hit:
            if hit.group(1) in numbers:
                duplicates.add(hit.group(1))
            numbers.add(hit.group(1))
    return numbers, duplicates

def audit(guide_path, playlist_path):
    published, duplicates = playlist_numbers(playlist_path)
    with Path(guide_path).open(encoding="utf-8-sig", newline="") as f:
        guide = [row for row in csv.DictReader(f)
                 if row.get("Channel number", "").strip()
                 and row.get("Status", "Active").strip().lower() == "active"]
    approved = {row["Channel number"].strip() for row in guide}
    missing = []
    for row in guide:
        number = row["Channel number"].strip()
        if number in published:
            continue
        missing.append({
            "channel_number": number,
            "channel_name": row["Channel name"].strip(),
            "genre": row["Genre"].strip(),
            "primary_provider": row.get("Source", "").strip(),
            "alternate_provider": row.get("Alternate source", "").strip(),
            "playlist_status": "ABSENT",
            "playback_status": "NOT_TESTED",
            "replacement_status": "INVESTIGATE",
            "disposition": "HOLD_REPLACEMENT_FIRST",
        })
    return missing, sorted(published - approved, key=int), sorted(duplicates, key=int), len(guide), len(published)

def main():
    p = argparse.ArgumentParser()
    p.add_argument("--guide", default="channel_guide.csv")
    p.add_argument("--playlist", default="playlist.m3u")
    p.add_argument("--output", default="audits/current_missing_channels.csv")
    args = p.parse_args()
    missing, extra, duplicates, guide_count, published_count = audit(args.guide, args.playlist)
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    columns = ["channel_number","channel_name","genre","primary_provider",
               "alternate_provider","playlist_status","playback_status",
               "replacement_status","disposition"]
    with output.open("w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=columns)
        writer.writeheader()
        writer.writerows(missing)
    print(f"Approved: {guide_count}; published IDs: {published_count}; missing: {len(missing)}")
    print(f"Published but not approved: {', '.join(extra) or 'none'}")
    print(f"Duplicate playlist numbers: {', '.join(duplicates) or 'none'}")
    print("WARNING: absence does not establish failed playback; no retirement is authorised by this audit.")

if __name__ == "__main__":
    main()
