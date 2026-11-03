# AUTV 36-channel replacement-source discovery — 3 November 2026

This is a **source discovery document**, not playback certification. No candidate has been verified as an authorised reusable HLS/DASH URL or on an Australian AUTV device. Preserve all channel numbers. No removal or source promotion permitted solely by this document.

## Three owner-requested live channels
- **227 Rugby Pass TV** — World Rugby confirms official RugbyPass TV free live/on-demand service: https://info.rugbypass.tv/ and FAQ https://info.rugbypass.tv/faqs . World Rugby confirms dedicated RugbyPass TV FAST carriage on Samsung TV Plus **UK and US** (https://www.world.rugby/news/1001046/world-rugby-partners-with-samsung-tv-plus-to-accelerate-us-and-uk-expansion-with-rugbypass-tv-fast-channel?lang=es). **Candidate:** inspect authorised Samsung UK/US live catalogue and World Rugby service; AU distribution and TV app stream-reuse rights unverified. Some live events licensed to Australian broadcasters, so do not infer World Rugby match rights from global availability.
- **239 Rally TV** — official 24/7 linear channel https://www.rally.tv/en/page/07f960dc-fd36-466c-971f-64a597518b83?play=true . WRC official FAST distribution lists LG/Rakuten/Samsung in selected European territories and Pluto Italy/Spain/UK; **not Australia**: https://www.wrc.com/en/misc/broadcast-guide . DAZN Australia offers paid standalone Rally.TV including Rally 24/7: https://www.dazn.com/en-AU/help/articles/19002884413469-rally-tv . **Candidate:** authorised local Rally.TV integration with subscription, subject to provider permission, or licensed AU FAST distribution if established. Do not copy EU geo-restricted feeds.
- **612 Whose Line Is It Anyway?** — **official Pluto US live linear channel with EPG** https://pluto.tv/us/watch/live-tv/32587/ . **Candidate:** match this official channel's identity to the current live Pluto US catalogue, test valid provider-issued stream and AU accessibility. Distinguish US linear channel from Australian/UK VOD episodes on Tubi https://tubitv.com/en-au/series/300005124/whose-line-is-it-anyway .

## Other 33 — candidate discovery and source equivalence
All 33 remain under investigation; these are **research leads**, not confirmed replacements.

| IDs | Channel(s) | Current source | Candidate investigation | Evidence level |
|---|---|---|---|---|
| 106,118 | CNNi; Newsmax2 | Pluto; Plex AU | Official regional news FAST catalogues and respective broadcaster feeds | Catalogue lookup pending |
| 279 | Stadium | Tubi | Tubi live sports catalogue, then other authorised distribution | Catalogue lookup pending |
| 322,324 | British Screen Classics; Curzon | Pluto | UK Pluto channel identity, official Curzon service, other authorised UK movie FAST providers | Catalogue lookup pending |
| 505,506,509,512 | Crimes That Shook Britain; Criminal Confessions; Evidence of Evil; Medical Detectives | Pluto | UK Pluto crime feeds, Crime+Investigation catalogue, alternate official regional FAST listings | Title availability not live-stream verified |
| 508 | Deadly Women | Pluto | Pluto true-crime programmes https://pluto.tv/us/watch/live-tv/category/true-crime/ and https://www-ipv4.pluto.tv/us/category/14ea42f8-c0d7-48bb-8eeb-79a00c899ed1/ ; distinguish series VOD from channel | Programme evidence only |
| 540,543 | Cagney & Lacey; Hawaii Five-O | Pluto | Pluto regional live drama catalogue, alternative authorised distributors | Not verified |
| 606 | Girlfriends | Pluto | Pluto title/on-demand and BET Pluto TV scheduling; look for true dedicated linear feed | Programme evidence only |
| 716,727,747,756,764,765 | Sea Patrol; Northern Exposure; Farscape; Earth: Final Conflict; Pluto TV Alien Invasion; Pluto TV Space | Pluto | Regional Pluto live catalogue and Roku/Plex FAST source-title identity | Not verified |
| 1030,1043,1045,1076,1084 | Gordon Ramsay; Top Chef; GARDEN with Monty Don; The Real Housewives; Geordie Shore | Tubi/Pluto/Roku | Authorised Tubi/Roku/Pluto linear catalogues, distinguish show VOD | Not verified |
| 1140,1144,1151,1152,1154 | Haunt TV; Pluto TV Conspiracy; History Hunters; World War TV; Pluto TV Britain at War | Curated/Pluto | Repair original curated Haunt feed, investigate regional documentary/war FAST | Not verified |
| 1202,1228,1229,1233 | Baby Shark TV; Mona The Vampire; The Raccoons; Pluto TV Retro Toons | Samsung/Pluto | Samsung AU guide, Roku Wurl authorised feed, official Pluto regional kids FAST | Not verified |

## Engineering verification per channel
1. Retrieve current provider-issued catalogue with country/region and timestamps. Identify exact provider channel ID and official canonical service URL. Name-only matching is not sufficient.
2. Inspect whether current source is licensed and intended for Australian playback. No bypass of geo/auth/DRM/advertising controls.
3. Test legally provided fresh HLS/DASH manifest, playable nonempty media segment and continuity, or provider-supported player integration. Never store credentials or signed sessions in Git history.
4. Verify on the Australian AUTV TV app; record first frame, 60-second continuity, audio, guide and fallback behaviour. A catalogue page or 200 response does not equal playable AUTV stream.
5. Reconcile overlapping native broadcaster programming without treating VOD as equivalent to a linear channel; preserve existing working 7plus.
6. Report one of: VERIFIED_PLAYABLE, PROVIDER_LISTED_ONLY, VOD_ONLY, RIGHTS_OR_AUTH_BLOCKED, NOT_FOUND, NOT_TESTED. None of these constitute a retirement decision.

No playlist changes or removals authorised.
