# Australian provider application integration

The production `PlaybackModel` composes the existing Nine adapter with SBS and any approved Ten/ABC registration. `ProviderAccountController` owns each additional account's challenge, polling, catalogue and sign-out. Resolving, catalogue refresh and sign-out serialize per provider; cancelling one tune cannot replace another provider's playback. The unchanged player receives the original `ProviderPlaybackRequest`, including its IMA/DRM options. Private channel IDs, stored channel numbers and Favourites remain separate from public FAST data. Native programme schedules now reach `EpgRepository` without borrowing similarly named public channels.

## Registered builds

`AustralianProviderConfiguration` is exposed by `AuTvApplication.australianProviders`. An approved build can subclass `AuTvApplication` and override that property. Supply the subclass source directory using `-PautvProviderSources=/private/approved-source` and its class name using `-PautvApplicationClass=your.package.RegisteredApplication`. Keep registrations and authorisation material out of the public repository and build logs. This is a composition hook for the existing concrete HTTP transports, not a substitute transport or a user-password setting.

Ten requires its AUTV-approved `TenClientRegistration`: API origins, configuration/device/playback fields, live format and request-authorisation implementation. `tenProofChannelKey` must be a returned Victorian main-channel key; any DAI network code must be provider-issued. The transport obtains device endpoints and catalogue from provider configuration, refreshes the encrypted account session and resolves a fresh descriptor with its returned `X-DAI-AUTH`. No borrowed app signing key, client registration or Google test asset is supplied. Without these inputs the default app explains the missing registration and offers no misleading Connect action.

ABC requires an AUTV-approved `AbcClientRegistration`, a returned Victorian main house number and the approved media-authorisation implementation. Profile linking additionally requires ABC's `productId` and profile API key. Explicitly approved anonymous live mode uses `accountLinkingEnabled=false` plus `abcAnonymousLiveApproved=true`; no profile request is sent and the UI does not claim an account is connected. Anonymous metadata alone does not grant stream authorisation. The default app has no such grant and exposes no fake account connection.

SBS reuses the existing successful adapter, original stream resolution and encrypted store aliases. Nine's adapter, account aliases and FAST refresh are unchanged. Seven now reuses its existing account/catalogue/Channel 7 resolver and encrypted aliases. The last owner result remains `7-DESCRIPTOR (HTTP 400)` with a non-JSON response; the full app retains this safe diagnosis and adds response-format classification. No successful descriptor or real Channel 7 playback is claimed. No hard-coded postcode, tracking identity or false SDK verification is copied from upstream.

## Full AUTV build and delivery

The normal owner build is the real television application, **AUTV**, package `dev.prestwich.autv`, launcher `MainActivity`:

```
gradle -p android -PautvMediaLab=false :app:assembleFullAppDebug
python3 scripts/verify-full-app-apk.py android/app/build/outputs/apk/full-app/AUTV-Full-App-Debug.apk
```

The explicit full-app task rejects Lab configuration. The packaged manifest check verifies the actual application name, ID and phone/TV launcher; it also records the public signing certificate fingerprint. The **AUTV Full Application Debug** workflow publishes artifact **AUTV-Full-App-Debug**, containing **AUTV-Full-App-Debug.apk**. Normal debug builds no longer advertise the QA activity as a launcher. Public Media Lab sample workflows run only when explicitly dispatched; their source and capabilities are retained.

This is a normal Android debug-signed APK, independent of permanent release-signing setup. It can update an existing installation only when its signing certificate and version are compatible. Do not uninstall an existing AUTV merely to overcome a signature mismatch: that can remove encrypted broadcaster sessions. Use the original signing identity or a separate test device/emulator. No private permanent signing key is generated or uploaded by this change.

ABC and Ten settings accurately show unavailable status until approved configuration is supplied. Technical registration requirements above belong outside the ordinary viewing workflow. Their existing transports, session renewal, catalogue, native resolver and IMA handoff are wired to the real app; this does not establish real broadcaster playback. Seven uses the restored native guide/resolver path and preserves precise failed-stage/HTTP diagnostics without stopping other channels.

Australian TV display and CH navigation order groups ABC, SBS, Seven, Nine and Ten, with known companion names before thematic channels. It changes no persisted channel numbers or provider IDs. Favourites membership/storage and their existing display ordering are untouched; native and similarly named FAST identities remain distinct.
