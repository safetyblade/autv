# Implemented provider media capability

SDKs: Media3 **1.11.1**, Google IMA DAI **3.39.0** (the Media3 extension's dependency),
OkHttp **4.12.0**, JDK library desugaring **2.1.5**. No broadcaster SDK identity or
signing key is included. This implements PR #85 deliverable A, not an ABC/Ten
entitlement. No automatic merge.

## Code and lifecycle

- ProviderPlaybackRequest now carries optional ephemeral ProviderMediaOptions:
  original MIME, provider-issued Widevine licence URI/headers, explicit IMA live
  asset/network/auth configuration, origin-scoped media headers, expiry and an
  HLS-interstitial opt-in. Sensitive objects redact toString; no guide, number,
  favourites, Cast or public playlist record stores them.
- AuTvPlayer uses actual Media3 DefaultMediaSourceFactory for HLS/DASH, the platform
  Widevine DrmSessionManager, the actual IMA DAI AdsLoader/source/URI builder and
  HlsInterstitialsAdsLoader. The official IMA setAuthToken API receives a provider
  token; AUTV never manufactures DAI manifests or licence URLs.
- Existing production callers remain HLS-only by default. Extended routes require
  constructor opt-in; the debug Media Lab opts in. No ABC/Ten/Seven UI is enabled.
- HTTP credential policy runs on **every OkHttp network hop**, including redirects.
  Media-origin and licence-origin factories are separate. Legacy media headers
  apply only to their original HTTPS origin. No automatic credential inheritance
  into a new CDN or licence origin; declared extra origins must be explicit.
- New tuning first stops/detaches old media, releases IMA/interstitial sessions and
  cancels expiry. Pause/resume retains the same session/player. Activity recreation
  reattaches the AdViewProvider through the retained player model. No prior-account
  AdsLoader state is reused for a new tune. Clear/release tears down SDK resources.
- Expired input is rejected before replacing playback. Active expiry stops its
  media and invokes PlaybackModel's existing provider-to-FAST recovery. Terminal
  renderer errors retain the existing fallback; a user retry resolves a fresh
  session rather than endlessly replaying an ad or stale signed URL.
- Original server-stitched content, discontinuities and ad URLs are untouched.
  No custom playlist filter, ad removal, quality rewriting or fallback around DRM.

## Executable validation

JVM tests cover old/new feature gates, input expiry, licence/header validation,
redaction, header scope and a real local HTTPS redirect with separate TLS origins.
No TLS verification is weakened in production (test certificate trust is local
to the unit test's client).

PublicMediaCapabilityTest uses the real Media3/IMA SDK and a visible PlayerView:

| Check | Fixture/source and required observations |
| --- | --- |
| HLS | Apple's sample: video first frame, audio position advancing |
| DASH | AndroidX clear Tears of Steel: video and audio rendering |
| Widevine | AndroidX encrypted Tears/licence sample: keys and A/V, or explicitly recorded device DRM limitation and safe FAST recovery |
| IMA live HLS | Google's live BBB asset: A/V, live timeline, release to local FAST |
| IMA live DASH | AndroidX's every-minute-ad sample: A/V, live, complete SDK ad period and continuing playback |
| SSAI-style discontinuities | Explicit synthetic local playlist, original discontinuities retained |
| Interstitial | Explicit synthetic **VOD** playlist through HlsInterstitialsAdsLoader and content return |
| Expiry | Ephemeral fixture deadline, same player returns to FAST fixture |

Official sample sources:
[AndroidX media.exolist.json](https://github.com/androidx/media/blob/release/demos/main/src/main/assets/media.exolist.json),
[Google DAI live samples](https://developers.google.com/ad-manager/dynamic-ad-insertion/streams),
[Media3 HLS support](https://developer.android.com/media/media3/exoplayer/hls),
[official IMA integration](https://developers.google.com/ad-manager/dynamic-ad-insertion/sdk/android/get-started/exoplayer-extension).
Sample IDs are public test identifiers, never broadcaster credentials. Samples
can be retired or unavailable; failed remote tests are reported, not replaced by
synthetic success. Renderer progress does not prove audible physical-TV output.

The published HLS support matrix limits live interstitial support. Wiring the
loader and testing VOD does not claim support for all live X-ASSET-LIST or
interstitial variants. Ordinary server-stitched live HLS is a separate path.
Widevine availability/provisioning on an emulator does not establish entitlement
or compatibility on a Kogan TV; no L3/security downgrade is attempted.

Local: Android debug and test APKs compile; JVM suite results accompany the PR.
Remote: provider-media.yml runs actual public playback on a Google APIs emulator;
scripts/test-provider-media.sh exports only fixed sample counters/error codes.
Existing provider workflow independently exercises Nine/QR/storage/FAST routing.
No physical Australian TV is attached. Current runtime egress does not permit
sample media hosts, so live tests run on the authorised GitHub CI executor rather
than bypassing that policy here. See CI for actual pass/failure, not this plan.

## Owner QA

Debug APK includes an additional **AUTV Media Lab** launcher. Pick HLS, DASH,
Widevine, Live IMA HLS and Live IMA DASH. Observe actual sound/picture; leave DAI
running through a complete normal break and return. Test pause/resume, Back,
recreation and FAST fixture return. Main AUTV retains its feature gate. Debug
signing may differ from distribution signing; do not uninstall and lose sessions.

## Precedent boundaries

[EasyPlayTV](https://scottrix.github.io/EasyPlayTV-docs/index.html) separates provider
resolution from Kodi playback and depends on InputStream Adaptive for DASH/DRM;
its server/PVR/MySQL architecture is not added to native AUTV.
[OpenTV](https://github.com/opentvproject/opentv) is a native player/TV UX reference,
not a source of Australian account authorisation. Existing ABC iView+ and actual
SlyGuy package traces remain in provider reports. Neither precedent supplies an
AUTV signing credential or proves Australian audio/video in this build.
