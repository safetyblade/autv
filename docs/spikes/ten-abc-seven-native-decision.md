# 10, ABC iview and 7plus: native connector decision

Investigated 23 October 2026. Decision: **hold native connector enablement for
all three** pending the specific contracts below. This is not a conclusion that
native playback is impossible. No new account connection, QR challenge, channel
or playback success is claimed by this change.

## Scope and inspected baseline

The full `9now-seinfeld-native-connector.md` brief requires evidence and an
architecture decision before implementation, and stopping when legitimate
authentication/licence acquisition is unavailable. The same gates apply here.
This document is based on main at `671751e` and inspection of the shared QR/SBS
work at `77dc9c5` (draft PRs [72](https://github.com/safetyblade/autv/pull/72)
and [73](https://github.com/safetyblade/autv/pull/73), both still open when checked).
It does not depend on merging them to record this decision.

Inspected AUTV's `ProviderAdapter`, `DeviceActivation`, `ProviderPlaybackRequest`,
Nine and SBS adapters, encrypted session/pending stores, `ProviderAccountController`,
settings, activation QR destination policy, retained `PlaybackModel`, and
`core-player/.../AuTvPlayer.kt` plus its Gradle dependencies.

The current player accepts HLS only. Its provider overload explicitly requires
`APPLICATION_M3U8` and constructs `HlsMediaSource`; it does not pass DRM
configuration or use a Google DAI session. Adding an activation URL or changing a
MIME type would not establish the missing account/playback contract.

## Evidence refreshed for this request

The GitHub mirror's current package directories were checked again: the latest
listed 7plus package remains 0.5.8 and 10 remains 0.4.5. This establishes only the
latest version available in that mirror, not the current production protocol or
the author's inaccessible package index. Packages were read at immutable mirror
commit [76d5a37e885f3943e0ec8eede05ee6ccefe8acef](https://github.com/matthuisman/slyguy.addons/tree/76d5a37e885f3943e0ec8eede05ee6ccefe8acef).

ABC's repository head was checked again: it is still
[d57a5390dd232aebdc47c2aef9d7a3365f2d769b](https://github.com/aussieaddons/plugin.video.abc_iview/tree/d57a5390dd232aebdc47c2aef9d7a3365f2d769b),
dated 24 May 2023. Its `comm.py`, `live.py`, `play.py` and `config.py` were read.
Its age is material: this is not evidence of the current device-account API.

The existing [activation matrix](australian-provider-activation.md) links official
consumer help. Those pages establish consumer entry points, not an AUTV client
registration or API grant. Their contents were not live-revalidated in this
request. The enforced environment policy does not allow broadcaster API/help
hosts. No blocked host was bypassed. No broadcaster account, token, activation
challenge, live catalogue, manifest, licence exchange or video/audio was obtained.
No physical TV, phone QR scan or live stream was tested for these providers.

## Provider decisions

| Provider | Account and QR | Native playback gap | Required next evidence |
| --- | --- | --- | --- |
| 10 | Official address: `https://10.com.au/activate`. Inspected client loads config, generates a code, then validates it every five seconds up to the returned absolute expiry. No supported prefill verified. | Config/catalogue/playback GETs use `X-N10-SIG`, an HMAC computed using embedded application signing material. Live resolution also requires a Google DAI session. | An approved AUTV signing/client method; documented config and token schemas; an original supported DAI manifest plus ad/event lifecycle contract. |
| ABC iview | Android TV consumer address: `https://www.abc.net.au/linktv`. Official help also describes a distinct `https://www.abc.net.au/link` flow for specified newer TV families. No current AUTV challenge/poll/token contract is established. | Historic source signs stream authorization with an embedded secret, obtains cookies and includes optional playlist handling. Its DRM code is partly commented out. | Current permitted device-account API, client registration, polling/error/expiry and refresh schemas; current channel discovery and original playback/licence contract. |
| 7plus | Official address: `https://7plus.com.au/connect`. Device authorize and token polling are visible in source. The returned `verification_uri_complete` is evidence of a server-issued complete URL, not a general query contract. | Observed playback requests explicitly negotiate CENC/Widevine/CMAF and process Brightcove media. Location verification, market and advertising fields need a supported client contract. | Authorized AUTV client configuration and location rules; representative original playback descriptor with platform licence headers and ad requirements, followed by an owner-device proof. |

### 10: do not port application signing material

In [10 0.4.5](https://github.com/matthuisman/slyguy.addons/blob/76d5a37e885f3943e0ec8eede05ee6ccefe8acef/slyguy.10play/slyguy.10play-0.4.5.zip),
`api.py:generate_x_n10_sig` signs the timestamp and URL with an embedded key.
`_request` signs GETs, `_config` supplies official-device attributes, `device_code`
loads `authConfig.generateCode`, and `device_login` uses `authConfig.validateCode`.
The successful response contains `jwt.alternativeToken`, `refreshToken` and
`expiresIn`; a browser's confirmation alone is not a connected AUTV session.

`play` asks for playback using a different official device family, consumes
`X-DAI-AUTH`, and creates a Google session from an asset key. Its live path derives
a manifest name from the events URL. The plugin also has optional quality
rewriting. AUTV will not copy the key/device identity, derive manifest variants,
remove advertising or treat fixture JWTs as live evidence. The key's value and
upstream implementation are not added to this repository.

### ABC: historic stream authorization is not account linking

`comm.py:get_auth` uses HMAC and a packaged secret. `get_stream_program` obtains
stream authorization/cookies; `get_drm_auth` has JWT/DRM-header requests.
`play.py` has commented DASH/licence handling. None of these implements the
current consumer TV-code exchange, account refresh or pending challenge restore.
An unrelated public ABC feed would not satisfy the requested ABC account connector
and will not be inserted into the public playlist as a substitute.

### 7plus: platform Widevine is viable, but needs a valid descriptor

In [7plus 0.5.8](https://github.com/matthuisman/slyguy.addons/blob/76d5a37e885f3943e0ec8eede05ee6ccefe8acef/slyguy.7plus/slyguy.7plus-0.5.8.zip),
`api.py:device_code` POSTs to
`https://auth2.swm.digital/account/device/authorize`; `device_login` and
`_refresh_token` POST to `https://auth2.swm.digital/connect/token`.
Responses expose `device_code`, `user_code`, `interval`, `expires_in`, then
access/refresh tokens. Exact rejection/error shapes remain unverified.

`plugin.py:_get_live_channels` deduplicates channel IDs from `live-tv` content;
`play_channel` resolves an account/reference pair from player metadata.
`api.py:play` requests `https://videoservice.swm.digital/playback` with Widevine
capabilities and sends media to a shared Brightcove parser. That shared parser
was not re-inspected in this request, so this is not a complete licence analysis.
The source includes a fixed postcode and a Sydney fallback for failed market
lookup. AUTV must not adopt those fallbacks or disable location verification.
No production catalogue payload has been validated for stable identities.

Android MediaDrm/Media3 can perform legitimate Widevine playback without extracting
keys. That is the intended direction once a provider-issued licence URL, headers,
scope, expiry, entitlement and device requirements are established. The absence
of that contract here is a research blocker, not a claim that Widevine itself is
unsupported by Android or that all 7plus channels are encrypted.

## Smallest implementation once the gates are met

Implement 7plus first because its inspected device-code contract is the clearest.
Keep authentication separate from playback success. Reuse the retained SBS
account controller only after its dependency is reviewed, with distinct encrypted
session/pending aliases and files per provider. Enable a card only when its
adapter can request a real challenge; never show a static code or infer Connected
from opening a website. Initially encode the reviewed bare official HTTPS URL and
keep the manual code visible. Prefill needs a separately validated contract.

Add a typed, ephemeral playback protection configuration only when needed by the
first real descriptor. Validate original media and licence HTTPS destinations and
separate API bearer, CDN and licence request headers. Use Media3/platform DRM,
including DASH support if the returned descriptor requires it, with no custom
key extraction, security-level downgrade, quality rewrite or generic proxy.
Keep media credentials out of guide caches, Cast and logs. Integrate the permitted
ad/session lifecycle rather than choosing an unadvertised alternate stream.

For each provider, prove one returned channel before adding its catalogue. Cover
approval/denial/expiry, pending restoration, refresh rotation, offline retries,
provider-scoped sign-out, cancellation of late tunes, unchanged FAST fallback,
licence/ad failures and the same-player lifecycle. Fixtures establish application
behaviour; owner-device video/audio, ads, remote focus and in-place update establish
the actual provider proof. No password or token should be pasted into an issue or
chat; obtain protocol documentation or sanitized schemas instead.

## Current result and alternative

No production adapter, connection button, DRM path or public channel was added.
No Android build is necessary for this documentation-only change; no new APK is
claimed. The SBS APK remains the development build in PR #73 and is not evidence
for 10, ABC or 7plus. Permanent distribution signing remains a separate owner-side
setup; disposable debug artifacts are not a replacement for it.

Recommended immediate viewing alternative while these contracts are unresolved:
use each broadcaster's official Android TV app separately. This is an interim
recommendation, not a replacement AUTV launcher feature or native playback success.
No external-app launcher was implemented. Keep the native connector goal and the
three concrete gates above for the next implementation step. No merge requested.
