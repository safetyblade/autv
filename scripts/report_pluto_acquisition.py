#!/usr/bin/env python3
"""Advisory regional Pluto source availability; never log URLs or JWTs."""
from pathlib import Path
from collections import Counter

REGIONS = {"US":"pluto_us_upstream.m3u","CA":"pluto_ca_upstream.m3u","UK":"pluto_gb_upstream.m3u"}

def names(path):
    if not path.exists():
        return []
    return [line.rsplit(",",1)[-1].strip() for line in path.read_text(encoding="utf-8-sig").splitlines() if line.startswith("#EXTINF:")]

def report():
    selected = [s.strip() for s in Path("selected_pluto_channels.txt").read_text(encoding="utf-8-sig").splitlines() if s.strip() and not s.lstrip().startswith("#")]
    seen=set()
    for region,path in REGIONS.items():
        channel_names=names(Path(path))
        seen.update(n.casefold() for n in channel_names)
        print(f"Pluto acquisition {region}: {len(channel_names)} catalogue entries")
    missing=[s for s in selected if not any(s.casefold() in n for n in seen)]
    print(f"Pluto selection: {len(selected)} requested; {len(selected)-len(missing)} approximate name matches; {len(missing)} unmatched")
    # Provider title matching is intentionally diagnostic only, not source replacement approval.
    if missing:
        print("Pluto unmatched selected names: " + " | ".join(missing[:40]))
    if not any(names(Path(p)) for p in REGIONS.values()):
        print("::warning::All three Pluto regions empty; check boot failure, remote catalogues, auth and regional availability")
    print("Advisory: catalogue names and valid sessions do not establish playback.")

if __name__=="__main__":
    report()
