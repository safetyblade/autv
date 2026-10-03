# AU TV

A small personal playlist aggregator for Android TV.

## Production playlist

Use this URL in the TV app:

`https://raw.githubusercontent.com/safetyblade/autv/main/playlist.m3u`

Only channels that have been manually approved are included in the production playlist.

## Test playlist

`https://raw.githubusercontent.com/safetyblade/autv/main/test-playlist.m3u`

New channels are staged here first. A channel is moved to production only after it has been confirmed to start and play correctly on the target TV.

## Files

- `selected_channels.txt` — approved extra channels.
- `candidate_channels.txt` — channels currently being tested.
- `custom.m3u` — approved direct or resolver-backed entries.
- `playlist.m3u` — generated production output.
- `test-playlist.m3u` — generated output including candidates.

## Refresh

The repository refreshes automatically once per day. A manual refresh can also be run from **Actions → Refresh playlists → Run workflow**.

If a refresh fails, the last committed production playlist remains available.

## Channel workflow

1. Add a channel to `candidate_channels.txt`.
2. Refresh the playlists.
3. Test it using `test-playlist.m3u`.
4. Only after it starts and plays reliably, move its name to `selected_channels.txt`.
5. Refresh again to publish it into `playlist.m3u`.

This keeps an unverified or incompatible stream from breaking the main TV guide.
