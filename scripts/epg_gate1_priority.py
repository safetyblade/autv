#!/usr/bin/env python3
"""Rank *evidence-backed* AUTV EPG exceptions from the measured published audit.

No automatic EPG crosswalk is created: schedule equivalence and territory must be
confirmed by a reviewer before any identity substitution is approved.
"""
import csv
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
SOURCE=ROOT/"audits/epg_gate1_channel_audit.csv"
DEST=ROOT/"audits/epg_gate1_recovery_queue.csv"

def prioritise(rows):
    exceptions=[]
    for row in rows:
        if row["published"]!="YES":
            continue
        status=row["status"]
        coverage=float(row["forward_24h_coverage_pct"])
        if status=="NO_EPG_CHANNEL":
            priority=1
            route="Find correct source channel ID; verify programme equivalence and region"
        elif status in ("NO_PROGRAMMES","NO_CURRENT_PROGRAMME"):
            priority=2
            route="Verify XMLTV source freshness, time window and programme continuity"
        elif coverage<80:
            priority=3
            route="Investigate programme horizon, gaps or missing future events"
        else:
            continue
        exceptions.append({
            "priority":priority,
            "number":row["number"],
            "name":row["name"],
            "genre":row["genre"],
            "tvg_id":row["tvg_id"],
            "status":status,
            "coverage_24h_pct":row["forward_24h_coverage_pct"],
            "programme_events":row["programme_events"],
            "recommended_investigation":route,
            "resolution":"REVIEW_REQUIRED",
        })
    return sorted(exceptions,key=lambda r:(int(r["priority"]),float(r["coverage_24h_pct"]),int(r["number"])))

def main():
    with SOURCE.open(newline="",encoding="utf-8") as stream:
        data=list(csv.DictReader(stream))
    queue=prioritise(data)
    DEST.parent.mkdir(exist_ok=True)
    with DEST.open("w",newline="",encoding="utf-8") as stream:
        writer=csv.DictWriter(stream,fieldnames=[
            "priority","number","name","genre","tvg_id","status",
            "coverage_24h_pct","programme_events","recommended_investigation","resolution"])
        writer.writeheader();writer.writerows(queue)
    print(f"EPG Gate 1: {len(queue)} published channels need investigation; {sum(r['priority']==1 for r in queue)} without an EPG channel record")
if __name__=="__main__":main()
