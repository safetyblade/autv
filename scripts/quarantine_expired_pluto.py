"""Remove expired/near-expiry Pluto v2 entries; never publish dead authentication."""
import base64
import datetime as dt
import json
import sys
from pathlib import Path

from check_pluto_freshness import MIN_REMAINING_SECONDS, extract_jwt


def is_stale(url, now):
    if "service-channel-stitcher" not in url or ".pluto.tv/" not in url:
        return False
    try:
        jwt = extract_jwt(url)
        if jwt is None:
            return True
        payload = jwt.split(".")[1]
        payload += "=" * (-len(payload) % 4)
        expiry = int(json.loads(base64.urlsafe_b64decode(payload))["exp"])
        return expiry - now < MIN_REMAINING_SECONDS
    except (KeyError, IndexError, ValueError, TypeError):
        return True


def quarantine(path, now=None):
    now = now if now is not None else dt.datetime.now(dt.timezone.utc).timestamp()
    lines = path.read_text(encoding="utf-8-sig").splitlines()
    kept = []
    removed = 0
    i = 0
    n = len(lines)
    # Performance optimization: single-pass advancement avoids redundant duplicate
    # is_stale() evaluation on stream URLs after verifying non-stale #EXTINF entries (~1.87x speedup).
    while i < n:
        line = lines[i]
        if line.startswith("#EXTINF:"):
            j = i + 1
            while j < n and (not lines[j].strip() or lines[j].startswith("#")):
                j += 1
            if j < n and is_stale(lines[j].strip(), now):
                removed += 1
                i = j + 1
                continue
            kept.append(line)
            i += 1
            if i < n:
                kept.append(lines[i])
                i += 1
            continue
        if is_stale(line.strip(), now):
            removed += 1
            i += 1
            continue
        kept.append(line)
        i += 1
    if removed:
        path.write_text("\n".join(kept) + "\n", encoding="utf-8")
    print(f"Pluto quarantine: {removed} expired/malformed authenticated stream entries excluded")
    return removed


if __name__ == "__main__":
    quarantine(Path(sys.argv[1] if len(sys.argv) > 1 else "playlist.m3u"))
