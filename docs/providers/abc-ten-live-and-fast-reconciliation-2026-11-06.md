# AUTV — Australian ABC/10 live TV acceptance and FAST gap review
**Reviewed 6 November 2026 · Australian TV · Source: owner physical test and public broadcaster/Paramount records**

## Six approved no-login channels
Owner physically tested and approved all six channels playing inside AUTV on 6 November 2026.

| AUTV channel | Display name | Technical playback | Provider label |
|---|---|---|---|
| 2 | ABC TV VIC | `https://c.mjh.nz/abc-vic.m3u8` | ABC iview |
| 20 | ABC NEWS | `https://c.mjh.nz/abc-news.m3u8` | ABC iview |
| 22 | ABC Kids | `https://c.mjh.nz/abc-kids.m3u8` | ABC iview |
| 10 | 10 VIC | `https://i.mjh.nz/.r/10-vic.m3u8` | 10 |
| 11 | 10 Drama | `https://i.mjh.nz/.r/10bold-vic.m3u8` | 10 |
| 12 | 10 Comedy | `https://i.mjh.nz/.r/10peach-vic.m3u8` | 10 |

Implement as **ordinary, stable Australian TV** channels in all builds without account linking. Unique IDs are owned by `AustralianLiveChannels.kt`; the app publishes no additional ABC/10 native adapter rows until native authorisation is proven. Show 3 available channels under each provider in Settings, consistent with 9Now/SBS/7plus card layouts. Do not present a false Connected account state or show old debug trial labels.

**Distinct technical and legal status:** The user has approved playback quality and selection. The public redirect operators `c.mjh.nz` and `i.mjh.nz` are third parties, and their successful playback does not in itself establish ABC or Network 10's permission to redistribute the streams in a third-party product. Preserve DAI/embedded ads, regional restrictions and original broadcaster media access requirements. Plan a source refresh/fallback only from verified lawful origins. No DRM, token or entitlement bypass.

### ABC account-link preservation
Existing ABC device-code request, polling, encrypted session, diagnostics and future registered adapter remain implemented. Live channels do not depend on a linked account. The ABC Settings account section is secondary and optional where an adapter is present, initially collapsed. A user who links ABC for a future feature must not lose the six live streams when the session expires or unlinks.

### Network 10 registered account
The unavailable `10.com.au/api/v1/config` endpoint returned HTTP 404; there is still no working 10 device-code login. It is **not needed for the three approved live channels**. Keep unsupported account login out of primary Settings, rather than showing 'Configuration required' for channels that now work.

## Other ABC streams actually offered in Australia
ABC official live collection `https://iview.abc.net.au/collection/959` contained **17** entries in the checked November public API:
- **Already approved (3):** ABC TV VIC, ABC NEWS, ABC Kids
- **Other national linear services (2):** ABC Entertains, ABC Family (NSW schedule, as per ABC Help)
- **Other state ABC TV feeds (7):** NSW, QLD, WA, SA, NT, TAS, ACT (plus VIC already approved)
- **Other themed streams (4):** ABC Drama, ABC Comedy, ABC Lifestyle, Back Roads
- **General ABC TV feed (1):** ABC TV (regional feed ambiguity; prefer VIC for Melbourne)

ABC's website also offers additional curated channels outside this 17-item collection, such as Good Game Squad and children's specials, which require fresh source-specific testing. Do **not** auto-add them in the six-channel close-out.

Official references:
- https://iview.abc.net.au/category/watch-live
- https://help.abc.net.au/hc/en-us/articles/8552173461519-How-do-I-watch-live-streams-on-ABC-iview
- https://iview.abc.net.au/collection/959

## 10 Australia FAST / Pluto reconciliation
Network 10 confirms that its app and website carry the regular 10/Drama/Comedy/Nickelodeon on-air services **plus Australian Pluto TV-powered FAST channels** (14 genre categories). These are an **Australian-specific, ad-supported curated catalogue** rather than a transferable US Pluto playlist. The live website could not be fetched from the engineering network (HTTP 403), so current 2026 app inventory is partly owner-observed; broadcaster historical lists are reference snapshots.

Sources:
- Official 10 FAST FAQ: https://helpdesk.tenplay.com.au/support/solutions/articles/16000165013-live-tv-with-pluto-tv
- Paramount's August 2023 initial 51-channel announcement: https://www.paramountanz.com.au/news/50-pluto-tv-fast-channels-now-available-on-10-play/
- June 2025 network rebrand/FAST expansion: https://www.paramountanz.com.au/news/a-stronger-network-a-bolder-10/

The file `audits/network10_au_fast_gap_2026-11-06.csv` compares **62 distinct channel names** from those official dated snapshots and seven owner-observed November 2026 app channels against AUTV's 637-row guide snapshot. **21 exact-name matches**, **4 related but non-equivalent names**, **37 not listed by that exact name**. A matching name is **not proof** of a working or identically programmed Australian channel; a related brand is not an interchangeable feed.

### User-identified 10 app gaps (high priority)
| 10 Australia app | AUTV comparison | Action |
|---|---|---|
| Embarrassing Bodies | No exact match | Seek Australian channel identity, legitimate feed and EPG |
| NickToons 90s | AUTV #1232 NickToons (different branding) | Verify distinct '90s' FAST schedule; don't relabel |
| Jamie Oliver | AUTV #1040 The Jamie Oliver Channel (related) | Compare programming and Australian feed |
| Medical Emergency | No exact match | Dedicated FAST source and rights check |
| Dallas Cowboys Cheerleaders | No exact match | Verify 10 channel identity and regional availability |
| Prisoner | No exact match | Prioritise Australian Pluto 10 source and EPG |
| Adult Animation | AUTV #1234 Animation+ (different source/provider) | Treat as missing until same content confirmed |

Do not re-add the failed Nickelodeon on-air source as a FAST substitute: these are different channel/distribution contexts.

### Channel discovery procedure
1. Obtain a dated **Australian** 10 live-app catalogue/EPG inventory with stable broadcaster/Pluto channel IDs and schedules; don't use US-region Pluto IDs as if they were equivalent.
2. Join against AUTV by **provider channel ID first**, then broadcaster-approved naming aliases, EPG and artwork. Check existing rows against actual AU streams, not name alone.
3. Prioritise channels absent from AUTV; use only genuinely lawful public, provider-sanctioned or separately licensed source paths. Preserve server-side advertising.
4. Perform HLS manifest, first segment, video+audio and 2-minute physical-device tests before adding. Update approved source refresh/fallback handling and correct genre/number.
5. No removals or replacement of existing AUTV channels without owner approval.

### Open product tasks
- **Six approved channels:** complete once merged, QA validates normal full-app install and TV guide status.
- **Extra ABC services:** source/permission/physical playback review, separate from this acceptance.
- **10 FAST gap:** maintain an audit and source queue under channel operations, not a mass import of unverified Pluto feeds.
- **Provider Settings:** ABC and 10 show live availability; keep ABC device-code feature accessible via optional, secondary account section.
