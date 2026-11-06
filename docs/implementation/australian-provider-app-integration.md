# AUTV — current Australian TV integration contract

Updated 6 November 2026. **Do not use historical integration notes as hard blocks on future source discovery.**

## Live channels available without an account

`android/app/src/main/java/dev/prestwich/autv/provider/AustralianLiveChannels.kt` supplies exactly six owner-tested live channel IDs and externally maintained HLS links:

- ABC TV VIC #2, ABC NEWS #20, ABC Kids #22
- 10 VIC #10, 10 Drama #11, 10 Comedy #12

`PlaybackModel.applyGuide` adds these as normal `Australian TV` entries with stable favourite identities. They appear in both trial and signed production builds, not as numbered negative debug fixtures. The still-unverified registered ABC/10 adapters must not publish duplicate unplayable listings. Streams are independent of logged-in account state. Source use and distribution permissions remain separate from picture/audio test approval.

## Linked broadcaster accounts

9Now, SBS and 7plus retain their existing device-code sign-in, secure session refresh and playback where supported. ABC's code linking is preserved as an **optional future account feature**; Settings explains that it is not required for the three approved ABC live channels and does not yet unlock native media authorisation. Network 10's registered login route is not configured or advertised as working.

`AuTvApplication.australianProviders` and `AustralianProviderConfiguration` are retained for optional approved ABC/10 adapters. Do not move provider credentials, private signing keys or user sessions into BuildConfig, source manifests, GitHub issues or app resources.

## Transport, guide and regression rules

- A resolved HLS/DASH manifest is **not** a playback result. Verify media segments, actual TV video/audio, Australian region, permitted distribution and ads, especially Google DAI/IMA continuity.
- A guide name match is an inventory match only; EPG and supplier identity can differ.
- Keep TV guide/favourites numbers stable and avoid breaking 7plus/9Now/SBS, Chromecast, local playback and startup selection when adding channels.
- The authoritative channel list is the app-side `AustralianLiveChannels.kt` for six free live services and the curated `channel_guide.csv` for FAST/IPTV. The other registered accounts have separate live catalogues.
- Current build path and signing rules: [Android updates](../android-updates.md).

Known open work: issue #164 permanent signing; issue #157 registered provider authorisation; issue #172 10 AU FAST catalogue/source and free BINGE alternatives. Earlier issue #140 is historical engineering context, not a negative decision about new sources.
