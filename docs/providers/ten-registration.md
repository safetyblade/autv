# Network 10 authorised distribution setup

The standard build does not contain Ten's registered client configuration. It must show **Configuration required**. Existing merged #84/#90/#94/#117/#119 provide config, device-code/renewal, regional catalogue, descriptor and Media3 IMA wiring; they do not establish broadcaster entitlement.

An approved operator implements `TenClientRegistration` with Ten-issued `allowedOrigins`, `configParameters`, `deviceFields`, `playbackParameters`, negotiated `liveFormat` and `authorise(method, exactUrl, jsonBody)`. GET request authorisation (`X-N10-SIG` in the reference) requires AUTV-approved signing access; never copy an embedded private key or claim another registered application's identity. The public POST timestamp header is already implemented and does not grant GET access.

Set `authorisedChannelKeys` to the approved live catalogue `row.key` values, then supply `AustralianProviderConfiguration(ten = registration, tenProofChannelKey = discoveredMainKey, tenDaiNetworkCode = issuedNetworkCode)`. The original main-key input is retained for source compatibility; approved companion keys now resolve too. Unapproved or no-longer-discovered identities never reach playback. The currently supported catalogue region is VIC; provider IP/location restrictions remain in force.

Override `AuTvApplication.australianProviders` in approved distribution sources. Build with `-PautvProviderSources=/path/to/approved/sources -PautvApplicationClass=your.package.App -PautvMediaLab=false :app:assembleFullAppDebug`. Keep signing credentials behind the approved signing operation; do not commit secrets.

Device-code approval and token refresh persist in the existing Ten encrypted namespace and stable UUID. Fresh descriptor URLs retain original query/session bytes. Provider-returned live assetKey and X-DAI-AUTH go to the existing Media3 IMA path with the negotiated format and optional issued network code; IMA owns the ad/session lifecycle. No bare manifest fallback, public sample identity or advertisement stripping is used. Descriptor 401 expires only Ten's account; missing DAI entitlement is a descriptor failure, not proof that the account token expired.

Outstanding: provider-issued registration/signing operation, live-channel grants and real device linking/playback acceptance. This environment's network policy excludes Ten API hosts; that is not a measured broadcaster denial. Automated fixtures verify the implementation, not real video/audio or commercial transitions.
