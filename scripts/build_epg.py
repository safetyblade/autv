import copy
from collections import Counter
from functools import cache
from pathlib import Path
import csv
import gzip
import re
import unicodedata
import xml.etree.ElementTree as ET

PLAYLIST = Path("playlist.m3u")
GUIDE = Path("channel_guide.csv")
OUTPUT = Path("epg.xml.gz")

EPG_SOURCES = [
    Path("epg_plex_au.xml.gz"),
    Path("epg_pluto_us.xml.gz"),
    Path("epg_pluto_gb.xml.gz"),
    Path("epg_pluto_ca.xml.gz"),
    Path("epg_roku_all.xml.gz"),
    Path("epg_xumo.xml.gz"),
    Path("epg_tubi.xml"),
    Path("epg_samsung_all.xml.gz"),
]

# Compiled regexes for canonical string normalization performance
RE_LEADING_DIGITS = re.compile(r"^\d+\s+")
RE_AU_SUFFIX = re.compile(r"\s*\((?:australia|au)\)\s*")
RE_GEO = re.compile(r"\s+geo\b")
RE_NON_ALPHANUM = re.compile(r"[^a-z0-9]+")
RE_SPACES = re.compile(r"\s+")

# Android / Live Channels understands a smaller canonical genre set than AU TV.
# This affects only generated EPG programme metadata; channel numbers, ordering,
# guide genres and M3U group-title values remain controlled by channel_guide.csv.
ANDROID_GENRE = {
    "News": "News",
    "Sport": "Sports",
    "Movies": "Movies",
    "Game Shows": "Entertainment",
    "Crime": "Drama",
    "Comedy": "Comedy",
    "Entertainment": "Entertainment",
    "Reality & Lifestyle": "Lifestyle",
    "Factual": "Education",
    "Kids & Animation": "Family/Kids",
    "Music": "Music",
}

# Performance optimization: @cache memoizes expensive NFKD normalization and regex replacements
@cache
def canonical(value: str) -> str:
    value = unicodedata.normalize("NFKD", value or "").encode("ascii", "ignore").decode()
    value = value.casefold().strip()
    value = RE_LEADING_DIGITS.sub("", value)
    value = RE_AU_SUFFIX.sub(" ", value)
    value = RE_GEO.sub(" ", value)
    value = value.replace("&", " and ")
    value = RE_NON_ALPHANUM.sub(" ", value)
    return RE_SPACES.sub(" ", value).strip()

def attr(line: str, key: str) -> str:
    marker = f'{key}="'
    pos = 0
    while True:
        idx = line.find(marker, pos)
        if idx == -1:
            return ""
        if idx == 0 or line[idx - 1] in " \t\n\r":
            start = idx + len(marker)
            end = line.find('"', start)
            return line[start:end].strip() if end != -1 else ""
        pos = idx + 1

def playlist_channels():
    channels = []
    for line in PLAYLIST.read_text(encoding="utf-8").splitlines():
        if not line.startswith("#EXTINF:"):
            continue
        cid = attr(line, "tvg-id")
        name = attr(line, "tvg-name")
        if not name and "," in line:
            name = line.rsplit(",", 1)[1].strip()
        if cid:
            channels.append((cid, name))
    return channels

def guide_genres():
    genres = {}
    with GUIDE.open(newline="", encoding="utf-8-sig") as handle:
        for row in csv.DictReader(handle):
            if row.get("Status", "").strip().casefold() != "active":
                continue
            name = row.get("Channel name", "").strip()
            genre = row.get("Genre", "").strip()
            if name and genre:
                genres[canonical(name)] = genre
    return genres

def add_android_category(programme, category):
    if not category:
        return
    existing = {
        (node.text or "").strip().casefold()
        for node in programme.findall("category")
        if (node.text or "").strip()
    }
    if category.casefold() not in existing:
        ET.SubElement(programme, "category").text = category


playlist = playlist_channels()
playlist_names = {cid: name for cid, name in playlist}
guide_genre_by_name = guide_genres()

