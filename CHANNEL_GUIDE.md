# Channel Guide

This document explains the numbering, genre and ordering model used by AU TV.

The authoritative data lives in `channel_guide.csv`. The generated `playlist.m3u` is rewritten from that guide on every refresh.

## Current channel order

| Range | Genre | Purpose |
| --- | --- | --- |
| 100–199 | News | News, business, current affairs and weather |
| 200–299 | Sport | All sport, wrestling, combat and motorsport |
| 300–399 | Movies | All movie channels and movie genre feeds |
| 400–499 | Game Shows | Game shows and competition formats |
| 500–599 | Crime | True crime, court, investigation and forensic |
| 600–699 | Comedy | Sitcoms, sketch and stand-up |
| 700–999 | Entertainment | Drama, classic TV, sci-fi, western series and broad entertainment |
| 1000–1099 | Reality & Lifestyle | Reality, food, home, garden, travel and renovation |
| 1100–1199 | Factual | Documentary, history, science and nature |
| 1200–1299 | Kids & Animation | Kids, cartoons and anime |
| 1300–1399 | Music | Music channels |

Numbers are intentionally separated into blocks so individual genres can grow without forcing a full renumber every time a channel is added.

## Authoritative fields

`channel_guide.csv` contains:

- **Channel number** — controls both `tvg-chno` and physical M3U order.
- **Channel name** — final user-facing name.
- **Genre** — final `group-title` written into the M3U.
- **EPG** — guide coverage status.
- **Source** — primary working provider.
- **Alternate source** — known useful alternative.
- **Status** — whether the channel belongs in the working guide.
- **Description** — a plain-English explanation of what type of channel it is.

## Genre policy

The project deliberately uses a small taxonomy. The final genres are:

- News
- Sport
- Movies
- Game Shows
- Crime
- Comedy
- Entertainment
- Reality & Lifestyle
- Factual
- Kids & Animation
- Music

The point is practical ordering, not perfect editorial taxonomy.

Examples:
- wrestling, combat sports, motorsport and poker → **Sport**
- movie westerns, horror movies and action movies → **Movies**
- true crime, court and forensic channels → **Crime**
- drama, classic TV, sci-fi series and western series → **Entertainment**
- reality, food, home, garden, travel and renovation → **Reality & Lifestyle**
- documentary, history, science and nature → **Factual**
- kids, cartoons and anime → **Kids & Animation**

If a source misclassifies a channel, the guide wins.

## Ordering policy

Within each genre block, the current ordering is alphabetical as a neutral baseline.

The next refinement phase can replace that with a more useful human order, for example:

1. strongest/most recognisable channels first;
2. dedicated franchise/binge channels next;
3. broad genre channels after that;
4. niche/long-tail channels last.

Because the guide is authoritative, reordering requires only changing channel numbers in `channel_guide.csv`; source files do not need to be touched.

## Channel descriptions

Every row in `channel_guide.csv` now includes a **Description** field.

Descriptions are intentionally concise and are used to answer a practical question: **what kind of channel is this?**

Examples:

- **Watch AEW** — AEW wrestling FAST channel, currently sourced from Tubi.
- **TNA Wrestling Channel** — 24/7 TNA wrestling channel with matches and archive programming.
- **Obsessive Compulsive Cleaners** — British factual-reality series channel focused on extreme cleaning and household transformations.
- **Gordon Ramsay** — Gordon Ramsay-focused FAST channel featuring food, competition and reality programming.
- **Monster Jam** — Monster Jam motorsport channel featuring monster-truck events and related programming.

For generic channels, descriptions identify the programming lane, for example movie-focused, rolling news, crime/investigation, documentary/factual, lifestyle/home/food, kids/animation or general entertainment.

These descriptions can be tightened over time as we refine the guide and ordering.

## Source policy

The guide describes the **working channel**, not every provider attempt.

If the same logical channel exists from multiple providers:
- keep one primary source;
- record a useful alternate source where appropriate;
- do not create duplicate logical guide rows unless they are genuinely different channels.

A failed provider attempt does not make the logical channel itself invalid. It can still be tested from another source later.

## Tubi

Tubi remains a supported source even with a small selected list.

Reason: its direct HLS path has already succeeded where another provider failed, making it useful for future targeted searches and alternate-source recovery.

## Refresh behaviour

The build order is:

1. download source catalogues;
2. build the selected working playlist;
3. apply `channel_guide.csv`;
4. write our channel numbers and genres into the M3U;
5. physically sort the M3U by our channel number;
6. build the combined filtered EPG;
7. publish.

This means future ordering work happens in the guide, not in upstream source lists.
## End-of-QA targeted playback test

Park these newly added Sport channels for one final playback check after EPG and genre QA are complete:

- New Japan Pro Wrestling World
- Brøndby TV
- BVB-Frauen
- Canal do Inter
- Hoop TV

This is a temporary QA note only. The final working guide remains limited to channels that pass playback testing.

