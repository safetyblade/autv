#!/usr/bin/env python3
"""Regenerate the household Markdown channel directory from channel_guide.csv.

Preserves the user's setup/troubleshooting guidance and editorial notes while
replacing the dated inventory, category totals and full numbered table.
Run after every approved channel-guide update.
"""
import csv
import re
from collections import Counter
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "channel_guide.csv"
TARGET = ROOT / "TV_GUIDE.md"

with SOURCE.open(newline="", encoding="utf-8-sig") as handle:
    rows = [r for r in csv.DictReader(handle) if r["Status"].strip().lower() == "active"]
rows.sort(key=lambda r: int(r["Channel number"]))
numbers = [int(r["Channel number"]) for r in rows]
if not rows or len(numbers) != len(set(numbers)):
    raise ValueError("Missing or duplicate active FAST/IPTV channel numbers")

existing = TARGET.read_text(encoding="utf-8")
if "## Catalogue summary\n" not in existing or "## Editorial notes\n" not in existing:
    raise ValueError("Household guide sections changed; refusing destructive rewrite")
intro = existing.split("## Catalogue summary\n", 1)[0]
tail = "## Editorial notes\n" + existing.split("## Editorial notes\n", 1)[1]
today = datetime.now(ZoneInfo("Australia/Melbourne")).strftime("%-d %B %Y")
snapshot = (f"**Catalogue snapshot:** {today} · **FAST/IPTV master guide:** "
            f"{len(rows)} active channels · **Source:** [channel_guide.csv](channel_guide.csv)")
intro, n = re.subn(r"\*\*Catalogue snapshot:\*\*[^\n]*", lambda _: snapshot, intro)
if n != 1:
    raise ValueError("Missing catalogue snapshot header")

def tidy(s):
    return (s or "").replace("|", r"\|").replace("\r", " ").replace("\n", " ").strip()

counts = Counter(r["Genre"] for r in rows)
category_names = list(dict.fromkeys(r["Genre"] for r in rows))
summary = "\n".join(f"| {tidy(name)} | {counts[name]} |" for name in category_names)
directory = "\n".join(
    "| " + " | ".join(tidy(r[k]) for k in (
        "Channel number", "Channel name", "Genre", "Source", "EPG", "Description")) + " |"
    for r in rows
)
middle = (
    "## Catalogue summary\n\n"
    "| Category | Configured active channels |\n"
    "| --- | ---: |\n"
    f"{summary}\n"
    f"| **FAST/IPTV total** | **{len(rows)}** |\n\n"
    "The current list is generated from the authoritative CSV. The published M3U may contain fewer "
    "channels due to source availability or refresh output. Australian TV native-provider channels "
    "are **not included** in this count.\n\n"
    "## Full numbered FAST/IPTV directory\n\n"
    "| Number | Channel | Category | Provider/source | Guide | Description |\n"
    "| ---: | --- | --- | --- | --- | --- |\n"
    f"{directory}\n\n"
)
TARGET.write_text(intro + middle + tail, encoding="utf-8")
print(f"Generated {TARGET.name}: {len(rows)} active channels ({len(counts)} categories)")
