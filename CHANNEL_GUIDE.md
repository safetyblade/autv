# Channel Guide

This document explains the numbering, genre and ordering model used by AU TV.

The authoritative data lives in `channel_guide.csv`. The generated `playlist.m3u` is rewritten from that guide on every refresh.

## Current channel order

| Range | Genre | Purpose |
| --- | --- | --- |
| 100–199 | News & Business | News, business, current affairs and weather |
| 200–299 | Sport | Sport, combat sports and motorsport |
| 300–399 | Movies | Movie channels and movie genre feeds |
| 400–499 | Game Shows | Game shows and competition channels |
| 500–599 | Crime & Mystery | Crime, court, investigation and mystery |
| 600–699 | Comedy | Sitcoms, comedy and stand-up |
| 700–799 | Drama | Scripted drama and action series |
| 800–899 | Reality | Reality and unscripted entertainment |
| 900–999 | Factual & Documentary | Documentary, factual, history and science |
| 1000–1099 | Lifestyle & Food | Food, home, garden, travel and renovation |
| 1100–1199 | Kids & Animation | Kids, family and animation |
| 1200–1299 | Music | Music channels |
| 1300–1399 | Sci-Fi & Horror | Sci-fi, horror, supernatural and cult |
| 1400–1449 | Westerns | Western film and television |
| 1450–1499 | Classic TV | Classic and archive television |
| 1600+ | General Entertainment | Broad/mixed channels that do not fit a stronger genre |

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

The project deliberately ignores the inconsistent genre labels supplied by FAST providers.

Examples:

- `Sports`, `Combat Sports`, motorsport and similar source labels → **Sport**
- `Crime TV`, `Crime`, `Law` → **Crime & Mystery**
- `Documentary`, factual/history/science labels → **Factual & Documentary**
- food, home, renovation, garden and travel → **Lifestyle & Food**
- source labels such as `Season of Scares` → **Sci-Fi & Horror**
- kids, animation and children's programming → **Kids & Animation**

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
