# AU TV — Plex + curated FAST extras for TVirl

This repository produces **one merged M3U playlist** for TVirl Free.

It keeps the working **Plex Australia** playlist as the base, then selectively pulls missing channels from other FAST sources. The original channel entries are kept intact, including their logos, names, groups, stream URLs and available EPG metadata.

## TVirl playlist URL

`https://raw.githubusercontent.com/safetyblade/autv/main/playlist.m3u`

TVirl only needs this one source.

## Current sources

- **Plex Australia** — included in full.
- **Samsung TV Plus Australia** — only channels named in `selected_channels.txt` are included.
- **custom.m3u** — for individual direct streams or resolver-backed channels that do not come from a normal FAST playlist.

## Current selected extras

`selected_channels.txt` currently includes:

- Don't Tell The Bride

The refresh job finds that channel in the Samsung AU source and appends its original M3U entry to Plex.

## Add another missing channel

Edit `selected_channels.txt` and put one channel name on each line.

Example:

```text
Don't Tell The Bride
Another Channel
```

Matching is case-insensitive. Exact matches are preferred; a partial name match is used as a fallback.

If a requested channel cannot be found, the workflow **fails without replacing the existing playlist**. This prevents a temporarily broken source from silently removing channels.

## How the refresh works

1. Download the current Plex AU playlist.
2. Download the current Samsung TV Plus AU playlist.
3. Preserve the Plex playlist unchanged.
4. Find each channel listed in `selected_channels.txt`.
5. Copy the matching FAST channel entry intact, including its metadata/logo where supplied upstream.
6. Append any entries in `custom.m3u`.
7. Validate the finished playlist.
8. Commit `playlist.m3u` only if it changed.

The scheduled refresh runs once daily. If a refresh fails, the last successfully generated playlist remains available to TVirl.

## Manual refresh

Open **Actions → Refresh merged playlist → Run workflow**.

## WWE Live

WWE's Australian YouTube live feed is different from a normal FAST channel because YouTube's underlying media URL changes. It belongs in `custom.m3u` once we have a stable resolver URL. That lets it sit in the same final playlist without affecting Plex or the FAST-channel selection system.
