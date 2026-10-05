# AU TV Channel Discovery & Review Process

## Purpose

AU TV does not add channels simply because they appear in a provider catalogue or search result.

Every proposed channel must move through a controlled discovery, reconciliation and QA process before it can enter the live lineup.

The objective is to keep AU TV useful, stable and curated rather than maximise channel count.

## Authority

- `channel_guide.csv` is the authority for the active AU TV lineup.
- `channel_candidates.csv` is the authority for discoveries that are not yet approved.
- Search results, provider catalogues and community lists are evidence only.
- A candidate is not live until it has been accepted into `channel_guide.csv` and passed the release checks below.

## Core principle

Before adding a channel, answer:

> **What does this add to AU TV that we cannot already watch?**

A second source for the same feed is normally an **alternate source**, not a new channel.

A same-brand channel may remain separate only where device QA shows materially different programming or scheduling.

## Review cadence

### Weekly discovery review

Run:

1. provider-change searches;
2. general new FAST-channel searches;
3. targeted high-value searches;
4. reconciliation against the current AU TV lineup;
5. feed discovery for strong candidates only.

The weekly review should finish with four short outputs:

- **Changes to existing AU TV channels**
- **Strong new candidates**
- **Candidates ready for device testing**
- **Nothing worth adding**

### Monthly deep review

Once per month:

1. review every top-level genre;
2. identify obvious content gaps;
3. search the strongest missing subgenres, programmes, leagues, brands or channel families;
4. reconcile the candidate register;
5. retire stale candidates where appropriate.

### Targeted review

Run immediately when a specific property becomes interesting, for example:

- a wrestling promotion;
- football club or league;
- sports competition;
- movie or programme brand;
- broadcaster FAST launch;
- replacement for a failed existing channel.

## Discovery pipeline

### 1. Establish the baseline

Read `channel_guide.csv` before searching.

Build the current **already-have** set using:

- channel name;
- obvious aliases;
- brand;
- programme/property;
- primary source;
- alternate source.

This prevents rediscovering existing channels as new candidates.

### 2. Scan for changes to existing channels

Search for:

- channel launches and closures;
- renames;
- replacements;
- provider migrations;
- regional changes;
- materially changed programming;
- newly available alternate sources.

Existing-channel changes should be reviewed before adding new channels.

### 3. Search for genuinely new channels

Use a repeatable search pack rather than ad-hoc browsing.

#### Provider searches

Search current catalogues and recent changes for:

- Plex
- Pluto TV
- Roku
- Xumo
- Samsung TV Plus
- LG Channels
- Tubi
- other approved or credible FAST sources

Use searches such as:

- `"new channels" <provider> FAST`
- `"new channel" <provider>`
- `site:<provider domain> "live channels"`
- `site:<provider domain> FAST channel`
- `<provider> channels October 2026`

#### General FAST launch searches

Use:

- `"new FAST channel"`
- `"FAST channel launches"`
- `"24/7 FAST channel"`
- `"free live channel launched"`
- `"free ad-supported streaming channel"`
- `"FAST channel" October 2026`

#### High-value AUTV searches

Prioritise known household-value areas.

Examples:

- `"WWE FAST channel"`
- `"AEW FAST channel"`
- `"TNA FAST channel"`
- `"wrestling 24/7 channel"`
- `"football FAST channel"`
- `"soccer FAST channel"`
- `"club TV FAST channel"`
- `"FIFA FAST channel"`
- basketball, NFL, NHL, motorsport and combat-sport equivalents.

Football/soccer searches may include useful non-English feeds where the programming itself is valuable.

#### Genre-gap searches

Run the same pattern for:

- Movies
- Game Shows
- Crime
- Comedy
- Entertainment
- Reality & Lifestyle
- Factual
- Kids & Animation
- Music

Do not add a channel just because it matches a genre. Compare it with what AU TV already provides in that subgenre.

### 4. Search other territories deliberately

Useful channels may appear in US, UK, Canadian or other provider catalogues before or instead of Australia.

A foreign catalogue result is a **candidate only**.

Final acceptance still requires successful playback from the household Australian environment.

### 5. Reconcile every candidate

For each candidate decide whether it is:

