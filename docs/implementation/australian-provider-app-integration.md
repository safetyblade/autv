# Australian provider application integration

The production `PlaybackModel` composes the existing Nine adapter with SBS and any approved Ten/ABC registration. `ProviderAccountController` owns each additional account's challenge, polling, catalogue and sign-out. Resolving, catalogue refresh and sign-out serialize per provider; cancelling one tune cannot replace another provider's playback. The unchanged player receives the original `ProviderPlaybackRequest`, including its IMA/DRM options. Private channel IDs, stored channel numbers and Favourites remain separate from public FAST data. Native programme schedules now reach `EpgRepository` without borrowing similarly named public channels.

## Registered builds

`AustralianProviderConfiguration` is exposed by `AuTvApplication.australianProviders`. An approved build can subclass `AuTvApplication` and override that property. Supply the subclass source directory using `-PautvProviderSources=/private/approved-source` and its class name using `-PautvApplicationClass=your.package.RegisteredApplication`. Keep registrations and authorisation material out of the public repository and build logs. This is a composition hook for the existing concrete HTTP transports, not a substitute transport or a user-password setting.

Ten requires its AUTV-approved `TenClientRegistration`: API origins, configuration/device/playback fields, live format and request-authorisation implementation. `tenProofChannelKey` must be a returned Victorian main-channel key; any DAI network code must be provider-issued. The transport obtains device endpoints and catalogue from provider configuration, refreshes the encrypted account session and resolves a fresh descriptor with its returned `X-DAI-AUTH`. No borrowed app signing key, client registration or Google test asset is supplied. Without these inputs the default app explains the missing registration and offers no misleading Connect action.

ABC requires an AUTV-approved `AbcClientRegistration`, a returned Victorian main house number and the approved media-authorisation implementation. Profile linking additionally requires ABC's `productId` and profile API key. Explicitly approved anonymous live mode uses `accountLinkingEnabled=false` plus `abcAnonymousLiveApproved=true`; no profile request is sent and the UI does not claim an account is connected. Anonymous metadata alone does not grant stream authorisation. The default app has no such grant and exposes no fake account connection.

SBS reuses the existing successful adapter, original stream resolution and encrypted store aliases. Nine's adapter, account aliases and FAST refresh are unchanged. Seven remains paused.

## Builds and QA

The separate Media Lab APK retains its own application ID. Its new **AUTV provider settings** control opens the real settings/guide integration inside that isolated package; it cannot read the production package's broadcaster sessions. Existing Media Lab HLS/DASH/Widevine/IMA/recovery controls remain available.

The native-provider workflow runs `AustralianProviderIntegrationTest` and `SbsProviderTest` alongside existing account, guide, QR, FAST and upgrade tests. The new cases exercise real application routing using explicitly synthetic provider responses and visible local media: Ten QR approval, native tuning, programme lookup, stable numbers/Favourites, approved anonymous ABC, scoped sign-out and cancellation of a late resolver. They do not establish real Ten or ABC broadcast playback.

The distribution workflow also builds this integration branch with the existing permanent signing identity and monotonic version code, so its release APK can update the installed production app. Do not install a debug production APK over a release installation; use the signed distribution artifact or the isolated Media Lab APK.

Owner QA: open Media Lab, run HLS/DASH/Widevine, then both live IMA modes; copy the observed QA summary after a complete ad break. Run FAST recovery and check moving picture plus audio, then select another sample. Open **AUTV provider settings** to inspect the actual app UI. In an approved registered build, connect Ten or refresh approved anonymous ABC, tune the main Victorian channel, test Guide/Back/CH+/CH− and Favourites, restart to check persistence, and sign out one provider while another plays. Real broadcaster video/audio and commercial return require separate device acceptance; public sample success is not broadcaster success.
