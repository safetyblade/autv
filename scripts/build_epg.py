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

def canonical(value: str) -> str:
    value = unicodedata.normalize("NFKD", value or "").encode("ascii", "ignore").decode()
    value = value.casefold().strip()
    value = re.sub(r"^\d+\s+", "", value)
    value = re.sub(r"\s*\((?:australia|au)\)\s*", " ", value)
    value = re.sub(r"\s+geo\b", " ", value)
    value = value.replace("&", " and ")
    value = re.sub(r"[^a-z0-9]+", " ", value)
    return re.sub(r"\s+", " ", value).strip()

def attr(line: str, key: str) -> str:
    marker = f'{key}="'
    if marker not in line:
        return ""
    return line.split(marker, 1)[1].split('"', 1)[0].strip()

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

source_channels = {}
source_names = {}
programmes_by_id = {}

for source in EPG_SOURCES:
    if not source.exists():
        continue
    opener = gzip.open if source.suffix == ".gz" else open
    with opener(source, "rb") as handle:
        for event, elem in ET.iterparse(handle, events=("end",)):
            if elem.tag == "channel":
                cid = elem.attrib.get("id", "")
                if cid and cid not in source_channels:
                    cloned = ET.fromstring(ET.tostring(elem, encoding="utf-8"))
                    source_channels[cid] = cloned
                    names = [
                        (node.text or "").strip()
                        for node in cloned.findall("display-name")
                        if (node.text or "").strip()
                    ]
                    for name in names:
                        key = canonical(name)
                        if key:
                            source_names.setdefault(key, set()).add(cid)
                elem.clear()
            elif elem.tag == "programme":
                cid = elem.attrib.get("channel", "")
                if cid:
                    programmes_by_id.setdefault(cid, []).append(
                        ET.fromstring(ET.tostring(elem, encoding="utf-8"))
                    )
                elem.clear()

root = ET.Element("tv", {"generator-info-name": "autv"})
covered = set()
fallback_matches = {}
missing = []

for target_id, target_name in playlist:
    guide_genre = guide_genre_by_name.get(canonical(target_name), "")
    android_category = ANDROID_GENRE.get(guide_genre, "Entertainment")

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

    channel = ET.fromstring(ET.tostring(source_channels[source_id], encoding="utf-8"))
    channel.set("id", target_id)
    root.append(channel)

    for programme in programmes_by_id.get(source_id, []):
        cloned = ET.fromstring(ET.tostring(programme, encoding="utf-8"))
        cloned.set("channel", target_id)
        add_android_category(cloned, android_category)
        root.append(cloned)

    covered.add(target_id)

xml_bytes = ET.tostring(root, encoding="utf-8", xml_declaration=True)
with gzip.open(OUTPUT, "wb", compresslevel=9) as handle:
    handle.write(xml_bytes)

programme_count = sum(
    len(programmes_by_id.get(fallback_matches.get(cid, cid), []))
    for cid in covered
)

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
