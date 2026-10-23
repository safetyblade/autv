# ADR: 9Now Seinfeld native provider connector

Date: 2026-10-23
Status: PROVISIONAL NO-GO for implementation; native feasibility remains unproven.
Baseline: safetyblade/autv main 4980ae8ef0b2a08638284e17b265103bb7d32374.
Commission: [PR #66](https://github.com/safetyblade/autv/pull/66), whose complete 63-line brief was read before inspection.

## Decision and gate

Do not implement a native connector or an external-app launcher in this spike on the evidence obtained. There is a technically plausible native HLS route in current upstream code, including user-initiated device activation. There is no demonstrated authorized AUTV session, Australian-network playback response, unmodified ad-supported manifest, or video/audio playback. Do not label this a proven DRM impossibility or say that 9Now only works in its own app.

The immediate investigation blocker is that the attached execution environment remains pending/offline, with no usable shell, Android runtime or physical devices. Its reported desired restricted allowlist does not include login.nine.com.au, api.9now.com.au, tv-api.9now.com.au or slyguy.uk; enforcement readiness was unknown, not established. No attempt was made to bypass that policy. The web reader returned 403 for both official Seinfeld URLs and could not retrieve the live API. These results are not evidence of Australian geoblocking, failed account entitlement or DRM licence rejection.

A supported third-party session/advertising/licence contract has also not been established. The official TV activation instructions describe use in 9Now; they do not document AUTV client registration. An upstream client identifier or a reachable endpoint is not permission to impersonate a first-party client. No public developer integration contract was found in the official sources inspected. This is an unresolved dependency, not a claim that no such contract exists.

Reopen implementation when there is a permitted client/session path and a user-initiated activation test from a supported Australian network, followed by inspection of the unchanged provider descriptor and successful native video/audio. If protected, require an authorized platform-CDM licence exchange. Stop if those permissions cannot be obtained. Do not obtain content keys or remove DRM, geo or ad controls.

## Sources and code actually inspected

- [AUTV baseline](https://github.com/safetyblade/autv/tree/4980ae8ef0b2a08638284e17b265103bb7d32374): native/README.md; app MainActivity.kt, PlaybackModel.kt, Favourites.kt, PlayerScreen.kt, TvDualGuide.kt and cast/CastSender.kt; core-data GuideRepository.kt and Playlist.kt; core-guide FavouriteIdentity.kt; core-player AuTvPlayer.kt and build.gradle.kts.
- [SlyGuy mirror update](https://github.com/matthuisman/slyguy.addons/commit/9e1bcb6ae6dc4207cac32e9229178eb281c749ee): 9Now 0.4.6, committed 2026-10-12. Read the packaged addon.xml, default.py and resources/lib/{api,plugin,constants,settings,language}.py from [the ZIP](https://github.com/matthuisman/slyguy.addons/blob/9e1bcb6ae6dc4207cac32e9229178eb281c749ee/slyguy.9now/slyguy.9now-0.4.6.zip).
- [SlyGuy Common update](https://github.com/matthuisman/slyguy.addons/commit/76d5a37e885f3943e0ec8eede05ee6ccefe8acef): 0.87.2, committed 2026-10-21. Read packaged addon.xml and resources/modules/slyguy/{session,userdata,inputstream}.py plus util.py's process_brightcove. 9Now declares Common >=0.86.23; the inspected newer package is not proof of every installed dependency version.
- [Current package index](https://slyguy.uk/.repo/addons.html) lists 9Now. Its linked image identifies the repository.slyguy package tree. XML/package retrieval through the available web reader failed; equality of the live repository release and mirror 0.4.6 is NOT verified. Mirror freshness alone does not prove current service compatibility.
- [Author's 2021 article](https://www.matthuisman.nz/2021/02/9now-kodi-add-on.html): historical unofficial add-on explanation. Its no-login statement is contradicted by the inspected current package's login gate.
- [Historic predecessor](https://github.com/glennguy/plugin.video.9now): resources/lib/comm.py and play.py, addon.xml and LICENSE. It used reference IDs, Brightcove discovery, direct live sources and a separate Widevine branch. Python 2/Kodi-era design history, not current auth evidence.
- Official [Seinfeld page](https://www.9now.com.au/seinfeld-247) is indexed, and Nine's [release archive](https://www.nineforbrands.com.au/media-release/page/85/) describes the 24/7 channel. Both direct Seinfeld page reads returned 403. Existence has documentary support; current authenticated availability has not been tested.
- Official [TV activation](https://help.9now.com.au/hc/en-au/articles/35830491543833-How-do-I-log-in-to-9Now-from-my-Smart-TV), [DRM explanation](https://help.9now.com.au/hc/en-au/articles/34867838566681-Digital-Rights-Management), [Australian geography restriction](https://help.9now.com.au/hc/en-au/articles/34179193418905-I-can-t-play-videos-due-to-geographic-restrictions), [Android TV support](https://help.9now.com.au/hc/en-au/articles/34863558826393-What-Android-TV-devices-can-I-watch-9Now-on), [mobile support](https://help.9now.com.au/hc/en-au/articles/34861442298137-Is-9Now-available-on-mobile-and-tablet-devices), and [production terms](https://login.nine.com.au/terms?client_id=nine-web&newsletter_client_id=nineatnine).

## Observed upstream sequence (static evidence, not a successful API trace)

1. api.new_session creates a requests-based Session with a desktop browser user agent, installs a saved bearer token and registers refresh upkeep. No password form exists in this 9Now package.
2. On explicit Login, device_code logs out old state, then POSTs login.nine.com.au/api/device/code using the upstream device client identifier. plugin._device_code shows the provider activation URL and code, polls at the supplied interval and stops on cancellation/expiry.
3. device_login GETs /api/device/token with the activation/device codes. It saves access token, refresh token and expiry in Common userdata; refresh POSTs /api/device/refresh-token. Saved tokens are installed into Session Authorization. Common userdata delegates to settings storage; inspection does not establish encrypted storage.
4. channels refreshes auth and GETs api.9now.com.au/ctv/livestreams with device=web, streamParams=web,chrome,windows, region and offset=0. It reads data.getLivestream, including channels/events. Returned referenceId selects the channel; slug/region is used in upstream IPTV identity. Listings carry name/startTime/endTime. No current Seinfeld referenceId was resolved.
5. play_channel is login-gated and fetches the catalogue again, matching referenceId and reading stream.url. It returns HLS(live=True) with the configured user agent. There is no explicit Widevine declaration in this ordinary live-channel branch. This does NOT establish that Seinfeld's current manifest is clear or HLS-compatible on every device.
6. IMPORTANT: play_channel tries to decode alternate/raw URLs from server-side-ad URL parameters and otherwise falls back to the returned stream. Do not copy that transformation, URL logging, or removal of ad routing. AUTV must retain the provider-returned path and fulfill any required ad/session callbacks.
7. The separate event/VOD path asks Brightcove for a descriptor using a packaged policy header; events may append SSAI parameters. Common process_brightcove prefers clear HLS, then MP4, then Widevine cenc sources, using key_systems licence URLs and optionally descriptor authorization. inputstream.Widevine configures Kodi InputStream Adaptive and Android's built-in Widevine; it does not grant licence rights. Actual codecs, encryption, licence headers and server response remain unobserved.
8. Common Session can maintain an in-memory cookie jar; the 9Now constructor provides no persistent cookies_key and contains no explicit persistent cookie handoff. The demonstrated design is bearer/session based, not browser-cookie harvesting. Logout deletes saved token/expiry records and recreates Session.

The endpoints are consumer application interfaces evidenced by unofficial code. They are not established public, supported integration APIs. Metadata/schema stability and refresh semantics remain fragile.

## AUTV integration surface

MainActivity retains PlaybackModel across UI recreation and owns lifecycle/PiP. PlaybackModel owns the one AuTvPlayer, catalogue refresh, selection, integer tune targets, last-channel preference, per-number failures, Full EPG recovery and Cast ownership. Preserve this ownership; do not create a provider Activity/player or duplicate guide screens.

GuideRepository consumes public JSON then reconciles the public playlist. Channel stores number, stream URL, tvgId and metadata with no provider/auth/capability type. A streamUrl currently gates guide playability. Keep the optional provider catalogue out of this public cache and reconciliation: a provider can be selectable for sign-in without possessing a stream URL.

AuTvPlayer forces APPLICATION_M3U8; core-player includes Media3 1.11.1 HLS, no DASH dependency or provider DRM/header configuration. Native HLS is plausible; DASH would need its module and typed MediaItem configuration only if actually returned. No current code validates a provider licence exchange.

Favourites stores canonical identities locally in SharedPreferences; FavouriteIdentity prefers tvgId, otherwise hashes a canonicalized URL. Provider favourites must use namespaced provider/channel identity, never a signed URL. Existing FAST keys must remain unchanged.

PlayerScreen and TvDualGuide already route CH keys, short/long OK, Back, Quick Guide and Full EPG. The deferred short-OK handler prevents tuning on a favourite long press. Preserve that handler, focus, phone touch, offline UI, and lifecycle pause/resume. Kogan Android TV support is documented by 9Now; hardware key mapping, codecs and CDM support are device tests, not inferred from the brand. AUTV min SDK is 24; official 9Now mobile support currently starts at Android 8.

CastSender uses Google's Default Media Receiver with HLS content IDs and no provider auth/licence handoff. Mark 9Now Cast unavailable until an authorized receiver route is demonstrated; never send account tokens or a local session URL to the default receiver.

## Proposed adapter contract (design only; not implemented)

- ProviderChannel: namespaced ProviderChannelId(provider, officialReferenceId), display metadata, optional region, optional EPG and capabilities. Resolve Seinfeld's ID from current authorized metadata; do not invent it from its web slug.
- Separate catalogue state, account state (NotConnected, Connecting, Connected, SessionExpired, Unsupported) and resolve/play outcome. Stable identity survives reauth, restart and refresh.
- ProviderPlaybackRequest: a short-lived, non-serializable request carrying MIME type, unchanged session-scoped URI, narrowly scoped media headers, optional authorized DRM configuration and playback/ad constraints. Give this type no generated toString containing secrets.
- Typed failures: AuthRequired/Expired, RegionDenied, StreamUnavailable, DrmUnsupported, LicenceDenied, UnsupportedIntegration, Offline. Missing EPG stays a neutral schedule fallback.
- Playback target distinguishes FastHlsChannel(existing Channel) from NineNowChannel(ProviderChannelId). The FAST branch invokes the current holder.play path unchanged. Provider resolution happens in the retained model, with cancellation/generation checks so late responses cannot replace a newer FAST tune.
- Add an opt-in Australian TV section within existing guide/player UI. Do not assign/change public channel numbers or use a fake stream URL to unlock rows. An internal typed identity must replace number-only assumptions for optional provider routing.
- Resolve before replacing the last working FAST item. If provider playback fails, cancel outstanding provider work, clear provider media/DRM state and restore that FAST channel through the existing tune path. Re-entry resolves a fresh request; pause/Back preserve the existing lifecycle contract.
- Sign-in is user initiated using a provider-permitted activation/client flow. Keep access credentials in memory where possible; any necessary refresh credential must be device-local, protected with Android Keystore-backed encryption, excluded from backup and crash/network logs. No passwords, browser cookie imports, background harvesting or hosted proxy.
- Sign-out cancels polling/refresh/resolution, destroys account records and cached sensitive descriptors, clears provider playback and returns to FAST. It must not erase FAST favourites or catalogue caches.

## Licence, terms and secret risks

The inspected 9Now and Common packages have empty licence elements and no licence file in their package listings; the mirror repository reports no licence. This does not establish permission to translate/reuse their implementation. The predecessor is GPLv3, requiring separate compatibility consideration if copied. This spike copies no upstream implementation.

Nine's inspected production terms limit content use to personal noncommercial streaming, prohibit circumventing DRM/technical measures including ad blocking, and restrict use outside Australia. They are not an AUTV API or SDK grant. Retain ad delivery and geography. Provider-supported client onboarding/permissions and ad obligations must be clarified before shipping; do not assert a blanket ban on native clients from missing documentation.

No user passwords, user bearer tokens, signed playback URLs or cookies were requested or retrieved. Do not adopt upstream general request logging for auth/playback. Log only allowlisted outcome categories, HTTP status, format and coarse device capability; never raw URLs, query strings, headers, activation/device codes, descriptors or exception bodies. The previously exposed password mentioned by the brief must be reset privately and never reused here.

## Validation and reopening checklist

| Evidence level | Result |
| --- | --- |
| Full PR #66 brief | Read completely |
| Current mirror 9Now/Common code | Studied as identified above |
| Current live repository version | 9Now listed; exact package version not verified |
| Official channel existence | Documentary/index evidence; direct pages returned 403 |
| Authenticated Australian catalogue/API | Not demonstrated |
| Seinfeld official referenceId | Not resolved |
| Actual manifest/codecs/protection | Not obtained |
| Authorized licence exchange | Not attempted/demonstrated |
| Video and audio inside AUTV | Not played |
| Android build/tests | Not run; no app code changed and execution environment unavailable |
| Physical Kogan TV / phone / tablet / Cast | Not tested |

After prerequisites are resolved, test activation cancellation/expiry, refresh denial and sign-out deletion; catalogue parsing without sensitive fixture URLs; resolve cancellation/late responses; missing stream/region/DRM/licence failures; unchanged ad playback; first frame plus sustained video/audio; pause, Back and re-entry; return to FAST; metadata refresh, favourites, remote long OK, CH keys and both guides; phone/tablet lifecycle and rotation. Run the focused existing core-data/core-guide suites, assembleDebug and appropriate instrumentation. Physical Kogan and mobile checks must be reported separately from emulator/build success. Do not introduce live account secrets into CI fixtures.

No application, playlist, generated guide, channel number, EPG, workflow or six-hour FAST refresh change is needed for this decision. There is no migration and no runtime regression introduced. Recheck latest main before any future implementation branch.

## Recommended next action and fallback

First obtain a permitted 9Now client integration path, then use a reachable Australian test device/network with the account owner completing official activation privately. Verify the unchanged ad-supported stream and platform licence if required. If that gate passes, create the smallest isolated one-channel native prototype and draft PR, without merging.

If native authorization or ad/licence integration cannot be supplied, recommend the official 9Now Android TV/mobile app as a fallback. A separately requested, verified official deep link may be considered later; none is implemented and external playback is not acceptance of Option B.
