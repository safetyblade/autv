#!/usr/bin/env python3
"""Read-only NBA Channel #228 HLS diagnostic, without exposing tokenized URLs.

Checks playlist attribution, source freshness, variant manifest and media segment.
Never edits any stream or bypasses regional/access policy.
"""
from __future__ import annotations
import json
import re
import ssl
import sys
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime,timezone
from pathlib import Path

NUMBER="228"
TARGET_ID="99951383"
SOURCE_URL="https://raw.githubusercontent.com/BuddyChewChew/xumo-playlist-generator/main/playlists/xumo_playlist.m3u"
OUT=Path("audits/nba_channel_228_diagnostic.json")
EXT=re.compile(r'(?:^|\s)tvg-chno="(\d+)"')
TVG=re.compile(r'(?:^|\s)tvg-id="([^"]+)"')

def entries(text, match_number=None, match_id=None):
    lines=text.splitlines()
    for i,line in enumerate(lines):
        if not line.startswith("#EXTINF:"):continue
        num=EXT.search(line)
        id_match=TVG.search(line)
        if (match_number is not None and (not num or num.group(1)!=match_number)) or (match_id is not None and (not id_match or id_match.group(1)!=match_id)):
            continue
        nextline=next((x.strip() for x in lines[i+1:] if x.strip() and not x.startswith("#")), "")
        return {"name":line.rsplit(",",1)[-1].strip(),"number":num.group(1) if num else "",
                "id":id_match.group(1) if id_match else "","url":nextline}
    return None

def safe_host(url):
    return urllib.parse.urlsplit(url).hostname or ""

def fetch(url,max_bytes=180000,timeout=16):
    if urllib.parse.urlsplit(url).scheme!="https":
        raise ValueError("Only HTTPS sources permitted")
    request=urllib.request.Request(url,headers={"User-Agent":"Mozilla/5.0 (AUTV source diagnostic)","Accept":"application/vnd.apple.mpegurl,application/x-mpegURL,*/*"})
    try:
        with urllib.request.urlopen(request,timeout=timeout) as response:
            body=response.read(max_bytes+1)
            return {"http_status":response.status,"content_type":response.headers.get("Content-Type",""),
                    "final_host":safe_host(response.geturl()),"bytes_sampled":len(body),
                    "truncated":len(body)>max_bytes,"text":body[:max_bytes].decode("utf-8","replace")}
    except urllib.error.HTTPError as error:
        return {"http_status":error.code,"content_type":error.headers.get("Content-Type",""),
                "final_host":safe_host(url),"bytes_sampled":0,"truncated":False,"text":""}
    except (urllib.error.URLError,TimeoutError,ssl.SSLError,ConnectionError,OSError) as error:
        return {"http_status":None,"network_error":type(error).__name__,
                "final_host":safe_host(url),"bytes_sampled":0,"truncated":False,"text":""}

def first_media_url(manifest_url,manifest):
    lines=[line.strip() for line in manifest.splitlines()]
    for idx,line in enumerate(lines):
        if line.startswith("#EXT-X-STREAM-INF"):
            variant=next((x for x in lines[idx+1:] if x and not x.startswith("#")),"")
            return urllib.parse.urljoin(manifest_url,variant) if variant else None,"variant"
    for line in lines:
        if line and not line.startswith("#"):
            return urllib.parse.urljoin(manifest_url,line),"segment"
    return None,"none"

def analyse_manifest(response):
    text=response.get("text","")
    if response.get("http_status")!=200:
        code=response.get("http_status")
        return ("HTTP_DENIED" if code in (401,403,451) else "MANIFEST_UNAVAILABLE")
    if not text.lstrip().startswith("#EXTM3U"):
        return "NON_HLS_RESPONSE"
    if "#EXT-X-STREAM-INF" in text:return "HLS_MASTER"
    if "#EXTINF:" in text:return "HLS_MEDIA"
    return "HLS_EMPTY_OR_UNSUPPORTED"

def main():
    published=entries(Path("playlist.m3u").read_text(encoding="utf-8-sig"),match_number=NUMBER)
    if not published or published["id"]!=TARGET_ID:
        raise SystemExit("NBA #228 identity changed; do not probe an unrelated channel")
    upstream_response=fetch(SOURCE_URL,max_bytes=800000,timeout=20)
    upstream=entries(upstream_response.get("text",""),match_id=TARGET_ID) if upstream_response.get("http_status")==200 else None
    result={
        "checked_utc":datetime.now(timezone.utc).isoformat(),
        "channel_number":NUMBER,"channel_name":published["name"],"tvg_id":published["id"],
        "published_host":safe_host(published["url"]),
        "upstream_catalogue_http":upstream_response.get("http_status"),
        "upstream_source_present":upstream is not None,
        "published_url_matches_upstream":upstream is not None and published["url"]==upstream["url"],
    }
    if not published["url"].startswith("https://"):
        raise SystemExit("NBA stream is not HTTPS; refusing unsafe probe")
    manifest=fetch(published["url"])
    result["manifest"]={k:v for k,v in manifest.items() if k!="text"}
    result["manifest_class"]=analyse_manifest(manifest)
    media=manifest
    current=published["url"]
    if result["manifest_class"]=="HLS_MASTER":
        next_url,kind=first_media_url(current,manifest["text"])
        if next_url and safe_host(next_url):
            current=next_url
            media=fetch(current)
            result["variant_manifest"]={k:v for k,v in media.items() if k!="text"}
            result["variant_class"]=analyse_manifest(media)
    if analyse_manifest(media)=="HLS_MEDIA":
        seg,kind=first_media_url(current,media["text"])
        if seg and safe_host(seg):
            sampled=fetch(seg,max_bytes=2048)
            result["media_probe"]={k:v for k,v in sampled.items() if k!="text"}
            result["media_probe"]["sampled_media_bytes"]=sampled["bytes_sampled"]>0
    result["assessment"]=(
        "SOURCE_HTTP_OR_NETWORK_FAILURE" if result["manifest_class"] not in ("HLS_MASTER","HLS_MEDIA") else
        "SEGMENT_UNAVAILABLE" if result.get("media_probe",{}).get("http_status")!=200 else
        "REMOTE_PROBE_OK_DEVICE_PLAYBACK_UNVERIFIED"
    )
    OUT.parent.mkdir(parents=True,exist_ok=True)
    OUT.write_text(json.dumps(result,indent=2)+"\n",encoding="utf-8")
    # Never log manifests or signed playlist URLs.
    print(json.dumps(result,indent=2))
if __name__=="__main__":main()
