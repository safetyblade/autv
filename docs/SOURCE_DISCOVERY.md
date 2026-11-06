# AUTV — ongoing channel discovery and source acceptance

**Status: living process (6 November 2026).** Do not treat a failed scan, missing US Pluto channel, outdated brand name, previous 403/404, or old engineering spike as a permanent conclusion about a channel's availability.

### Source priority

1. Search the current provider's **Australian catalogue, channel ID, EPG and official live page**.
2. Cross-reference authorised public FAST/IPTV sources: Pluto Australia, 10/Paramount FAST, Plex, Samsung, LG, Roku, Tubi, Xumo, Rakuten, official broadcaster/CDN HLS and other suitable services.
3. Treat a region-specific foreign Pluto ID as a **candidate only**. A user account, playlist token or another app's login does not authorise AUTV to borrow playback entitlements.
4. Verify source owner and access rights, manifest, encryption/key and first segment, programme identity, TV audio/video for at least two minutes, ad insertion and geo restrictions before adding a source.
5. If successful: preserve stable number and source refresh/fallback. If unsuccessful: record exact tested date, region and failure stage, keep alternatives open for later reassessment.

### Current discovery programmes

- **#172:** Network 10 AU FAST: Embarrassing Bodies, Medical Emergency, Dallas Cowboys Cheerleaders and Nicktoons 90s. Also BINGE-listed TVSN, Al Jazeera English, NHK World-Japan and CGTN public HLS candidates. None is approved by source availability alone.
- **#157:** ABC and Network 10 registered-client authorisation, retained as potential future functionality beyond six no-login channels.
- **#166:** Wrestling, CHARGE!, Comet, additional free English-language sources.
- **#134:** Channel recovery, replacement-first and no removals without approval.
- **#164:** Stable Android signing identity and app account retention.

### Approved Australian live channels

Owner's 6 November physical tests confirmed ABC TV VIC, ABC NEWS, ABC Kids, 10 VIC, 10 Drama and 10 Comedy. These are declared in `AustralianLiveChannels.kt` as in-guide channels requiring no account login, with working but externally maintained source redirects. Current playback acceptance does not by itself prove content redistribution permission.

### Records and interpretation

- `audits/network10_au_fast_gap_2026-11-06.csv` is a **dated name comparison** and neither a full current 10 Australia catalogue nor proof that a foreign Pluto feed plays.
- Keep links to significant source PRs and CI diagnostics inside the relevant GitHub issue instead of growing standalone 'final solution' Markdown documents.
- Use the current guide (CSV and `guide.json`) for duplication decisions; historical lists can have different numbers, regions or channel branding.

This document deliberately contains no universal 'unavailable' verdicts. Revisit potential sources whenever providers, regional catalogues or transport requirements change.
