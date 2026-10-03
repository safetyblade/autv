# Channel Guide

This document defines the channel-number, genre, subgenre and ordering model for AU TV.

The authoritative data lives in `channel_guide.csv`. The generated playlist is rebuilt from that guide, and the guide is also intended to become the data source for the old-school TV guide presentation.

## Canonical channel blocks

| Range | Genre | Purpose |
| --- | --- | --- |
| 100–199 | News | Australian/local news, international news, business and weather |
| 200–299 | Sport | Wrestling, football, basketball, major sport, motorsport and specialist sport |
| 300–399 | Movies | General movies plus movie genre channels |
| 400–499 | Game Shows | Traditional game shows and competition formats |
| 500–599 | Crime | True crime, police, court, forensic and crime drama |
| 600–699 | Comedy | Sitcoms, sketch, panel, stand-up and alternative comedy |
| 700–999 | Entertainment | Scripted drama, sci-fi, classic TV, broad entertainment and variety |
| 1000–1099 | Reality & Lifestyle | Unscripted, food, home, travel, pets and reality |
| 1100–1199 | Factual | Documentary, nature, science, survival, paranormal and history |
| 1200–1299 | Kids & Animation | Preschool, family animation, anime and youth entertainment |
| 1300–1399 | Music | Pop, rock, urban, jazz, classical, concerts and ambient music |

## Authoritative fields

Every active row in `channel_guide.csv` contains:

- **Channel number** — final TV channel number and playlist order.
- **Channel name** — final display name.
- **Genre** — top-level TV guide section.
- **Subgenre** — editorial ordering lane within the genre.
- **EPG** — expected schedule coverage: normally `Full` or `Channel only`.
- **Source** — primary working source for maintenance.
- **Alternate source** — working fallback where one is useful.
- **Status** — whether the channel is part of the active lineup.
- **Description** — concise guide description.

The CSV is the single source of truth for the final lineup.

## Current ordering model

Ordering is deliberately **human-designed**, not alphabetical.

### News
1. Australian / Local
2. International
3. Business / Finance
4. Weather

### Sport
1. Wrestling & Combat
2. Football
3. Basketball
4. US Major Sports
5. Motorsport
6. Racquet & Golf
7. Cue, Darts & Poker
8. Outdoor & Surf
9. Niche & Competition
10. General Sports

### Movies
1. Major / Broad Movies
2. Action & Martial Arts
3. Horror / Thriller / Cult
4. Romance & Lifetime
5. Classics / British / Arthouse
6. Black Cinema
7. Westerns
8. Specialty / Niche

### Game Shows
1. Major / Classic Game Shows
2. Physical / Competition
3. General Game Show Network
4. Competition / Reality

### Crime
1. True Crime / Investigations
2. Police / Jail / Live Enforcement
3. Court / Legal
4. Forensic / Medical
5. Crime Drama / Mystery

### Comedy
1. Major / Modern Sitcoms
2. Sketch / Panel / Variety
3. Stand-up / Broad Comedy Networks
4. Alternative / Visual / Creator Comedy
5. Classic Sitcoms

### Entertainment
1. Drama & Action Series
2. Sci-Fi / Fantasy / Cult
3. Classic TV & Western Series
4. Broad Entertainment Networks
5. Creator / Pop Culture / Variety
6. Specialty Entertainment

### Reality & Lifestyle
1. People / Work / Unscripted
2. Food & Cooking
3. Home / Property / Garden
4. Travel / Lifestyle
5. Animals / Pets
6. Reality / Relationships & Celebrity

The grounded unscripted section is intentionally first. Relationship/celebrity reality is deliberately placed last.

### Factual
1. Documentary / General Factual
2. Nature / Wildlife
3. Science / Engineering / Discovery
4. Survival / Disaster / Real World
5. Paranormal / Unexplained
6. History / War

History is intentionally placed last in this section.

### Kids & Animation
1. Preschool / Young Kids
2. Kids / Family Animation
3. Anime
4. Teen / Family Live Action
5. Youth Creator / Gaming

Anime remains inside Kids & Animation rather than becoming another top-level genre.

### Music
1. Hits / Pop / Decades
2. Rock / Metal
3. Hip-Hop / R&B / Soul
4. Jazz / Classical / Country
5. Concerts / Specialty
6. Chill / Ambient

## Genre policy

The project intentionally uses a small number of top-level genres.

Subgenres exist for ordering and guide presentation; they are not extra top-level categories.

If an upstream catalogue classifies a channel differently, `channel_guide.csv` wins.

## Language policy

The normal lineup is English-language.

Non-English channels are removed during curation unless there is an explicit reason to retain them. Sport is the main exception: multilingual football channels can remain when they provide genuinely useful coverage.

## Source policy

The guide describes the working logical channel.

Where multiple feeds exist:

- keep one primary source;
- record a useful alternate when appropriate;
- avoid duplicate logical channels;
- remove failed feeds from the final working guide.

Source details remain in `channel_guide.csv` for maintenance, but source acquisition is not part of the user-facing guide design.

## EPG policy

`Full` means programme-guide data is expected from the combined EPG system.

`Channel only` means the channel is intentionally retained even though a reliable schedule is not currently available.

EPG generation first matches exact IDs and may use a conservative unique-name fallback for curated/renamed channels.

The build reports remaining EPG misses so coverage can be improved without guessing.

## Refresh behaviour

The build:

1. obtains current approved inputs;
2. builds the selected working playlist;
3. applies `channel_guide.csv`;
4. drops channels that are not in the guide;
5. writes the final names, genres and channel numbers;
6. physically sorts the M3U by final channel number;
7. builds the filtered combined EPG;
8. publishes the generated files.

## Curation standard

A channel belongs in the final guide only when it adds useful programming and the feed is sufficiently reliable.

The guide is not a history of testing. Failed or rejected channels are removed.

This keeps `channel_guide.csv` clean enough to serve both the IPTV build and the next-generation old-school TV guide UI.
