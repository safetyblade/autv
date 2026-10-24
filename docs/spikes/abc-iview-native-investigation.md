# ABC iview native connector: current investigation and decision

Investigated 24 October 2026 for AUTV. Priority: ABC first, Network 10 next;
7plus stays parked at its existing HTTP-400 diagnostic. Read all three documents
in [PR #80](https://github.com/safetyblade/autv/pull/80), beginning with
[the ABC work package](https://github.com/safetyblade/autv/blob/7f610a1c1aeb46db6faf89794007ae07b4f80c54/docs/spikes/abc-iview-codex-next.md),
and the full [PR #74 decision](https://github.com/safetyblade/autv/blob/7e2499cc98b04a9172275e3bdeea51b212a14053/docs/spikes/ten-abc-seven-native-decision.md).

**Decision: hold runtime enablement; the account protocol evidence has improved,
but an authorised AUTV playback route is not established.** This is not a claim
that ABC is technically unplayable. The follow-up implements network-free ABC protocol components and
tests; no production adapter, sessions, public playlist or provider settings are
changed. The next concrete dependency is an ABC-approved integration contract, not a guessed URL or a ported signing secret.

## New evidence versus the previous decision

| Question | Current evidence | What it establishes |
| --- | --- | --- |
| Is login always necessary? | [Official no-login FAQ](https://help.abc.net.au/hc/en-us/articles/8551373302543-Can-I-watch-programs-on-ABC-iview-without-logging-in), updated 30 January 2025 | Website live broadcast viewing has a no-login exception. It does not grant native third-party Android TV API access or remove stream authorisation. |
| Does Android TV support linking? | [Official device list](https://help.abc.net.au/hc/en-us/articles/8551748804495-What-devices-and-TVs-can-log-in-to-ABC-iview) and [linking instructions](https://help.abc.net.au/hc/en-us/articles/8551749872655-How-do-I-link-my-TV-to-my-ABC-Account) | Android/Google TV consumer linking uses `https://www.abc.net.au/linktv`. The separate newer-smart-TV flow uses `/link`, eight letters and a 20-minute code. Do not apply that lifetime or URL to every device family. |
| Is there newer source than 2023? | [ABC iView+ 2.4.1](https://github.com/JCalvi/plugin.video.abc_iviewjc/tree/1ce1e18fd30858296adf5e4f5ea667a4217474fa), commit 1ce1e18, 12 August 2026 | A maintained unofficial SlyGuy-based implementation exposes connected-TV link/status and token flow. This supersedes the earlier absence of account-protocol evidence, not the need to validate/authorise it. |
| Is it approved for AUTV? | [ABC access guidelines](https://help.abc.net.au/hc/en-us/articles/8632184741263-ABC-iview-Access-Guidelines), updated October 2025; [iview terms](https://help.abc.net.au/hc/en-us/articles/8632119753231-ABC-iview-Terms-of-Use) | ABC describes partner selection and an ABC-approved branded environment, content control and regional restrictions. Consumer access and an unofficial add-on do not establish AUTV approval. |

The guideline framework includes technical/user-experience compliance, free or
lowest-tier access, ABC control, branding and Australian availability (with a
News simulcast exception). These are specific integration requirements to resolve
with ABC. The terms also reserve playback to iview and prohibit interference with
protection. No broad legal conclusion is inferred beyond the lack of an approved
AUTV integration demonstrated here.

## Exact source trace: newer integration

All references below are to **ABC iView+ 2.4.1, commit 1ce1e18fd30858296adf5e4f5ea667a4217474fa**.
Its [addon.xml](https://github.com/JCalvi/plugin.video.abc_iviewjc/blob/1ce1e18fd30858296adf5e4f5ea667a4217474fa/addon.xml)
requires SlyGuy >=0.86.64 and explicitly describes an unofficial add-on. Code
comments claiming a captured production flow are author evidence, not an AUTV
capture or provider-published API specification.

### Linking, polling and session continuity

[api.py](https://github.com/JCalvi/plugin.video.abc_iviewjc/blob/1ce1e18fd30858296adf5e4f5ea667a4217474fa/resources/lib/api.py):

- `device_code` (116–209): POST `https://mylogin-api.abc.net.au/latest/ctv/link`
  with JSON productId, persistent deviceId and deviceName. The implementation
  uses a packaged profile API key and browser origin headers. Their presence is
  not an AUTV registration; neither key values nor an official-app identity are
  copied here. It accepts direct/data-wrapped response forms, code/linkCode,
  returned deviceId, optional requestId, URL and expiry forms.
- `_get_link_status` (211–229): GET `/latest/ctv/status`, productId/deviceId and
  optional requestId; 204/404 are pending. `device_login` (231–262) treats pending
  states as incomplete and requires UID before obtaining an account token.
- `_get_seesaw_token` (264–299): GET `https://api.seesaw.abc.net.au/v2/token`
  with UID and source=iview, deliberately without a stale bearer. It reads token
  and expiry variants. UID/deviceId are private session material; never QR data.
- `_refresh_token` (301–315) renews from UID; `_seesaw_request` retries once after
  401. This differs from Nine/SBS refresh-token rotation and must not be forced
  into their contract. `new_session` attempts restoration from device-link status.
- `logout` (674 onward) attempts `/latest/ctv/unlink` and clears local account
  state. Cancellation, expiry/rejection variants and server invalidation need
  current schema verification before a production adapter is enabled.

The upstream defaults missing expiry to 600 seconds and interval to 3 seconds;
these are **client defaults**, not demonstrated provider requirements. Its remote
QR image is not a design to copy. AUTV would encode only a reviewed official HTTPS
activation page on-device, show the manual code and use code-prefill only with an
explicit provider contract. No valid ABC challenge was requested in this session.

### Catalogue, identity and region

`get_livestreams` (363–368) GETs `https://api.iview.abc.net.au/v2/home`, finds the
live-channel collection by title and calls `get_collection` (`/v2/collection/{id}`).
[plugin.py:live_streams](https://github.com/JCalvi/plugin.video.abc_iviewjc/blob/1ce1e18fd30858296adf5e4f5ea667a4217474fa/resources/lib/plugin.py#L436)
filters type=livestream and uses `_links.self.href`, houseNumber and provider
showTitle/title. `play` follows that program metadata via `api.get_program`.
Those metadata fields, not display names or public guide numbers, are the
identity candidates. Their stability and current Melbourne/Victoria mapping have
not been validated. No live production catalogue or schedule was retrieved here;
no companion feeds or guessed region-specific IDs are hard-coded.

ABC's official access page names ABC TV, Entertains, Family, Kids and NEWS as its
broadcast offering. That is a product list, not evidence that a particular
returned live feed/houseNumber exists or is playable. The first intended proof
remains the provider's main Victorian ABC TV feed, before catalogue expansion.

### Original playback, protection and the blocking step

`plugin.py:play` (450–482) gets program metadata and checks playable, then
`_resolve_stream` (485–502) chooses an original HLS entry from the embedded
program/livestream playlist. **With houseNumber present it calls `api.get_auth`.**
`api.py:get_auth` (659–665) signs `/auth/hls/sign` parameters using an embedded
HMAC secret and an Android-tablet context, then uses the returned auth on HLS.
Its exception path returns the unsigned base source. AUTV will copy neither the
secret/client context nor that silent unsigned fallback. A successful user login
would not satisfy this separate media-authorisation step.

`get_drm_token` (667–672) has JWT/DRM-token requests, but this does not establish
that the current main live channel is protected or provide a validated AUTV
licence contract. The live plugin configures SlyGuy HLS; it does not prove a
DASH/Widevine path is necessary or functioning for this owner. No licence URL,
headers, manifest, audio/video or normal boundary transition was tested.

The historical [Aussie Add-ons head d57a539, May 2023](https://github.com/aussieaddons/plugin.video.abc_iview/tree/d57a5390dd232aebdc47c2aef9d7a3365f2d769b)
was also re-read (`comm.py`, `live.py`, `play.py`, `config.py`): it has signed
stream authorisation/cookies and no comparable current TV-account exchange.
The SlyGuy package-mirror root currently has no standalone ABC package; IPTV AU
0.20.5 uses regional third-party metadata/master routing, not this account API.
Neither is evidence of an approved replacement playback contract.

## AUTV architecture once access is established

Reviewed the working branch's NineNowAdapter, SbsAdapter, ProviderAccountController,
EncryptedProviderSessionStore, ProviderActivationScreen/ActivationQr, PlaybackModel
and AuTvPlayer. Nine/SBS provide useful separation of authentication, discovery
and ephemeral media resolution; their endpoints, token fields and session rules
are not reusable ABC protocol facts.

1. Add an ABC-only adapter/transport with explicit API/credential destination
   scopes. Use a truthful AUTV device name and provider-approved client config.
2. Reuse retained activation UI, TV focus and locally generated QR. Extend the
   encrypted ABC-specific pending record only if requestId/returned deviceId are
   required; current DeviceActivation cannot safely stand in for all those fields.
   Use a new ABC alias/file; never migrate or delete Nine/SBS/Seven sessions.
3. Keep UID/device-link restoration and token renewal private to ABC. Separate
   approved anonymous-live access from Connected; do not invent account tokens
   for no-login feeds. Sign-out must be explicit and provider-scoped, offline-safe.
4. Resolve fresh provider metadata at tune time, preserve complete original URLs
   and content/ad metadata. Pass only scoped media headers; no API bearer to a CDN.
   Do not copy upstream's unsigned fallback or add a public playlist substitution.
5. Assess actual descriptor/manifest before adding any ABC-specific protection or
   ad integration. Existing Media3 provider path is HLS-only; do not broadly change
   it based on commented historical DRM code. Preserve the original ABC feed and
   its programme junctions/promos; never add or remove advertising.

## Concrete unblock request (draft only; not sent)

Ask ABC's platform-access team, via [official contact](https://help.abc.net.au/hc/en-us/requests/new),
for an AUTV live-simulcast integration assessment under the access guidelines:

- Whether main Victorian ABC TV may use anonymous native playback, or must link an
  account; approved API/client registration and allowed branding/UI conditions.
- Supported link/status/unlink, expiry/poll/rejection and account-renewal schemas;
  a registered AUTV client configuration rather than another client's key.
- Approved live catalogue, Victorian feed/schedule identity and stable identifier
  fields; supported stream-authorisation method that does not embed a copied key.
- Original HLS/DASH and any licence, session, geolocation, consent/measurement and
  content-junction requirements, plus a permitted test device/account procedure.

No passwords, bearer tokens, private URLs, key values or session captures are
needed in a public issue/PR. This is a concrete information request, not a claim
that support has granted permission. No message was sent on the owner's behalf.

## Validation and acceptance status

| Gate | Result |
| --- | --- |
| Supported AUTV client/interface | Not established; runtime enablement held |
| Account linked in AUTV | Not attempted; newer upstream flow documented only |
| Current channel/region discovery | Not verified |
| Authorised descriptor/manifest | Not obtained |
| Actual main ABC TV video and audio | Not tested |
| Programme junctions/continued live reloads | Not tested |
| Remote/Guide/Back/CH/Favourites/session recovery | Acceptance plan only |

Once approved, test one Victorian main ABC feed on an Australian Android TV/Kogan:
link/pending restore/expiry/denial, restart and in-place update, offline retry,
scoped sign-out, original manifest/video/audio and sustained playback through
programme junctions; then Guide/Back/CH/Favourites and Nine/SBS/FAST regressions.
Synthetic fixtures cannot satisfy the media gates.

The original investigation was documentation-only. The follow-up below adds
compiled protocol components and reports its own build/test evidence; existing
provider successes are not relabelled as ABC results. The enforced runtime
network policy excludes ABC API/CDN hosts; no denial was bypassed. Public help and
GitHub sources were read through their available tools. No physical TV is attached.
Seven's implementation, current diagnostic, normal ads, all sessions and FAST
remain unchanged. Network 10 proceeds in its own independent workstream.

## Engineering follow-up: executable protocol boundary

`android/core-data/.../provider/AbcLiveProtocol.kt` now implements the source-derived
live collection, livestream entry and programme-resolution components. It has no
network transport, copied key, account impersonation or production UI registration.

- Extract the single live collection and provider houseNumber/programme metadata;
  restrict programme requests to the official API route, reject conflicting entries,
  wrong identities and unavailable/non-live records. HouseNumber remains a candidate
  identity until its live-feed stability is verified; no Favourites keys are added.
- Select an original HLS variant without rewriting its path or session parameters.
  Require an explicit MediaAuthoriser and propagate failures; there is no unsigned
  fallback. Separate ad/DRM declarations cannot be silently treated as clear media.
- Produce the existing ephemeral ProviderPlaybackRequest only after the authoriser
  succeeds. Media carries no account headers. The future registered ABC transport
  must supply this authoriser, using the approved auth/cookie/licence contract.

This is **partial engineering**, not an enabled provider or proof that copied
fixture JSON is production output. The four new tests use explicitly synthetic,
source-derived records: original signed-session preservation, authorisation failure
without fallback, identity/host/protection rejection and ambiguous/missing live
metadata. Android debug APK build and **87 current-main JVM tests pass locally**, including
the four new ABC tests.
No real ABC link, catalogue, manifest, audio/video or TV QA has occurred.

### Comparison against successful AUTV connectors

Reviewed Nine in main and the working SBS/Seven branch without modifying either.
The owner reports real Nine/SBS playback; that evidence is distinct from local tests.

| Boundary | Nine | SBS | ABC follow-up |
| --- | --- | --- | --- |
| Login/QR | Provider code/token; local QR | Device link/poll; local QR | Newer link/status protocol documented; no AUTV-approved client wired |
| Catalogue/identity | Provider channel/live-event IDs | mpxMediaID | Source-derived livestream houseNumber/self link parsed; regional stability unverified |
| Renewal | Refresh-token flow | Refresh-token/JWT expiry | UID/Seesaw differs; no fabricated refresh token |
| Descriptor | Provider-returned HLS | Original returned HLS | Programme/HLS parsing implemented; authorised media exchange still external |
| Media/DRM | Supported HLS only | Supported HLS; separate DAI/DRM gated | HLS request after explicit authoriser; no invented DASH/licence support |
| Advertising | Original stream retained | Original stream retained | Original URL retained; no unsigned fallback or ad rewriting |
| Media3 | Existing native HLS lifecycle | Same provider-scoped player | Existing request type; no player modification |
| Evidence | Owner-confirmed working | Owner-confirmed working | Four protocol tests/build only; no actual playback |

### Precise remaining dependency

An ABC-approved AUTV client must provide current media authorisation for the
provider-returned Victorian feed without the add-on's embedded HMAC secret. The
independently read access guidelines describe the approval route; the maintained
source demonstrates the signing dependency but is not a grant. Request the native
anonymous-live alternative explicitly, since the website login exemption alone
does not describe it. Also confirm the current device-link client configuration
and stable Victorian feed identity. The unsent request above specifies those items.
No permission request is being substituted for parser/resolver work that can be
implemented without it; that work is now present. UI linking remains unenabled
until real provider-issued challenges and the authorised network interface exist.
