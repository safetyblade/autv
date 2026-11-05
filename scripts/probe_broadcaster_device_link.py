#!/usr/bin/env python3
"""One-time, non-credentialed REAL network challenge to two broadcaster link APIs.

This is NOT an account-link test, an entitlement grant, or an approved integration.
No borrowed client identity, access keys, user passwords, response payloads, tokens,
device codes or signed URLs are logged or saved.
"""
import json
import os
import uuid
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

MAX_BYTES = 96 * 1024
TIMEOUT = 12
USER_AGENT = "AUTV/1.0 native-provider-link-smoke (unregistered diagnostic)"
PROBE_ID = str(uuid.uuid4())


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


opener = urllib.request.build_opener(NoRedirect)


def request(method, url, payload=None):
    encoded = json.dumps(payload, separators=(",", ":")).encode() if payload is not None else None
    headers = {"Accept": "application/json", "User-Agent": USER_AGENT}
    if encoded is not None:
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(url, headers=headers, data=encoded, method=method)
    try:
        with opener.open(req, timeout=TIMEOUT) as response:
            raw = response.read(MAX_BYTES + 1)
            return {"kind": "http", "status": response.status, "truncated": len(raw) > MAX_BYTES}, raw[:MAX_BYTES]
    except urllib.error.HTTPError as e:
        # Do not expose the body. Error responses may contain session details.
        return {"kind": "http", "status": e.code}, b""
    except Exception as e:
        # Error strings may include private signed URLs; only the exception type is allowed.
        return {"kind": "network-error", "error_class": type(e).__name__}, b""


def real_data(payload):
    # Never retain raw authentication fields from live services in build artifacts.
    try:
        document = json.loads(payload)
        if isinstance(document, dict) and isinstance(document.get("data"), dict):
            return document["data"]
        return document if isinstance(document, dict) else {}
    except (ValueError, TypeError):
        return {}


def issued_code(response):
    value = real_data(response)
    # Boolean only; returned device code is not logged or persisted.
    return any(isinstance(value.get(field), str) and value.get(field) for field in ("code", "userCode", "activationCode"))


def permitted_ten_host(value):
    p = urllib.parse.urlsplit(value)
    host = (p.hostname or "").lower()
    return (p.scheme == "https" and p.port in (None, 443) and p.username is None and p.password is None
            and (host == "10.com.au" or host.endswith(".10.com.au")
                 or host == "tenplay.com.au" or host.endswith(".tenplay.com.au"))
            and p.path.startswith("/api/") and p.fragment == "")


result = {"test_type": "real_unregistered_broadcaster_device_challenge", "client": "AUTV",
          "account_linked": False, "media_playback_verified": False, "region": "runner-not-AU-verified"}

abc_meta, _ = request("GET", "https://api.iview.abc.net.au/v2/home")
result["abc_live_catalogue_public_get"] = abc_meta
abc_link, abc_response = request("POST", "https://mylogin-api.abc.net.au/latest/ctv/link",
                                 {"productId": "autv", "deviceId": PROBE_ID, "deviceName": "AUTV"})
result["abc_link_challenge_without_broadcaster_registration"] = {**abc_link, "challenge_issued": issued_code(abc_response)}

ten_config, ten_bytes = request("GET", "https://10.com.au/api/v1/config")
result["ten_public_config_get_without_registered_signer"] = ten_config
if ten_config.get("status") == 200:
    config = real_data(ten_bytes)
    auth = config.get("authConfig") if isinstance(config.get("authConfig"), dict) else {}
    generate_url = auth.get("generateCode")
    if isinstance(generate_url, str) and permitted_ten_host(generate_url):
        ten_link, ten_response = request("POST", generate_url,
                                         {"deviceIdentifier": PROBE_ID, "deviceName": "AUTV"})
        result["ten_link_challenge_without_broadcaster_registration"] = {**ten_link, "challenge_issued": issued_code(ten_response)}
    else:
        result["ten_link_challenge_without_broadcaster_registration"] = {"kind": "not_attempted",
                     "reason": "No approved-format first-party generateCode URL in returned config"}
else:
    result["ten_link_challenge_without_broadcaster_registration"] = {"kind": "not_attempted",
                 "reason": "Cannot discover first-party generateCode endpoint without config response"}

out = Path("artifacts/broadcaster-device-link-probe.json")
out.parent.mkdir(exist_ok=True)
out.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
print(json.dumps(result, indent=2))
