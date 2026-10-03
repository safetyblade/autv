# AU TV

A simple personal M3U playlist for Android TV.

The repository keeps the main channel list up to date and adds a small number of extra channels that have been manually selected.

## Playlist URL

Use this URL in the TV app:

`https://raw.githubusercontent.com/safetyblade/autv/main/playlist.m3u`

This is the only playlist URL you need.

## Current setup

`playlist.m3u` contains:

- the main channel list
- selected extra channels
- any manually added direct streams

Extra channels are added at the bottom of the generated playlist under:

`# ---- Curated extras ----`

## Add a channel from the secondary channel list

Use this process:

1. Find the direct stream URL for the channel.
2. Test the `.m3u8` URL manually in a browser/HLS player.
3. Only continue if the stream actually plays.
4. Edit `selected_channels.txt`.
5. Add the channel name on a new line.
6. Open **Actions** in GitHub.
7. Open **Refresh playlist**.
8. Select **Run workflow**.
9. Wait for the workflow to complete successfully.
10. The existing playlist URL will automatically contain the refreshed list.

Do **not** use **Re-run jobs** on an old workflow run. Always start a new **Run workflow** from the current `main` branch.

## selected_channels.txt

This file is for channels that already exist in the secondary source list.

Example:

```text
Don't Tell The Bride
Another Channel
```

The refresh script finds the matching channel and adds it to the curated section of `playlist.m3u`.

## custom.m3u

Use `custom.m3u` only when a channel does not come from the normal secondary list and you already have a working direct or resolver-backed M3U entry.

Normal channel selection should use `selected_channels.txt`.

## Refreshing the playlist

A refresh is **manual only** to avoid unnecessary GitHub Actions usage.

Run it when:

- you add or remove a curated channel
- a channel source needs refreshing
- you deliberately want to update the main channel list

Path:

**Actions → Refresh playlist → Run workflow**

If nothing has changed, the workflow does not create a new playlist commit.

If the refresh fails, the existing published `playlist.m3u` remains unchanged.

## Remove a curated channel

1. Delete its name from `selected_channels.txt`.
2. Run a new **Refresh playlist** workflow.
3. The channel will be removed from the curated section.

## Basic rule

Keep this project simple:

**Test the stream first → update the channel list → run the workflow once.**
