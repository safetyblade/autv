# AUTV — Australian television, one guide

AUTV is a remote-first Android TV / Google TV application that brings a curated FAST/IPTV channel lineup and supported Australian broadcaster channels into a familiar TV guide.

## Install and use

1. Obtain the **latest verified APK** from the project's approved build/release distribution. Do not install an old build based solely on a version in a historical note.
2. Install and open AUTV on an Android TV or Google TV device.
3. Navigate with the remote direction keys; press **OK** to tune and **Back** to exit or return to the guide.
4. Browse **Favourites**, **Australian TV**, or the genre-based numbered channel guide. Long-press **OK** on a supported channel to toggle favourite status.
5. Use **Settings** to choose a starting channel where supported and to connect Australian broadcaster accounts. Follow the on-screen activation or QR instructions when displayed.
6. Following a playlist update, refresh the guide in the app.

For troubleshooting, provider setup and the **complete current numbered FAST/IPTV directory**, see [TV Guide](TV_GUIDE.md).

### Australian TV

The broadcaster area is distinct from the repository's FAST/IPTV channel CSV. Account linking and video playback depend on the installed build and provider authorisation. Some providers may show **Configuration required** or other incomplete states. Consult the on-device Settings screen instead of assuming a service is available.

### Channel guide and playlist

- [Numbered channel directory and household guide](TV_GUIDE.md)
- [Guide source data](channel_guide.csv)
- [Published playlist](playlist.m3u)
- [Combined EPG](epg.xml.gz)

The guide source records **636 active FAST/IPTV channels as of 3 November 2026**; the generated playlist can differ when sources are temporarily unavailable. Native Australian TV channels must be reconciled separately.

## Project maintenance

The repository also contains playlist and EPG generation automation. Engineering/source management procedures are **not** household setup instructions. Keep those in [Maintenance notes](docs/MAINTENANCE.md) and current workflows, not in this README.

**Documentation ownership:** Mara Collins · **Engineering verification:** Elliot Mercer · **Product acceptance:** Helena. See [documentation issue #151](https://github.com/safetyblade/autv/issues/151).
