# Channel 7 playback recovery: evidence and decision

24 October 2026, Australia/Sydney. Continuation of PR #75 at `2217ecc`.
Status: investigate then implement a single-channel experimental original-HLS path;
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

## Architecture decision before implementation

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
