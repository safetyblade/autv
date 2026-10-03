from pathlib import Path
import re

BASE = Path("base_upstream.m3u")
SECONDARY = Path("secondary_upstream.m3u")
APPROVED = Path("selected_channels.txt")
CANDIDATES = Path("candidate_channels.txt")
CUSTOM = Path("custom.m3u")
OUTPUT = Path("playlist.m3u")
TEST_OUTPUT = Path("test-playlist.m3u")

def read(path: Path) -> str:
    if not path.exists():
        return ""
    return path.read_text(encoding="utf-8-sig").replace("\r\n", "\n").strip()

def wanted(path: Path):
    out = []
    for line in read(path).splitlines():
        line = line.strip()
        if line and not line.startswith("#"):
            out.append(line)
    return out

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
            url = ""
            for x in reversed(block):
                x = x.strip()
                if x and not x.startswith("#"):
                    url = x
                    break
            entries.append((name, url, "\n".join(block).strip()))
            i = j + 1
        else:
            i += 1
    return entries

def select(entries, names):
    blocks, missing = [], []
    for name in names:
        exact = next((e for e in entries if e[0].casefold() == name.casefold()), None)
        match = exact or next((e for e in entries if name.casefold() in e[0].casefold()), None)
        if match:
            blocks.append(match[2])
            print(f"Selected: {match[0]} -> {match[1]}")
        else:
            missing.append(name)
    return blocks, missing

base = read(BASE)
if not base.startswith("#EXTM3U"):
    raise SystemExit("Base upstream is not a valid M3U playlist")

secondary = read(SECONDARY)
entries = parse_entries(secondary) if secondary else []

approved_blocks, missing_approved = select(entries, wanted(APPROVED))
candidate_blocks, missing_candidates = select(entries, wanted(CANDIDATES))

if missing_approved:
    raise SystemExit("Approved channels not found: " + ", ".join(missing_approved))

custom = read(CUSTOM)
custom_body = ""
if custom:
    lines = custom.splitlines()
    if lines and lines[0].strip().startswith("#EXTM3U"):
        lines = lines[1:]
    custom_body = "\n".join(lines).strip()

def build(extra_blocks):
    merged = base
    extras = list(extra_blocks)
    if custom_body:
        extras.append(custom_body)
    if extras:
        merged += "\n\n# ---- Curated extras ----\n" + "\n".join(extras)
    merged += "\n"
    count = sum(1 for line in merged.splitlines() if line.startswith("#EXTINF:"))
    if count < 10:
        raise SystemExit(f"Refusing suspicious playlist: only {count} channels")
    return merged, count

main, main_count = build(approved_blocks)
OUTPUT.write_text(main, encoding="utf-8")
print(f"Wrote {OUTPUT} with {main_count} channels")

test, test_count = build(approved_blocks + candidate_blocks)
TEST_OUTPUT.write_text(test, encoding="utf-8")
print(f"Wrote {TEST_OUTPUT} with {test_count} channels")

if missing_candidates:
    print("WARNING: candidate channels not found: " + ", ".join(missing_candidates))
