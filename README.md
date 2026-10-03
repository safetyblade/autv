# AU TV

A personal FAST/IPTV playlist for Android/Google TV, built around a stable Australian base plus manually curated channels from additional FAST services.

## Published playlist

Use this M3U in TVirl:

`https://raw.githubusercontent.com/safetyblade/autv/main/playlist.m3u`

Combined EPG:

`https://raw.githubusercontent.com/safetyblade/autv/main/epg.xml.gz`

The M3U already points to the combined EPG.

## Project model

The project has two separate layers:

1. **Source acquisition** — Plex AU plus selected channels from Samsung, LG, Pluto, Roku, Xumo, Tubi and custom direct feeds.
2. **Guide control** — `channel_guide.csv` owns the final channel number, genre, display order and project metadata.

Upstream source category/order/channel numbers are not authoritative.

Every refresh:

1. downloads the current source playlists and EPG files;
2. builds the working playlist from the selected-channel files;
3. applies `channel_guide.csv` to rewrite `tvg-chno`, `group-title` and display order;
4. builds the filtered combined EPG;
5. publishes the generated files.

The physical order of `playlist.m3u` is also sorted by our channel number so the order remains useful even if a TV app does not display `tvg-chno` consistently.

## Channel-number blocks

| Range | Genre |
| --- | --- |
| 100–199 | News & Business |
| 200–299 | Sport |
| 300–399 | Movies |
| 400–499 | Game Shows |
| 500–599 | Crime & Mystery |
| 600–699 | Comedy |
| 700–799 | Drama |
| 800–899 | Reality |
| 900–999 | Factual & Documentary |
| 1000–1099 | Lifestyle & Food |
| 1100–1199 | Kids & Animation |
| 1200–1299 | Music |
| 1300–1399 | Sci-Fi & Horror |
| 1400–1449 | Westerns |
| 1450–1499 | Classic TV |
| 1600+ | General Entertainment |

See `CHANNEL_GUIDE.md` for the detailed guide rules and maintenance model.

## Source stack

### Base
- Plex AU — retained as the main starting catalogue.

### Curated sources
- Samsung TV Plus
- LG Channels
- Pluto TV
- Roku Channel
- Xumo Play
- Tubi
- Custom direct feeds

Each source is only useful when it adds something genuinely worthwhile or provides a better working alternate stream.

Tubi remains intentionally wired into the project even with a small selected set because it has already proven useful as an alternate-source/rescue option for channels such as Watch AEW.

## Selection files

- `selected_channels.txt` — Samsung
- `selected_lg_channels.txt` — LG
- `selected_pluto_channels.txt` — Pluto
- `selected_roku_channels.txt` — Roku
- `selected_xumo_channels.txt` — Xumo
- `selected_tubi_channels.txt` — Tubi
- `custom.m3u` — direct/manual feeds

## Channel guide

`channel_guide.csv` is the master metadata file.

Current fields:

- **Channel number** — our final channel number and sort order.
- **Channel name** — final display name.
- **Genre** — our canonical genre, not the source service's genre.
- **EPG** — whether useful programme-guide data is available.
- **Source** — primary working source.
- **Alternate source** — known usable alternate when relevant.
- **Status** — normally Active for channels intended to remain in the build.
- **Description** — plain-English summary of what kind of channel it is.

The guide is intended to describe the actual working channel lineup, not record every failed source attempt.

## EPG

The combined EPG is filtered to channels that exist in the final playlist.

Current programme-guide inputs include:
- Plex AU
- Pluto US / UK / Canada
- Roku
- Xumo
- Tubi

`curated.xml` remains a lightweight fallback channel-definition file for curated channels.

## Refresh workflow

Always start a **fresh** run:

**Actions → Refresh playlist → Run workflow**

Do not use **Re-run jobs** on an old Action run.

After the run:
1. rescan/refresh TVirl;
2. Android TV may leave newly added channels disabled by default;
3. enable new channels in the Live Channels/TV channel settings if they imported but are hidden;
4. remove channels that repeatedly fail playback.

## Import log

The **Build playlist** step reports entries such as:

- `ADDED SAMSUNG: ...`
- `ADDED LG: ...`
- `ADDED PLUTO: ...`
- `ADDED ROKU: ...`
- `ADDED XUMO: ...`
- `ADDED TUBI: ...`
- `NOT FOUND ...`

`ADDED` means the current source contained the channel. It does not by itself prove the stream plays from Australia.

## Curation rules

- Prefer genuinely additive channels over raw catalogue size.
- Movies, game shows and sports are high-value lanes.
- Recognisable binge/franchise FAST channels are useful.
- News/weather/local duplication is low value.
- Non-English feeds are normally excluded.
- Likely duplicates are treated as duplicates.
- Previously rejected low-value channels remain low value unless there is a clear reason to revisit them.
- Failed source attempts do not block trying the same channel from another provider.
- Alternate sources are useful when a preferred feed fails.
- Tubi, Xumo, Roku, Pluto, Samsung and LG can all be revisited for targeted searches.

## Core rule

**Discover → compare → test → keep only what works → assign our genre/number → refresh.**
