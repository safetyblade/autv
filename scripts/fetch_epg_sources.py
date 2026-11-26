"""Conditionally fetch the EPG source files used by scripts/build_epg.py.

Trial scope (SABLE AUTV EPG optimisation): the refresh workflow used to
download every EPG source unconditionally with curl on every run, and a
failed download either failed the whole refresh or silently degraded the
combined guide (build_epg.py skips missing sources). This fetcher instead:

- sends If-None-Match / If-Modified-Since using validators stored in a
  small cache manifest (the cache directory is preserved between workflow
  runs with actions/cache),
- on 304 reuses the cached body instead of downloading the file again,
- on a network/HTTP failure falls back to the cached body when one exists
  and reports the source as degraded instead of silently dropping it,
- fails loudly only when a source cannot be obtained at all (no fresh
  body and no cache), so a total outage is never mistaken for a guide
  with fewer channels.

Only the Python standard library is used. Playlists, providers and
channel inventory are out of scope: this module fetches exactly the EPG
source files listed in EPG_SOURCES below, with the same URLs the refresh
workflow previously passed to curl.
"""
from __future__ import annotations

import hashlib
import json
import sys
import urllib.error
import urllib.request
from dataclasses import dataclass
from pathlib import Path

# filename -> URL, identical to the curl downloads this replaces.
EPG_SOURCES = {
    "epg_plex_au.xml.gz": "https://github.com/matthuisman/i.mjh.nz/raw/master/Plex/au.xml.gz",
    "epg_pluto_us.xml.gz": "https://github.com/matthuisman/i.mjh.nz/raw/master/PlutoTV/us.xml.gz",
    "epg_pluto_gb.xml.gz": "https://github.com/matthuisman/i.mjh.nz/raw/master/PlutoTV/gb.xml.gz",
    "epg_pluto_ca.xml.gz": "https://github.com/matthuisman/i.mjh.nz/raw/master/PlutoTV/ca.xml.gz",
    "epg_roku_all.xml.gz": "https://github.com/matthuisman/i.mjh.nz/raw/master/Roku/all.xml.gz",
    "epg_xumo.xml.gz": "https://raw.githubusercontent.com/BuddyChewChew/xumo-playlist-generator/main/playlists/xumo_epg.xml.gz",
    "epg_tubi.xml": "https://raw.githubusercontent.com/BuddyChewChew/tubi-scraper/main/tubi_epg.xml",
    "epg_samsung_all.xml.gz": "https://i.mjh.nz/SamsungTVPlus/all.xml.gz",
}

DEFAULT_CACHE_DIR = Path(".epg-cache")
MANIFEST_NAME = "manifest.json"
TIMEOUT_SECONDS = 60
USER_AGENT = "autv-epg-fetch/1.0"


class FetchError(RuntimeError):
    """A source could not be obtained fresh or from the cache."""


@dataclass
class FetchResult:
    filename: str
    # fresh | not-modified | fallback
    status: str
    detail: str = ""


def _load_manifest(cache_dir: Path) -> dict:
    path = cache_dir / MANIFEST_NAME
    if not path.exists():
        return {}
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {}
    return value if isinstance(value, dict) else {}


def _save_manifest(cache_dir: Path, manifest: dict) -> None:
    cache_dir.mkdir(parents=True, exist_ok=True)
    (cache_dir / MANIFEST_NAME).write_text(
        json.dumps(manifest, indent=2, sort_keys=True), encoding="utf-8"
    )


def _cached_body(cache_dir: Path, filename: str) -> bytes | None:
    path = cache_dir / filename
    if not path.exists():
        return None
    data = path.read_bytes()
    return data if data else None


