# Issue 140 native integration validation

Branch: `validation/issue140`. Initial workspace base: `6b3d18fafb9c65987fe5369c027a76222c39e7fe`. Integrated remote `validation/issue140` at `90e2419d00dca971df335ab7e9bc9217342fe072` before final validation, preserving its newer guide, QR and screenshot fixes.

## Working provider baseline

Read-only review covered NineNowAdapter, SevenPlusAdapter and SbsAdapter, their encrypted factories, ProviderAccountController, PlaybackModel, provider EPG merging and AuTvPlayer. Their production files are unchanged. The common sequence is device activation, encrypted session/challenge persistence, refresh, provider catalogue, fresh per-channel resolution and native Media3 handoff. Stable ProviderChannelId values own favourites; internal guide numbers do not. AustralianChannelOrder retains ABC, SBS, Seven, Nine, Ten ordering, and native row labels hide negative internal numbers.

## W1–W2: ABC comparison and changes

Reference: JCalvi/plugin.video.abc_iviewjc at `1ce1e18fd30858296adf5e4f5ea667a4217474fa`, resources/lib/api.py and plugin.py. The reference was inspected for protocol behaviour only; no GPL source or private constants were copied into AUTV.

- Reference linking uses POST `/latest/ctv/link` and GET `/latest/ctv/status`, with product/device fields and a profile API key on the POST. AUTV already implements this and locally renders a QR for its reviewed official activation destination. Added the reference's `expires` and `linkingStatus` aliases and reuse of the requested device ID when the response omits it. Missing expiry still fails closed; AUTV does not guess a code lifetime or accept arbitrary QR destinations.
- Reference supports UID-based Seesaw token issuance without an old bearer; AUTV already implements this, preserving the returned device ID and UID in its encrypted session. Expiry renewal happens when media is requested. No change to credential handling or account namespace.
- Reference home/collection/programme discovery performs no required account refresh. Added a registration-free `AbcHttpAccess()` metadata-only transport; its linking, token and signing operations fail before network IO. Removed AUTV's unnecessary token gate from catalogue discovery; discovery failures cannot erase an account as a side effect of renewal. This does not turn on anonymous playback.
- Reference exposes returned live house numbers but establishes no universal Victorian ID list. AUTV uses the operator's approved `region=VIC` and house-number set, now filtering the active guide catalogue to that set. No guessed IDs or five fabricated rows are added. ABC Family/Kids may have different regional/time availability; real discovery must determine available feeds.
- Reference gets programme playlist HLS and separately requests `/auth/hls/sign`, appending returned `hdnea`. AUTV already preserves the original URL, rejects unsupported DRM/ads and never falls back to unsigned media. The signer is still an external approval boundary. Account linking is not that approval.
- Programme metadata is requested during tune. Existing guide EPG consumes returned ProviderProgramme data where available; this change does not invent ABC schedules or assume undocumented timestamp fields.

Exact missing ABC inputs: AUTV-issued product/profile registration (profile key acquired through approved runtime access), allowed VIC house numbers/main house number, and a permitted signer for the exact UTF-8 `/auth/hls/sign?ts=<unix-seconds>&hn=<urlencoded-house>&d=<approved-device-type>` string. AbcHlsSigningRegistration.sign returns a lowercase 64-character hex signature. AbcHlsAuthoriser sends that exact path plus `sig`, receives a plain-text media token, and appends its encoded value as `hdnea` to the original HLS URL. Alternatively supply the approved full `mediaAuthoriser(house, originalHls, accountToken?) -> authorisedHttpsUrl` operation. Obtain registration/device/signing access from ABC or its authorised integrator; no borrowed app keys are accepted.

## W3–W4: Network 10 comparison and changes

