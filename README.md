# AU TV

A simple personal M3U playlist for Android TV.

## Playlist URL

Use this URL in the TV app:

`https://raw.githubusercontent.com/safetyblade/autv/main/playlist.m3u`

This is the only generated feed used by the project.

## How it works

The refresh keeps the main channel list intact and appends any selected extra channels it can find under:

`# ---- Curated extras ----`

Channels that are not available in the secondary source are skipped and reported in the Action log. They do not cause the whole refresh to fail.

## Add or remove channels

1. Test the direct stream manually first if you have one.
2. Edit `selected_channels.txt`.
3. Add or remove channel names, one per line.
4. Open **Actions → Refresh playlist → Run workflow**.
5. Start a fresh run from the current `main` branch.
6. When it finishes, the existing playlist URL contains the refreshed list.

Do not use **Re-run jobs** on an old Action run.

## Check what was imported

Open the **Build playlist** step in the Action log.

It reports each requested channel as:

- `ADDED: channel name`
- `NOT FOUND: channel name`

At the end it gives the total added and missing counts.

## custom.m3u

Use `custom.m3u` only for a direct or resolver-backed channel that does not come from the normal secondary list.

## TVirl note

When new channels are added after the original TVirl setup, Android TV may leave those new channels disabled by default.

If a new channel scans but does not appear in the guide, enable it in the TV/Live Channels channel settings.

## Basic rule

**Choose channels → run one fresh workflow → enable newly added channels in the TV guide if required.**
