#!/usr/bin/env python3
"""Read-only 2026 ABC/10 live-source discovery. Redacts signed URLs and user information."""
import json
import re
import urllib.error
import urllib.request
from urllib.parse import urlsplit

SOURCES = (
  ("10 VIC proven", "https://i.mjh.nz/.r/10-vic.m3u8"),
  ("10 Drama old", "https://i.mjh.nz/10bold-vic.m3u8"),
  ("10 Drama redirect", "https://i.mjh.nz/.r/10bold-vic.m3u8"),
  ("10 Comedy old", "https://i.mjh.nz/10peach-vic.m3u8"),
  ("10 Comedy redirect", "https://i.mjh.nz/.r/10peach-vic.m3u8"),
  ("Nickelodeon old", "https://i.mjh.nz/10shake-vic.m3u8"),
  ("Nickelodeon redirect", "https://i.mjh.nz/.r/10shake-vic.m3u8"),
)
REGIONAL = ("https://i.mjh.nz/au/Melbourne/raw.m3u8", "https://i.mjh.nz/au/Melbourne/kodi-tv.m3u8")
ABC_VIDEO = "https://api.iview.abc.net.au/v2/video/LS1807V001S00"

def fetch(address, max_bytes=512000):
    req=urllib.request.Request(address, headers={"Accept":"application/json, application/vnd.apple.mpegurl, */*",
        "User-Agent":"AUTV Public Live Source Diagnostic"})
    try:
        with urllib.request.urlopen(req,timeout=12) as response:
            body=response.read(max_bytes+1)
            return {"status":response.status, "host":urlsplit(response.url).hostname,
                "content_type":response.headers.get_content_type(),"truncated":len(body)>max_bytes}, body[:max_bytes]
    except urllib.error.HTTPError as exc:
        return {"status":exc.code,"host":urlsplit(exc.url).hostname,"content_type":"http-error"}, b""
    except (urllib.error.URLError,TimeoutError,OSError,ValueError) as exc:
        return {"status":type(exc).__name__,"host":urlsplit(address).hostname}, b""

def abc():
    status,body=fetch(ABC_VIDEO)
    print("ABC_VIDEO_STATUS",json.dumps(status,sort_keys=True))
    if status.get("status")!=200:return
    try: obj=json.loads(body)
    except ValueError:return
    def shape(o):
        return {"keys":sorted(o.keys())} if isinstance(o,dict) else {"type":type(o).__name__}
    print("ABC_VIDEO_SCHEMA",json.dumps({"top":shape(obj),"embedded":shape(obj.get("_embedded")),
        "playlist":shape((obj.get("_embedded") or {}).get("playlist"))}))
    layers=[obj,obj.get("_embedded") or {}]
    playlist=layers[1].get("playlist") if isinstance(layers[1],dict) else []
    if isinstance(playlist,list):layers+=playlist[:4]
    for layer in layers:
        if not isinstance(layer,dict):continue
        streams=layer.get("streams") or {}
        hls=streams.get("hls") or {} if isinstance(streams,dict) else {}
        if isinstance(hls,dict):
            for rendition,u in list(hls.items())[:6]:
                if not isinstance(u,str) or not u.startswith("https://"):continue
                parsed=urlsplit(u)
                print("ABC_HLS_VARIANT",json.dumps({"rendition":rendition,"host":parsed.hostname,
                    "has_query":bool(parsed.query),"looks_manifest":parsed.path.endswith(".m3u8")}))
        if any(k in layer for k in ("drm","key_systems","licenseUrl","adConfig","adTagUrl")):
            print("ABC_PROTECTION_FIELDS",json.dumps(sorted(set(layer)&{"drm","key_systems","licenseUrl","adConfig","adTagUrl"})))

def regional():
    for source in REGIONAL:
        status,raw=fetch(source)
        print("TEN_REGIONAL_PLAYLIST_STATUS",json.dumps({"source_type":"raw" if "raw.m3u8" in source else "kodi","status":status}))
        if status.get("status")!=200:continue
        text=raw.decode("utf-8","replace")
        lines=text.splitlines()
        matched=0
        for i,row in enumerate(lines):
            if "#EXTINF" not in row: continue
            title=row.rsplit(",",1)[-1].strip()
            if not re.search(r"(?:^|\b)(?:10(?: HD| VIC| Drama| Comedy| Bold| Peach| Shake)?|Nickelodeon)(?:\b|$)", title,re.I):continue
            next_url=next((z.strip() for z in lines[i+1:i+4] if z.strip() and not z.startswith("#")), "")
            parsed=urlsplit(next_url)
            print("TEN_REGIONAL_CHANNEL",json.dumps({"name":title[:100],"source_host":parsed.hostname,
                "is_https":parsed.scheme=="https","is_redirect_alias":parsed.path.startswith("/.r/"),
                "direct_region_path":parsed.path.startswith("/au/"),"has_query":bool(parsed.query)}))
            matched+=1
            if matched>20:break
        print("TEN_REGIONAL_COUNT",matched)

def aliases():
    for label,url in SOURCES:
        status,raw=fetch(url,max_bytes=400)
        print("TEN_ALIAS",json.dumps({"name":label,"response":status,"body_is_hls":raw.lstrip().startswith(b"#EXTM3U"),
            "body_is_html":raw.lstrip().startswith(b"<!DOCTYPE") or raw.lstrip().startswith(b"<html")},sort_keys=True))

if __name__=="__main__":
    abc();regional();aliases()
