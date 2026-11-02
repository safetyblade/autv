#!/usr/bin/env python3
"""Read-only missing AUTV channel triage. No network probes or removals."""
import csv
import re
from pathlib import Path
from collections import Counter

SOURCES = {
    "Pluto": "selected_pluto_channels.txt",
    "Plex AU": "selected_plex_channels.txt",
    "Tubi": "selected_tubi_channels.txt",
    "Roku": "selected_roku_channels.txt",
    "Samsung AU": "selected_channels.txt",
    "Curated": "custom.m3u",
}
CHANNEL = re.compile(r'(?:^|\s)tvg-chno="(\d+)"')

def collect():
    with Path("channel_guide.csv").open(encoding="utf-8-sig", newline="") as f:
        guide = [r for r in csv.DictReader(f) if r["Status"].strip().lower() == "active"]
    text = Path("playlist.m3u").read_text(encoding="utf-8-sig")
    published = {m.group(1) for ln in text.splitlines() if ln.startswith("#EXTINF:")
                 if (m := CHANNEL.search(ln))}
    selections = {source: Path(path).read_text(encoding="utf-8-sig").casefold()
                  if Path(path).exists() else "" for source, path in SOURCES.items()}
    output = []
    for r in guide:
        number = r["Channel number"].strip()
        if number in published:
            continue
        provider = r["Source"].strip()
        name = r["Channel name"].strip()
        listed = name.casefold() in selections.get(provider, "")
        output.append({
            "channel_number": number, "channel_name": name,
            "genre": r["Genre"].strip(),
            "primary_provider": provider, "alternate_provider": r["Alternate source"].strip(),
            "selected_name_present": "YES" if listed else "NO_OR_UNVERIFIED",
            "playback": "NOT_TESTED", "action": "TRACE_V1_AND_PROVIDER",
            "removal": "NOT_APPROVED",
        })
    return sorted(output, key=lambda r:int(r["channel_number"]))

def main():
    rows = collect()
    path = Path("audits/missing_channels_recovery_queue.csv")
    path.parent.mkdir(parents=True, exist_ok=True)
    keys = ["channel_number","channel_name","genre","primary_provider",
            "alternate_provider","selected_name_present","playback","action","removal"]
    with path.open("w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=keys)
        writer.writeheader()
        writer.writerows(rows)
    print(f"Unpublished approved channels: {len(rows)}")
    for key, count in sorted(Counter((r["primary_provider"],r["selected_name_present"]) for r in rows).items()):
        print(*key, count, sep=" | ")
    print("No candidate has been tested; do not use this report to remove channels.")

if __name__ == "__main__":
    main()
