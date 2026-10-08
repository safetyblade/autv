from pathlib import Path
import re
import unicodedata
from xml.sax.saxutils import escape

BASE = Path("base_upstream.m3u")
SAMSUNG = Path("secondary_upstream.m3u")
LG = Path("lg_upstream.m3u")
PLUTO_US = Path("pluto_us_upstream.m3u")
PLUTO_CA = Path("pluto_ca_upstream.m3u")
PLUTO_GB = Path("pluto_gb_upstream.m3u")
ROKU = Path("roku_upstream.m3u")
XUMO = Path("xumo_upstream.m3u")
TUBI = Path("tubi_upstream.m3u")
SELECTED_PLEX = Path("selected_plex_channels.txt")
SELECTED_SAMSUNG = Path("selected_channels.txt")
SELECTED_LG = Path("selected_lg_channels.txt")
SELECTED_PLUTO = Path("selected_pluto_channels.txt")
SELECTED_ROKU = Path("selected_roku_channels.txt")
SELECTED_XUMO = Path("selected_xumo_channels.txt")
SELECTED_TUBI = Path("selected_tubi_channels.txt")
CUSTOM = Path("custom.m3u")
OUTPUT = Path("playlist.m3u")
XMLTV = Path("curated.xml")
EPG_URL = "https://raw.githubusercontent.com/safetyblade/autv/main/epg.xml.gz"

def read(path: Path) -> str:
    if not path.exists():
        return ""
    return path.read_text(encoding="utf-8-sig").replace("\r\n", "\n").strip()

def wanted(path: Path):
    return [
        line.strip()
        for line in read(path).splitlines()
        if line.strip() and not line.strip().startswith("#")
    ]

def parse_entries(text: str):
    lines = text.splitlines()
    entries = []
    i = 0
    while i < len(lines):
        line = lines[i].strip()
        if line.startswith("#EXTINF:"):
            j = i + 1
            url = ""
            while j < len(lines):
                candidate = lines[j].strip()
                if candidate and not candidate.startswith("#"):
                    url = candidate
                    break
                j += 1

            name_match = re.search(r",(.*)$", line)
            name = name_match.group(1).strip() if name_match else ""
            entries.append((name, line, url))
            i = j + 1
        else:
            i += 1
    return entries

def attr(extinf: str, key: str) -> str:
    match = re.search(rf'{re.escape(key)}="([^"]*)"', extinf)
    return match.group(1) if match else ""

def slug(value: str) -> str:
    value = unicodedata.normalize("NFKD", value).encode("ascii", "ignore").decode()
    value = re.sub(r"[^a-zA-Z0-9]+", "-", value).strip("-").lower()
    return value or "channel"

def xml_escape(value: str) -> str:
    """Safely escape special XML characters including quotes for attribute values."""
    return escape(value or "", entities={'"': '&quot;', "'": '&apos;'})

def normalise(name: str, extinf: str, url: str):
    tvg_name = attr(extinf, "tvg-name") or name
    tvg_logo = attr(extinf, "tvg-logo")
    group = attr(extinf, "group-title") or "Australia"
    tvg_id = attr(extinf, "tvg-id") or f"autv.{slug(name)}"

    fields = [
        f'tvg-id="{tvg_id}"',
        f'tvg-name="{tvg_name}"',
    ]
    if tvg_logo:
        fields.append(f'tvg-logo="{tvg_logo}"')
    fields.append(f'group-title="{group}"')

    m3u = f'#EXTINF:-1 {" ".join(fields)},{name}\n{url}'
    xml = {"id": tvg_id, "name": tvg_name, "logo": tvg_logo}
    return m3u, xml

def add_selected(source_name: str, entries, selected_file: Path, extras, xml_channels):
    missing = []
    for name in wanted(selected_file):
        exact = next((e for e in entries if e[0].casefold() == name.casefold()), None)
        match = exact or next((e for e in entries if name.casefold() in e[0].casefold()), None)

        if match:
            m3u, xml = normalise(match[0], match[1], match[2])
            extras.append(m3u)
            xml_channels.append(xml)
            print(f"ADDED {source_name}: {match[0]}")
        else:
            missing.append(name)
            print(f"NOT FOUND {source_name}: {name}")
    return missing