- **NEW** — meaningfully new viewing;
- **ALTERNATE** — same service/feed but useful fallback;
- **DUPLICATE** — adds no meaningful value;
- **REPLACEMENT** — better source for an existing channel;
- **WATCHLIST** — interesting but not ready;
- **REJECT** — not worth further work.

Check:

- name and aliases;
- programme rotation;
- stream identity;
- branding;
- existing AU TV equivalents;
- regional availability;
- likely guide/EPG availability.

### 6. Discover a usable feed only after editorial interest is established

Do not spend time hunting streams for weak candidates.

For a strong candidate, search the exact channel name with:

- `m3u8`
- `live stream`
- `FAST`
- `EPG`
- `XMLTV`
- GitHub/community references where appropriate.

Prefer direct, testable streams and known provider endpoints.

### 7. Browser test before playlist promotion

Before changing AU TV:

- confirm the stream opens;
- verify it is the intended channel;
- verify playback lasts beyond startup;
- watch for redirect failures;
- watch for geo-blocking;
- check whether the stream is stable enough to warrant TV testing.

A browser failure does not enter the live playlist.

### 8. Device QA

Test strong candidates in AU TV on the actual household TV.

Verify:

- tune succeeds;
- acceptable tune time;
- stable playback;
- correct channel identity;
- guide entry appears correctly;
- remote navigation works;
- changing away and back recovers properly;
- app does not regress because of the feed.

Record the result in `channel_candidates.csv`.

### 9. Editorial acceptance

A technically working feed can still be rejected.

Prefer:

- recognisable brands;
- programme-specific channels;
- strong sports properties;
- channels that fill an actual content gap;
- distinctive scheduling.

Avoid:

- near-duplicates;
- generic filler;
- weak creator compilations;
- channels that add catalogue size without a new viewing decision.

### 10. Promote to the live guide

Only after acceptance:

1. assign Genre;
2. assign Subgenre;
3. choose a stable channel number;
4. add/update `channel_guide.csv`;
5. add a working alternate source if one exists;
6. regenerate playlist and EPG;
7. refresh the native app guide;
8. update the household Word guide when the lineup materially changes.

## Candidate register

`channel_candidates.csv` records discoveries before they enter the live lineup.

Minimum fields:

- Candidate
- Genre
- Subgenre
- Why interesting
- Found on
- Existing AU TV equivalent
- Stream found
- EPG
- Browser test
- TV test
- Region
- Decision
- Notes
- Last reviewed

## Candidate states

Use the following progression:

`DISCOVERED → FEED FOUND → BROWSER PASS → TV PASS → ACCEPTED`

Terminal or holding states:

- **DUPLICATE**
- **FAILED**
- **REGION BLOCKED**
- **LOW VALUE**
- **WATCHLIST**
- **REJECTED**

A previously failed candidate may be reopened only when a genuinely different feed or provider becomes available.

## Duplicate and alternate-source rule

Do not create a second AU TV channel solely because a second provider carries the same stream.

Use the second provider as an alternate source instead.

Separate channels are acceptable only where they create a different viewing choice.

Example:

- **TNA Wrestling Channel** and **TNA Wrestling Xumo** remain separate because household device QA demonstrated different programme rotation.

## Release checks

After promoting candidates:

1. confirm there are no duplicate channel numbers;
2. confirm every active channel sits in its approved genre-number block;
3. confirm no existing channel lost its display title;
4. confirm subgenre ordering remains logical;
5. confirm Australian/local channels remain first where that rule applies;
6. confirm playlist generation succeeds;
7. confirm EPG generation succeeds;
8. spot-test new and changed feeds;
9. verify the AU TV app guide/search still behaves correctly;
10. update documentation counts.

## Search-review reporting format

Each discovery session should return only actionable findings.

### Changes to existing AU TV channels

Existing feeds, replacements, renames or closures requiring action.

### Strong new candidates

Candidates that add a clear new viewing decision.

### Ready for device testing

Feeds that have already passed discovery/reconciliation and browser QA.

### No action

Searches that produced only duplicates, blocked feeds, weak content or already-known failures.

## Maintenance rule

The process is intentionally conservative.

A growing provider catalogue must never automatically grow AU TV.

The live lineup changes only after deliberate editorial approval and successful QA.
