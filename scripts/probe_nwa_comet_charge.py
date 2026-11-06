#!/usr/bin/env python3
"""Read-only live HLS assessment of Comet and CHARGE! for the NWA Powerrr trial.

Published Sinclair endpoints are sourced from iptv-org us.m3u; these are not
official AUTV distribution permissions. Never modify AUTV production feeds.
"""
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urljoin, urlsplit
from urllib.request import Request, urlopen
import json
import re

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/"audits"/"issue166_nwa_comet_charge_probe.json"
SOURCES={
    "Comet TV":"https://fast-channels.sinclairstoryline.com/COMET/index.m3u8",
    "CHARGE!":"https://fast-channels.sinclairstoryline.com/CHARGE/index.m3u8",
}

def get(uri,limit=65536):
    if urlsplit(uri).scheme!="https":
        raise ValueError("INSECURE_URL")
    req=Request(uri,headers={"User-Agent":"AUTV-ReadOnly-Source-Trial/1.0","Accept":"application/vnd.apple.mpegurl, */*"})
    with urlopen(req,timeout=10) as res:
        end=res.geturl()
        if urlsplit(end).scheme!="https": raise ValueError("INSECURE_REDIRECT")
        return end,res.read(limit)

def inspect(channel,uri):
    result={"channel":channel,"source_host":urlsplit(uri).hostname,"master":"NOT_CHECKED",
            "media":"NOT_CHECKED","segments":"NOT_CHECKED","status":"NOT_TESTED",
            "source":"Public iptv-org US channel listing","geo_or_rights_checked":False,
            "australian_device_tested":False}
    try:
        manifest=uri
        seen=set()
        for depth in range(4):
            if manifest in seen: raise ValueError("PLAYLIST_LOOP")
            seen.add(manifest)
            base,body=get(manifest)
            lines=[line.strip() for line in body.decode("utf-8-sig",errors="replace").splitlines() if line.strip()]
            if not lines or lines[0]!="#EXTM3U": raise ValueError("NOT_HLS")
            if depth==0: result["master"]="HLS_MANIFEST_OK"
            # Don't probe encrypted or DRM protected streams
            if any(l.startswith("#EXT-X-KEY") and ("SAMPLE-AES" in l or "KEYFORMAT=" in l) for l in lines):
                result["status"]="ENCRYPTED_OR_DRM";return result
            variant=next((i for i,l in enumerate(lines) if l.startswith("#EXT-X-STREAM-INF")),None)
            if variant is not None:
                nexturi=next((l for l in lines[variant+1:] if not l.startswith("#")),None)
                if not nexturi: raise ValueError("NO_VARIANT_URI")
                manifest=urljoin(base,nexturi)
                continue
            if not any(l.startswith("#EXTINF:") for l in lines):
                result["media"]="NO_SEGMENTS";result["status"]="NOT_PLAYABLE";return result
            result["media"]="MEDIA_MANIFEST_OK"
            segment=next((l for l in lines if not l.startswith("#")),None)
            if not segment: raise ValueError("NO_MEDIA_URI")
            _,data=get(urljoin(base,segment),limit=4096)
            result["segments"]="NONEMPTY" if data else "EMPTY"
            result["status"]="SEGMENT_REACHABLE" if data else "NOT_PLAYABLE"
            return result
        result["status"]="NESTED_LIMIT"
    except Exception as error:
        result["status"]="FETCH_"+type(error).__name__.upper()
        result["error_type"]=type(error).__name__  # Never log exception text; it may contain a tokenized URL
    return result

def main():
    with ThreadPoolExecutor(max_workers=2) as pool:
        rows=list(pool.map(lambda a:inspect(*a),SOURCES.items()))
    OUT.parent.mkdir(parents=True,exist_ok=True)
    output={"date_utc":datetime.now(timezone.utc).isoformat(),"purpose":"NWA Powerrr scheduled on Comet; temporary Charge simulcast not assumed current",
            "production_modified":False,"note":"Runner HLS reachability does not prove Australian access, broadcaster carriage rights, adverts, EPG or TV playback",
            "channels":rows}
    OUT.write_text(json.dumps(output,indent=2)+"\n",encoding="utf-8")
    for row in rows:
        print(row["channel"],row["master"],row["media"],row["segments"],row["status"])
    print("No production files modified or new channels activated.")

if __name__=="__main__":main()
