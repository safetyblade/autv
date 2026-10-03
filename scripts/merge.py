from pathlib import Path
import re
import unicodedata

BASE = Path("base_upstream.m3u")
SECONDARY = Path("secondary_upstream.m3u")
SELECTED = Path("selected_channels.txt")
CUSTOM = Path("custom.m3u")
OUTPUT = Path("playlist.m3u")

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

def slug(value: str) -> str:
    value = unicodedata.normalize("NFKD", value).encode("ascii", "ignore").decode()
    value = re.sub(r"[^a-zA-Z0-9]+", "-", value).strip("-").lower()
    return value or "channel"

def attr(extinf: str, key: str) -> str:
    match = re.search(rf'{re.escape(key)}="([^"]*)"', extinf)
    return match.group(1) if match else ""

def normalise(name: str, extinf: str, url: str) -> str:
    channel_id = attr(extinf, "channel-id") or f"autv.{slug(name)}"
    tvg_id = attr(extinf, "tvg-id") or channel_id
    tvg_name = attr(extinf, "tvg-name") or name
    tvg_chno = attr(extinf, "tvg-chno")
    tvg_logo = attr(extinf, "tvg-logo")
    group = attr(extinf, "group-title") or "Australia"

    fields = [
        f'channel-id="{channel_id}"',
        f'tvg-id="{tvg_id}"',
        f'tvg-chno="{tvg_chno}"',
        f'tvg-name="{tvg_name}"',
    ]
    if tvg_logo:
        fields.append(f'tvg-logo="{tvg_logo}"')
    fields.append(f'group-title="{group}"')

    return f'#EXTINF:-1 {" ".join(fields)},{name}\n{url}'

base = read(BASE)
if not base.startswith("#EXTM3U"):
    raise SystemExit("Base playlist is invalid")

secondary_entries = parse_entries(read(SECONDARY))
extras = []
missing = []

for name in wanted(SELECTED):
    exact = next((e for e in secondary_entries if e[0].casefold() == name.casefold()), None)
    match = exact or next((e for e in secondary_entries if name.casefold() in e[0].casefold()), None)

    if not match:
        missing.append(name)
        continue

    extras.append(normalise(match[0], match[1], match[2]))

for name, extinf, url in parse_entries(read(CUSTOM)):
    extras.append(normalise(name, extinf, url))

if missing:
    raise SystemExit("Selected channels not found: " + ", ".join(missing))

merged = base
if extras:
    merged += "\n\n# ---- Curated extras ----\n" + "\n".join(extras)
merged += "\n"

count = sum(1 for line in merged.splitlines() if line.startswith("#EXTINF:"))
if count < 10:
    raise SystemExit(f"Refusing suspicious playlist: only {count} channels")

OUTPUT.write_text(merged, encoding="utf-8")
print(f"Wrote {OUTPUT} with {count} channels")
