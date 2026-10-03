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

## Add or remove channels

1. Edit `selected_channels.txt`.
2. Add or remove channel names, one per line.
3. Open **Actions → Refresh playlist → Run workflow**.
4. Start a fresh run from the current `main` branch.
5. When it finishes, `playlist.m3u` and `curated.xml` are refreshed.

Do not use **Re-run jobs** on an old Action run.

## Check what was imported

Open the **Build playlist** step in the Action log.

It reports:

- `ADDED: channel name`
- `NOT FOUND: channel name`

## TVirl note

When new channels are added after the original TVirl setup, Android TV may leave those new channels disabled by default.

If a new channel scans but does not appear in the guide, enable it in the TV/Live Channels channel settings.

## XMLTV note

`curated.xml` currently defines the curated channels and preserves any logo supplied by the source. It does not yet contain programme listings.

We can enrich it later without changing the M3U import process.

## Basic rule

**Choose channels → run one fresh workflow → enable newly added channels in the TV guide if required.**
