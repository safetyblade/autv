#!/usr/bin/env python3
"""Read-only Wrestling Central source trial, using its known official Roku identity.

A channel's Roku webpage is NOT a raw HLS stream. Do not infer a playable feed from
the watch URL, fabricate an endpoint, or inject a regional source into production.
"""
from datetime import datetime, timezone
from pathlib import Path
from urllib.request import Request, urlopen
from urllib.parse import urlsplit
import importlib.util
import json
import re

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "audits" / "issue166_wrestling_central_probe.json"
ROKU_ID = "439096119777e0f33894343b551bece4"
OFFICIAL_WATCH = f"https://therokuchannel.roku.com/watch/{ROKU_ID}"
OTHER_SOURCES = ["roku", "tubi", "xumo", "plex_au", "pluto_us", "pluto_ca", "pluto_uk"]

spec=importlib.util.spec_from_file_location("autv_trial", ROOT / "scripts" / "probe_issue166_sources.py")
trial=importlib.util.module_from_spec(spec)
spec.loader.exec_module(trial)

def scan():
    out={
        "timestamp_utc": datetime.now(timezone.utc).isoformat(),
        "channel": "Wrestling Central",
        "official_roku_id": ROKU_ID,
        "official_distribution_regions": ["US","CA"],
        "evidence_scope": "GitHub runner; not Australian TV playback or licence clearance",
        "published_media_changed": False,
        "source_catalogues": {},
        "matches": [],
        "official_page": "NOT_CHECKED",
        "decision": "NOT_VERIFIED",
    }
    for name in OTHER_SOURCES:
        file=ROOT / "discovery_sources" / (name+".m3u")
        if not file.exists():
            out["source_catalogues"][name]="UNAVAILABLE"
            continue
        matches=[]
        for e in trial.parse_entries(file.read_text(encoding="utf-8-sig",errors="replace")):
            by_name=any(k in trial.key(e["name"]) for k in ["wrestlingcentral", "nwawrestlingcentral"])
            by_id=ROKU_ID in e["id"] or ROKU_ID in e["line"] or ROKU_ID in e["url"]
            if not (by_name or by_id):
                continue
            matches.append(e)
        out["source_catalogues"][name]=f"READ_{len(matches)}_MATCHES"
        for e in matches[:5]:
            result=trial.media_probe(e["url"])
            out["matches"].append({"source": name,"catalogue_name":e["name"],
                                   "tvg_id":e["id"],"stream_host":urlsplit(e["url"]).hostname,
                                   "network_result":result, "is_same_roku_id":ROKU_ID in (e["id"]+e["line"]+e["url"])})
    try:
        req=Request(OFFICIAL_WATCH,headers={"User-Agent":"AUTV-ReadOnly-Discovery/1.0"})
        with urlopen(req,timeout=8) as response:
            page=response.read(100000).decode("utf-8",errors="replace")
            out["official_page"]="HTTP_200"
            out["page_names_wrestling_central"]=bool(re.search(r"Wrestling Central",page,re.I))
    except Exception as error:
        out["official_page"]="FETCH_"+type(error).__name__.upper()
    if any(x["network_result"]=="SEGMENT_REACHABLE" for x in out["matches"]):
        out["decision"]="TECHNICALLY_REACHABLE_AU_NOT_VERIFIED"
    elif out["matches"]:
        out["decision"]="INDEXED_BUT_NOT_REACHABLE"
    else:
        out["decision"]="NOT_LISTED_NO_HLS_CANDIDATE"
    return out

if __name__=="__main__":
    result=scan()
    OUT.parent.mkdir(exist_ok=True,parents=True)
    OUT.write_text(json.dumps(result,indent=2)+"\n",encoding="utf-8")
    print("Wrestling Central identity",ROKU_ID)
    print("Official Roku watch-page status:",result["official_page"])
    for key,val in result["source_catalogues"].items():
        print("Catalogue",key,val)
    for e in result["matches"]:
        print("Candidate",e["source"],e["network_result"])
    print("Decision",result["decision"],"— no production change")
