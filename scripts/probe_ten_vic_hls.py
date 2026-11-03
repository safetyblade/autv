#!/usr/bin/env python3
"""Read-only Network 10 VIC HLS delivery probe.

Run from an Australian connection. Never logs signed URLs, keys or media bytes.
This tool is diagnostic only; sources/rights/playback are NOT validated by inclusion.
"""
import argparse
import ipaddress
import json
import re
import sys
from urllib.parse import urljoin, urlsplit
from urllib.request import Request, build_opener, HTTPRedirectHandler
from urllib.error import HTTPError, URLError

CANDIDATE = "https://i.mjh.nz/.r/10-vic.m3u8"
MAX_MANIFEST = 131072
SUFFIXES = (
    "mjh.nz", "10play.com.au", "akamaized.net", "akamaihd.net",
    "cbsivideo.com", "fastly.net", "cloudfront.net", "doubleclick.net",
    "google.com", "googleapis.com", "googlevideo.com",
)


def safe_host(url):
    parsed = urlsplit(url)
    if parsed.scheme != "https" or parsed.username or parsed.password or parsed.port not in (None, 443):
        raise ValueError("Only credential-free HTTPS sources on port 443 are allowed")
    host = (parsed.hostname or "").lower()
    try:
        ipaddress.ip_address(host)
        raise ValueError("Literal IP destinations are not supported")
    except ValueError as error:
        if str(error) == "Literal IP destinations are not supported":
            raise
    if not any(host == suffix or host.endswith("." + suffix) for suffix in SUFFIXES):
        raise ValueError("Source origin not on approved diagnostic allowlist")
    return host


class RestrictedRedirects(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        safe_host(newurl)
        count = int(req.get_header("X-Autv-Redirect-Count") or 0) + 1
        if count > 5:
            raise ValueError("More than five redirects")
        next_req = super().redirect_request(req, fp, code, msg, headers, newurl)
        if next_req is not None:
            next_req.add_header("X-Autv-Redirect-Count", str(count))
        return next_req


def fetch(url, method="GET", limit=MAX_MANIFEST, timeout=8):
    safe_host(url)
    headers = {"User-Agent": "AUTV Ten playback diagnostic", "Accept": "*/*"}
    if method == "GET" and limit < MAX_MANIFEST:
        headers["Range"] = "bytes=0-" + str(limit - 1)
    opener = build_opener(RestrictedRedirects())
    req = Request(url, headers=headers, method=method)
    try:
        with opener.open(req, timeout=timeout) as response:
            safe_host(response.url)
            body = response.read(limit + 1) if method == "GET" else b""
            if len(body) > limit:
                raise ValueError("Response exceeded diagnostic limit")
            return response.status, response.url, body, response.headers.get_content_type()
    except HTTPError as error:
        return error.code, url, b"", "http-error"


def manifest_lines(payload):
    text = payload.decode("utf-8-sig", "replace")
    if not text.lstrip().startswith("#EXTM3U"):
        raise ValueError("Response is not an HLS manifest")
    return [line.strip() for line in text.splitlines() if line.strip()]


def attributes(line):
    return dict(re.findall(r'([A-Z0-9-]+)="([^"]*)"', line))


def summarise(lines):
    variants = []
    segments = []
    key = None
    for idx, line in enumerate(lines):
        if line.startswith("#EXT-X-STREAM-INF"):
            uri = next((x for x in lines[idx+1:] if not x.startswith("#")), None)
            if uri:
                variants.append({"uri": uri, "codecs": attributes(line).get("CODECS", "unknown")})
        elif line.startswith("#EXT-X-KEY:"):
            key = {"method": re.search(r"METHOD=([^,]+)", line).group(1) if "METHOD=" in line else "unknown",
                   "uri": attributes(line).get("URI")}
        elif not line.startswith("#"):
            segments.append(line)
    return {"variants": variants, "first_segment": segments[0] if segments else None,
            "key": key, "is_live": "#EXT-X-ENDLIST" not in lines,
            "has_discontinuity": any(x.startswith("#EXT-X-DISCONTINUITY") for x in lines)}


def run(url, timeout):
    outcome = {"channel": "10 VIC", "candidate_host": safe_host(url),
               "production_approved": False, "real_device_playback": "NOT_TESTED"}
    status, resolved, body, content_type = fetch(url, timeout=timeout)
    outcome.update({"manifest_status": status, "manifest_host": safe_host(resolved),
                    "manifest_content_type": content_type})
    if status != 200:
        return outcome
    master = summarise(manifest_lines(body))
    outcome["variant_count"] = len(master["variants"])
    outcome["codecs"] = sorted({v["codecs"] for v in master["variants"]})
    if master["variants"]:
        child = urljoin(resolved, master["variants"][0]["uri"])
        code, resolved, body, _ = fetch(child, timeout=timeout)
        outcome.update({"variant_manifest_status": code, "variant_host": safe_host(resolved)})
        if code != 200:
            return outcome
        master = summarise(manifest_lines(body))
    outcome["live"] = master["is_live"]
    outcome["discontinuity_seen"] = master["has_discontinuity"]
    key = master["key"]
    outcome["key_method"] = key["method"] if key else "NONE"
    if key and key["uri"]:
        key_url = urljoin(resolved, key["uri"])
        outcome["key_host"] = safe_host(key_url)
        # HEAD only: do not retrieve or store encryption keys.
        outcome["key_head_status"] = fetch(key_url, method="HEAD", timeout=timeout)[0]
    if master["first_segment"]:
        segment = urljoin(resolved, master["first_segment"])
        outcome["segment_host"] = safe_host(segment)
        # Tiny sample, no media saved. 206/200 is transport evidence, not playback proof.
        code, _, _, _ = fetch(segment, limit=1024, timeout=timeout)
        outcome["segment_http_status"] = code
    else:
        outcome["first_segment"] = "NOT_PRESENT"
    return outcome


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--url", default=CANDIDATE, help="Network 10 VIC source, never printed")
    parser.add_argument("--timeout", type=float, default=8.0)
    args = parser.parse_args()
    try:
        result = run(args.url, args.timeout)
        print(json.dumps(result, indent=2, sort_keys=True))
        return 0 if result.get("manifest_status") == 200 else 2
    except (ValueError, HTTPError, URLError, TimeoutError, OSError) as error:
        print(json.dumps({"channel": "10 VIC", "stage": "PROBE_FAILED",
                          "failure": type(error).__name__, "detail": "See source/region/network restrictions; no URL logged"}))
        return 2


if __name__ == "__main__":
    sys.exit(main())
