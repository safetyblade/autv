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

## Approved printed guide — immutable master

**The original owner-approved PDF is the design authority:** `docs/templates/AUTV_TV_Booklet_Master.pdf`.
It is the byte-exact 18-face A4 duplex booklet approved on 6 November 2026 (SHA-256 `96c309e947ee742e454126af9e5d6376745bc703eb1041855530c31db140c2ff`).

The generator `scripts/build_printable_guide.py` preserves this PDF's actual artwork, brand logos, Free-To-Air TV cards, fonts, wording, category structure, page styling, duplex order and editorial content. **No layout may be generated from an approximate ReportLab recreation.** Channel numbers, names, page contents and totals come from the controlled `channel_guide.csv`. A catalogue-identical build copies the approved print PDF byte-for-byte. A changed channel list changes only its relevant entries and totals; the immutable master remains untouched. If approved pagination capacity is exceeded, publishing fails rather than changing page design.

The `publish-printable-guide.yml` workflow runs when the controlled catalogue, native Free-To-Air channel declarations or rendering engine change. It publishes:

- `docs/AUTV_TV_Booklet_Print.pdf` — A4 landscape, imposed duplex, print both sides / short edge.
- `docs/AUTV_TV_Booklet_Reader.pdf` — 36 A5 pages, normal reading order.

The master SHA-256 is checked on every build. Ordinary six-hour stream/EPG token refreshes do not change the printed channel directory and do not require republishing identical PDFs. The generic `AUTV_Printable_TV_Guide.pdf` is superseded and is not an approved design.
