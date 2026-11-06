#!/usr/bin/env python3
"""Channel operations recovery monitor. Never removes or replaces stream sources."""
import csv
import re
from pathlib import Path

GUIDE = Path("channel_guide.csv")
PLAYLIST = Path("playlist.m3u")
RECOVERY = Path("audits/36_channel_outcome_2026-11-03.csv")
OUTPUT = Path("audits/channel_recovery_current.csv")
CONFIRMED = {"Rugby Pass TV", "Rally TV", "Whose Line Is It Anyway?"}
RE_CHNO = re.compile(r'(?:^|\s)tvg-chno="(\d+)"')

def main():
    with GUIDE.open(encoding="utf-8-sig",newline="") as f:
        approved = {r["Channel number"].strip(): r for r in csv.DictReader(f)
                    if r["Status"].strip().casefold() == "active"}
    # Historical recovery rows retain old numbers; the current guide controls numbering.
    approved_by_name = {r["Channel name"].strip().casefold(): number for number, r in approved.items()}
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
            name = row["channel_name"].strip()
            number = approved_by_name.get(name.casefold(), row["channel_number"])
            found = number in published and published[number].casefold() == name.casefold()
            confirmed = name in CONFIRMED
            writer.writerow({
                "channel_number":number, "channel_name":row["channel_name"],
                "assigned_source":row["assigned_source"],
                "published":"YES" if found else "NO",
                "owner_playback":"OWNER_CONFIRMED_2026_11_03" if confirmed else "NOT_TESTED",
                "next_action":("MONITOR_REFRESH_AND_PLAYBACK" if found and confirmed else
                               "VERIFY_DEVICE_PLAYBACK" if found else
                               "RESEARCH_AUTHORISED_SOURCE_AND_VALIDATE"),
                "removal":"NOT_APPROVED"})
    absent = [name for name in sorted(CONFIRMED)
              if approved_by_name.get(name.casefold()) not in published]
    changed = [name for name in sorted(CONFIRMED)
               if (number := approved_by_name.get(name.casefold())) in published
               and name.casefold() != published[number].casefold()]
    print(f"Published approved: {sum(n in published for n in approved)}/{len(approved)}")
    restored = sum(published.get(approved_by_name.get(r["channel_name"].strip().casefold(), r["channel_number"]), "").casefold() == r["channel_name"].strip().casefold() for r in original)
    print(f"Original 36 restored in playlist: {restored}/36")
    print(f"Still unpublished: {len(original) - restored}/36")
    print(f"Owner-confirmed source regressions: missing={absent}, mismatched={changed}")
    if absent or changed:
        raise SystemExit("Owner-confirmed recovery channels regressed")
    print("This audit does not validate CDN rights, source reachability or real-time playback.")

if __name__ == "__main__":
    main()
