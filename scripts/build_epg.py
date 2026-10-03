from pathlib import Path
import gzip
import xml.etree.ElementTree as ET

PLAYLIST = Path("playlist.m3u")
OUTPUT = Path("epg.xml.gz")

EPG_SOURCES = [
    Path("epg_plex_au.xml.gz"),
    Path("epg_plex_us.xml.gz"),
    Path("epg_plex_gb.xml.gz"),
    Path("epg_plex_ca.xml.gz"),
    Path("epg_plex_nz.xml.gz"),
    Path("epg_pluto_us.xml.gz"),
    Path("epg_pluto_gb.xml.gz"),
    Path("epg_pluto_ca.xml.gz"),
]

def playlist_ids():
    ids = set()
    for line in PLAYLIST.read_text(encoding="utf-8").splitlines():
        if not line.startswith("#EXTINF:"):
            continue
        marker = 'tvg-id="'
        if marker not in line:
            continue
        value = line.split(marker, 1)[1].split('"', 1)[0].strip()
        if value:
            ids.add(value)
    return ids

wanted = playlist_ids()
root = ET.Element("tv", {"generator-info-name": "autv"})

channels = {}
programmes = []

for source in EPG_SOURCES:
    if not source.exists():
        continue
    with gzip.open(source, "rb") as handle:
        for event, elem in ET.iterparse(handle, events=("end",)):
            if elem.tag == "channel":
                cid = elem.attrib.get("id", "")
                if cid in wanted and cid not in channels:
                    channels[cid] = ET.fromstring(ET.tostring(elem, encoding="utf-8"))
                elem.clear()
            elif elem.tag == "programme":
                cid = elem.attrib.get("channel", "")
                if cid in wanted:
                    programmes.append(ET.fromstring(ET.tostring(elem, encoding="utf-8")))
                elem.clear()

for cid in sorted(channels):
    root.append(channels[cid])

for programme in programmes:
    root.append(programme)

xml_bytes = ET.tostring(root, encoding="utf-8", xml_declaration=True)
with gzip.open(OUTPUT, "wb", compresslevel=9) as handle:
    handle.write(xml_bytes)

print(f"Wrote {OUTPUT} for {len(channels)} channels with {len(programmes)} programmes")
print(f"Playlist IDs: {len(wanted)}")