def fetch_source(
    filename: str,
    url: str,
    cache_dir: Path,
    out_dir: Path,
    manifest: dict,
    opener=urllib.request.urlopen,
) -> FetchResult:
    """Fetch one EPG source into out_dir, using and updating the cache.

    Returns a FetchResult describing which path produced the file. Raises
    FetchError when no usable body exists at all.
    """
    entry = manifest.get(filename, {}) if isinstance(manifest.get(filename), dict) else {}
    headers = {"User-Agent": USER_AGENT}
    if entry.get("etag"):
        headers["If-None-Match"] = entry["etag"]
    if entry.get("last_modified"):
        headers["If-Modified-Since"] = entry["last_modified"]
    request = urllib.request.Request(url, headers=headers)

    body: bytes | None = None
    status = ""
    detail = ""

    def use_cache(kind: str, note: str = "") -> FetchResult:
        cached = _cached_body(cache_dir, filename)
        if cached is None:
            raise FetchError(
                f"{filename}: {kind} but the cache is empty"
            )
        out_dir.mkdir(parents=True, exist_ok=True)
        (out_dir / filename).write_bytes(cached)
        return FetchResult(filename=filename, status=kind, detail=note)

    try:
        with opener(request, timeout=TIMEOUT_SECONDS) as response:
            if response.status == 304:
                return use_cache("not-modified")
            body = response.read()
            if not body:
                raise FetchError(f"{filename}: empty response body")
            cache_dir.mkdir(parents=True, exist_ok=True)
            (cache_dir / filename).write_bytes(body)
            manifest[filename] = {
                "etag": response.headers.get("ETag", ""),
                "last_modified": response.headers.get("Last-Modified", ""),
                "sha256": hashlib.sha256(body).hexdigest(),
            }
            status = "fresh"
    except FetchError:
        raise
    except urllib.error.HTTPError as exc:
        # urllib raises for 304 as well as for 4xx/5xx. A 304 is success:
        # the cached body is authoritative. Other HTTP errors degrade to
        # the cached body when one exists, reported, never silent.
        if exc.code == 304:
            return use_cache("not-modified")
        cached = _cached_body(cache_dir, filename)
        if cached is None:
            raise FetchError(
                f"{filename}: download failed (HTTP {exc.code}) and no cache"
            ) from exc
        out_dir.mkdir(parents=True, exist_ok=True)
        (out_dir / filename).write_bytes(cached)
        return FetchResult(filename=filename, status="fallback", detail=f"HTTP {exc.code}")
    except (urllib.error.URLError, OSError) as exc:
        cached = _cached_body(cache_dir, filename)
        if cached is None:
            raise FetchError(f"{filename}: download failed and no cache: {exc}") from exc
        out_dir.mkdir(parents=True, exist_ok=True)
        (out_dir / filename).write_bytes(cached)
        return FetchResult(filename=filename, status="fallback", detail=str(exc))

    out_dir.mkdir(parents=True, exist_ok=True)
    (out_dir / filename).write_bytes(body)
    return FetchResult(filename=filename, status=status, detail=detail)


def fetch_all(
    cache_dir: Path = DEFAULT_CACHE_DIR,
    out_dir: Path = Path("."),
    sources: dict = EPG_SOURCES,
    opener=urllib.request.urlopen,
) -> list[FetchResult]:
    manifest = _load_manifest(cache_dir)
    results: list[FetchResult] = []
    failures: list[str] = []
    for filename, url in sources.items():
        try:
            result = fetch_source(filename, url, cache_dir, out_dir, manifest, opener)
        except FetchError as exc:
            failures.append(str(exc))
            continue
        results.append(result)
        note = f" ({result.detail})" if result.detail else ""
        print(f"EPG source {filename}: {result.status}{note}")
    _save_manifest(cache_dir, manifest)
    if failures:
        for failure in failures:
            print(f"EPG source error: {failure}", file=sys.stderr)
        raise FetchError(f"{len(failures)} EPG source(s) unavailable")
    return results


def main(argv: list[str] | None = None) -> int:
    args = argv if argv is not None else sys.argv[1:]
    cache_dir = DEFAULT_CACHE_DIR
    if "--cache-dir" in args:
        cache_dir = Path(args[args.index("--cache-dir") + 1])
    try:
        fetch_all(cache_dir=cache_dir)
    except FetchError as exc:
        print(f"EPG fetch failed: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
