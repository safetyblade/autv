from pathlib import Path
import re
import unicodedata

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
            block = [lines[i]]
            j = i + 1
            while j < len(lines):
                block.append(lines[j])
                if lines[j].strip() and not lines[j].lstrip().startswith("#"):
                    break
                j += 1

            m = re.search(r",(.*)$", line)
            name = m.group(1).strip() if m else ""
            url = next(
                (x.strip() for x in reversed(block) if x.strip() and not x.lstrip().startswith("#")),
                "",
            )
            entries.append((name, line, url, "\n".join(block).strip()))
            i = j + 1
        else:
            i += 1
    return entries

def slug(value: str) -> str:
    value = unicodedata.normalize("NFKD", value).encode("ascii", "ignore").decode()
    value = re.sub(r"[^a-zA-Z0-9]+", "-", value).strip("-").lower()
    return value or "channel"

def attr(extinf: str, key: str) -> str:
    m = re.search(rf'{re.escape(key)}="([^"]*)"', extinf)
    return m.group(1) if m else ""

def normalise_extra(name: str, extinf: str, url: str) -> str:
    # Secondary sources can contain bare '#EXTINF:-1,Name' entries.
    # Give extras a stable metadata shape so TV apps see them consistently.
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

def select(entries, names):
    blocks, missing = [], []
    for name in names:
        exact = next((e for e in entries if e[0].casefold() == name.casefold()), None)
        match = exact or next((e for e in entries if name.casefold() in e[0].casefold()), None)
        if match:
            blocks.append(normalise_extra(match[0], match[1], match[2]))
            print(f"Selected: {match[0]} -> {match[2]}")
        else:
            missing.append(name)
    return blocks, missing

base = read(BASE)
if not base.startswith("#EXTM3U"):
    raise SystemExit("Base upstream is not a valid M3U playlist")

entries = parse_entries(read(SECONDARY))

approved_blocks, missing_approved = select(entries, wanted(APPROVED))
candidate_blocks, missing_candidates = select(entries, wanted(CANDIDATES))

if missing_approved:
    raise SystemExit("Approved channels not found: " + ", ".join(missing_approved))

# Only real channel blocks from custom.m3u are appended.
# Standalone comments are ignored.
custom_blocks = [entry[3] for entry in parse_entries(read(CUSTOM))]

def build(extra_blocks):
    merged = base
    extras = list(extra_blocks) + custom_blocks
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
