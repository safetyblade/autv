# AUTV #166 — NWA Powerrr via Comet TV and CHARGE! (6 November 2026)

**Result:** Two direct Sinclair HLS candidates passed manifest/media-segment checks from GitHub Actions. **No production channel added.**

## Confirmed broadcast arrangement
- [NWA official watch information](https://www.nationalwrestlingalliance.com/watch): NWA Powerrr broadcasts weekly on Comet Saturdays at 4 PM US Eastern Time, plus replay; the rest of Comet's schedule is principally sci-fi, fantasy and cult entertainment.
- [Comet's NWA page](https://comettv.com/NWA/) and [official live TV](https://comettv.com/watch-live/): Comet is a continuous English-language television channel, **not 24/7 wrestling**.
- [Tubi official NWA Powerrr](https://tubitv.com/series/300021716/nwa-powerrr): free on-demand episodes. No linear programme stream inferred from Tubi.
- [NWA/CHARGE! simulcast report, 26 Aug 2026](https://prowrestling.net/site/2026/08/26/nwa-powerrr-simulcasts-start-this-weekend/): temporary five-episode dual broadcast; no evidence CHARGE! continues airing Powerrr in November.

## Technical trial
GitHub Actions [run 42128023829](https://github.com/safetyblade/autv/actions/runs/42128023829), [PR #170](https://github.com/safetyblade/autv/pull/170), public upstream `iptv-org/iptv streams/us.m3u`.

| Channel | Publicly listed Sinclair HLS | Master manifest | Media manifest | Actual segment | Result |
| --- | --- | --- | --- | --- | --- |
| Comet | `https://fast-channels.sinclairstoryline.com/COMET/index.m3u8` | Valid | Valid | Nonempty | **SEGMENT_REACHABLE** |
| CHARGE! | `https://fast-channels.sinclairstoryline.com/CHARGE/index.m3u8` | Valid | Valid | Nonempty | **SEGMENT_REACHABLE** |

Runner checks are not Australian TV playback tests. Rights, geo access, ad insertion, audio/video continuity, EPG and permission to incorporate these public streams into AUTV were not established.

## Recommendation / gate
1. **Comet — controlled Australian-device trial next.** User can decide whether to add *Comet TV* as a new **Entertainment / Sci-Fi** channel if it plays and its distribution is permissible in AU. Guide text should note *NWA Powerrr on Saturday US broadcast schedule*, not call it a dedicated wrestling channel.
2. **CHARGE! — optional secondary trial** as its own entertainment/action network. Do not advertise current NWA content without a verified November schedule.
3. **NWA Powerrr on Tubi — VOD-only**; keep out of the live channel directory unless a properly integrated on-demand section exists.
4. Preserve existing AEW/TNA/NXT feeds and all other published sources. Neither HLS candidate may replace a working channel.
5. No user-facing channels, playlists, selected-source lists, app code or numbering were changed.

## Needed for release
Physical Australian AUTV device test, at least 60 seconds of video/audio continuity, correct ads, EPG matching, local accessibility and distribution entitlement. If unsuccessful, retain this evidence only and do not publish.