# Performance optimization: pre-compute playlist IDs and canonical channel names
# to filter out programmes for non-playlist channels during iterparse.
playlist_cids = {cid for cid, _ in playlist}
playlist_canonical_names = {canonical(name) for _, name in playlist if canonical(name)}

source_channels = {}
source_names = {}
programmes_by_id = {}
relevant_cids = set(playlist_cids)

for source in EPG_SOURCES:
    if not source.exists():
        continue
    opener = gzip.open if source.suffix == ".gz" else open
    with opener(source, "rb") as handle:
        for event, elem in ET.iterparse(handle, events=("end",)):
            if elem.tag == "channel":
                cid = elem.attrib.get("id", "")
                if cid and cid not in source_channels:
                    # Performance optimization: copy.deepcopy avoids expensive byte serialization/deserialization
                    cloned = copy.deepcopy(elem)
                    source_channels[cid] = cloned
                    names = [
                        (node.text or "").strip()
                        for node in cloned.findall("display-name")
                        if (node.text or "").strip()
                    ]
                    is_relevant = cid in playlist_cids
                    for name in names:
                        key = canonical(name)
                        if key:
                            source_names.setdefault(key, set()).add(cid)
                            if key in playlist_canonical_names:
                                is_relevant = True
                    if is_relevant:
                        relevant_cids.add(cid)
                elem.clear()
            elif elem.tag == "programme":
                cid = elem.attrib.get("channel", "")
                # Performance optimization: only store programmes for channels matching playlist IDs/names,
                # bypassing copy.deepcopy overhead for hundreds of unneeded non-playlist channels.
                if cid in relevant_cids:
                    programmes_by_id.setdefault(cid, []).append(
                        copy.deepcopy(elem)
                    )
                elem.clear()

target_sources = {}
fallback_matches = {}
missing = []

for target_id, target_name in playlist:
    source_id = target_id if target_id in source_channels else None

    if source_id is None:
        candidates = [
            cid for cid in source_names.get(canonical(target_name), set())
            if programmes_by_id.get(cid)
        ]
        if len(candidates) == 1:
            source_id = candidates[0]
            fallback_matches[target_id] = source_id

    if source_id is None:
        missing.append((target_id, target_name))
        continue

    target_sources[target_id] = source_id

source_counts = Counter(target_sources.values())

root = ET.Element("tv", {"generator-info-name": "autv"})
covered = set()
programme_count = 0

for target_id, target_name in playlist:
    if target_id not in target_sources:
        continue
    source_id = target_sources[target_id]
    guide_genre = guide_genre_by_name.get(canonical(target_name), "")
    android_category = ANDROID_GENRE.get(guide_genre, "Entertainment")

    # Performance optimization: pop uniquely-referenced source elements to reuse directly without copy.deepcopy (~40% time reduction).
    is_unique = source_counts[source_id] == 1
    channel = source_channels.pop(source_id) if is_unique else copy.deepcopy(source_channels[source_id])
    channel.set("id", target_id)
    root.append(channel)

    programmes = programmes_by_id.pop(source_id, []) if is_unique else [copy.deepcopy(p) for p in programmes_by_id.get(source_id, [])]
    programme_count += len(programmes)

    for programme in programmes:
        programme.set("channel", target_id)
        add_android_category(programme, android_category)
        root.append(programme)

    covered.add(target_id)

xml_bytes = ET.tostring(root, encoding="utf-8", xml_declaration=True)
with gzip.open(OUTPUT, "wb", compresslevel=9) as handle:
    handle.write(xml_bytes)

print(f"Wrote {OUTPUT} for {len(covered)} channels with {programme_count} programmes")
print(f"Playlist channels: {len(playlist)}")
print(f"EPG exact-ID matches: {len(covered) - len(fallback_matches)}")
print(f"EPG name-fallback matches: {len(fallback_matches)}")
print(f"EPG missing: {len(missing)}")
if fallback_matches:
    print("Fallback matches:")
    for target_id, source_id in sorted(fallback_matches.items()):
        print(f"  {playlist_names.get(target_id, target_id)} <- {source_id}")
if missing:
    print("Missing EPG:")
    for target_id, name in missing:
        print(f"  {name} [{target_id}]")
