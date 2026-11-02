#!/usr/bin/env python3
"""Channel operations recovery monitor. Never removes or replaces stream sources."""
import csv
import re
from pathlib import Path

GUIDE = Path("channel_guide.csv")
PLAYLIST = Path("playlist.m3u")
RECOVERY = Path("audits/36_channel_outcome_2026-11-03.csv")
OUTPUT = Path("audits/channel_recovery_current.csv")
CONFIRMED = {"227": "Rugby Pass TV", "239": "Rally TV", "612": "Whose Line Is It Anyway?"}
RE_CHNO = re.compile(r'(?:^|\s)tvg-chno="(\d+)"')

def main():
    with GUIDE.open(encoding="utf-8-sig",newline="") as f:
        approved = {r["Channel number"].strip(): r for r in csv.DictReader(f)
                    if r["Status"].strip().casefold() == "active"}
    published = {}
    lines = PLAYLIST.read_text(encoding="utf-8-sig").splitlines()
    for i, line in enumerate(lines):
        if line.startswith("#EXTINF:") and (m := RE_CHNO.search(line)):
            published[m.group(1)] = line.rsplit(",",1)[-1].strip()
    with RECOVERY.open(encoding="utf-8-sig",newline="") as f:
        original = list(csv.DictReader(f))
    OUTPUT.parent.mkdir(parents=True,exist_ok=True)
    with OUTPUT.open("w",encoding="utf-8",newline="") as f:
        fields = ["channel_number","channel_name","assigned_source","published",
                  "owner_playback","next_action","removal"]
        writer = csv.DictWriter(f,fieldnames=fields); writer.writeheader()
        for row in original:
            number = row["channel_number"]
            found = number in published
            confirmed = number in CONFIRMED
            writer.writerow({
                "channel_number":number, "channel_name":row["channel_name"],
                "assigned_source":row["assigned_source"],
                "published":"YES" if found else "NO",
                "owner_playback":"OWNER_CONFIRMED_2026_11_03" if confirmed else "NOT_TESTED",
                "next_action":("MONITOR_REFRESH_AND_PLAYBACK" if found and confirmed else
                               "VERIFY_DEVICE_PLAYBACK" if found else
                               "RESEARCH_AUTHORISED_SOURCE_AND_VALIDATE"),
                "removal":"NOT_APPROVED"})
    absent = [n for n in CONFIRMED if n not in published]
    changed = [n for n,name in CONFIRMED.items()
               if n in published and name.casefold() != published[n].casefold()]
    print(f"Published approved: {sum(n in published for n in approved)}/{len(approved)}")
    print(f"Original 36 restored in playlist: {sum(r['channel_number'] in published for r in original)}/36")
    print(f"Still unpublished: {sum(r['channel_number'] not in published for r in original)}/36")
    print(f"Owner-confirmed source regressions: missing={absent}, mismatched={changed}")
    if absent or changed:
        raise SystemExit("Owner-confirmed recovery channels regressed")
    print("This audit does not validate CDN rights, source reachability or real-time playback.")

if __name__ == "__main__":
    main()
