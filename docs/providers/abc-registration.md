# ABC authorised distribution setup

The standard application has no ABC registration. It must show **Configuration required**, not Connected. Previous merged work (#83/#89/#95/#117) implemented linking, UID renewal and the HLS authorisation transport; no owner account or signing access was supplied. Issue #140 expands the adapter beyond its single proof feed, without removing grant checks.

An authorised operator supplies an `AuTvApplication` subclass overriding `australianProviders` with an `AustralianProviderConfiguration(abc = AbcClientRegistration(...))`. Build with `-PautvProviderSources=/path/to/approved/sources -PautvApplicationClass=your.package.App -PautvMediaLab=false :app:assembleFullAppDebug`.

Required provider inputs:

- ABC-issued AUTV `productId` and `profileApiKey` for `/latest/ctv/link` and `/ctv/unlink`, with the supported profile/UID contract. AUTV does not invent a profile or import another application's registration.
- Approved media authorisation, for example `AbcHlsAuthoriser(AbcHlsSigningRegistration(approvedDeviceType, approvedSigner))`. The signer must authorise the exact `/auth/hls/sign` path; do not embed a borrowed HMAC key. Account linking alone is not a media grant.
- The currently supported `region = "VIC"`, a discovered `mainHouseNumber`, and the explicit set `authorisedHouseNumbers` for all approved live feeds. Defaults retain the previous single-feed grant. Discovery alone never extends the grant.
- Anonymous live is opt-in only with ABC approval: disable `accountLinkingEnabled` and enable `abcAnonymousLiveApproved`. This never reports an account connection.

Catalogue discovery does not require an account token and does not renew or expire the saved account. The configured adapter filters discovered house numbers to the approved regional grant set before exposing guide rows. Media resolution still requires an account unless anonymous live is explicitly approved.

The adapter revalidates the current live catalogue before each tune, obtains fresh programme metadata and authorises the exact original HLS URL. Expired authorisation clears only ABC's session; transient failures retain the UID/session for retry. The existing provider-specific encrypted store and stable device UUID persist across restart.

Live acceptance remains pending provider configuration, account linking on a device and Australian network access. This execution environment excludes the ABC API hosts from its network allowlist. That restriction is not a measured broadcaster rejection. Synthetic tests prove control flow, not a working ABC account, manifest, video or audio.

## Independent startup source

The default `AuTvApplication` can now load a public no-argument implementation of `AbcRegistrationSource` directly. Its `configuration(context)` returns `AbcProviderConfiguration(registration, anonymousLiveApproved)`. Use `-PautvAbcProviderSources=/approved/abc-source -PautvAbcRegistrationSource=your.package.AbcSource`. No application subclass is required. The existing shared `autvProviderSources` and application override remain supported.

The source is distribution-owned code, not a credential file. It must perform local composition only and use approved runtime signing/registration operations. Do not place a profile key or HMAC secret in source, Gradle properties, generated BuildConfig or the APK. A missing source keeps Connect hidden; a failed source reports a configuration failure and cannot suppress Network 10, SBS or Seven construction. See `docs/implementation/issue140-native-integration.md` for the exact authorisation boundary and validation status.
