# AU TV — Plex + FAST extras for TVirl

This repository produces **one merged M3U playlist** for TVirl Free.

It keeps the working **Plex Australia** playlist as the base, preserving its channel names, logos, stream URLs, groups and EPG metadata, then appends only the extra FAST channels we want.

## TVirl playlist URL

Use this URL in TVirl:

`https://raw.githubusercontent.com/safetyblade/autv/main/playlist.m3u`

TVirl only needs this one source.

## What is included

- Plex Australia — fetched from the maintained upstream playlist.
- Custom FAST extras — stored in `custom.m3u`.
- Automatic daily refresh through GitHub Actions.
- Manual refresh from the Actions tab.
- Fail-closed validation so a broken upstream response does not replace a good playlist.

## Planned extras

The custom list starts deliberately small:

- **Don't Tell The Bride** — add once its current Australian FAST stream and metadata are confirmed.
- **WWE Live** — add once a stable resolver for the official YouTube live feed available in Australia is in place.

## How it works

1. GitHub Actions downloads the current Plex AU M3U.
2. `scripts/merge.py` validates the upstream playlist.
3. Plex entries are retained unchanged.
4. Entries from `custom.m3u` are appended.
5. The result is written to `playlist.m3u`.
6. GitHub commits the file only when it has actually changed.

If GitHub Actions stops or a refresh fails, the last successfully generated `playlist.m3u` remains committed and available to TVirl.

## Adding custom channels

Add normal M3U entries to `custom.m3u` beneath its `#EXTM3U` header.

```m3u
#EXTINF:-1 tvg-id="example" tvg-name="Example" tvg-logo="https://example.com/logo.png" group-title="FAST Extras",Example
https://example.com/live.m3u8
```

Use proper `tvg-id`, `tvg-name`, `tvg-logo` and `group-title` values where available so channels present cleanly in Live TV.

## Refresh manually

Open **Actions → Refresh merged playlist → Run workflow**.

The scheduled refresh runs once per day and commits only when the resulting playlist changes.
