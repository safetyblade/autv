#!/usr/bin/env python3
"""Read-only cross-source XMLTV schedule consensus for AUTV's EPG candidates.

Matching schedules across independent feeds supports review but does not prove
the stream is airing those programmes. Never writes the approved crosswalk.
"""
import csv
import gzip
import itertools
import json
import re
import xml.etree.ElementTree as ET
from collections import defaultdict
from datetime import datetime, timedelta, timezone
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
CANDIDATES=ROOT/"audits/epg_candidate_identity_triage.csv"
OUTPUT=ROOT/"audits/epg_candidate_schedule_consistency.csv"
SUMMARY=ROOT/"audits/epg_candidate_schedule_consistency_summary.json"
TIME_RE=re.compile(r"^(\d{12})(\d{2})?\s*([+-]\d{4})?$")

def instant(value):
    m=TIME_RE.match((value or "").strip())
    if not m:return None
    try:
        d=datetime.strptime(m[1]+(m[2] or "00"),"%Y%m%d%H%M%S")
        zone=m[3]
        if zone:
            sign=1 if zone[0]=="+" else -1
            mins=sign*(int(zone[1:3])*60+int(zone[3:5]))
            return d.replace(tzinfo=timezone(timedelta(minutes=mins))).astimezone(timezone.utc)
        return d.replace(tzinfo=timezone.utc)
    except (ValueError,OverflowError):return None

def title_key(title):
    return re.sub(r"[^a-z0-9]","",(title or "").casefold())

def load_programmes(candidates,sources,now):
    wanted=defaultdict(set)
    for row in candidates:
        sid=row.get("candidate_source_id","")
        source=row.get("candidate_source_file","")
        if sid and source:wanted[source].add(sid)
    result=defaultdict(list)
    until=now+timedelta(hours=24)
    for source,ids in wanted.items():
        path=sources.get(source)
        if not path or not path.is_file():continue
        opener=gzip.open if path.suffix==".gz" else open
        with opener(path,"rb") as stream:
            for event,elem in ET.iterparse(stream,events=("end",)):
                if elem.tag=="programme":
                    sid=elem.get("channel") or ""
                    if sid in ids:
                        start=instant(elem.get("start"));stop=instant(elem.get("stop"))
                        if start and stop and stop>now and start<until and stop>start:
                            title=elem.findtext("title") or ""
                            if title_key(title):
                                result[(source,sid)].append((max(start,now),min(stop,until),title_key(title)))
                    elem.clear()
                elif elem.tag=="channel":elem.clear()
    for key in result:result[key].sort()
    return result

def comparison(left,right,now):
    # Compare occupied programme spans sampled at 15-minute slots.
    # Excludes absent times from the similarity denominator.
    sample=timedelta(minutes=15)
    cursor=now.replace(second=0,microsecond=0)
    if cursor.minute%15:cursor+=timedelta(minutes=15-cursor.minute%15)
    end=now+timedelta(hours=24)
    shared=agree=conflict=0
    while cursor<end:
        a=next((title for start,stop,title in left if start<=cursor<stop),None)
        b=next((title for start,stop,title in right if start<=cursor<stop),None)
        if a and b:
            shared+=1
            if a==b:agree+=1
            else:conflict+=1
        cursor+=sample
    return shared,agree,conflict

def produce(candidates, programmes, now):
    groups=defaultdict(list)
    for row in candidates:
        if row.get("candidate_source_id") and row.get("review_status") not in ("BLOCKED_STREAM_IDENTITY_CONFLICT","PROVIDER_ID_MISMATCH"):
            groups[row["number"]].append(row)
    out=[]
    for number,rows in sorted(groups.items(),key=lambda x:int(x[0])):
        for a,b in itertools.combinations(rows,2):
            left=programmes.get((a["candidate_source_file"],a["candidate_source_id"]),[])
            right=programmes.get((b["candidate_source_file"],b["candidate_source_id"]),[])
            shared,agree,conflict=comparison(left,right,now)
            pct=round(agree/shared*100,1) if shared else 0
            independent=a["candidate_source_file"]!=b["candidate_source_file"]
            disposition=("INSUFFICIENT_SHARED_TIMES" if shared<8 else
                         "SAME_FEED_VARIANT_AGREEMENT" if pct>=90 and not independent else
                         "CROSS_SOURCE_SCHEDULE_AGREEMENT" if pct>=90 else
                         "REGIONAL_OR_SERVICE_CONFLICT" if pct<50 else
                         "PARTIAL_SCHEDULE_AGREEMENT")
            out.append({
                "number":number,"channel_name":a["channel_name"],
                "source_a":a["candidate_source_file"],"id_a":a["candidate_source_id"],
                "source_b":b["candidate_source_file"],"id_b":b["candidate_source_id"],
                "shared_15min_slots":shared,"matching_slots":agree,
                "conflicting_slots":conflict,"agreement_pct":pct,
                "finding":disposition,"review_status":"NOT_APPROVED",
            })
    return out

def main():
    with CANDIDATES.open(newline="",encoding="utf-8") as stream:
        candidates=list(csv.DictReader(stream))
    now=datetime.now(timezone.utc)
    sources={p.name:p for p in ROOT.glob("epg_*.xml*")}
    schedules=load_programmes(candidates,sources,now)
    report=produce(candidates,schedules,now)
    OUTPUT.parent.mkdir(exist_ok=True,parents=True)
    fields=["number","channel_name","source_a","id_a","source_b","id_b",
            "shared_15min_slots","matching_slots","conflicting_slots","agreement_pct",
            "finding","review_status"]
    with OUTPUT.open("w",newline="",encoding="utf-8") as out:
        writer=csv.DictWriter(out,fieldnames=fields)
        writer.writeheader();writer.writerows(report)
    summary={
        "generated_at_utc":now.isoformat(),
        "compared_pairs":len(report),
        "cross_source_agreement_pairs":sum(x["finding"]=="CROSS_SOURCE_SCHEDULE_AGREEMENT" for x in report),
        "same_feed_variant_agreement_pairs":sum(x["finding"]=="SAME_FEED_VARIANT_AGREEMENT" for x in report),
        "conflicting_pairs":sum(x["finding"]=="REGIONAL_OR_SERVICE_CONFLICT" for x in report),
        "partial_pairs":sum(x["finding"]=="PARTIAL_SCHEDULE_AGREEMENT" for x in report),
        "insufficient_pairs":sum(x["finding"]=="INSUFFICIENT_SHARED_TIMES" for x in report),
        "channels_with_strong_agreement":sorted({x["number"] for x in report if x["finding"]=="CROSS_SOURCE_SCHEDULE_AGREEMENT"},key=int),
        "not_evidence_of_on_air_playback":True,
        "not_independent_if_same_feed":True,
    }
    SUMMARY.write_text(json.dumps(summary,indent=2)+"\n",encoding="utf-8")
    print(json.dumps(summary,indent=2))
if __name__=="__main__":main()
