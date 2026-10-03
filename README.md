# AU TV

A simple personal M3U playlist for Android TV.

## Playlist URL

Use this URL in the TV app:

`https://raw.githubusercontent.com/safetyblade/autv/main/playlist.m3u`

## Optional curated XMLTV URL

The refresh also generates:

`https://raw.githubusercontent.com/safetyblade/autv/main/curated.xml`

This XMLTV file is optional. It is not required for channel exposure, but it gives curated channels stable XMLTV IDs and can be used later for guide data, logos and other metadata.

## How it works

The refresh keeps the main channel list intact and appends selected extra channels under:

`# ---- Curated extras ----`

It also creates a matching XMLTV channel definition for each curated extra.

## What we have learned

- A channel appearing in a source playlist does **not** guarantee that its stream still plays.
- Some external FAST lists contain stale, geo-restricted or changed stream URLs.
- TVirl may successfully import a newly added channel but Android TV can leave that channel disabled by default.
- If a channel scans but does not appear in the guide, first check the TV/Live Channels channel settings and enable it.
- XMLTV is **not** required for a channel to exist or appear. It is an optional enrichment layer for guide data, logos and metadata.
- New sources should be treated as discovery feeds first. Channels should only remain in the curated list after they have been confirmed to play.

## Add or remove channels

1. Find or select a candidate channel.
2. Test the stream manually where practical.
3. Edit `selected_channels.txt`.
4. Add or remove channel names, one per line.
5. Open **Actions → Refresh playlist → Run workflow**.
6. Start a fresh run from the current `main` branch.
7. When it finishes, `playlist.m3u` and `curated.xml` are refreshed.
8. Rescan TVirl and enable newly added channels in Android TV if required.
9. Remove any channel that imports but repeatedly fails on playback.

Do not use **Re-run jobs** on an old Action run.

## Check what was imported

Open the **Build playlist** step in the Action log.

It reports:

- `ADDED: channel name`
- `NOT FOUND: channel name`

`ADDED` means the channel was found in the source and written to the playlist. It does **not** prove the stream is currently playable.

## TVirl note

When new channels are added after the original TVirl setup, Android TV may leave those new channels disabled by default.

If a new channel scans but does not appear in the guide, enable it in the TV/Live Channels channel settings.

## XMLTV note

`curated.xml` currently defines the curated channels and preserves any logo supplied by the source. It does not yet contain programme listings.

Guide enrichment can be added later without changing the M3U import process.

## Source strategy

Keep the project simple:

- one published M3U playlist
- one optional curated XMLTV file
- source playlists used only to discover and populate extra channels
- manually remove stale or non-playing streams
- add new source feeds only when they provide genuinely useful channels not already present

## Basic rule

**Discover → test → add → refresh → enable → remove anything that does not play.**
