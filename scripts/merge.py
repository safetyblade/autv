from pathlib import Path
import re

PLEX = Path("plex_upstream.m3u")
SAMSUNG = Path("samsung_au_upstream.m3u")
CUSTOM = Path("custom.m3u")
SELECTED = Path("selected_channels.txt")
OUTPUT = Path("playlist.m3u")

def read(path: Path) -> str:
    if not path.exists():
        return ""
    return path.read_text(encoding="utf-8-sig").replace("\r\n", "\n").strip()

def parse_entries(text: str):
    lines = text.splitlines()
    entries = []
    i = 0
    while i < len(lines):
        line = lines[i].strip()
        if line.startswith("#EXTINF:"):
            block = [lines[i]]
            j = i + 1
            while j < len(lines):
                block.append(lines[j])
                if lines[j].strip() and not lines[j].lstrip().startswith("#"):
                    break
                j += 1
            m = re.search(r",(.*)$", line)
            name = m.group(1).strip() if m else ""
            entries.append((name, "\n".join(block).strip()))
            i = j + 1
        else:
            i += 1
    return entries

plex = read(PLEX)
if not plex.startswith("#EXTM3U"):
    raise SystemExit("Plex upstream is not a valid M3U playlist")

selected = []
for line in read(SELECTED).splitlines():
    line = line.strip()
    if line and not line.startswith("#"):
        selected.append(line)

extras = []
missing = []

samsung = read(SAMSUNG)
if selected and samsung:
    samsung_entries = parse_entries(samsung)
    for wanted in selected:
        match = next(
            ((name, block) for name, block in samsung_entries
             if wanted.casefold() == name.casefold()),
            None,
        )
        if match is None:
            match = next(
                ((name, block) for name, block in samsung_entries
                 if wanted.casefold() in name.casefold()),
                None,
            )
        if match:
            extras.append(match[1])
            print(f"Selected from Samsung AU: {match[0]}")
        else:
            missing.append(wanted)

custom = read(CUSTOM)
if custom:
    lines = custom.splitlines()
    if lines and lines[0].strip().startswith("#EXTM3U"):
        lines = lines[1:]
    body = "\n".join(lines).strip()
    if body:
        extras.append(body)

if missing:
    raise SystemExit("Requested channels not found in secondary sources: " + ", ".join(missing))

merged = plex
if extras:
    merged += "\n\n# ---- Curated FAST extras ----\n" + "\n".join(extras)
merged += "\n"

count = sum(1 for line in merged.splitlines() if line.startswith("#EXTINF:"))
if count < 10:
    raise SystemExit(f"Refusing to write suspicious playlist: only {count} channels")

OUTPUT.write_text(merged, encoding="utf-8")
print(f"Wrote {OUTPUT} with {count} channels")
