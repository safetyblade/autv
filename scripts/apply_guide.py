from pathlib import Path
import csv
import functools
import re
import unicodedata

PLAYLIST = Path("playlist.m3u")
GUIDE = Path("channel_guide.csv")

def parse_entries(text):
    lines = text.splitlines()
    header = next((line for line in lines if line.startswith("#EXTM3U")), "#EXTM3U")
    entries = []
    i = 0
    while i < len(lines):
        line = lines[i].strip()
        if line.startswith("#EXTINF:"):
            url = ""
            j = i + 1
            while j < len(lines):
                candidate = lines[j].strip()
                if candidate and not candidate.startswith("#"):
                    url = candidate
                    break
                j += 1
            # Optimization: Fast string search instead of re.search for channel display name
            idx = line.rfind(",")
            name = line[idx + 1:].strip() if idx != -1 else ""
            entries.append((name, line, url))
            i = j + 1
        else:
            i += 1
    return header, entries

def set_attr(extinf, key, value):
    # Optimization: Fast string search for key="value" attribute update avoiding regex overhead
    marker = f'{key}="'
    idx = extinf.find(marker)
    if idx != -1:
        val_start = idx + len(marker)
        val_end = extinf.find('"', val_start)
        if val_end != -1:
            return extinf[:val_start] + str(value) + extinf[val_end:]
    return extinf.replace("#EXTINF:-1", f'#EXTINF:-1 {key}="{value}"', 1)

def set_name(extinf, name):
    # Optimization: Fast string search for setting display name after last comma
    idx = extinf.rfind(",")
    if idx != -1:
        return extinf[:idx + 1] + name
    return f"{extinf},{name}"
    # Sanitize value to prevent attribute breakout and line injection
    safe_value = str(value).replace("\r", " ").replace("\n", " ").replace('"', "'")
    pattern = rf'{re.escape(key)}="[^"]*"'
    if re.search(pattern, extinf):
        # Use callable replacement to prevent regex backreference injection (e.g. \1)
        return re.sub(pattern, lambda _: f'{key}="{safe_value}"', extinf)
    return extinf.replace("#EXTINF:-1", f'#EXTINF:-1 {key}="{safe_value}"', 1)

def set_name(extinf, name):
    # Sanitize name to prevent line injection and use callable replacement for backreference safety
    safe_name = str(name).replace("\r", " ").replace("\n", " ")
    return re.sub(r",(.*)$", lambda _: f",{safe_name}", extinf)

@functools.lru_cache(maxsize=1024)
def canonical(value):
    # Optimization: Cache normalized strings to avoid redundant regex/unicode normalizations
    value = unicodedata.normalize("NFKD", value or "").encode("ascii", "ignore").decode()
    value = value.casefold().strip()
    value = re.sub(r"^\d+\s+", "", value)
    value = re.sub(r"\s*\((?:australia|au)\)\s*", " ", value)
    value = re.sub(r"\s+geo\b", " ", value)
    value = value.replace("&", " and ")
    value = re.sub(r"[^a-z0-9]+", " ", value)
    return re.sub(r"\s+", " ", value).strip()

if __name__ == "__main__":
    with GUIDE.open(newline="", encoding="utf-8-sig") as handle:
        rows = list(csv.DictReader(handle))

    guide_all = {
        canonical(row["Channel name"]): row
        for row in rows
        if row.get("Channel name")
    }
    guide = {
        name: row
        for name, row in guide_all.items()
        if row.get("Status", "Active").strip().casefold() == "active"
    }

    header, entries = parse_entries(PLAYLIST.read_text(encoding="utf-8-sig"))
    ranked = []
    missing = []
    seen_names = set()

    for position, (name, extinf, url) in enumerate(entries):
        logical_name = canonical(name)
        if logical_name in seen_names:
            continue
        seen_names.add(logical_name)
        guide_row = guide_all.get(logical_name)
        if guide_row and guide_row.get("Status", "Active").strip().casefold() != "active":
            continue
        row = guide.get(logical_name)
        if row:
            channel_no = int(row["Channel number"])
            genre = row["Genre"].strip() or "General Entertainment"
            display_name = row["Channel name"].strip()
            extinf = set_attr(extinf, "tvg-chno", str(channel_no))
            extinf = set_attr(extinf, "group-title", genre)
            extinf = set_attr(extinf, "tvg-name", display_name)
            extinf = set_name(extinf, display_name)
            ranked.append((channel_no, position, extinf, url))
        else:
            missing.append(name)
            continue

    ranked.sort(key=lambda item: (item[0], item[1]))

    out = [header]
    for _, _, extinf, url in ranked:
        out.extend([extinf, url])

    PLAYLIST.write_text("\n".join(out) + "\n", encoding="utf-8")

    print(f"Applied guide metadata to {len(entries) - len(missing)} channels")
    print(f"Guide-unmatched channels dropped: {len(missing)}")
    if missing:
        print("Unmatched: " + " | ".join(missing))