def main():
    plex_text = read(BASE)
    if not plex_text.startswith("#EXTM3U"):
        raise SystemExit("Plex playlist is invalid")

    plex_entries = parse_entries(plex_text)
    samsung_entries = parse_entries(read(SAMSUNG))
    lg_entries = parse_entries(read(LG))
    pluto_entries = (
        parse_entries(read(PLUTO_US))
        + parse_entries(read(PLUTO_CA))
        + parse_entries(read(PLUTO_GB))
    )
    roku_entries = parse_entries(read(ROKU))
    xumo_entries = parse_entries(read(XUMO))
    tubi_entries = parse_entries(read(TUBI))

    extras = []
    xml_channels = []

    missing_plex = add_selected("PLEX", plex_entries, SELECTED_PLEX, extras, xml_channels)
    missing_samsung = add_selected("SAMSUNG", samsung_entries, SELECTED_SAMSUNG, extras, xml_channels)
    missing_lg = add_selected("LG", lg_entries, SELECTED_LG, extras, xml_channels)
    missing_pluto = add_selected("PLUTO", pluto_entries, SELECTED_PLUTO, extras, xml_channels)
    missing_roku = add_selected("ROKU", roku_entries, SELECTED_ROKU, extras, xml_channels)
    missing_xumo = add_selected("XUMO", xumo_entries, SELECTED_XUMO, extras, xml_channels)
    missing_tubi = add_selected("TUBI", tubi_entries, SELECTED_TUBI, extras, xml_channels)

    for name, extinf, url in parse_entries(read(CUSTOM)):
        m3u, xml = normalise(name, extinf, url)
        extras.append(m3u)
        xml_channels.append(xml)
        print(f"ADDED CUSTOM: {name}")

    merged = f'#EXTM3U url-tvg="{EPG_URL}"\n'
    if extras:
        merged += "\n# ---- Selected channels ----\n" + "\n".join(extras)
    merged += "\n"

    count = sum(1 for line in merged.splitlines() if line.startswith("#EXTINF:"))
    if count < 10:
        raise SystemExit(f"Refusing suspicious playlist: only {count} channels")

    OUTPUT.write_text(merged, encoding="utf-8")

    xml_lines = ['<?xml version="1.0" encoding="UTF-8"?>', '<tv generator-info-name="autv">']
    for channel in xml_channels:
        xml_lines.append(f'  <channel id="{xml_escape(channel["id"])}">')
        xml_lines.append(f'    <display-name>{xml_escape(channel["name"])}</display-name>')
        if channel["logo"]:
            xml_lines.append(f'    <icon src="{xml_escape(channel["logo"])}" />')
        xml_lines.append('  </channel>')
    xml_lines.append('</tv>')
    XMLTV.write_text("\n".join(xml_lines) + "\n", encoding="utf-8")

    print("")
    print(f"Wrote {OUTPUT} with {count} channels")
    print(f"Curated channels added: {len(extras)}")
    print(f"Wrote {XMLTV} with {len(xml_channels)} channel definitions")
    print(f"Plex requested channels not found: {len(missing_plex)}")
    print(f"Samsung requested channels not found: {len(missing_samsung)}")
    print(f"LG requested channels not found: {len(missing_lg)}")
    print(f"Pluto requested channels not found: {len(missing_pluto)}")
    print(f"Roku requested channels not found: {len(missing_roku)}")
    print(f"Xumo requested channels not found: {len(missing_xumo)}")
    print(f"Tubi requested channels not found: {len(missing_tubi)}")
    if missing_plex:
        print("Missing Plex list: " + " | ".join(missing_plex))
    if missing_samsung:
        print("Missing Samsung list: " + " | ".join(missing_samsung))
    if missing_lg:
        print("Missing LG list: " + " | ".join(missing_lg))
    if missing_pluto:
        print("Missing Pluto list: " + " | ".join(missing_pluto))
    if missing_roku:
        print("Missing Roku list: " + " | ".join(missing_roku))
    if missing_xumo:
        print("Missing Xumo list: " + " | ".join(missing_xumo))
    if missing_tubi:
        print("Missing Tubi list: " + " | ".join(missing_tubi))

if __name__ == "__main__":
    main()
