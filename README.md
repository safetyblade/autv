# AU TV

A simple personal M3U playlist for Android TV.

## Playlist URL

Use this URL in the TV app:

`https://raw.githubusercontent.com/safetyblade/autv/main/playlist.m3u`

## EPG

The refresh now generates a combined filtered programme guide:

`https://raw.githubusercontent.com/safetyblade/autv/main/epg.xml.gz`

The published M3U points to this file automatically.

It currently merges programme data for:
- Plex AU
- future selected Plex US / UK / Canada / NZ channels
- selected Pluto US / UK / Canada channels

Only channels present in the final playlist are retained in the generated EPG.

`curated.xml` is still generated as a lightweight fallback channel-definition file for curated channels that do not yet have a programme-guide source.

## How it works

The refresh keeps the main channel list intact and appends selected extra channels from the supported secondary sources under:

`# ---- Curated extras ----`

Selected Samsung-style channels are kept in `selected_channels.txt`.

Selected LG Australia channels are kept in `selected_lg_channels.txt`.

Selected global Pluto channels are kept in `selected_pluto_channels.txt`. The refresh checks the current US, Canada and UK Pluto catalogues and keeps only the selected channels.

It also creates a matching XMLTV channel definition for each curated extra.

## What we have learned

- A channel appearing in a source playlist does **not** guarantee that its stream still plays.
- Some external FAST lists contain stale, geo-restricted or changed stream URLs.
- TVirl may successfully import a newly added channel but Android TV can leave that channel disabled by default.
- If a channel scans but does not appear in the guide, first check the TV/Live Channels channel settings and enable it.
- XMLTV is **not** required for a channel to exist or appear.
- Plex and Pluto programme data are now merged into the generated `epg.xml.gz`.
- Samsung Australia and LG curated channels may still have channel/logo metadata without programme listings where no reliable matching XMLTV source is available.
- New sources should be treated as discovery feeds first. Channels should only remain in the curated list after they have been confirmed to play.

## Add or remove channels

1. Add or remove channel names in the relevant selected list.
2. Open **Actions → Refresh playlist → Run workflow**.
3. Start a fresh run from the current `main` branch.
4. When it finishes, `playlist.m3u` and `curated.xml` are refreshed.
5. Rescan TVirl and enable newly added channels in Android TV if required.
6. Remove any channel that imports but repeatedly fails on playback.

Do not use **Re-run jobs** on an old Action run.

## Check what was imported

Open the **Build playlist** step in the Action log.

It reports:

- `ADDED SAMSUNG: channel name`
- `NOT FOUND SAMSUNG: channel name`
- `ADDED LG: channel name`
- `NOT FOUND LG: channel name`
- `ADDED PLUTO: channel name`
- `NOT FOUND PLUTO: channel name`

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
- global Pluto channels are eligible when they are useful, English-language, genuinely additive and actually play from Australia
- music is kept only when it adds a genre or format not already well covered by the existing Stingray lineup

## Basic rule

**Discover → test → add → refresh → enable → remove anything that does not play.**
