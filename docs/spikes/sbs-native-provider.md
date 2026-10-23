# SBS On Demand native provider prototype

Status: experimental implementation; no agent-verified live SBS sign-in or playback.
This extends the shared QR work in PR #72. It does not enable ABC, 7plus or 10.

## Evidence and architecture decision

The [activation feasibility matrix](australian-provider-activation.md) was completed
before this connector. Official SBS help documents owner-initiated QR sign-in at
https://sbs.com.au/connect and manual entry of the TV's code. Inspected SlyGuy SBS
0.0.14, pinned at [76d5a37e885f3943e0ec8eede05ee6ccefe8acef](https://github.com/matthuisman/slyguy.addons/blob/76d5a37e885f3943e0ec8eede05ee6ccefe8acef/slyguy.sbs.ondemand/slyguy.sbs.ondemand-0.0.14.zip),
contains device link/poll/refresh, guide discovery and HLS/GoogleDAI descriptors.
The api.py, plugin.py, constants.py and addon.xml were inspected. No implementation
code is copied. Public client configuration is protocol evidence, not a provider
integration grant. Registered client/API-key permission and ad obligations remain
release gates; no permission request has been sent.

Decision: implement a small isolated consumer-flow prototype using truthful AUTV
identification, official manual-code activation and original returned HTTPS HLS.
Do not impersonate the add-on's NVIDIA/Apple TV identities, rewrite quality/hosts,
remove originpath/ad fields, construct alternate manifests, or bypass protection.
GoogleDAI-only responses have a distinct unsupported-ad-session result. This is a
known implementation boundary, not proof that native SBS playback is impossible.
An approved DAI session/SDK contract can be investigated separately.

## Implementation

SbsAdapter requests /device/link, polls /device/poll, refreshes /refresh and stores
access/refresh tokens only after a complete successful response. Token expiry is
read from the HTTPS provider response's JWT payload for refresh scheduling; this
is not independent JWT signature verification. Expiry gets a safety margin and a
conservative maximum refresh interval. Offline/rate-limit/outage errors preserve
the session; terminal refresh failure expires only SBS's session.

The challenge's expiresIn/interval are bounded. Pending challenges use the same
Keystore AES-GCM/AtomicFile storage implementation as Nine with distinct SBS
aliases/files. Recreating the adapter resumes the same unexpired code. The
controller keeps a challenge through lifecycle cancellation, retries transient
poll failures within its original deadline, and clears it on explicit cancel,
denial, expiry or approval. An unsupported response never becomes Connected.

QR output uses only https://sbs.com.au/connect; arbitrary qrCodeURI data is ignored
and no code-prefill query is invented. The manual userCode remains visible. No
password or browser cookie is requested. Account approval and catalogue/playback
success remain separate states. The existing QR component is shared unchanged.

ProviderAccountController is retained by PlaybackModel and owns SBS account jobs
and IO serialization, not a second player. Nine's existing account path remains
in place. Both providers' metadata merge into the runtime guide after the public
cache is loaded. Playback routes by ProviderChannelId.provider, so an SBS row
cannot accidentally resolve through Nine. SBS IDs use official mpxMediaID values;
its persisted internal guide numbers and favourite keys do not collide with Nine
or public FAST rows. Sign-out only cancels/removes that provider's routes, waits
for its blocking IO, then deletes its own credentials. Other accounts remain
connected. Late signed-out playback results cannot replace the current channel.

HttpSbsTransport permits only the three reviewed SBS API hosts and expected paths,
uses JSON and strict HTTPS, disables redirects, bounds response sizes and exposes
only safe failures/status numbers. The account bearer is restricted to SBS guide
and playback APIs, never the CDN. Public distributed-client API configuration
stays on SBS API hosts. AUTV identifies itself as AUTV Android TV. The persistent
random device ID is device-local in noBackupFilesDir, with targeting opt-outs in
the observed playback request; no tracking service or hardware advertising ID is
added.

Discovery requests the guide and deduplicates official channel media IDs. Each
tune re-fetches that catalogue, validates the chosen ID, then obtains a fresh
playback descriptor. It accepts the first original HTTPS HLS descriptor without
declared protection, retaining its exact quality filename and all query fields.
No account token is placed in media headers, private metadata cache, Cast or the
public guide. DRM, DAI-only, invalid/missing sources and removed channels fail
explicitly. The last working FAST path remains available. Cast remains unavailable
for account channels. No external-app playback substitute is added.

## Validation

JVM fixtures cover link/poll, pending restoration, URL rejection, token expiry and
refresh rotation, transient refresh retention, denial/expiry, media ID routing,
metadata-only discovery, unchanged original HLS URLs, DRM/DAI rejection and API
credential scoping. SBS QR tests check only the reviewed official bare URL.

Android fixtures cover the SBS Settings/QR flow, two SBS channel identities beside
Nine, native local-media tuning, sign-out isolation in both directions, late SBS
resolution after sign-out, isolated encrypted records/keys and Android JWT decoding.
Existing Nine, QR, player, lifecycle and actual in-place update tests remain in CI.
These use local media/fixtures. Names such as SBS NSW and SBS VICELAND in tests do
not mean those real channels were played. No real SBS account or live stream was
used by the agent. The environment lacks broadcaster API access/owner credentials.

## Owner-device acceptance

Use a permanently signed build after completing the existing signing setup. In
Settings select SBS On Demand, Connect, scan the QR or visit the official address,
then enter the shown code and sign in on SBS's site. Return to AUTV if using the
same phone. Verify Connected separately from whether the channel catalogue loads.
Try each returned channel; record only its displayed name and sanitized AUTV error,
never signed URLs, tokens, codes or raw account/API bodies in chat or GitHub.

Verify video/audio, ad delivery and normal stream quality, then restart/reopen and
perform an in-place update. Verify pending-code restoration, expiry/cancel/retry,
Australian access errors, offline refresh, favourites, guide navigation and sign-out.
Confirm signing out SBS leaves Nine intact and vice versa; public FAST must remain
usable after every failure. DAI-only or protected variants remain unavailable until
their permitted native path is implemented and verified. Physical remote/Cast
regression and provider permission are still release gates. Keep draft; no merge.

No public playlist, guide/EPG, existing FAST channel number or six-hour refresh
infrastructure changes. Permanent signing secrets are still a separate owner-side
setup; this connector does not resolve or bypass that missing configuration.
