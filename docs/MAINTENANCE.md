# AUTV — maintenance

The operational source of truth is the current repository code and datasets, not a historical discovery memo.

| Area | Current authority |
| --- | --- |
| Curated channels, numbers, status | `channel_guide.csv` |
| Published playback inventory | `playlist.m3u`, `guide.json`, `custom.m3u` |
| EPG publication | `epg.xml.gz` and approved refresh workflows |
| Six no-login ABC and 10 live channels | `android/app/src/main/java/dev/prestwich/autv/provider/AustralianLiveChannels.kt` |
| Other linked Australian TV | Provider adapters and current account/session configuration |
| Source discoveries, failed candidates and future search | `docs/SOURCE_DISCOVERY.md` and dated GitHub issues |
| Android install/update validity | `docs/android-updates.md` |

**Refresh and recovery:** Keep known working primaries; compare provider/channel IDs, AU programme schedules and permitted-origin source quality. Preserve DAI advertising and rights boundaries. An HTTP 200, foreign-region playlist ID or matching title is not physical device playback proof. Validate a segment, video, audio, ad transitions and Australian availability before publication. Never remove a channel without approval and a documented recovery investigation.

**Documentation:** Maintain only `README.md` and `TV_GUIDE.md` as everyday TV entrypoints. Generate the numbered directory from CSV rather than editing independent manual totals. Keep investigation details in GitHub issues/dated audits; old design decisions stay available in Git history and must not become permanent constraints.

**Release:** Use the signed Native app distribution for `dev.prestwich.autv`, and the isolated Trial for device QA. Compare certificate SHA-256 with the device's existing package before promising an over-install update.
