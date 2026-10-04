from pathlib import Path
import csv
import json
import re

ROOT = Path(__file__).resolve().parents[2]
GUIDE = ROOT / "channel_guide.csv"
PLAYLIST = ROOT / "playlist.m3u"
OUTPUT = ROOT / "guide.json"

def attr(line: str, key: str) -> str:
    match = re.search(rf'{re.escape(key)}="([^"]*)"', line)
    return match.group(1) if match else ""

def playlist_by_channel():
    lines = PLAYLIST.read_text(encoding="utf-8-sig").splitlines()
    found = {}
    for i, line in enumerate(lines):
        if not line.startswith("#EXTINF:"):
            continue
        channel_no = attr(line, "tvg-chno")
        if not channel_no:
            continue
        url = ""
        for candidate in lines[i + 1:]:
            candidate = candidate.strip()
            if candidate and not candidate.startswith("#"):
                url = candidate
                break
        if not url:
            continue
        found[channel_no] = {
            "streamUrl": url,
            "tvgId": attr(line, "tvg-id") or None,
            "logoUrl": attr(line, "tvg-logo") or None,
        }
    return found

streams = playlist_by_channel()
channels = []

with GUIDE.open(newline="", encoding="utf-8-sig") as handle:
    for row in csv.DictReader(handle):
        if row.get("Status", "Active").strip().casefold() != "active":
            continue
        number = row["Channel number"].strip()
        stream = streams.get(number)
        channels.append({
            "number": int(number),
            "name": row["Channel name"].strip(),
            "genre": row["Genre"].strip(),
            "subgenre": row["Subgenre"].strip(),
            "description": row.get("Description", "").strip(),
            "epg": row.get("EPG", "").strip(),
            "available": stream is not None,
            "streamUrl": stream["streamUrl"] if stream else None,
            "tvgId": stream["tvgId"] if stream else None,
            "logoUrl": stream["logoUrl"] if stream else None,
        })

payload = {
    "schemaVersion": 1,
    "generatedFrom": {"channelGuide": "channel_guide.csv", "playlist": "playlist.m3u"},
    "endpoints": {
        "playlist": "https://raw.githubusercontent.com/safetyblade/autv/main/playlist.m3u",
        "epg": "https://raw.githubusercontent.com/safetyblade/autv/main/epg.xml.gz",
        "guide": "https://raw.githubusercontent.com/safetyblade/autv/main/guide.json",
    },
    "activeCount": len(channels),
    "availableCount": sum(1 for channel in channels if channel["available"]),
    "channels": channels,
}
OUTPUT.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
print(f"Wrote {OUTPUT} with {len(channels)} guide channels and {payload['availableCount']} available streams")
