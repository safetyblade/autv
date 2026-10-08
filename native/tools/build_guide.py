"""Build a fresh native snapshot from the authoritative CSV; never merge old JSON."""
from pathlib import Path
import csv
import json
import re

ROOT = Path(__file__).resolve().parents[2]
GUIDE = ROOT / "channel_guide.csv"
PLAYLIST = ROOT / "playlist.m3u"
OUTPUT = ROOT / "guide.json"
HEADERS = ["Channel number", "Channel name", "Genre", "Subgenre", "EPG", "Source", "Alternate source", "Status", "Description"]
REQUIRED = [field for field in HEADERS if field != "Alternate source"]


def attr(line: str, key: str) -> str:
    # Fast string search for key="value" pattern in M3U header lines, avoiding regex overhead
    marker = f'{key}="'
    idx = line.find(marker)
    if idx == -1:
        return ""
    start = idx + len(marker)
    end = line.find('"', start)
    return line[start:end] if end != -1 else ""


def playlist_by_channel(path=PLAYLIST):
    found = {}
    entry = None
    for line in path.read_text(encoding="utf-8-sig").splitlines():
        line = line.strip()
        if line.startswith("#EXTINF:"):
            entry = line
        elif line and not line.startswith("#") and entry is not None:
            number = attr(entry, "tvg-chno")
            if number:
                found[number] = {"streamUrl": line, "tvgId": attr(entry, "tvg-id") or None,
                                 "logoUrl": attr(entry, "tvg-logo") or None}
            entry = None
    return found


def active_rows(path):
    rows = []
    seen = set()
    with path.open(newline="", encoding="utf-8-sig") as handle:
        reader = csv.DictReader(handle, strict=True)
        if reader.fieldnames != HEADERS:
            raise ValueError("Channel guide headers must match the expected CSV schema")
        for row in reader:
            if None in row or any(value is None for value in row.values()):
                raise ValueError(f"CSV line {reader.line_num}: extra or missing fields")
            row = {key: value.strip() for key, value in row.items()}
            for field in REQUIRED:
                if not row[field]:
                    raise ValueError(f"CSV line {reader.line_num}: missing required {field}")
            if not re.fullmatch(r"[0-9]+", row["Channel number"]):
                raise ValueError(f"CSV line {reader.line_num}: invalid channel number")
            number = int(row["Channel number"])
            if number in seen:
                raise ValueError(f"CSV line {reader.line_num}: duplicate channel number {number}")
            seen.add(number)
            if row["Status"].casefold() in {"active", "app test"}:
                rows.append(row)
    if not rows:
        raise ValueError("No Active/App Test channels; refusing to publish an empty guide")
    return rows


def build(guide=GUIDE, playlist=PLAYLIST, output=OUTPUT):
    rows = active_rows(guide)  # Native guide includes Active plus isolated App Test rows.
    streams = playlist_by_channel(playlist)
    channels = []
    for row in rows:
        number = int(row["Channel number"])
        stream = streams.get(str(number))
        channels.append({
            "number": number, "name": row["Channel name"], "genre": row["Genre"],
            "subgenre": row["Subgenre"], "description": row["Description"], "epg": row["EPG"],
            "available": stream is not None, "streamUrl": stream["streamUrl"] if stream else None,
            "tvgId": stream["tvgId"] if stream else None, "logoUrl": stream["logoUrl"] if stream else None,
        })
    channels.sort(key=lambda channel: channel["number"])
    if {channel["number"] for channel in channels} != {int(row["Channel number"]) for row in rows}:
        raise ValueError("Generated channels do not exactly match Active CSV rows")
    payload = {
        "schemaVersion": 1,
        "generatedFrom": {"channelGuide": "channel_guide.csv", "playlist": "playlist.m3u"},
        "endpoints": {
            "playlist": "https://raw.githubusercontent.com/safetyblade/autv/main/playlist.m3u",
            "epg": "https://raw.githubusercontent.com/safetyblade/autv/main/epg.xml.gz",
            "guide": "https://raw.githubusercontent.com/safetyblade/autv/main/guide.json",
        },
        "activeCount": len(channels),
        "availableCount": sum(channel["available"] for channel in channels), "channels": channels,
    }
    temporary = output.with_name(output.name + ".tmp")
    try:
        temporary.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
        temporary.replace(output)
    finally:
        temporary.unlink(missing_ok=True)
    return payload


if __name__ == "__main__":
    result = build()
    print(f"Wrote {OUTPUT} with {result['activeCount']} guide channels and {result['availableCount']} available streams")
