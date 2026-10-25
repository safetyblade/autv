#!/usr/bin/env python3
"""Availability diagnostics only; playback always uses Media3/IMA, never these REST sessions.
Public Google samples only. Do not print bodies, stream IDs, manifest URLs or credentials.
"""
import json
import urllib.request
import urllib.error
import urllib.parse
from pathlib import Path

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None

opener = urllib.request.build_opener(NoRedirect)
# https://developers.google.com/ad-manager/dynamic-ad-insertion/streams
# Probe only the documented no-key REST contract. The keyed sample is verified
# by IMA setApiKey in instrumentation; its REST auth shape is not assumed here.
samples = [
    ("hls-original", "hls", "c-rArva4ShKVIAkNfy6HUQ", {}),
    ("dash-owner-confirmed", "dash", "PSzZMzAkSXCmlJOWDmRj8Q", {}),
]
results = []
for label, fmt, asset, fields in samples:
    result = {"sample": label, "format": fmt, "session_fields": False}
    try:
        request = urllib.request.Request(
            f"https://dai.google.com/linear/v1/{fmt}/event/{asset}/stream",
            data=json.dumps(fields).encode(), headers={"Content-Type": "application/json"}, method="POST")
        with opener.open(request, timeout=20) as response:
            result["request_http"] = response.status
            data = json.loads(response.read(2*1024*1024))
        result["session_fields"] = all(isinstance(data.get(k), str) and data[k] for k in
                                       ("stream_id", "stream_manifest", "metadata_url", "media_verification_url"))
        manifest = data.get("stream_manifest", "")
        parsed = urllib.parse.urlsplit(manifest)
        if parsed.scheme == "https" and parsed.hostname == "dai.google.com" and not parsed.username:
            with opener.open(manifest, timeout=20) as response:
                result["manifest_http"] = response.status
                body = response.read(2*1024*1024)
                result["manifest_type_valid"] = b"#EXTM3U" in body if fmt == "hls" else b"<MPD" in body
    except urllib.error.HTTPError as error:
        result["http_error"] = error.code
    except Exception:
        result["transport_or_schema_failure"] = True
    results.append(result)
Path("dai-sample-probes.json").write_text(json.dumps(results, indent=2)+"\n")
print(json.dumps(results))
