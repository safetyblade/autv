#!/usr/bin/env python3
"""Generate an empty dynamic-source playlist.

The retired TNA 203 multi-provider resolver has been removed. Future approved
dynamic sources can be added independently without blocking playlist refresh.
"""
from pathlib import Path


def main():
    Path("dynamic_sources.m3u").write_text("#EXTM3U\n", encoding="utf-8")
    print("No approved dynamic sources; wrote empty dynamic_sources.m3u")


if __name__ == "__main__":
    main()
