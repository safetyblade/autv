from pathlib import Path
import csv
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
            name_match = re.search(r",(.*)$", line)
            name = name_match.group(1).strip() if name_match else ""
            entries.append((name, line, url))
            i = j + 1
        else:
            i += 1
    return header, entries

def set_attr(extinf, key, value):
    pattern = rf'{re.escape(key)}="[^"]*"'
    replacement = f'{key}="{value}"'
    if re.search(pattern, extinf):
        return re.sub(pattern, lambda _: replacement, extinf)
    return extinf.replace("#EXTINF:-1", f'#EXTINF:-1 {replacement}', 1)

def set_name(extinf, name):
    return re.sub(r",(.*)$", lambda _: f",{name}", extinf)

def canonical(value):
    value = unicodedata.normalize("NFKD", value or "").encode("ascii", "ignore").decode()
    value = value.casefold().strip()
    value = re.sub(r"^\\d+\\s+", "", value)
    value = re.sub(r"\\s*\\((?:australia|au)\\)\\s*", " ", value)
    value = re.sub(r"\\s+geo\\b", " ", value)
    value = value.replace("&", " and ")
    value = re.sub(r"[^a-z0-9]+", " ", value)
    return re.sub(r"\\s+", " ", value).strip()

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
