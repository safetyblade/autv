#!/usr/bin/env python3
"""EPG candidate safety triage from published playlist evidence.

Produces reviewer worklist; never writes EPG crosswalk or XMLTV and never
contacts playback hosts. Title agreement is NOT proof of channel identity.
"""
import base64
import csv
import re
from collections import defaultdict
from pathlib import Path
from urllib.parse import urlsplit, parse_qs

ROOT=Path(__file__).resolve().parents[1]
CANDIDATES=ROOT/"audits/epg_source_candidate_review.csv"
PLAYLIST=ROOT/"playlist.m3u"
OUTPUT=ROOT/"audits/epg_candidate_identity_triage.csv"
NUM_RE=re.compile(r'(?:^|\s)tvg-chno="(\d+)"')
ATTR_RE=re.compile(r'(?:^|\s)(tvg-id|tvg-name)="([^"]*)"')

def identity_tokens(text):
    return set(re.findall(r"[a-z0-9]+",(text or "").lower()))

def stream_clues(url):
    parsed=urlsplit(url)
    parts=parsed.path.split("/")
    query=parse_qs(parsed.query)
    result={"host":parsed.hostname or "","embedded_identity":"","xumo_id":"","region_hint":""}
    # Wurl-style stream path may contain a base64 encoded provider/channel
    # identifier; decode without attempting any network request.
    for part in parts:
        if len(part)<14 or not re.fullmatch(r"[A-Za-z0-9_-]+",part):
            continue
        try:
            raw=base64.urlsafe_b64decode(part+"="*(-len(part)%4)).decode("utf-8")
        except (ValueError,UnicodeDecodeError):
            continue
        if 5<len(raw)<180 and re.search(r"(?:hls|samsung|pluto|medical|detective)",raw,re.I):
            result["embedded_identity"]=raw
            if "samsung-gb" in raw.lower():result["region_hint"]="GB"
            break
    ids={query.get(key,[""])[0] for key in ("ads.xumo_channelId","ads.xumo_streamId","ads.channelId")}
    ids.discard("")
    if len(ids)==1:result["xumo_id"]=next(iter(ids))
    for text in [parsed.path,parsed.query]:
        if "lgau" in text.lower() or "lg-au" in text.lower():result["region_hint"]="AU"
    return result

def playlist_by_number(path):
    out={}
    lines=path.read_text(encoding="utf-8-sig").splitlines()
    for i,line in enumerate(lines):
        if not line.startswith("#EXTINF:"):continue
        m=NUM_RE.search(line)
        if not m:continue
        attributes=dict(ATTR_RE.findall(line))
        out[m.group(1)]={
            "channel_name":attributes.get("tvg-name",""),
            "tvg_id":attributes.get("tvg-id",""),
            "stream":lines[i+1] if i+1<len(lines) else "",
        }
    return out

def triage(rows, playlist):
    output=[]
    for row in rows:
        number=row["number"]
        listing=playlist.get(number,{})
        clues=stream_clues(listing.get("stream",""))
        source=row.get("source_file","")
        candidate_id=row.get("candidate_source_id","")
        claimed=listing.get("channel_name",row["channel_name"])
        notes=[]
        status="REVIEW_REQUIRED"
        if not candidate_id:
            status="SOURCE_DISCOVERY_REQUIRED"
            notes.append("No matching source channel ID in current feeds")
        if clues["embedded_identity"]:
            # Strong channel identity embedded by stream provider contradicts
            # an unrelated label, e.g. MedicalDetectives vs BloodlineDetectives.
            embedded=identity_tokens(clues["embedded_identity"])
            claim=identity_tokens(claimed)
            tokens=embedded-{"samsung","gb","us","au","hls","tv"}
            if any("detective" in token for token in tokens) and \
               not (tokens & claim):
                status="BLOCKED_STREAM_IDENTITY_CONFLICT"
                notes.append("Encoded stream identity appears inconsistent with printed channel name")
        if source=="epg_xumo.xml.gz" and clues["xumo_id"] and \
           candidate_id.isdigit() and candidate_id!=clues["xumo_id"]:
            if status!="BLOCKED_STREAM_IDENTITY_CONFLICT":
                status="PROVIDER_ID_MISMATCH"
            notes.append("Embedded Xumo stream ID differs from the candidate XMLTV ID")
        if clues["region_hint"]=="AU" and source in ("epg_pluto_gb.xml.gz","epg_pluto_ca.xml.gz","epg_pluto_us.xml.gz"):
            notes.append("Stream has AU marker but EPG is from another territory")
            if status=="REVIEW_REQUIRED":status="REGIONAL_VERIFICATION_REQUIRED"
        if not notes:
            notes.append("Matching names alone do not establish identical channel schedules")
        output.append({
            "number":number,"channel_name":claimed,"target_tvg_id":listing.get("tvg_id",""),
            "candidate_source_id":candidate_id,"candidate_source_file":source,
            "source_display_name":row.get("source_display_name",""),
            "source_programmes_current":row.get("current_programmes",""),
            "playlist_host":clues["host"],"stream_identity_hint":clues["embedded_identity"],
            "stream_xumo_id":clues["xumo_id"],"stream_region_hint":clues["region_hint"],
            "review_status":status,"review_notes":"; ".join(notes),
            "approved_crosswalk":"NO",
        })
    return output

def main():
    with CANDIDATES.open(newline="",encoding="utf-8") as f:
        candidates=list(csv.DictReader(f))
    result=triage(candidates,playlist_by_number(PLAYLIST))
    OUTPUT.parent.mkdir(exist_ok=True,parents=True)
    with OUTPUT.open("w",newline="",encoding="utf-8") as f:
        writer=csv.DictWriter(f,fieldnames=list(result[0]))
        writer.writeheader();writer.writerows(result)
    from collections import Counter
    print(f"Wrote {len(result)} candidate review rows")
    print("Status counts:",dict(Counter(r["review_status"] for r in result)))
    print("Output:",OUTPUT)
if __name__=="__main__":main()
