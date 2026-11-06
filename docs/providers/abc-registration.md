# ABC iview — optional account integration

**Current viewing path:** AUTV provides **ABC TV VIC (#2), ABC NEWS (#20) and ABC Kids (#22)** in Australian TV without a sign-in. These three external HLS sources passed Australian physical playback on 6 November 2026. Their sources are listed in `AustralianLiveChannels.kt`. This viewing path does not depend on ABC's device-code session.

**Preserve optional ABC linking:** The app retains ABC's supported real device-code request, polling and stored session through `AbcExperimentalLinkAccess` and the registered provider integration. In Settings the link is an optional, secondary feature; a successful account code must not imply that additional ABC media is authorised. Do not remove the code or wipe session storage during ordinary guide changes.

## Registered-client integration (future)

An ABC-approved implementation of `AbcRegistrationSource` may provide `AbcProviderConfiguration(AbcClientRegistration(...))`. Example build configuration: `-PautvAbcProviderSources=/approved/abc-source -PautvAbcRegistrationSource=approved.package.AbcSource`.

- ABC-issued `productId`, `profileApiKey` and device-account contract are required for formally registered activation.
- Media resolution is **separate** from a linked account; it needs an approved signing/authorisation operation for the exact HLS request and a specific broadcaster channel grant. The existing experimental media adapter does not supply an authorised live-stream URL.
- Honour Australian region, licence, ad, DRM, source expiry and broadcaster requirements. Do not import another app's credentials, reverse engineer client secrets or advertise unauthorised native playback.
- Catalogue discovery can be public even when programme metadata has no playable HLS URL. Do not treat a parsed programme response as a media grant.

The original native adapter and authorisation-boundary code remains in the repository. Its historical experiments are retrievable from issues #140 and #157 and Git history, not treated as current setup instructions.
