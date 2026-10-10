"""Fail closed if published Pluto v2 streams have expired or near-expiry JWTs."""
import base64
import datetime as dt
import json
import sys
from pathlib import Path
from urllib.parse import parse_qs, urlsplit

MIN_REMAINING_SECONDS = 6 * 3600

def check(path: Path, now=None):
    now = now or dt.datetime.now(dt.timezone.utc).timestamp()
    checked = 0
    expiry_values = []
    errors = []
    lines = path.read_text(encoding="utf-8-sig").splitlines()
    for line in lines:
        if not line.startswith("http") or "service-channel-stitcher" not in line or ".pluto.tv/" not in line:
            continue
        checked += 1
        try:
            query = parse_qs(urlsplit(line).query)
            jwt = query["jwt"][0]
            payload = jwt.split(".")[1]
            payload += "=" * (-len(payload) % 4)
            claims = json.loads(base64.urlsafe_b64decode(payload))
            exp = int(claims["exp"])
            expiry_values.append(exp)
            if exp - now < MIN_REMAINING_SECONDS:
                errors.append(f"Pluto stream {checked}: JWT expires too soon or expired")
        except (KeyError, IndexError, ValueError, TypeError) as exc:
            errors.append(f"Pluto stream {checked}: missing or malformed JWT ({type(exc).__name__})")
    if checked == 0:
        errors.append("No Pluto v2 streams found; refusing unexpected empty source group")
    print(f"Pluto streams checked: {checked}; earliest expiry UTC: {dt.datetime.fromtimestamp(min(expiry_values), dt.timezone.utc).isoformat() if expiry_values else 'unavailable'}")
    if errors:
        for message in errors[:10]:
            print(message, file=sys.stderr)
        raise SystemExit(f"Refusing publication: {len(errors)} Pluto JWT freshness failures")
    print("Pluto JWT freshness check passed (minimum 6h remaining).")

if __name__ == "__main__":
    check(Path(sys.argv[1] if len(sys.argv) > 1 else "playlist.m3u"))
