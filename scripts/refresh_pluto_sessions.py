"""Refresh Pluto v2 playlist sessions using Pluto's current boot endpoint.

Never log session tokens or URLs. Preserve existing channel identity and source
metadata; replace source JWTs only when Pluto issues a fresh valid token.
"""
import argparse
import base64
import datetime as dt
import json
from urllib.error import HTTPError, URLError
from pathlib import Path
from urllib.parse import parse_qsl, urlencode, urlsplit, urlunsplit
from urllib.request import Request, urlopen
from uuid import uuid4

BOOT_URL = "https://boot.pluto.tv/v4/start"
MIN_SECONDS = 6 * 3600


def token_expiry(token):
    try:
        payload = token.split(".")[1]
        payload += "=" * (-len(payload) % 4)
        return int(json.loads(base64.urlsafe_b64decode(payload))["exp"])
    except (IndexError, TypeError, ValueError, KeyError) as exc:
        raise ValueError("Pluto returned a malformed session token") from exc


def get_session():
    client = str(uuid4())
    params = {
        "appName": "web", "appVersion": "8.0.0-111b2b9dc00bd0bea9030b30662159ed9e7c8bc6", "deviceVersion": "122.0.0",
        "deviceModel": "web", "deviceMake": "chrome", "deviceType": "web",
        "clientID": client, "clientModelNumber": "1.0.0",
        "serverSideAds": "false", "drmCapabilities": "widevine:L3",
        "blockingMode": "",
    }
    request = Request(
        BOOT_URL + "?" + urlencode(params),
        headers={"Accept": "application/json", "Origin": "https://pluto.tv",
                 "Referer": "https://pluto.tv/", "User-Agent": "Mozilla/5.0"},
    )
    try:
        with urlopen(request, timeout=15) as response:
            # Security: Ensure redirect target uses HTTP(S) to prevent scheme escalation or local file disclosure
            final_scheme = urlsplit(response.geturl()).scheme
            if final_scheme not in ("http", "https"):
                raise RuntimeError(f"Unsupported URL scheme: {final_scheme}")
            data = json.load(response)
    except HTTPError as exc:
        raise RuntimeError(f"Pluto boot rejected request: HTTP {exc.code}") from None
    except URLError as exc:
        raise RuntimeError(f"Pluto boot transport failure: {type(exc.reason).__name__}") from None
    token = data.get("sessionToken")
    if not token or token_expiry(token) - dt.datetime.now(dt.timezone.utc).timestamp() < MIN_SECONDS:
        raise RuntimeError("Pluto boot did not provide a fresh session (6h minimum)")
    return token, client


def refresh(playlist, token, client):
    updated = []
    changed = 0
    for line in playlist.splitlines():
        if line.startswith("http") and ".pluto.tv/" in line and "service-channel-stitcher" in line:
            parts = urlsplit(line)
            query = dict(parse_qsl(parts.query, keep_blank_values=True))
            query["jwt"] = token
            query["clientID"] = client
            query["deviceId"] = client
            query["masterJWTPassthrough"] = "true"
            # Keep provider-supplied channel IDs and metadata, replace stale auth.
            line = urlunsplit(parts._replace(query=urlencode(query)))
            changed += 1
        updated.append(line)
    return "\n".join(updated) + "\n", changed


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("path", type=Path)
    args = parser.parse_args()
    original = args.path.read_text(encoding="utf-8-sig")
    try:
        token, client = get_session()
    except RuntimeError as exc:
        raise SystemExit(str(exc)) from None
    result, count = refresh(original, token, client)
    if not count:
        raise SystemExit("Source contains no Pluto stitcher streams")
    args.path.write_text(result, encoding="utf-8")
    print(f"Pluto: refreshed session for {count} playlist entries (token redacted)")


if __name__ == "__main__":
    main()
