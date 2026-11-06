#!/usr/bin/env python3
"""Read-only ABC public live-catalogue schema diagnostic; never prints programme URLs or tokens."""
import json
import re
import urllib.request
from urllib.parse import urlparse

ROOT = "https://api.iview.abc.net.au/v2"
def load(url):
    address = urlparse(url)
    assert address.scheme == "https" and address.hostname == "api.iview.abc.net.au"
    assert (address.path == "/v2/home" or re.fullmatch(r"/v2/collection/[A-Za-z0-9_-]{1,128}", address.path))
    req = urllib.request.Request(url, headers={"Accept": "application/json", "User-Agent": "AUTV Android TV"})
    with urllib.request.urlopen(req, timeout=15) as result:
        raw = result.read(2 * 1024 * 1024 + 1)
        if len(raw) > 2 * 1024 * 1024: raise ValueError("Response too large")
        return json.loads(raw)

def shape(value):
    if isinstance(value, dict): return {"keys": sorted(value.keys()), "arrays": {k:len(v) for k,v in value.items() if isinstance(v,list)}}
    if isinstance(value,list): return {"length":len(value),"first_keys":sorted(value[0].keys()) if value and isinstance(value[0],dict) else []}
    return {"type":type(value).__name__}

def safe(value):
    return str(value)[:100] if isinstance(value,str) and re.fullmatch(r"[A-Za-z0-9_ -]{1,100}",value) else None

def main():
    home=load(ROOT+"/home")
    print("ABC_HOME", json.dumps({"shape":shape(home), "_embedded":shape(home.get("_embedded")),"data":shape(home.get("data"))}))
    for root_name,root in [("top",home),("embedded",home.get("_embedded")),("data",home.get("data"))]:
        if not isinstance(root,dict):continue
        for key,rows in root.items():
            if not isinstance(rows,list):continue
            samples=[]
            for item in rows[:30]:
                if not isinstance(item,dict):continue
                samples.append({"keys":sorted(item.keys()),"id":safe(item.get("id")),"title":safe(item.get("title")),"type":safe(item.get("type")),"name":safe(item.get("name"))})
            print("COLLECTION",json.dumps({"root":root_name,"key":key,"count":len(rows),"samples":samples}))
            # Only fetch obvious metadata collection references.
            for item in rows:
                if not isinstance(item,dict):continue
                title=str(item.get("title") or item.get("name") or "").lower()
                id=safe(item.get("id"))
                if id and ("live" in title or "channels" in title):
                    try:
                        collection=load(ROOT+"/collection/"+id)
                        paths=[str((row.get("_links") or {}).get("self",{}).get("href","")) for row in collection.get("items",[]) if isinstance(row,dict)]
                        print("LIVE_HREF_FORMAT",json.dumps({"count":len(paths),"relative_video":sum(bool(re.fullmatch(r"/video/[A-Za-z0-9_-]{1,128}",p)) for p in paths),"absolute_video":sum(bool(re.fullmatch(r"https://api.iview.abc.net.au/v2/video/[A-Za-z0-9_-]{1,128}",p)) for p in paths),"other":len(paths)-sum(bool(re.fullmatch(r"(?:/video/|https://api.iview.abc.net.au/v2/video/)[A-Za-z0-9_-]{1,128}",p)) for p in paths)}))
                        print("LIVE_COLLECTION_ITEMS",json.dumps([{"type":safe(row.get("type")),"houseNumber":safe(row.get("houseNumber")),"title":safe(row.get("showTitle") or row.get("title") or row.get("displayTitle")), "self_href_kind": "relative" if str((row.get("_links") or {}).get("self",{}).get("href","")).startswith("/") else "absolute"} for row in collection.get("items",[]) if isinstance(row,dict)]))
                        print("LIVE_COLLECTION",json.dumps({"id":id,"shape":shape(collection),"embedded":shape(collection.get("_embedded")),
                            "items_first":shape(next((v for v in collection.values() if isinstance(v,list)),[]))}))
                    except Exception as error:
                        print("LIVE_COLLECTION_ERROR",json.dumps({"id":id,"error":type(error).__name__}))
    print("DONE: no auth or playback attempted")
if __name__=="__main__":main()
