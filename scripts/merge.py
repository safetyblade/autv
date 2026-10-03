from pathlib import Path

plex_path = Path("plex_upstream.m3u")
custom_path = Path("custom.m3u")
output_path = Path("playlist.m3u")

if not plex_path.exists():
    raise SystemExit("plex_upstream.m3u was not downloaded")

plex = plex_path.read_text(encoding="utf-8-sig").replace("\r\n", "\n").strip()
custom = custom_path.read_text(encoding="utf-8-sig").replace("\r\n", "\n").strip()

if not plex.startswith("#EXTM3U"):
    raise SystemExit("Plex upstream is not a valid M3U playlist")

if custom:
    custom_lines = custom.splitlines()
    if custom_lines and custom_lines[0].strip().startswith("#EXTM3U"):
        custom_lines = custom_lines[1:]
    custom_body = "\n".join(custom_lines).strip()
else:
    custom_body = ""

merged = plex
if custom_body:
    merged += "\n\n# ---- Custom FAST extras ----\n" + custom_body
merged += "\n"

extinf_count = sum(1 for line in merged.splitlines() if line.startswith("#EXTINF:"))
if extinf_count < 10:
    raise SystemExit(f"Refusing to write suspicious playlist: only {extinf_count} channels")

output_path.write_text(merged, encoding="utf-8")
print(f"Wrote {output_path} with {extinf_count} channels")
