# AUTV — Australian television in one guide

AUTV brings approved free live television, Australian broadcaster services and a curated FAST/IPTV guide into one Android TV application.

## Install and update

The **AUTV signed update** and **AUTV Trial** are different builds. Use the signed update for the existing AUTV application only if Android accepts the matching signing certificate and a newer version; the trial is a separate installation and has separate account data. Never uninstall to work around a signing mismatch without accepting the loss of stored accounts. See [Android updates](docs/android-updates.md).

## Printable TV booklet

The approved design is maintained automatically from the current channel catalogue. Both downloads keep the same editorial layout as channel numbers and totals change.

- [A5 Reading Edition](docs/AUTV_TV_Booklet_Reader.pdf)
- [A4 Duplex Print Edition](docs/AUTV_TV_Booklet_Print.pdf) — print landscape, double-sided, flip on short edge, then fold.

The booklet includes the Free-To-Air TV section and the full numbered FAST channel directory. The older generic printable channel list is no longer the approved edition.

## Watch TV

1. Open the guide and choose **Australian TV** for the six tested no-login channels below, or select a FAST/IPTV channel by number and genre.
2. Use the directional remote keys, **OK** to play and **Back** to return. Long-press **OK** to add or remove a favourite.
3. In Settings, select your startup channel. **7plus, 9Now and SBS** can offer account linking through their supported activation screens.
4. ABC and Network 10's six approved free live channels do **not** require a login. ABC's separate device-code linking remains optional for potential future features.
5. Refresh the guide after an approved source update; a listed channel can still become temporarily unavailable.

### Australian TV — six tested streams

| Number | Channel | Account |
| ---: | --- | --- |
| 2 | ABC TV VIC | Not required |
| 10 | 10 VIC | Not required |
| 11 | 10 Drama | Not required |
| 12 | 10 Comedy | Not required |
| 20 | ABC NEWS | Not required |
| 22 | ABC Kids | Not required |

The six were physically played on the owner's Australian device on **6 November 2026**. Sources currently depend on external redirect infrastructure; successful playback does not imply a broadcaster redistribution licence, permanent availability or verified EPG.

## Directory and support

- [Numbered TV guide](TV_GUIDE.md) — the currently configured FAST/IPTV directory plus the six Australian live channels
- [Channel source of truth](channel_guide.csv) — channel numbers and editorial status
- [Current published playlist](playlist.m3u) and [EPG](epg.xml.gz)
- [Maintenance](docs/MAINTENANCE.md) — internal operational procedures
- [Source discovery](docs/SOURCE_DISCOVERY.md) — evolving candidate evidence, never a permanent block on future sources

**Snapshot 6 November 2026:** 639 active curated FAST/IPTV entries in CSV and 634 published playlist entries. These are configured/listed counts, not a claim that every source currently plays.
