# Network 10 native connector: current investigation and decision

Investigated 24 October 2026, after the ABC workstream in
[draft PR #81](https://github.com/safetyblade/autv/pull/81). This is an independent
Network 10 workstream, following the full
[PR #80 Ten brief](https://github.com/safetyblade/autv/blob/7f610a1c1aeb46db6faf89794007ae07b4f80c54/docs/spikes/ten-codex-next.md)
and [PR #74 decision](https://github.com/safetyblade/autv/blob/7e2499cc98b04a9172275e3bdeea51b212a14053/docs/spikes/ten-abc-seven-native-decision.md).
Seven remains paused with its account/catalogue and HTTP-400 diagnostics intact.

**Decision: NO-GO for runtime enablement on current evidence.** A native player
and normal commercials are technically compatible, but no authorised AUTV live
catalogue/account/playback contract was established. Do not copy application
signing material, impersonate an official device family or substitute a public
playlist. This report is the concrete deliverable; it adds no nonfunctional
activation button or speculative SDK integration.

## Current official service and account experience

The service is branded **10**, at `https://10.com.au`, with older tenplay support
and package names still in use. Current official sources:

- [Activate 10 on your Smart TV](https://helpdesk.tenplay.com.au/support/solutions/articles/16000103948-activate-10-on-your-smart-tv),
  updated 10 February 2026: supported TV apps show a five-character code; phone
  activation/QR and manual `https://10.com.au/activate` complete consumer linking.
  The page lists Android TV app >=6.2.0. It does not publish third-party API
  registration, a prefill parameter, polling contract or an AUTV client grant.
- [Persistent sign-in help](https://helpdesk.tenplay.com.au/support/solutions/articles/16000239775-why-am-i-constantly-being-asked-to-sign-in-activate-my-tv-),
  updated 20 August 2026: TV activation should normally persist; app updates can
  occasionally require renewed login. Consumer persistence is not a token schema.
- [Location settings](https://helpdesk.tenplay.com.au/support/solutions/articles/16000112112-location-settings)
  describes IP/capital-city selection. [State correction help](https://helpdesk.tenplay.com.au/support/solutions/articles/16000134051-10-play-thinks-i-m-in-the-wrong-state-how-do-i-change-it-),
  updated 10 March 2026, describes accurate account postcode and mobile region
  selection. A Victoria feed must follow supported location rules, not a fake IP.
- [Why 10 has ads](https://helpdesk.tenplay.com.au/support/solutions/articles/16000125615-why-does-10-have-ads-)
  confirms ad-supported live/catch-up viewing. [Live black-screen help](https://helpdesk.tenplay.com.au/support/solutions/articles/16000134050-i-get-a-black-screen-when-i-watch-live-tv)
  identifies ad blocking as a cause of failure. Normal ads are required here.

The current [terms page](https://10.com.au/terms-of-use) could not be fully fetched
(403 through the web reader). Do not claim its complete terms were inspected or
that consumer account ownership constitutes third-party platform authorisation.
No public developer registration or documented AUTV interface was found in the
sources searched; that is an unresolved access dependency, not proof none exists.

## Actual packaged SlyGuy implementation

Latest listed package in the inspected mirror remains **slyguy.10play 0.4.5**:
[immutable package](https://github.com/matthuisman/slyguy.addons/blob/958fc666555f35d79ee4191b7a2533aa4e001e97/slyguy.10play/slyguy.10play-0.4.5.zip),
introduced 14 July 2026, Git blob `8a1afd2b14fb4707d4d5464b87827aea7c32f8c1`.
Downloaded the actual ZIP again; its api.py, plugin.py and constants.py match the
inspected extracted files. addon.xml requires shared SlyGuy >=0.86.59. The shared
0.87.3 source previously inspected with Nine/SBS/Seven is an architectural helper,
not a substitute for Ten's protocol or permission. No embedded key is reproduced
in AUTV or this report.

| Stage | Exact package code | Mechanism and limitation |
| --- | --- | --- |
| Request/config | `resources/lib/api.py:generate_x_n10_sig` 24–29, `_request` 48–63, `_config` 41–46; `constants.py:CONFIG_URL` | GET `https://10.com.au/api/v1/config` and other GETs are signed with X-N10-SIG using embedded HMAC material. POST uses a distinct X-Network-Ten-Auth helper. Config declares Android/NVIDIA attributes; request headers include UAP/feature flags. These are not registered AUTV attributes. |
| Challenge | `api.py:device_code` 147–166 | Config's authConfig.generateCode receives a deviceIdentifier and machine/system/platform/version fields. Returns code plus absolute expiry. Client subtracts a margin and polls every five seconds. No verified response/error/denial schema or supported code-prefill for AUTV. |
| Approval/renewal | `api.py:device_login` 179–190, `_refresh_token` 192–202, `_save_jwt` 204–209 | POST authConfig.validateCode with code/deviceIdentifier/expiry; only returned jwt establishes success. Saves alternativeToken, refreshToken and expiresIn; refresh posts to configured authConfig.refreshToken. Browser confirmation alone is not connection. |
| Region/catalogue | `api.py:state`, `_get_state`, `live_channels` 73–101; `settings.py:State` | Signed config provides geoLocationEndpoint/liveTvEndpoint. Uses detected state or configured state including VIC; appends state and schedule limit. Actual owner VIC catalogue not retrieved. |
| Channel selection | `plugin.py:live_tv` 313–337 | Iterates channels, name/logo and schedule start/end/title. Passes provider row.streamUrl to api.play. This is a resolver address, not proof of a playable manifest or stable favourite ID. |
| Descriptor | `api.py:play` 107–128 | Refreshes session; sends saved bearer to signed playback GET, declares device=Tv, platform=lg, appVersion=v1. Reads dai and X-DAI-AUTH. An Android AUTV adapter must not copy the LG identity as an approved context. |
| Live session | `api.py:play` 130–145 | With dai.assetKey, POSTs to Google DAI live streams endpoint, passes returned auth-token when present. Derives a master name from id3_events_url. AUTV needs an approved returned stream/session descriptor; do not assume that filename derivation is a supported contract. |
| Player | `plugin.py:play` 389–405 | SlyGuy live HLS with a DAI-specific user agent. Optional non-live quality middleware exists; it is not required live architecture and is not adopted. |

The inspected live flow does not call shared `util.process_brightcove` for source
resolution. Shared Session/request and InputStream HLS utilities cannot turn an
unregistered Ten client into an authorised one, nor implement Android DAI
measurement simply by returning an HLS URL.

### Working reports versus current proof

[SlyGuy issue #1100](https://github.com/matthuisman/slyguy.addons/issues/1100)
is closed. September 2025 comments explain the old-app failure and a transition
to Google DAI in 0.3.x, including embedded commercials and a user confirmation on
0.3.2. This is historical evidence for the DAI route, not evidence that 0.4.5 works
on AUTV today or fulfils all measurement obligations. Ad/quality workarounds
mentioned in that discussion are excluded from this design.

## Independent maintained comparison: not every route uses X-N10-SIG

Read current
[yt-dlp tenplay.py at 3d28cf4780fa88689fc50010d72cf2f40ce3a3b8](https://github.com/yt-dlp/yt-dlp/blob/3d28cf4780fa88689fc50010d72cf2f40ce3a3b8/yt_dlp/extractor/tenplay.py),
21 October 2026. `TenPlayIE:_call_playback_api` (187–207) uses a web playback GET
under `https://10.com.au/api/v1/videos/playback/{content_id}/` with a Samsung
platform query and optional bearer, without SlyGuy's X-N10-SIG. `_real_extract`
(209 onward) consumes VOD contentSourceId/videoId and X-DAI-AUTH to obtain a Google
DAI on-demand stream. Its login takes username/password, and it filters ads and
attempts format upgrades. None of that is ported.

This is a material qualification to PR #74: **the inspected SlyGuy route requires
embedded signing, not necessarily every Ten route**. The alternate extractor does
not establish current live catalogue, TV linking, approved AUTV platform fields,
normal-ad lifecycle or provider permission. A downloader's working VOD output is
not a native live-TV integration. No passwords are requested, no signature or
approved device identity reconstructed, and no ad-removal logic is adopted.

## Original advertising and Media3 decision

The reference supports a plausible original **Google DAI HLS** live architecture.
It does not prove the current owner's regional descriptor, live stream format,
licence/protection or session lifetime. Do not assume all Ten streams are clear,
that a DAI auth token is a Widevine licence, or that a successful HTTP request is
video/audio success. Preserve original broadcaster ads and regional programming.

Google's [official Media3/IMA DAI guide](https://developers.google.com/ad-manager/dynamic-ad-insertion/sdk/android/get-started/exoplayer-extension)
uses ImaServerSideAdInsertionMediaSource with an AdsLoader, source factory,
provider stream inputs, lifecycle/state restoration and ad events. This is the
candidate supported Android architecture after Ten approves the session interface.
A raw HLS source, derived master URL or generic SSAI wrapper does not establish
consent, ad metadata/beacons, session expiry/renewal or licence obligations.

AUTV currently constructs provider-scoped HlsMediaSource and ephemeral media
requests. Its working Nine/SBS adapters keep accounts, discovery and tune-time
resolution separate. Retain that structure; add a Ten-specific authorised DAI
request/context only when real descriptors establish the need. Do not add an IMA
SDK, backend, generic proxy or broad player changes speculatively. Any Widevine
path must use platform MediaDrm/Media3 with provider-issued licence configuration,
never key extraction or security-level downgrade.

## Smallest implementation once unblocked

- Obtain an approved AUTV configuration/signing or alternative native client
  interface. Do not use the package's secret, fabricated device fields or a
  website session belonging to another application.
- Add an isolated Ten adapter and endpoint/header allowlist. Use provider-issued
  challenges and exact poll/expiry/error semantics. Generate QR locally for the
  official bare activation URL; manual code remains visible. Prefill needs evidence.
- Reuse ProviderAccountController, TV focus and encrypted noBackupFilesDir storage
  with a Ten-only alias/file. Preserve pending deviceIdentifier/absolute expiry as
  required. Refresh rotation and sign-out must affect Ten only; no password fields.
- Discover main Victorian Ten through approved catalogue metadata and establish a
  stable provider ID. Do not use display titles, public channel numbers or transient
  signed stream URLs as private/Favourites identity. Fetch fresh session at tune.
- Preserve original descriptor, commercials, ad event/consent lifecycle, session
  renewal and scoped API/CDN/licence headers. Expand to returned companion channels
  only after actual main Ten video/audio and a complete commercial break succeed.

## Concrete access request (draft; not sent)

Through [Network 10 contact](https://10.com.au/contact-us) or
[official support](https://helpdesk.tenplay.com.au/support/home), request routing to
platform/API integrations for AUTV's Australian Android TV live-TV proposal:

1. A registered client method for configuration, TV-code activation, token renewal
   and live playback; whether an approved unsigned/native alternative exists to
   the package's signing path. Request documentation/client onboarding, not another
   app's secret or a user's session.
2. Victorian live catalogue/schedule, stable identities, supported accurate region
   selection and entitlement/error schemas.
3. Original DAI live descriptor and supported IMA or REST session interface,
   consent/advertising identifiers, ID3/events/beacon obligations, expiry/renewal,
   permitted device context, and any DASH/Widevine licence requirements.
4. An authorised first-channel test procedure including normal commercials and
   return to programmes, branding/accessibility obligations and supported TVs.

No external message was sent. Consumer activation help is not an API grant.

## Validation and acceptance

| Gate | Current result |
| --- | --- |
| Supported AUTV client/registration | Not established |
| Account linking/expiry/refresh in AUTV | Not attempted; source trace only |
| Main Ten VIC discovery/entitlement | Not verified |
| Authorised descriptor/DAI session | Not obtained |
| Original manifest, video and audio | Not tested |
| Normal commercials and programme return | Not tested |
| Continued playback/Guide/Back/CH/Favourites/recovery | Acceptance plan only |

After approved access, test real Australian Android TV/Kogan main Ten: activation,
pending recreation, rejection/expiry, refresh and scoped sign-out; main VIC
identity and original descriptor; media/DRM/ad acquisition; video/audio; an entire
commercial break and return to programming, repeated live playlist updates,
Guide/Back/CH/Favourites, offline recovery, retained sessions across restart and
in-place update, then Nine/SBS/FAST regression. Synthetic clips do not prove any
broadcaster media gate.

This work changes only this document. Whitespace and changed-path checks apply;
there is no implementation to Android-build, no new APK and no claim of synthetic
or real playback success. Current enforced runtime policy excludes Ten API/CDN
hosts; none was bypassed. Official help was researched through available web
tools; no owner account, challenge, token, catalogue, manifest or physical TV was
accessed. ABC code, Seven's preserved latest diagnostic, existing encrypted
sessions, QR linking, public playlist, six-hour refresh and all FAST remain intact.
