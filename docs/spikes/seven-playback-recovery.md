# Channel 7 playback recovery: evidence and decision

24 October 2026, Australia/Sydney. Continuation of PR #75 at `2217ecc`.
Status: single-channel experimental original-HLS path; identity/ad/player recovery
revision follows the original research below. The earlier Yospace-only gate is
superseded by the final section;
real Channel 7 video/audio and continued playback are **awaiting owner test**.
No claim that a fixture, redirect or connected account proves playback.

## Sources inspected, in order

All SlyGuy package code below was extracted from actual ZIPs at immutable mirror
commit [485b3f4b8aae9080138bca5d4cf87bbf877c34d2](https://github.com/matthuisman/slyguy.addons/tree/485b3f4b8aae9080138bca5d4cf87bbf877c34d2).
Latest listed versions in that mirror: 7plus **0.5.8**, IPTV AU **0.20.5**, shared
SlyGuy **0.87.3**. The [current public index](https://slyguy.uk/.repo/addons.html)
was read and lists both services; its XML could not be retrieved through the web
tool. Thus these are latest available mirror packages, not a verified installed
Kodi combination. Empty package reuse-licence metadata means no implementation
code is copied into AUTV.

- [A: slyguy.7plus 0.5.8 ZIP](https://github.com/matthuisman/slyguy.addons/blob/485b3f4b8aae9080138bca5d4cf87bbf877c34d2/slyguy.7plus/slyguy.7plus-0.5.8.zip):
  `resources/lib/api.py`, `plugin.py`, `constants.py`, `addon.xml`.
- [Shared script.module.slyguy 0.87.3 ZIP](https://github.com/matthuisman/slyguy.addons/blob/485b3f4b8aae9080138bca5d4cf87bbf877c34d2/script.module.slyguy/script.module.slyguy-0.87.3.zip):
  `resources/modules/slyguy/util.py:process_brightcove`, `inputstream.py:HLS/MPD/Widevine`,
  `session.py:RawSession/Session.request/gz_json`, `gui.py:Item.get_li`,
  `plugin.py:Item.play` and `addon.xml`.
- [B: plugin.video.au.freeview 0.20.5 ZIP](https://github.com/matthuisman/slyguy.addons/blob/485b3f4b8aae9080138bca5d4cf87bbf877c34d2/plugin.video.au.freeview/plugin.video.au.freeview-0.20.5.zip):
  named **IPTV AU** in `addon.xml`; `resources/lib/plugin.py:get_channels/play`,
  `settings.py:DATA_URL/Region`. The current public `i.mjh.nz/au/all/tv.json.gz`
  metadata was inspected separately; it is mutable, not a packaged historical snapshot.
- [C: historical Aussie Add-ons 7plus 4.0.1](https://github.com/glennguy/plugin.video.plus7/tree/8a807e7ffefa397da8978e0b5ad660548cb41c36),
  18 February 2018: `resources/lib/comm.py:get_live/get_program`,
  `play.py:play`, `live.py`, `addon.xml`, README. Also inspected
  [aussieaddons/repo tree ecc87fcd264a089781fac609ed6f14c54f67f3dd](https://github.com/aussieaddons/repo/tree/ecc87fcd264a089781fac609ed6f14c54f67f3dd);
  that tree contains no plus7/7plus package. Historical architecture is not current API proof.
- D: AUTV's NineNowAdapter, SbsAdapter, SevenPlusAdapter, SevenTransport,
  ProviderAccountController, PlaybackModel and AuTvPlayer at PR #75 head.

## Comparative playback trace

| Stage | SlyGuy 7plus 0.5.8 + shared 0.87.3 | IPTV AU 0.20.5 | AUTV PR #75 baseline |
| --- | --- | --- | --- |
| Channel identification | `plugin._get_live_channels`: `content/live-tv`, `ChannelItem.channelId`; `_get_url` takes `channel-id` query or final path component from `contentLink.url` | `plugin.get_channels`: region JSON keyed by third-party slug; displayed channel number/name/network | `SevenPlusAdapter.channels`: official channel ID/name, but discards content link needed for playback |
| Authentication | `api.device_code/device_login/_refresh_token`: Seven device auth/token/refresh; `play` uses saved account bearer | `plugin.play` has no Seven login guard/token exchange; generic Session downloads metadata | Encrypted account and pending challenge, refresh and provider-scoped sign-out; owner confirmed connection/discovery |
| Live media resolution | `plugin.play_channel` → `api.video_player(slug)` → `videoPlayer.videoUrl` query's accountId/referenceId → `api.play` GET videoservice playback with bearer + current market | `plugin.play` looks up slug in all-region JSON and uses `channel.mjh_master`; **does not call the standalone Seven resolver** | `resolve` validates provider/session then deliberately throws PLAYBACK_UNSUPPORTED; transport has no player/playback API routes |
| Playback manifest | `util.process_brightcove` reads original `media.sources[].src`; prefers clear HLS, then MP4, then CENC Widevine; HLS version ordering | `mjh_master` plus metadata headers; manifest defaults HLS. Current Sydney master HEAD returned 302 to a Yospace HTTPS HLS address, not a fetched/played manifest | HLS player exists, but no Seven media descriptor is requested |
| DRM/licence | `process_brightcove` obtains Widevine licence URL from source key_systems, not a fabricated endpoint; optional source authorization token. `gui.Item.get_li` maps licence/challenge/headers into IA properties | `plugin.play` assigns MPD or HLS configuration; no Widevine licence setup in this path | Provider request has no DRM configuration; player explicitly requires HLS |
| Ads/session | `api.play` requests `deliveryId=csai`; reported live issue concerns SSAI. Source includes fixed postcode, tracking/verification fields: these are not an AUTV contract. `process_brightcove` itself does not implement a separate CSAI SDK | Metadata/redirected manifest supplies route; no broadcaster ad SDK in `play`. Preserving SSAI content is distinct from proving all beacon obligations | No Seven session/media/ad implementation |
| Player | `plugin._play` sets every live inputstream `live=True`, `force=True`. Shared HLS.check consequently requires IA, independent of normal HLS setting. `gui.Item.get_li` sets manifest/stream/common headers, MIME, licence data, live delay (24 on Kodi 21+); `plugin.Item.play` calls setResolvedUrl | `inputstream.HLS(live=True)` with default `force=False`; HLS.check follows user's live-HLS setting, so native Kodi fallback is possible. MPD still uses IA | Retained Media3 HlsMediaSource; no Kodi IA setting/patch can be directly ported |
| Video/audio evidence | September 2025 user report says these main live channels crash; no agent-run Kodi playback | Same user reports main live channels work. Current metadata + one redirect observed, **not** current video/audio verification | Seven owner confirmed account/catalogue only; owner separately reports Nine/SBS native success; no agent Seven playback |

### Why IPTV AU reportedly worked

[Issue #1090](https://github.com/matthuisman/slyguy.addons/issues/1090) was read
with its comments. The author suspected IA/SSAI incompatibility and suggested
disabling IA for HLS; this was a diagnosis suggestion, not a demonstrated fix.
[Issue #1092](https://github.com/matthuisman/slyguy.addons/issues/1092) remains open
with no comments at inspection. It reports working IPTV AU for Seven/7two/7flix/
7bravo/7mate but no exact manifest comparison or player log proving causation.
The verified differences are **metadata/redirect source routing** and **unforced
versus forced HLS IA**. Either/both may explain the result; claiming a known root
cause would exceed the evidence. The current JSON supplies headers and HLS routes
but not proof that its current source equals September 2025's source.

Checked labelled and text-matched open SlyGuy issues: #1092, watchlist #1282,
[ad-break freeze #1193](https://github.com/matthuisman/slyguy.addons/issues/1193)
and [quality-dialog cancellation #1085](https://github.com/matthuisman/slyguy.addons/issues/1085).
#1193 remains open: April 2026 comments suspect SSAI and recommend newer Kodi ad
support, without a confirmed resolution. This is material evidence that continued
playback through real ad transitions must be tested, even if initial video works.
Private logs/attached stream URLs from those issues were not reused.
[InputStream Adaptive #2061](https://github.com/xbmc/inputstream.adaptive/issues/2061)
is closed (updated June 2026) and concerns VOD m4f parsing; 0.5.8's Kodi<21 VOD
exception references it. It is not evidence that live SSAI #1092 is fixed.

## Owner identifier

`01a166f5-2aca-7276-91c9-a174b18520aa` is the upload-group component in the
attachment paths supplied in this conversation. It was not found in AUTV source
or the fetched IPTV AU metadata. There is **no evidence it is a Seven object**;
it must not be sent as channelId/accountId/referenceId or interpreted as HLS.
No authenticated owner catalogue is accessible to the agent. Actual identity must
come from the owner's fresh catalogue, following the exact chain above.

## Initial architecture decision before implementation (superseded below)

Implement **Channel 7 only**, selected through the actual row's reviewed content
link/channel slug, retaining its official channelId and guide number/favourite key.
Re-fetch current market and catalogue per tune; verify the selected ID exists;
resolve player metadata and extract account/reference IDs from that returned
videoUrl. Do not follow videoUrl as an arbitrary network destination. Request the
provider playback API with the saved Seven bearer scoped solely to that endpoint,
truthful AUTV User-Agent, local device UUID, detected market. Accept only the supported clear HLS result from the returned
default descriptor. Do not copy fixed postcode, official-app User-Agent, tracking ID or
claim SDK verification. If omitted fields are mandatory, report the provider
failure; do not synthesize them or disable location verification.

Use only an original clear HTTPS Yospace SSAI HLS source actually returned in the
fresh descriptor, with no key_systems/protection and no explicit separate ad
requirements. Preserve every query field, quality path, ad/discontinuity segment
and normal Media3 playlist reload. Reject protected/DASH/MP4/unknown ad paths;
do not select an ad-free alternative. Yospace hosting is evidence of the SSAI
candidate, not proof that all tracking/consent obligations have been satisfied.
No third-party metadata/redirect becomes an AUTV resolver and no cookies/tokens
are imported. Media3 HLS is already present; do not change the shared player or
introduce a speculative Widevine/DASH implementation before it is needed.

Supported integration/client permission and ad-session requirements remain
unconfirmed. The inspected [Seven terms, updated 4 June 2024](https://support.7plus.com.au/hc/en-au/article_attachments/27273316114445)
require authorised location and personal use and prohibit technical-control
circumvention and ad blocking (clauses 9–15). They are not an AUTV API grant.
Seek confirmation from Seven for AUTV playback client/API access, approved
location/device fields and SSAI tracking/consent obligations; if protected content
is required, obtain its authorised licence contract too. No exact third-party
registration process was found, so none is invented and no permission email sent.
Keep proof draft pending those requirements and actual device playback.

## Acceptance and expansion gates

Obtain a fresh original descriptor without logging account tokens, signed media
URLs or raw bodies. A successful descriptor or manifest is only one stage. Owner
must verify correct Channel 7 identity, video **and** audio, continued playback
through ad breaks/playlist reload/expiry, Back/Guide/CH+/CH−, offline/region/expired
session errors, sign-out cancellation and FAST recovery. No physical test is
claimed. Only after that proof assess each official 7two/7mate/7flix/7bravo row;
do not enable them by name or assume shared protection/ad behaviour. No public
M3U, FAST refresh, public guide, numbering/order or unrelated providers change.

## Implementation and verification record

`SevenPlusAdapter.catalogue/resolve/channelSlug/playbackIdentity` now implements
the fresh identity chain. The proof recognises the main network by its exact
current display name (7/Seven/Channel 7/7HD), not a fabricated channel ID; other
rows remain listed but unsupported. An official content link and unambiguous
account/reference pair are mandatory. `SevenLiveSource.select` accepts only the
original clear Yospace candidate. `HttpSevenTransport.playback` sends the Seven
bearer only to the exact videoservice playback path, with no redirects and no
media/CDN bearer. The API request deliberately omits undocumented tracking,
postcode, delivery-mode and SDK-attestation fields. Provider rejection of those
omissions is a real failure to investigate, not something to conceal.

There is no persistent signed source cache. Every tune/retry resolves afresh;
normal live-playlist reload is Media3's existing HLS implementation. No automatic
Seven-specific renewal endpoint has been established: a terminal media/session
expiry still requires the existing retry/reconnect path. Stable long playback
and ad transitions are not yet proven. Explicit separate ad configuration,
protection or unfamiliar clear sources fail closed without a substitute URL.

Local validation: 107 JVM tests passed, app and instrumentation APKs built. Added
fixtures cover the fresh identity chain, original SSAI URL, unsupported sibling
channels, malicious/duplicate query IDs, source/DRM/ad rejection, API-only bearer
scope and terminal Seven session handling. Existing account/QR/store tests remain.
Two new Android tests run the actual adapter with simulated API responses, then
substitute bundled local video after resolution to exercise retained-player
routing, video decoder output across a synthetic HLS discontinuity, pause/resume,
simulated Guide/Back/CH+/CH− keys, Activity recreation, sign-out/FAST recovery and
descriptor denial. The bundled
clip has no audio, so even local audible-audio verification is not claimed. This local substitution is **not** Seven video/audio or real SSAI evidence.
CI also runs the existing Nine/SBS/settings/QR/encrypted-store and APK-upgrade suite.

Observed real-network evidence in this task stops at IPTV AU metadata and one
redirect status/target host. No Seven account was used by the agent; official
Seven API and Yospace CDN hosts are outside the execution environment's enforced
HTTP allowlist. No manifest, licence, video/audio, ad break, physical phone/TV or
remote result is claimed. Owner-provided connection/discovery success remains
valid evidence for PR #75 only. Keep the playback proof draft and test Channel 7
before enabling siblings. This does not yet meet the mission's playback success
criterion.

## Owner failure follow-up

The owner tested the first playback APK and supplied a screenshot showing failed
Seven and 7mate rows. Seven's generic unsupported message does not establish
which resolution step failed; no real playback success is claimed. 7mate remains
outside this proof. A source review found that AUTV required an absolute content
link, whereas SlyGuy's `_get_url` also handles relative links. AUTV now resolves
relative metadata links against the official Seven origin before extracting the
slug. It never follows the content link as a network request, and still rejects
external/protocol-relative authorities, credentials, HTTP and ambiguous queries.
This fixes a demonstrated parser limitation, **not a confirmed cause of the
owner's failure**; no owner payload was obtained.

Fixed, non-sensitive failure-stage codes now distinguish `7-CAT` (catalogue),
`7-CHANNEL` (missing/out-of-scope row), `7-LINK` (content link), `7-PLAYER` (player
metadata), `7-IDENTITY` (account/reference extraction), `7-DESCRIPTOR` (media API)
and `7-SOURCE` (source selection). The UI preserves sanitized HTTP status and
failure category. No URL, token, account/reference/channel ID or response body is
included. These codes let the next owner test identify the actual gap without
requesting private API dumps. This update does not broaden source/DRM/ad handling
or enable sibling channels. Real Seven video/audio and ad-break stability remain
unverified. The owner should select **Seven**, and report the full displayed
error including the fixed code if it fails.

## Identity, normal advertising and native-player revision

The owner's latest evidence is **7plus Connected, activation complete, 53 channel
rows and failure `7-IDENTITY` on Seven**. This proves the main row, link and player
metadata stages completed and that the former `playbackIdentity(videoUrl)` failed
before `transport.playback`. It does not prove that either the descriptor or
manifest was requested. It does not identify which internal check rejected the
value, because the previous build collapsed them into one code.

### Immediate identity mismatch and evidence limit

Re-read the extracted standalone package, especially `plugin.py:play_channel`,
`api.py:video_player/play` and shared `util.py:process_brightcove`. Standalone
extracts the query's `accountId` and `referenceId` and makes a separate fixed Seven
playback request. AUTV instead required the metadata URL itself to have one exact
host/path, rejected any repeated or valueless query parameter, and limited the
reference to an ASCII punctuation regex. Those extra restrictions do not come
from the inspected query-only flow. They were AUTV assumptions, not a verified
provider identity contract. The metadata URL is never fetched, so its path is not
a network allowlist boundary.

`SevenPlaybackIdentity.read` now extracts only the two required query fields from
the provider's `videoPlayer.videoUrl`. It ignores unrelated flags/repeated fields,
allows repeated identical required values, rejects conflicting required values,
and safely bounds/encodes the original values without a guessed reference-ID
character subset. The fixed Seven playback endpoint, TLS, bearer scoping and
redirect restrictions remain unchanged. No account/reference is manufactured;
metadata hosts/paths cannot become network destinations. No alternative JSON
layout or path-only Brightcove identity is invented if the required query fields
are absent. `videoPlayer.videoUrl` must be text, not an object coerced into text.

**The exact rejected owner value/shape remains unknown.** The agent has no owner
metadata payload and cannot reach Seven API hosts under its enforced network
policy. The new fixtures are source-derived regression cases (changed metadata
path, unrelated repeated query fields, trailing flag/ampersand and punctuation in
a reference), not falsely labelled captured owner responses. A complete observed
response-shape fixture can only be recorded after the value-free diagnostic
identifies that shape or the provider supplies a sanitized schema. The next
build distinguishes missing accountId, missing referenceId, absent query,
non-text videoUrl, conflicting fields and invalid values using fixed subcodes.
No private values are needed in chat or repository fixtures.

The [Brightcove fields reference](https://apis.support.brightcove.com/playback/references/playback-api-video-fields-reference.html)
describes reference IDs as account-unique values rather than AUTV's former narrow
regex. It is not a Seven API grant, and AUTV does not switch to Brightcove's
separate playback endpoint, use someone else's policy key or scrape licence data.

### Original television and advertising

The current authenticated Channel 7 mechanism cannot be classified yet: identity
failure prevents obtaining its descriptor. The old Yospace observation was from
IPTV AU's third-party route, not the owner's authenticated Seven session. A host
suffix is therefore not a valid permanent source-selection policy. The selector
now accepts original HTTPS provider-returned HLS in provider order, without a
Yospace restriction, ad-free ranking, origin/quality rewrite, query stripping,
commercial skipping, source substitution or ad-tag removal. Normal commercials,
local advertisements and original session parameters are intended behaviour.
No CDN account bearer is attached. Empty optional ad/protection containers are
not misclassified as an integration requirement; actual non-empty declarations
remain meaningful.

Non-empty separate advertising configuration reports `7-ADS` until its supported
session/SDK contract is established. Declared protected media reports `7-DRM`;
DASH remains outside the existing HLS implementation. Neither is worked around
by removing metadata or seeking an unprotected/ad-free substitute. If Seven
requires CSAI, Google DAI, a Yospace SDK/beacons or another lifecycle, establish
its actual returned config, client permission and consent requirements first.
The inspected source's `deliveryId=csai` request and its eventual HLS output do
not establish which of those applies to this owner. Nothing in this revision
claims that all commercial tracking obligations are already satisfied.

### Ad-transition research and Media3 evaluation

Re-read [IA #1071](https://github.com/xbmc/inputstream.adaptive/issues/1071),
[SlyGuy #305](https://github.com/matthuisman/slyguy.addons/issues/305) and
[#1193](https://github.com/matthuisman/slyguy.addons/issues/1193), including comments.
#1071 closed in December 2023 referring to merged
[IA PR #1431, head 545bd4b](https://github.com/xbmc/inputstream.adaptive/pull/1431):
its HLS update-interval bug could stop the refresh worker after a manifest update.
That is a Kodi/IA implementation fix, not a patch for AUTV's Media3. #305's history
also shows that disabling IA was not a universal cure; removing discontinuities
was discussed as a hack that could break timestamp/format handling. No such
workaround is adopted. #1193 remains open with 2026 commercial-transition failure
reports. Thus IPTV AU's historical success is not stable-current-playback proof.
No attached signed stream/private licence/session log is reused.

[Official Media3 HLS support](https://developer.android.com/media/media3/exoplayer/hls)
was reviewed against AUTV's HLS module **1.11.1**. It supports regular live HLS,
playlist reloads, TS/fMP4 and ID3; codec/device capability remains separate.
Actual Seven discontinuity sequences, A/V format changes, timed ad metadata,
local ad transitions and live-edge behaviour still require a real manifest and
Kogan/device testing. The existing HlsMediaSource retains default reload,
format/timestamp and transient-load handling; this revision makes no broad
buffering, codec, seek/live-edge or retry changes. A terminal error retains the
existing FAST recovery and explicit fresh-resolver retry rather than looping
around a failed commercial or stripping its playlist entry.

The documentation lists **live HLS interstitial support as incomplete**. Default
ExoPlayer ignores interstitials; HlsInterstitialsAdsLoader is a separate path.
Do not treat an interstitial-dependent live manifest as proven ordinary SSAI.
[ServerSideAdInsertionMediaSource](https://developer.android.com/reference/androidx/media3/exoplayer/source/ads/ServerSideAdInsertionMediaSource)
needs supplied ad playback states; merely wrapping HLS does not implement an
unknown broadcaster's ad contract. If Seven supplies supported Google DAI
configuration, use the documented
[Media3/IMA DAI extension architecture](https://developers.google.com/ad-manager/dynamic-ad-insertion/sdk/android/get-started/exoplayer-extension),
with provider-authorized session data and tracking; do not guess an asset key or
advertising endpoint. No IMA/DRM component is wired speculatively in this build.

### Diagnostics and acceptance

The existing typed stage enum is extended rather than adding a second framework:
`7-CATALOGUE`, `7-LINK`, `7-IDENTITY` (with value-free field subcodes),
`7-DESCRIPTOR`, `7-MANIFEST`, `7-DRM`, `7-ADS` and actual `7-PLAYER` failure.
Player-metadata lookup is separately labelled `7-PLAYER-METADATA`.
Only numeric Media3 error codes and fixed stage labels are surfaced after Seven
player failure, and they survive the existing FAST recovery. HTTP/resource or
manifest parsing errors are classified as manifest/load failures; this is not
proof the manifest loaded successfully. Other providers' error handling is
unchanged. No bearer, private URL, cookie, password, licence challenge, identity
value or raw personal response is logged or displayed.

Local fixture tests verify the revised query extraction, absent/conflicting field
codes, original media/session URL preservation across CDN hosts, non-empty ad/DRM
gates and Seven media-load error recovery. Fixtures remain distinct from actual
Seven output. The native synthetic clip has video only; no audible audio or
complete broadcaster commercial break is claimed. CI continues to run existing
Nine/SBS/FAST/guide/navigation/session-storage and APK-upgrade regressions.

**Final acceptance remains unmet:** no agent-accessible Australian Android TV,
authenticated Seven descriptor/manifest, authorised licence exchange or real
Seven video/audio/commercial-break result is available. The owner must retest
Seven with the new field-specific diagnostics; only after playback begins can a
complete commercial break, return to programming, continued playlist refresh,
CH+/CH−/Guide/Back, session recovery and physical Kogan behaviour be verified.
Sibling channels remain out of scope. This work stays on PR #79's existing branch;
#75 and its dependencies are still open, so no overlapping branch or automatic
merge/consolidation is introduced.

## Descriptor HTTP 400 investigation — 24 October 2026

Owner evidence now reaches `7-DESCRIPTOR (HTTP 400)` on main Seven, with the
account connected, activation complete and 53 discovered channels. This confirms
AUTV passed its local identity parser and attempted the descriptor request. It
**does not confirm Seven accepted the account/reference pair**. The actual 400
body is unavailable; its cause is not yet established. Do not substitute a
parameter hypothesis for a confirmed provider validation failure.

### Exact request reconstruction and package comparison

Current mirror directory listings still end at **slyguy.7plus 0.5.8** (blob
`6f58375d1a9f7845add32b208f64f3085b8a7c98`) and **plugin.video.au.freeview 0.20.5**
(blob `709cc31bad789be49b945de2bf75c98fc5c245e6`). Actual extracted package files,
not README examples, were re-read. The pinned package links and shared-helper
version earlier in this report remain the source references.

| Stage/field | SlyGuy standalone 0.5.8 | AUTV current request |
| --- | --- | --- |
| Identity | `plugin.py:play_channel` lines 435–445: `api.video_player(slug)`, parse `videoUrl` query; pass `accountId`, `referenceId` to `_play(..., live=True)` | `SevenPlusAdapter.resolve`: fresh catalogue row by `ChannelItem.channelId`, official `contentLink` → slug → `videoPlayer.videoUrl` → query identity; values preserved, query encoded once |
| Metadata | `api.py:video_player` lines 83–92: GET component `/player/live/{slug}`, platform, market, versions and `signedUp=True` | Same endpoint and parameter names; platform androidtv, platform-version 5.25.0.0, api-version 5.9.0.0 |
| Descriptor method/path/body | `api.py:play` lines 137–166: GET `https://videoservice.swm.digital/playback`, no body | Same GET/path; no body |
| Common parameters | `appId=7plus`, `deviceType=androidtv`, `platformType=tv`, deviceId, accountId, referenceId, marketId; videoType=live | These eight keys only; persistent AUTV device-local UUID, provider metadata IDs, current IP-detected market |
| Additional request fields | `pc=3181`, `advertid=null`, `deliveryId=csai`, fixed ozid, `sdkverification=true` | Omitted; do not copy a fixed postcode/tracking identity or assert SDK verification that AUTV has not performed |
| Capability fields | `cp.encryptionType=cenc`, `cp.drmSystems=widevine`, `cp.containerFormat=cmaf`, `cp.supportedCodecs=avc`, `cp.drmAuth=true` | Omitted; this proof has no established authorised Seven DRM/licence integration and must not assert it |
| Region | `api.py:_market_id`: configured region or IP lookup, caches 5 min; failure falls back to Sydney | IP lookup, same market used for catalogue/player/descriptor; fails instead of inventing a fallback region |
| Auth/session | `api.play` calls `_refresh_token`; saved bearer; shared session headers from `constants.py` | Existing session refreshed through catalogue flow as needed; saved bearer scoped only to descriptor; same distributed public API header configuration, not a permission grant |
| Headers | `constants.py:HEADERS`: Seven-branded Android/ExoPlayer user agent, public x-swm-apikey | Honest `AUTV/0.5 (Android TV)` user agent; Accept application/json, accept-language en, public x-swm-apikey; no cookies or redirects |
| Errors | `api.play` lines 174–185 parses JSON before rejecting; handles `location_verification_required`, `video_not_in_market`; other messages may be displayed by Kodi | Previously threw on non-2xx before reading the body, losing validation context. Revision inspects only bounded HTTP-400 descriptor responses and retains enum-only hints |

**IPTV AU differs at acquisition, not just stream format.** In 0.20.5
`resources/lib/plugin.py:get_channels` lines 99 onward, regional gzipped JSON
provides channel rows. `play` lines 76–96 reads the selected row's `mjh_master`,
headers/certificate and manifest hint, then configures MPD or live HLS. It does
not construct this Eight/Seven video-service descriptor query, exchange the
owner's Seven bearer, or establish which fields Seven requires for AUTV.
`script.module.slyguy 0.87.3:util.process_brightcove` consumes media sources after
resolution; it cannot repair HTTP 400 before a descriptor exists. No alternate
IPTV source or old signed link is adopted.

### What is established and what remains uncertain

- **Identity interpretation/format:** values come from the metadata query as in
  `play_channel`, with no guessed account or catalogue-ID substitution. Only
  local parsing succeeded; provider validity remains unconfirmed.
- **Missing field:** ten additional query fields exist in the reference.
  Source presence alone does not establish their requiredness or legitimate
  values for AUTV. No one-field trial or fabricated value is justified yet.
- **Client/platform:** common platform fields agree, user-agent differs honestly;
  whether Seven permits this third-party client or requires an approved SDK/API
  agreement remains unestablished. No approved-device identity is impersonated.
- **Auth/session:** connection survives this 400; that alone does not establish
  token audience/descriptor entitlement. Existing 401 session expiry remains
  separate. No extra token or private credential is requested.
- **Region/market:** AUTV propagates a single current IP market through the flow;
  no owner-specific comparison result or documented postcode obligation is known.
- **Advertising/session:** `deliveryId=csai`, SDK and related fields are a concrete
  difference, not proof CSAI is mandatory or optional. If required, obtain the
  authorised ad-session interface before adding it. Original broadcaster ads
  remain intact; no source-selection or Media3 change is made in this revision.

### Minimal diagnostic correction and privacy boundary

The proven implementation gap is discarded HTTP-400 error context. The request
itself is unchanged. `HttpSevenTransport` now reads at most **16,385 bytes** to
detect an over-limit body, parses at most 16 KB in memory and drops all raw
content. `SevenDescriptorRejection` retains only enum labels: the two exact error
codes explicitly handled by upstream `api.play`, and a fixed allowlist of query
field names found in bounded error/message/validation properties. Hints are
labelled **not a confirmed cause**; a field mention can describe an invalid
value, a missing field or incidental context. Arbitrary error codes, descriptions,
URLs, account/reference values and personal session data never reach UI/logs.
These are upstream-recognised codes, not a claim of a published Seven API spec.
Malformed, absent, oversized and unrecognised bodies produce fixed diagnostics;
no raw-body export or disk storage is offered. The existing exception/stage path
carries this safe context across the adapter boundary to the guide/settings.
No new failure framework or shared player behaviour is introduced.

Regression fixtures cover the confirmed **loss of HTTP-400 context**, not a
fabricated reproduction of the unknown owner body: request destination/method,
bearer scope, disabled redirects, known-code allowlisting, unknown-code/value
redaction, validation hints, malformed/oversized response bounds, and context plus
connected-session preservation through `SevenPlusAdapter.step`.

### Sequential gates and next owner test

| Gate | Evidence/status |
| --- | --- |
| 1. Identity accepted | AUTV local extraction passed; **Seven acceptance unconfirmed** |
| 2. Descriptor accepted | **Failed: HTTP 400**; exact reason awaits safe diagnostics |
| 3. Supported stream information | Not reached |
| 4. Manifest loads | Not reached |
| 5. Video and audio | Not reached |
| 6. Normal commercials | Not reached |
| 7. Return to programme | Not reached |

Install the PR's new test build using a compatible signing key; preserve the
existing connected account. Select main **Seven** once. Report only the displayed
stage, HTTP status and `Descriptor rejection` summary, including fixed code and
field hints if present. Do not send private URLs, tokens, account IDs or raw
responses. A known location/market rejection identifies the provider requirement;
a field hint narrows investigation but does not authorise inventing a value.
If the summary has no recognised code/hints, provider documentation or an
authorised developer support interface is still needed; do not claim the cause
is known. If descriptor resolution succeeds, report the next stage separately
before attempting the full video/audio/commercial-break acceptance sequence.

No Australian Android TV or owner session is available in this environment.
Current enforced network policy excludes Seven API hosts; no bypass is attempted.
No real-device result, successful descriptor or corrected request is claimed.
