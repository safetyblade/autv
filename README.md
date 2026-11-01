# AU TV

A personal FAST/IPTV lineup designed to behave like a traditional television service rather than a raw streaming catalogue.

The project curates a fixed channel lineup, assigns human-designed channel numbers and genres, combines available programme-guide data, and publishes a single M3U + EPG for TV apps.

## AU TV app

The household's primary viewing path is now the native **AU TV app v0.4.0** for Android TV / Google TV. It consumes the published project guide and playlist data, supports remote-first guide navigation and direct tuning, and keeps the existing published M3U + EPG available for legacy TV-input setups.

See `TV_GUIDE.md` for the household quick-start guide and complete numbered channel directory.

## Published files

Playlist:

`https://raw.githubusercontent.com/safetyblade/autv/main/playlist.m3u`

Combined EPG:

`https://raw.githubusercontent.com/safetyblade/autv/main/epg.xml.gz`

The playlist already points to the combined EPG.

## How the project works

The system has three layers:

1. **Acquisition** — approved channels are pulled from a set of supported FAST/catalogue inputs plus a small number of curated direct feeds.
2. **Guide control** — `channel_guide.csv` is the authoritative record of what belongs in AU TV and how it should appear.
3. **Generation** — the refresh workflow builds the playlist, applies the guide, combines available EPG data and publishes the generated files.

The acquisition implementation is intentionally kept separate from the user-facing guide. Upstream numbering, ordering and genre labels are never authoritative.

## Channel guide

`channel_guide.csv` is the master control file and the basis for all future guide presentation.

It contains:

- **Channel number** — final channel number and physical playlist order.
- **Channel name** — final user-facing display name.
- **Genre** — one of the canonical top-level guide genres.
- **Subgenre** — internal ordering lane within that genre.
- **EPG** — whether programme-guide coverage is expected.
- **Source** — primary working source used for maintenance.
- **Alternate source** — known working fallback where useful.
- **Status** — whether the channel belongs in the active lineup.
- **Description** — concise explanation of the channel.

The guide represents the **working lineup only**. Failed tests and rejected channels are not retained as guide rows.

## Current lineup

The current guide contains **643 active channels**. Channel numbers are intentionally grouped into the canonical genre ranges below. A refresh may temporarily publish fewer channels when an upstream feed is unavailable; that does not change the guide's intended lineup.

## Channel-number blocks

| Range | Genre |
| --- | --- |
| 100–199 | News |
| 200–299 | Sport |
| 300–399 | Movies |
| 400–499 | Game Shows |
| 500–599 | Crime |
| 600–699 | Comedy |
| 700–999 | Entertainment |
| 1000–1099 | Reality & Lifestyle |
| 1100–1199 | Factual |
| 1200–1299 | Kids & Animation |
| 1300–1399 | Music |

Each genre is then ordered by approved subgenres and editorial priority rather than alphabetically.

See `CHANNEL_GUIDE.md` for the detailed taxonomy and ordering model.

## Channel changes

Research and source investigation are intentionally kept outside this repository. The repository records only the active lineup, approved source configuration and generated outputs.

## Playlist generation

A refresh performs the following steps:

1. obtain the current versions of the approved channel inputs;
2. select only approved channels;
3. merge the working streams;
4. apply `channel_guide.csv`;
5. remove anything not present in the guide;
6. write final channel numbers, names and genres;
7. physically sort the M3U by channel number;
8. combine available EPG data;
9. publish the generated playlist and EPG.

This means the generated playlist cannot grow simply because an upstream catalogue grows.

## EPG

The combined EPG is filtered to channels in the final playlist.

EPG matching prefers the channel's exact provider ID. Where a curated or renamed channel uses a project ID, the builder can use a conservative unique-name match to recover programme data without changing the final channel identity.

The EPG build reports:

- exact-ID matches;
- safe name-fallback matches;
- channels with programme data;
- channels still missing programme data.

Some channels are intentionally marked **Channel only** where a useful schedule is not available.

## Weekly FAST source discovery and retirement review

Perform a weekly **read-only** review of legal public FAST catalogues and provider-authorised M3U/HLS sources: Plex, Pluto, Samsung TV Plus, LG Channels, Roku, Tubi, Xumo, Rakuten, DistroTV, IPTV-org and specialist distributors. Prioritise English-language wrestling and sport, classic animation, anime, comedy, cult/sci-fi, game shows and movies; exclude explicit adult feeds.

Reconcile each lead against `channel_guide.csv`, provider selection files, existing curated entries, known failed streams and previously rejected channels. Record the channel, genre, provider, existing/duplicate status, catalogue evidence, stream evidence, Australian availability and confidence. Classify **Recommend / Investigate / Reject**. Distinguish an official channel listing, discovered URL, valid HLS manifest, downloaded non-empty media segment and actual Australian-device playback; none implies the next. Record geo restrictions, DRM, authentication, token expiry and advertising/session requirements.

**Retirement rule:** A provider delisting is a reason to investigate or retire that *provider path*, not proof every alternative is dead. Remove a channel from the active guide only when the existing source is confirmed withdrawn/unusable or the editorial decision is to retire it; preserve its number for a replacement. Do not silently renumber other channels.

**Backup rule:** Newly discovered alternates may be added to provider acquisition selections for discovery, but are not automatically trusted or promoted. Keep an established working primary first; switch only after HLS segment and device playback checks, ideally in Australia. A selection alone is not a tested fallback. In particular, #201 CW Presents WWE NXT retains its curated primary; Pluto US is discovery-only. #819 Penn & Teller retains Roku primary; Pluto US is discovery-only.

**Children's-channel regression check:** Track #1214 SpongeBob SquarePants, #1215 Pokémon and #1216 LEGO Kids TV individually, including tune/start time, buffering, stalling, segment continuity, token expiry and Australian-device behaviour. #1215 was reported fixed on 1 November 2026; do not regress or change its source without evidence. Investigate the others independently before replacing them.

The weekly discovery report is advisory. Do not edit the repository, import feeds, create PRs or modify live playlists *as part of the scan*. Approved follow-up maintenance is a separate change, reviewed against these rules.

## Refresh workflow

Start a fresh run from:

**Actions → Refresh playlist → Run workflow**

The refresh workflow rebuilds the published playlist and EPG from the current guide and approved channel selections.

After refreshing:

1. refresh/rescan the playlist in the TV app;
2. confirm newly added channels are enabled;
3. test any new or changed feeds;
4. remove channels that do not reliably deliver the intended service.

## Curation rules

- The final lineup is designed for use, not catalogue size.
- The taxonomy stays deliberately simple.
- Internal subgenres create a sensible old-school TV order inside each genre.
- Australian/local content is prioritised where useful.
- Non-English channels are generally excluded, except where specifically approved for Sport.
- A logical channel should appear only once unless two feeds are genuinely different services; known duplicates are consolidated into the stronger single listing.
- Failed channels are removed from the working guide.
- Source information is maintenance metadata; the viewing experience is driven by the final guide.
- The guide, not any upstream catalogue, is authoritative.

## Core rule

**Discover → compare → test → keep only what works → place it in the guide → refresh.**
