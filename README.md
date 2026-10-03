# AU TV

A small personal playlist aggregator for Android TV.

## Playlist URL

`https://raw.githubusercontent.com/safetyblade/autv/main/playlist.m3u`

## How it works

The generated playlist keeps the main channel list intact and appends manually selected channels under:

`# ---- Curated extras ----`

## Add a channel

1. Test the direct stream URL manually first.
2. Add the channel name to `selected_channels.txt`.
3. Run **Actions → Refresh playlist → Run workflow**.
4. The refreshed `playlist.m3u` is published automatically.

Direct or resolver-backed entries can be placed in `custom.m3u`.

If a selected channel cannot be found, the refresh fails and the existing playlist stays unchanged.