Reference: matthuisman/slyguy.addons at `faf507af5df348871bb70939bec34dce7069688b`, packaged slyguy.10play 0.4.5 api.py. Issue 1100 and all 12 comments were read. The maintainer attributes the 2025 fix to DAI, states login is enforced, and reports 720p maximum with embedded advertisements. These are historical reference reports, not AUTV playback evidence.

AUTV already follows config -> generateCode -> validateCode -> alternativeToken/refreshToken -> VIC liveTvEndpoint -> streamUrl descriptor -> assetKey/X-DAI-AUTH. POST uses the public timestamp encoding, while GET requires approved X-N10-SIG authorisation. Catalogue uses stable `row.key`, not a descriptor URL. Refresh rotates the persisted tokens. Network 10 already hands live DAI values and negotiated HLS/DASH to Media3 IMA, preserving the advertising/measurement session and releasing it on channel/account changes. AUTV does not adopt the reference's derived-manifest shortcut, borrowed platform identity, secret, or ad changes.

Published catalogue rows now require an explicitly approved channel key, matching the existing tune-time grant check. Otherwise the guide could expose a channel the adapter must refuse to resolve. There is no evidence that account activation alone grants arbitrary catalogue identities or that removing registration checks is permitted.

Exact missing Ten inputs: AUTV-approved origin allowlist, config query fields, device fields, playback query fields, HLS/DASH format, approved `row.key` set/main key and optional issued DAI network code. `authorise(method, exactUrl, jsonBody?) -> requestHeaderMap` signs the final URL including preserved original query bytes and appended approved parameters. The body is the exact transmitted JSON for POST. Obtain this operation and client parameters from Ten or its authorised integrator; keep signing material on that approved service. GET config, catalogue and descriptor access remains blocked without it. Public POST timestamp generation is not a GET signing grant.

## W5: application composition and isolation

The default AuTvApplication now loads independent AbcRegistrationSource and TenRegistrationSource implementations, selected by class-name-only Gradle properties. Existing approved application subclass and shared source directory injection remain supported. Each source is local runtime composition and returns that provider's configuration; private credentials must not be embedded. A missing source retains Configuration required and hides Connect. A broken source or ABC/Ten adapter construction reports a fixed configuration error without logging the underlying exception or preventing its sibling/working providers from being constructed.

Use `-PautvAbcProviderSources=/approved/abc -PautvAbcRegistrationSource=your.package.AbcSource` and/or `-PautvTenProviderSources=/approved/ten -PautvTenRegistrationSource=your.package.TenSource`, plus `-PautvMediaLab=false :app:assembleFullAppDebug`. These properties identify code, not secrets. There is no safe default registered identity to ship in a stock APK; the configuration guard is retained until the actual authorised source exists.

Network evidence: both `https://api.iview.abc.net.au/v2/home` and `https://10.com.au/api/v1/config` failed at the environment proxy with CONNECT 403 / HTTP 000. Neither host is in the configured HTTP allowlist. This is not a broadcaster HTTP rejection or a geography test. ABC profile/Seesaw, media/CDN, Ten and Google DAI hosts also need approved network access for end-to-end acceptance. No attempt was made to bypass the proxy.

## W6: acceptance handoff

Automated results and artifact hashes are recorded in the delivery report. Keep SettingsScreenshotsTest and the existing UI suites mandatory. No passing fixture is evidence of broadcaster native audio/video.

On an authorised Australian TV installation: link each configured account by QR/code, restart and verify session restoration, discover actual VIC feeds, record names and stable official identities, check real programme metadata, favourite feeds, refresh/removal and reconnect. Tune ABC feeds and verify rendered video and audible audio. Tune Ten feeds and verify video, audio, ad break entry/exit and ongoing IMA events. Switch networks using the remote, return to the guide, expire/renew tokens and disconnect during resolution. Confirm only the affected provider stops, sessions/favourites survive, and diagnostics contain fixed stages/statuses only. Record device, network, date and per-feed evidence. Until then both broadcasters' live playback is unverified/blocked, regardless of build results.
