#!/usr/bin/env python3
"""Regenerate AUTV's household Markdown directory from canonical data.

The curated FAST/IPTV catalogue comes from channel_guide.csv.
The app's approved no-login Australian TV catalogue comes from
AustralianLiveChannels.kt; never maintain an independent fake status list.
"""
import csv
import re
from collections import Counter
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "channel_guide.csv"
APPROVED = ROOT / "android/app/src/main/java/dev/prestwich/autv/provider/AustralianLiveChannels.kt"
TARGET = ROOT / "TV_GUIDE.md"

with SOURCE.open(encoding="utf-8-sig", newline="") as stream:
    rows = [r for r in csv.DictReader(stream) if r["Status"].strip().casefold() == "active"]
rows.sort(key=lambda r: int(r["Channel number"]))
numbers = [int(r["Channel number"]) for r in rows]
if not rows or len(numbers) != len(set(numbers)):
    raise ValueError("The current active catalogue is empty or contains duplicate numbers")
if any(not 100 <= n <= 1399 for n in numbers):
    raise ValueError("Curated FAST/IPTV channel number outside its supported range")

source = APPROVED.read_text(encoding="utf-8")
pattern = re.compile(r'LiveChannel\(\s*(\d+)\s*,\s*"([^"]+)"\s*,\s*"([^"]+)"\s*,\s*"([^"]+)"', re.S)
live = [(int(number), name, provider) for number, name, _identity, provider in pattern.findall(source)]
if not live or len({number for number, _, _ in live}) != len(live):
    raise ValueError("Australian TV approval list missing or contains duplicate channel numbers")
live.sort()

def tidy(value):
    return str(value or "").replace("|", r"\|").replace("\r", " ").replace("\n", " ").replace("<", "‹").strip()

date = datetime.now(ZoneInfo("Australia/Melbourne")).strftime("%-d %B %Y")
counts = Counter(r["Genre"] for r in rows)
categories = ["News", "Sport", "Movies", "Game Shows", "Crime", "Comedy",
              "Entertainment", "Reality & Lifestyle", "Factual", "Kids & Animation", "Music"]
summary = "\n".join(f"| {tidy(category)} | {counts[category]} |" for category in categories)
australian = "\n".join(f"| {number} | {tidy(name)} | {tidy(provider)} | No |"
                       for number, name, provider in live)
directory = "\n".join(
    "| " + " | ".join(tidy(r[col]) for col in
    ("Channel number", "Channel name", "Genre", "Source", "EPG")) + " |"
    for r in rows
)

document = f"""# AUTV — TV guide and current channel directory

Generated **{date}** from `channel_guide.csv` and `AustralianLiveChannels.kt`.
The curated source lists **{len(rows)} active FAST/IPTV channels**. The published playlist can have fewer streams during upstream disruptions. A listed source is not proof of working audio/video, distribution permission or current EPG data.

## Getting started

Browse categories or channel numbers with your TV remote; press **OK** to watch and long-press **OK** to add/remove a Favourite. Select a startup channel in Settings. Following an approved playlist refresh, use **Settings → Refresh channels and TV guide** in supported versions to update local channel and programme data.

## Australian TV — approved free live channels

| Number | Channel | Provider | Account required |
| ---: | --- | --- | --- |
{australian}

The channel streams are physically approved on the owner's Australian TV and play without a broadcaster account. ABC's separate optional device-code link is retained for possible future capabilities. Provider-labelled services 9Now, 7plus and SBS have independent activation requirements.

## Catalogue summary

| Category | Active channels |
| --- | ---: |
{summary}
| **Curated FAST/IPTV total** | **{len(rows)}** |

The six Australian TV rows above are app-managed and not counted in this FAST/IPTV CSV total.

## Full numbered FAST/IPTV directory

| Number | Channel | Category | Source | Guide |
| ---: | --- | --- | --- | --- |
{directory}

## Editorial notes

For the live app catalogue use the code and datasets, not obsolete manual lists. Historical investigations and unsuccessful stream tests are dated evidence—not permanent prohibitions. See [Source discovery](docs/SOURCE_DISCOVERY.md) and [Maintenance](docs/MAINTENANCE.md). Never import a foreign-region playlist or paid account token without permission and physical playback verification.
"""
TARGET.write_text(document, encoding="utf-8")
print(f"Published {TARGET}: {len(rows)} curated channels and {len(live)} approved Australian TV channels")
